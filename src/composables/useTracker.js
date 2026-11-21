import { computed, onMounted, onUnmounted, ref } from 'vue'
import { haversineMeters, shouldAcceptPoint, MAX_POINTS } from '../utils/geo'
import { wgs84ToGcj02 } from '../utils/coord'
import {
  hasNativeLocation,
  nativeIsTracking,
  nativePendingPoints,
  nativeStartTracking,
  nativeStopTracking
} from '../utils/native'

const DRAFT_KEY = 'dlm_route_draft'
const DRAFT_TTL_MS = 24 * 60 * 60 * 1000
const PERSIST_INTERVAL_MS = 2000

// 原生壳通过 evaluateJavascript 回调这两个全局函数，先留好入口
let positionSink = null
let permissionSink = null

if (typeof window !== 'undefined') {
  window.__donglemeGeo = (point) => {
    if (positionSink) positionSink(point)
  }
  window.__donglemeGeoPermission = (granted) => {
    if (permissionSink) permissionSink(Boolean(granted))
  }
}

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
  let usingNative = false
  let lastNativeTs = 0
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
    if (watchId !== null || usingNative) return
    if (hasNativeLocation()) {
      startNativeWatch()
      return
    }
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

  function startNativeWatch() {
    usingNative = true
    lastNativeTs = 0
    positionSink = feedNativePoint
    permissionSink = handleNativePermission
    const result = nativeStartTracking()
    if (result === 'permission_requested') {
      error.value = '正在申请定位权限…'
    } else if (result !== 'ok') {
      usingNative = false
      error.value = '无法启动定位服务'
      return
    }
    syncNativeBacklog()
  }

  /** 后台期间的点由原生缓存，回到前台一次性补上。 */
  function syncNativeBacklog() {
    if (!usingNative) return
    const backlog = nativePendingPoints()
    if (backlog.length === 0) return
    backlog.forEach(feedNativePoint)
  }

  function feedNativePoint(point) {
    if (!point) return
    const lat = Number(point.lat)
    const lng = Number(point.lng)
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) return
    const ts = Number(point.ts) || Date.now()
    if (ts <= lastNativeTs) return
    lastNativeTs = ts
    applyPosition(lat, lng, Number(point.acc), ts)
  }

  function handleNativePermission(granted) {
    if (granted) {
      if (error.value) error.value = ''
      syncNativeBacklog()
      return
    }
    error.value = '定位权限被拒绝，请在系统设置中允许定位'
    if (status.value === 'tracking') {
      status.value = 'paused'
      pausedAt = Date.now()
      stopWatch()
      releaseWakeLock()
      persistDraft(true)
    }
  }

  function onPosition(position) {
    applyPosition(
      position.coords.latitude,
      position.coords.longitude,
      position.coords.accuracy,
      Date.now()
    )
  }

  function applyPosition(latitude, longitude, acc, timestamp) {
    const gcj = wgs84ToGcj02(longitude, latitude)
    const lat = gcj.lat
    const lng = gcj.lng
    current.value = { lat, lng, acc, timestamp }
    if (error.value) error.value = ''

    if (status.value !== 'tracking') return

    const last = points.value[points.value.length - 1]
    const t = Math.max(0, Math.floor((timestamp - startedAt.value - pausedMs) / 1000))
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

  /** 主动取一次高精度定位，用于「刷新定位」按钮；记录中会顺带补一个点。 */
  function refreshPosition(timeout = 10000) {
    return new Promise((resolve, reject) => {
      if (!('geolocation' in navigator)) {
        reject(new Error('unsupported'))
        return
      }
      let settled = false
      const timer = setTimeout(() => {
        if (settled) return
        settled = true
        reject(new Error('timeout'))
      }, timeout)
      navigator.geolocation.getCurrentPosition(
        (position) => {
          if (settled) return
          settled = true
          clearTimeout(timer)
          const acc = Number(position.coords.accuracy)
          applyPosition(
            position.coords.latitude,
            position.coords.longitude,
            acc,
            Date.now()
          )
          resolve({ lat: current.value?.lat, lng: current.value?.lng, acc })
        },
        (geoError) => {
          if (settled) return
          settled = true
          clearTimeout(timer)
          reject(geoError)
        },
        { enableHighAccuracy: true, maximumAge: 0, timeout }
      )
    })
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
    if (usingNative) {
      nativeStopTracking()
      usingNative = false
      positionSink = null
      permissionSink = null
    }
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
    if (document.visibilityState !== 'visible') return

    // 通知栏里点了「结束记录」，页面这边要跟着停
    if (usingNative && status.value === 'tracking' && !nativeIsTracking()) {
      status.value = 'paused'
      pausedAt = Date.now()
      stopWatch()
      releaseWakeLock()
      persistDraft(true)
      return
    }

    syncNativeBacklog()
    if (status.value === 'tracking') {
      requestWakeLock()
    }
  }

  onMounted(() => {
    loadDraft()
    document.addEventListener('visibilitychange', handleVisibility)
    // 上次异常退出可能留下空转的前台服务，这里收掉
    if (status.value === 'idle' && hasNativeLocation() && nativeIsTracking()) {
      nativeStopTracking()
    }
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
    refreshPosition,
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
