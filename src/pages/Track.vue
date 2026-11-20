<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import {
  AlertTriangle,
  ArrowLeft,
  Check,
  ChevronRight,
  Navigation,
  Pause,
  Play,
  Route as RouteIcon,
  Square
} from 'lucide-vue-next'
import { createRoute, errorMessage, getProfile, getRoutes } from '../utils/api'
import { SPORTS, calcCalories } from '../utils/sport'
import {
  distanceParts,
  formatDistance,
  formatDuration,
  formatPace,
  formatSpeed,
  limitPoints
} from '../utils/geo'
import { toast, confirmDialog } from '../utils/ui'
import { useTracker } from '../composables/useTracker'
import { wgs84ToGcj02 } from '../utils/coord'
import RouteMap from '../components/RouteMap.vue'
import RouteThumb from '../components/RouteThumb.vue'

const route = useRoute()
const router = useRouter()

const {
  status,
  sport,
  points,
  distance,
  duration,
  gpsState,
  error: trackerError,
  draft,
  clientId,
  startedAt,
  endedAt,
  pointLimitReached,
  start,
  pause,
  resume,
  finish,
  reset,
  resumeDraft,
  discardDraft
} = useTracker()

const sportOptions = SPORTS.filter((item) => ['跑步', '快走', '骑行'].includes(item.name))
const queryType = typeof route.query.type === 'string' ? route.query.type : ''
const selectedSport = ref(
  sportOptions.some((item) => item.name === queryType) ? queryType : '跑步'
)

const profile = ref(null)
const recent = ref([])
const loadingRoutes = ref(true)
const mapMissing = ref(false)
const saving = ref(false)
const saveError = ref('')
const previewCenter = ref(null)
const locateState = ref('idle')

const locateLabel = computed(() => ({
  idle: 'GPS 轨迹',
  locating: '正在定位…',
  ready: '已定位到你附近',
  failed: 'GPS 轨迹'
}[locateState.value] || 'GPS 轨迹'))

const isLive = computed(() => ['tracking', 'paused'].includes(status.value))
const isFinished = computed(() => status.value === 'finished')
const currentSport = computed(
  () => sportOptions.find((item) => item.name === sport.value) || sportOptions[0]
)
const distanceView = computed(() => distanceParts(distance.value))
const weight = computed(() => Number(profile.value?.weight) || 60)
const gender = computed(() => profile.value?.gender || 'secret')
const estimatedCalories = computed(() => calcCalories(
  currentSport.value.met,
  weight.value,
  Math.max(1, Math.round(duration.value / 60)),
  gender.value
))
const paceLabel = computed(() => (sport.value === '骑行' ? '时速' : '配速'))
const paceValue = computed(() => (
  sport.value === '骑行'
    ? `${formatSpeed(duration.value, distance.value)} km/h`
    : formatPace(duration.value, distance.value)
))
const gpsLabel = computed(() => ({
  waiting: '定位中',
  good: 'GPS 良好',
  fair: 'GPS 一般',
  weak: 'GPS 较弱'
}[gpsState.value] || 'GPS'))
const canSaveSummary = computed(() => (
  points.value.length >= 2 && distance.value >= 50 && duration.value >= 60
))
const draftText = computed(() => {
  if (!draft.value) return ''
  return `${draft.value.sport || '跑步'} · ${formatDistance(draft.value.distance || 0)} · ${formatDuration(draft.value.duration || 0)}`
})

function parseApiDate(value) {
  if (value == null) return null
  if (value instanceof Date) return value
  if (typeof value === 'number') return new Date(value)
  if (/[zZ]|[+-]\d{2}:\d{2}$/.test(value)) return new Date(value)
  return new Date(`${value}+08:00`)
}

function formatWhen(value) {
  const date = parseApiDate(value)
  if (!date || Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'Asia/Shanghai'
  }).format(date)
}

function sportMeta(name) {
  return SPORTS.find((item) => item.name === name)
}

function routePace(item) {
  return item.type === '骑行'
    ? `${formatSpeed(item.durationSec, item.distanceM)} km/h`
    : formatPace(item.durationSec, item.distanceM)
}

