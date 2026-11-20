import { computed, onMounted, onUnmounted, ref } from 'vue'
import { haversineMeters, shouldAcceptPoint, MAX_POINTS } from '../utils/geo'
import { wgs84ToGcj02 } from '../utils/coord'

const DRAFT_KEY = 'dlm_route_draft'
const DRAFT_TTL_MS = 24 * 60 * 60 * 1000
const PERSIST_INTERVAL_MS = 2000

function newClientId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `route-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
}

function readStoredDraft() {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) return null
    const data = JSON.parse(raw)
    if (!data
      || !Array.isArray(data.points)
      || data.points.length < 2
      || !Number.isFinite(data.savedAt)
      || Date.now() - data.savedAt > DRAFT_TTL_MS) {
      localStorage.removeItem(DRAFT_KEY)
      return null
    }
    return data
  } catch {
    localStorage.removeItem(DRAFT_KEY)
    return null
  }
}

export function useTracker() {
  const status = ref('idle')
  const sport = ref('跑步')
  const points = ref([])
  const current = ref(null)
  const distance = ref(0)
  const duration = ref(0)
  const error = ref('')
  const draft = ref(null)
  const clientId = ref('')
  const startedAt = ref(null)
  const endedAt = ref(null)
  const pointLimitReached = ref(false)

  let watchId = null
  let timer = null
  let pausedAt = 0
  let pausedMs = 0
  let wakeLock = null
  let lastPersistAt = 0

  const accuracy = computed(() => (
    Number.isFinite(current.value?.acc) ? Math.round(current.value.acc) : null
  ))

  const gpsState = computed(() => {
    if (!current.value) return 'waiting'
    const acc = current.value.acc
    if (!Number.isFinite(acc)) return 'fair'
    if (acc <= 15) return 'good'
    if (acc <= 35) return 'fair'
    return 'weak'
  })

  const canSave = computed(() => points.value.length >= 2 && distance.value >= 50)

  function startWatch() {
    if (watchId !== null) return
    if (!('geolocation' in navigator)) {
      error.value = '当前浏览器不支持定位'
      return
    }
    watchId = navigator.geolocation.watchPosition(onPosition, onGeoError, {
      enableHighAccuracy: true,
      maximumAge: 1000,
      timeout: 15000
    })
  }

  function onPosition(position) {
    const coords = position.coords
    const acc = coords.accuracy
    const gcj = wgs84ToGcj02(coords.longitude, coords.latitude)
    const lat = gcj.lat
    const lng = gcj.lng
    current.value = { lat, lng, acc, timestamp: Date.now() }
    if (error.value) error.value = ''

    if (status.value !== 'tracking') return

    const last = points.value[points.value.length - 1]
    const t = Math.max(0, Math.floor((Date.now() - startedAt.value - pausedMs) / 1000))
    if (last) {
      const gap = haversineMeters(last.lat, last.lng, lat, lng)
      if (t - last.t < 3 && gap < 15) return
    }

    const point = { lat, lng, t, acc: Math.round(acc) }
    if (!shouldAcceptPoint(point, last, sport.value)) return
    if (points.value.length >= MAX_POINTS) {
      pointLimitReached.value = true
      return
    }

    const gap = last ? haversineMeters(last.lat, last.lng, lat, lng) : 0
    points.value = [...points.value, point]
    distance.value += gap
    duration.value = Math.max(duration.value, t)
    persistDraft()
  }

  function onGeoError(geoError) {
    if (geoError.code === 1) {
      error.value = '定位权限被拒绝，请在浏览器或系统设置中允许定位'
      if (status.value === 'tracking') {
        status.value = 'paused'
        pausedAt = Date.now()
        stopWatch()
        releaseWakeLock()
        persistDraft(true)
      }
      return
    }
    if (geoError.code === 2) {
      error.value = '暂时无法获取定位，请到空旷处重试'
      return
    }
    error.value = '定位超时，正在等待下一次更新'
  }

  function startTimer() {
    stopTimer()
    timer = setInterval(() => {
      if (status.value !== 'tracking') return
      duration.value = Math.max(
        0,
        Math.floor((Date.now() - startedAt.value - pausedMs) / 1000)
      )
    }, 1000)
  }

  function stopWatch() {
    if (watchId !== null) {
      navigator.geolocation.clearWatch(watchId)
      watchId = null
    }
  }

  function stopTimer() {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  async function requestWakeLock() {
    if (!('wakeLock' in navigator) || wakeLock) return
    try {
      wakeLock = await navigator.wakeLock.request('screen')
    } catch {
      wakeLock = null
    }
  }

  function releaseWakeLock() {
    if (!wakeLock) return
    try {
      wakeLock.release()
    } catch {
      /* ignore */
    }
    wakeLock = null
  }

  function teardown() {
    stopWatch()
    stopTimer()
    releaseWakeLock()
  }

  function persistDraft(force = false) {
    if (!['tracking', 'paused', 'finished'].includes(status.value)) return
    if (!startedAt.value || points.value.length === 0) return
    const now = Date.now()
    if (!force && now - lastPersistAt < PERSIST_INTERVAL_MS) return
    lastPersistAt = now
    try {
      localStorage.setItem(DRAFT_KEY, JSON.stringify({
        savedAt: now,
        status: status.value,
        sport: sport.value,
        points: points.value,
        distance: distance.value,
        duration: duration.value,
        startedAt: startedAt.value,
        endedAt: endedAt.value,
        clientId: clientId.value
      }))
    } catch {
      /* 存储空间不足时忽略草稿 */
    }
  }

  function removeDraft() {
    try {
      localStorage.removeItem(DRAFT_KEY)
    } catch {
      /* ignore */
    }
  }

  function loadDraft() {
    const data = readStoredDraft()
    draft.value = data
    return data
  }

  function start(selectedSport) {
    if (status.value === 'tracking' || status.value === 'paused') return false
    teardown()
    removeDraft()
    draft.value = null
    points.value = []
    distance.value = 0
    duration.value = 0
    current.value = null
    error.value = ''
    pointLimitReached.value = false
    sport.value = selectedSport || '跑步'
    clientId.value = newClientId()
    startedAt.value = Date.now()
    endedAt.value = null
    pausedAt = 0
    pausedMs = 0
    status.value = 'tracking'
    startTimer()
    startWatch()
    requestWakeLock()
    persistDraft(true)
    return true
  }

  function pause() {
    if (status.value !== 'tracking') return
    status.value = 'paused'
    pausedAt = Date.now()
    stopWatch()
    releaseWakeLock()
    persistDraft(true)
  }

  function resume() {
    if (status.value !== 'paused') return
    if (pausedAt) {
      pausedMs += Date.now() - pausedAt
      pausedAt = 0
    }
    status.value = 'tracking'
    startWatch()
    requestWakeLock()
    persistDraft(true)
  }

  function finish() {
    if (!['tracking', 'paused'].includes(status.value)) return null
    if (pausedAt) {
      pausedMs += Date.now() - pausedAt
      pausedAt = 0
    }
    endedAt.value = Date.now()
    duration.value = Math.max(
      0,
      Math.floor((endedAt.value - startedAt.value - pausedMs) / 1000)
    )
    status.value = 'finished'
    teardown()
    persistDraft(true)
    return {
      sport: sport.value,
      points: [...points.value],
      distance: distance.value,
      duration: duration.value,
      startedAt: startedAt.value,
      endedAt: endedAt.value,
      clientId: clientId.value
    }
  }

  function reset() {
    teardown()
    removeDraft()
    status.value = 'idle'
    sport.value = '跑步'
    points.value = []
    current.value = null
    distance.value = 0
    duration.value = 0
    error.value = ''
    draft.value = null
    clientId.value = ''
    startedAt.value = null
    endedAt.value = null
    pointLimitReached.value = false
    pausedAt = 0
    pausedMs = 0
  }

  function resumeDraft() {
    const data = draft.value
    if (!data || !Array.isArray(data.points) || data.points.length < 2) return false
    if (status.value === 'tracking' || status.value === 'paused') return false
    teardown()
    sport.value = data.sport || '跑步'
    points.value = data.points
    distance.value = Number(data.distance) || 0
    duration.value = Number(data.duration) || 0
    clientId.value = data.clientId || newClientId()
    startedAt.value = Date.now() - duration.value * 1000
    endedAt.value = null
    pausedAt = 0
    pausedMs = 0
    error.value = ''
    pointLimitReached.value = false
    status.value = 'tracking'
    draft.value = null
    removeDraft()
    startTimer()
    startWatch()
    requestWakeLock()
    persistDraft(true)
    return true
  }

  function discardDraft() {
    removeDraft()
    draft.value = null
  }

  function handleVisibility() {
    if (document.visibilityState === 'visible' && status.value === 'tracking') {
      requestWakeLock()
    }
  }

  onMounted(() => {
    loadDraft()
    document.addEventListener('visibilitychange', handleVisibility)
  })

  onUnmounted(() => {
    document.removeEventListener('visibilitychange', handleVisibility)
    if (['tracking', 'paused'].includes(status.value)) persistDraft(true)
    teardown()
  })

  return {
    status,
    sport,
    points,
    current,
    distance,
    duration,
    accuracy,
    gpsState,
    error,
    draft,
    clientId,
    startedAt,
    endedAt,
    pointLimitReached,
    canSave,
    loadDraft,
    start,
    pause,
    resume,
    finish,
    reset,
    resumeDraft,
    discardDraft
  }
}