async function loadProfile() {
  try {
    profile.value = await getProfile()
  } catch {
    /* 热量估算使用默认体重 */
  }
}

function locatePreview() {
  if (!('geolocation' in navigator)) {
    locateState.value = 'failed'
    return
  }
  locateState.value = 'locating'
  navigator.geolocation.getCurrentPosition(
    (position) => {
      const gcj = wgs84ToGcj02(position.coords.longitude, position.coords.latitude)
      previewCenter.value = [gcj.lng, gcj.lat]
      locateState.value = 'ready'
    },
    () => {
      locateState.value = 'failed'
    },
    { enableHighAccuracy: true, maximumAge: 30000, timeout: 10000 }
  )
}

async function loadRecent() {
  loadingRoutes.value = true
  try {
    recent.value = await getRoutes(8)
  } catch {
    recent.value = []
  } finally {
    loadingRoutes.value = false
  }
}

async function startRun() {
  if (draft.value) {
    try {
      await confirmDialog({
        title: '有未完成记录',
        message: '开始新的记录会丢弃上次草稿。',
        confirmText: '开始新的',
        cancelText: '取消',
        danger: true
      })
    } catch {
      return
    }
    discardDraft()
  }
  if (!start(selectedSport.value)) {
    toast('当前已有记录在进行')
  }
}

async function askFinish() {
  try {
    await confirmDialog({
      title: '结束本次记录',
      message: `已记录 ${formatDistance(distance.value)}，结束后可以保存并生成打卡。`,
      confirmText: '结束',
      cancelText: '继续记录'
    })
  } catch {
    return
  }
  finish()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function discardRun() {
  try {
    await confirmDialog({
      title: '放弃本次记录',
      message: '轨迹和打卡都不会保存，确定放弃吗？',
      confirmText: '放弃',
      cancelText: '取消',
      danger: true
    })
  } catch {
    return
  }
  reset()
}

async function saveRun(overwrite = false) {
  if (!canSaveSummary.value) {
    toast('轨迹太短或不足 1 分钟，暂时无法保存')
    return
  }
  saving.value = true
  saveError.value = ''
  try {
    const payload = {
      type: sport.value,
      durationSec: duration.value,
      startedAt: new Date(startedAt.value || Date.now()).toISOString(),
      endedAt: new Date(endedAt.value || Date.now()).toISOString(),
      clientId: clientId.value,
      source: 'web',
      overwrite,
      points: limitPoints(points.value, 5000)
    }
    const result = await createRoute(payload)
    const id = result?.route?.id
    toast('已保存并生成打卡')
    reset()
    router.replace(id ? `/track/${id}` : '/')
  } catch (error) {
    if (error?.response?.status === 409 && !overwrite) {
      try {
        await confirmDialog({
          title: '今日已打卡',
          message: `今天已经记录过「${sport.value}」，要用本次轨迹覆盖吗？`,
          confirmText: '覆盖',
          cancelText: '取消'
        })
        saving.value = false
        await saveRun(true)
        return
      } catch {
        saving.value = false
        return
      }
    }
    saveError.value = errorMessage(error, '保存轨迹失败')
    toast(saveError.value)
  } finally {
    saving.value = false
  }
}

function openRoute(id) {
  router.push(`/track/${id}`)
}

function resumeDraftRun() {
  if (!resumeDraft()) toast('草稿无法恢复')
}

function discardDraftRun() {
  discardDraft()
}

function handleBeforeUnload(event) {
  if (!isLive.value) return
  event.preventDefault()
  event.returnValue = ''
}

onMounted(() => {
  locatePreview()
  loadProfile()
  loadRecent()
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

onBeforeRouteLeave(async (to, from, next) => {
  if (!isLive.value) {
    next()
    return
  }
  try {
    await confirmDialog({
      title: '还在记录中',
      message: '离开会保留草稿，下次进入可以继续。',
      confirmText: '离开',
      cancelText: '继续记录'
    })
    next()
  } catch {
    next(false)
  }
})
</script>

<template>
  <div class="track-page" :class="{ 'track-page--live': isLive }">
    <template v-if="isLive">
      <div class="track-live">
        <RouteMap
          :points="points"
          mode="live"
          :follow="true"
          :center="previewCenter"
          class="track-live__map"
          @missing-key="mapMissing = true"
        />

        <div class="track-live__top">
          <button type="button" class="track-round" aria-label="返回" @click="router.back()">
            <ArrowLeft :size="19" />
          </button>
          <div class="track-live__title">
            <strong>{{ sport }}</strong>
            <span>{{ status === 'paused' ? '已暂停' : '正在记录' }}</span>
          </div>
          <span class="gps-chip" :class="'gps-chip--' + gpsState">{{ gpsLabel }}</span>
        </div>

        <div class="track-live__bottom">
          <div class="track-distance">
            <strong>{{ distanceView.value }}</strong>
            <span>{{ distanceView.unit }}</span>
          </div>
          <div class="track-metrics">
            <div>
              <span>时长</span>
              <strong>{{ formatDuration(duration) }}</strong>
            </div>
            <div>
              <span>{{ paceLabel }}</span>
              <strong>{{ paceValue }}</strong>
            </div>
            <div>
              <span>热量</span>
              <strong>{{ estimatedCalories }}<small>千卡</small></strong>
            </div>
          </div>

          <p v-if="trackerError" class="track-error">{{ trackerError }}</p>
          <p v-if="pointLimitReached" class="track-tip">轨迹点已达上限，后续点位不再记录。</p>

          <div class="track-actions">
            <button
              type="button"
              class="track-pause"
              @click="status === 'paused' ? resume() : pause()"
            >
              <component :is="status === 'paused' ? Play : Pause" :size="18" />
              {{ status === 'paused' ? '继续' : '暂停' }}
            </button>
            <button type="button" class="track-finish" @click="askFinish">
              <Square :size="16" />
              结束
            </button>
          </div>
        </div>
      </div>
    </template>

    <template v-else-if="isFinished">
      <header class="topbar">
        <button type="button" class="topbar__btn" aria-label="返回首页" @click="router.replace('/')">
          <ArrowLeft :size="20" />
        </button>
        <h1>本次轨迹</h1>
      </header>

      <section class="track-summary-map">
        <RouteMap
          :points="points"
          mode="detail"
          :follow="false"
          :center="previewCenter"
          @missing-key="mapMissing = true"
        />
      </section>

      <section class="card track-summary">
        <div class="track-summary__head">
          <span class="track-summary__icon">
            <component :is="currentSport.icon" :size="20" />
          </span>
          <div>
            <h2>{{ sport }}</h2>
            <p>{{ formatWhen(startedAt) }}</p>
          </div>
        </div>

        <div class="track-summary__stats">
          <div>
            <span>距离</span>
            <strong>{{ distanceView.value }}<small>{{ distanceView.unit }}</small></strong>
          </div>
          <div>
            <span>时长</span>
            <strong>{{ formatDuration(duration) }}</strong>
          </div>
          <div>
            <span>热量</span>
            <strong>{{ estimatedCalories }}<small>千卡</small></strong>
          </div>
        </div>

        <p v-if="!canSaveSummary" class="track-tip track-tip--warn">
          轨迹至少 50 米且时长至少 1 分钟才能生成打卡。
        </p>
        <p v-if="saveError" class="form-error">{{ saveError }}</p>

        <button
          type="button"
          class="btn-primary track-save"
          :disabled="saving || !canSaveSummary"
          @click="saveRun(false)"
        >
          {{ saving ? '保存中…' : '保存并打卡' }}
        </button>
        <button type="button" class="track-discard" :disabled="saving" @click="discardRun">
          放弃这次记录
        </button>
      </section>
    </template>

    <template v-else>
      <header class="topbar">
        <button type="button" class="topbar__btn" aria-label="返回" @click="router.back()">
          <ArrowLeft :size="20" />
        </button>
        <h1>运动轨迹</h1>
      </header>

      <section class="track-hero">
        <RouteMap
          :points="[]"
          mode="detail"
          :follow="false"
          :center="previewCenter"
          class="track-hero__map"
          @missing-key="mapMissing = true"
          @ready="mapMissing = false"
        />
        <span class="track-hero__badge">
          <Navigation :size="13" />
          {{ locateLabel }}
        </span>
      </section>

      <div v-if="mapMissing" class="track-notice">
        <AlertTriangle :size="16" />
        <span>
          地图未配置：在 <code>.env</code> 填写 <code>VITE_AMAP_JS_KEY</code> 和
          <code>VITE_AMAP_SECURITY_CODE</code> 后重启。
        </span>
      </div>

      <div v-if="locateState === 'failed'" class="track-notice">
        <AlertTriangle :size="16" />
        <span>
          未能获取当前位置：请允许浏览器定位权限；手机用局域网 HTTP 地址访问时，浏览器会禁用定位，需要 HTTPS。
        </span>
      </div>

      <div class="section-title">
        <h2>选择运动</h2>
        <span>结束后自动打卡</span>
      </div>

      <div class="track-sports">
        <button
          v-for="item in sportOptions"
          :key="item.name"
          type="button"
          class="track-sport"
          :class="{ active: selectedSport === item.name }"
          :aria-pressed="selectedSport === item.name"
          @click="selectedSport = item.name"
        >
          <span class="track-sport__icon">
            <component :is="item.icon" :size="18" />
          </span>
          <strong>{{ item.name }}</strong>
          <small>MET {{ item.met.toFixed(1) }}</small>
          <span v-if="selectedSport === item.name" class="track-sport__check">
            <Check :size="11" />
          </span>
        </button>
      </div>

      <button type="button" class="btn-primary track-start" @click="startRun">
        <Navigation :size="18" />
        开始记录
      </button>
      <p class="track-tip">
        页面保持在前台时记录轨迹；锁屏或切后台可能暂停定位。
      </p>

      <section v-if="draft" class="draft-card">
        <div>
          <strong>上次记录未完成</strong>
          <span>{{ draftText }}</span>
        </div>
        <div class="draft-actions">
          <button type="button" @click="discardDraftRun">丢弃</button>
          <button type="button" class="draft-primary" @click="resumeDraftRun">继续</button>
        </div>
      </section>

      <div class="section-title">
        <h2>最近轨迹</h2>
        <span>{{ recent.length }} 条</span>
      </div>

      <div v-if="loadingRoutes" class="card loading-card">
        <div class="loading-line"><span></span></div>
      </div>
      <div v-else-if="recent.length" class="route-list">
        <button
          v-for="item in recent"
          :key="item.id"
          type="button"
          class="route-item"
          @click="openRoute(item.id)"
        >
          <span
            class="route-item__icon"
            :class="{ 'route-item__icon--map': item.preview && item.preview.length >= 2 }"
          >
            <RouteThumb v-if="item.preview && item.preview.length >= 2" :points="item.preview" />
            <component v-else :is="sportMeta(item.type)?.icon || RouteIcon" :size="18" />
          </span>
          <span class="route-item__main">
            <strong>{{ item.type }} · {{ formatDistance(item.distanceM) }}</strong>
            <small>{{ formatWhen(item.startedAt) }} · {{ formatDuration(item.durationSec) }} · {{ routePace(item) }}</small>
          </span>
          <span class="route-item__cal">{{ item.calories }}<small>千卡</small></span>
          <ChevronRight :size="16" class="chev" />
        </button>
      </div>
      <div v-else class="card empty-routes">
        <RouteIcon :size="24" />
        <strong>还没有轨迹</strong>
        <span>开始一次跑步、快走或骑行吧</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.track-page {
  min-height: 100vh;
  padding: 0 20px calc(28px + env(safe-area-inset-bottom));
  background: var(--bg);
}

.track-page--live {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 50%;
  z-index: 120;
  width: 100%;
  max-width: 480px;
  padding: 0;
  transform: translateX(-50%);
  background: #0f172a;
  overflow: hidden;
}

.track-live {
  position: absolute;
  inset: 0;
}

.track-live__map {
  position: absolute;
  inset: 0;
}

.track-live__top {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 4;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: calc(14px + env(safe-area-inset-top)) 16px 14px;
  background: linear-gradient(to bottom, rgba(2, 8, 23, 0.5), transparent);
}

.track-round {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  border: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.94);
  color: #0f172a;
  box-shadow: 0 8px 20px rgba(2, 8, 23, 0.16);
}

.track-live__title {
  display: flex;
  flex: 1;
  flex-direction: column;
  color: #fff;
  min-width: 0;
}

.track-live__title strong {
  font-size: 14px;
  line-height: 18px;
}

.track-live__title span {
  font-size: 11px;
  line-height: 15px;
  opacity: 0.82;
}

.gps-chip {
  flex: 0 0 auto;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.94);
  padding: 7px 10px;
  color: #16a34a;
  font-size: 11px;
  font-weight: 700;
}

.gps-chip--fair {
  color: #d97706;
}

.gps-chip--weak,
.gps-chip--waiting {
  color: #64748b;
}

.track-live__bottom {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 4;
  padding: 18px 20px calc(18px + env(safe-area-inset-bottom));
  border-radius: 26px 26px 0 0;
  background: #fff;
  box-shadow: 0 -12px 40px rgba(2, 8, 23, 0.16);
}

.track-distance {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.track-distance strong {
  color: #0f172a;
  font-size: 48px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.04em;
}

.track-distance span {
  color: #64748b;
  font-size: 16px;
  font-weight: 600;
}

.track-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-top: 14px;
}

.track-metrics > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.track-metrics span {
  color: #94a3b8;
  font-size: 11px;
}

.track-metrics strong {
  color: #0f172a;
  font-size: 18px;
  line-height: 22px;
}

.track-metrics small {
  margin-left: 2px;
  color: #94a3b8;
  font-size: 10px;
  font-weight: 500;
}

.track-actions {
  display: grid;
  grid-template-columns: 1fr 1.4fr;
  gap: 10px;
  margin-top: 16px;
}

.track-pause,
.track-finish {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 52px;
  border-radius: 16px;
  font-size: 15px;
  font-weight: 600;
}

.track-pause {
  border: 1px solid #dbe3f0;
  background: #f8fafc;
  color: #334155;
}

.track-finish {
  border: 0;
  background: linear-gradient(135deg, #2f6bff, #5b8cff);
  color: #fff;
  box-shadow: 0 12px 24px rgba(47, 107, 255, 0.28);
}

.track-error {
  margin: 10px 0 0;
  border-radius: 10px;
  background: #fef2f2;
  padding: 8px 10px;
  color: #b91c1c;
  font-size: 11px;
  line-height: 16px;
}

.track-hero {
  position: relative;
  height: 220px;
  margin-top: 8px;
  border-radius: 24px;
  overflow: hidden;
  background: #dbeafe;
  box-shadow: 0 14px 30px rgba(47, 107, 255, 0.12);
}

.track-hero__map {
  height: 100%;
}

.track-hero__badge {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 3;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  padding: 6px 10px;
  color: var(--blue);
  font-size: 11px;
  font-weight: 600;
  backdrop-filter: blur(8px);
}

.track-notice {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 12px;
  border: 1px solid #fed7aa;
  border-radius: 14px;
  background: #fff7ed;
  padding: 10px 12px;
  color: #9a3412;
  font-size: 11px;
  line-height: 17px;
}

.track-notice code {
  border-radius: 4px;
  background: rgba(154, 52, 18, 0.08);
  padding: 1px 3px;
  font-size: 10px;
}

.track-sports {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.track-sport {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--surface);
  padding: 14px 12px;
  text-align: left;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.track-sport:active {
  transform: scale(0.98);
}

.track-sport.active {
  border-color: rgba(47, 107, 255, 0.5);
  background: linear-gradient(160deg, #eef4ff, #fff);
  box-shadow: 0 8px 20px rgba(47, 107, 255, 0.12);
}

.track-sport__icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  margin-bottom: 2px;
  border-radius: 12px;
  background: rgba(47, 107, 255, 0.08);
  color: var(--blue);
}

.track-sport strong {
  color: var(--text);
  font-size: 14px;
}

.track-sport small {
  color: var(--muted);
  font-size: 10px;
}

.track-sport__check {
  position: absolute;
  top: 10px;
  right: 10px;
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--blue);
  color: #fff;
}

.track-start {
  width: 100%;
  margin-top: 16px;
}

.track-tip {
  margin: 10px 4px 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 17px;
}

.track-tip--warn {
  margin: 10px 0 0;
  color: #b45309;
}

.draft-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  border: 1px solid rgba(47, 107, 255, 0.18);
  border-radius: 18px;
  background: linear-gradient(135deg, #eef4ff, #fff);
  padding: 13px 14px;
}

.draft-card > div:first-child {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.draft-card strong {
  color: var(--text);
  font-size: 13px;
}

.draft-card span {
  color: var(--muted);
  font-size: 11px;
}

.draft-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

.draft-actions button {
  border: 0;
  border-radius: 999px;
  background: transparent;
  padding: 7px 10px;
  color: var(--muted);
  font-size: 11px;
  font-weight: 600;
}

.draft-actions .draft-primary {
  background: var(--blue);
  color: #fff;
}

.route-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.route-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--surface);
  padding: 12px;
  text-align: left;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.04);
}

.route-item:active {
  transform: scale(0.99);
}

.route-item__icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  border-radius: 13px;
  background: rgba(47, 107, 255, 0.08);
  color: var(--blue);
}

.route-item__icon--map {
  padding: 2px;
  background: linear-gradient(150deg, #eef4ff, #e3edff);
  color: #3468e8;
}

.route-item__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.route-item__main strong {
  overflow: hidden;
  color: var(--text);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.route-item__main small {
  overflow: hidden;
  color: var(--muted);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.route-item__cal {
  flex: 0 0 auto;
  color: var(--orange);
  font-size: 13px;
  font-weight: 700;
}

.route-item__cal small {
  margin-left: 1px;
  color: var(--muted);
  font-size: 9px;
  font-weight: 500;
}

.chev {
  flex: 0 0 auto;
  color: #cbd5e1;
}

.empty-routes {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  padding: 24px 18px;
  color: var(--muted);
  text-align: center;
}

.empty-routes strong {
  color: var(--text);
  font-size: 13px;
}

.empty-routes span {
  font-size: 11px;
}

.track-summary-map {
  height: 280px;
  margin-top: 8px;
  border-radius: 24px;
  overflow: hidden;
  background: #dbeafe;
  box-shadow: 0 14px 30px rgba(47, 107, 255, 0.12);
}

.track-summary {
  margin-top: 14px;
  padding: 18px;
}

.track-summary__head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.track-summary__icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: rgba(47, 107, 255, 0.08);
  color: var(--blue);
}

.track-summary__head h2 {
  margin: 0;
  color: var(--text);
  font-size: 18px;
}

.track-summary__head p {
  margin: 2px 0 0;
  color: var(--muted);
  font-size: 11px;
}

.track-summary__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-top: 16px;
}

.track-summary__stats > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
  border-radius: 14px;
  background: #f8fafc;
  padding: 10px;
}

.track-summary__stats span {
  color: var(--muted);
  font-size: 10px;
}

.track-summary__stats strong {
  color: var(--text);
  font-size: 17px;
}

.track-summary__stats small {
  margin-left: 2px;
  color: var(--muted);
  font-size: 9px;
  font-weight: 500;
}

.track-save {
  width: 100%;
  margin-top: 16px;
}

.track-discard {
  width: 100%;
  margin-top: 8px;
  border: 0;
  background: transparent;
  padding: 10px;
  color: var(--muted);
  font-size: 12px;
}

.loading-card {
  padding: 18px;
}

.loading-line {
  height: 14px;
  border-radius: 999px;
  background: #eef2f7;
  overflow: hidden;
}

.loading-line span {
  display: block;
  width: 42%;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #e2e8f0, #f8fafc, #e2e8f0);
  animation: trackLoading 1.2s ease-in-out infinite;
}

@keyframes trackLoading {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(260%); }
}
</style>
