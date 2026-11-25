<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import {
  AlertTriangle,
  ArrowLeft,
  Check,
  ChevronRight,
  History,
  Music,
  Navigation,
  Pause,
  Play,
  Route as RouteIcon,
  Square
} from 'lucide-vue-next'
import { createRoute, errorMessage, getProfile } from '../utils/api'
import { SPORTS, calcCalories } from '../utils/sport'
import {
  distanceParts,
  formatDistance,
  formatDuration,
  formatPace,
  formatSpeed,
  limitPoints
} from '../utils/geo'
import { formatWhen, routePace, sportMeta } from '../utils/routeFormat'
import { toast, confirmDialog } from '../utils/ui'
import { useTracker } from '../composables/useTracker'
import { useRouteHistory } from '../composables/useRouteHistory'
import { useLocateProbe } from '../composables/useLocateProbe'
import RouteMap from '../components/RouteMap.vue'
import RouteThumb from '../components/RouteThumb.vue'
import StartCountdown from '../components/StartCountdown.vue'
import MusicSheet from '../components/MusicSheet.vue'
import { loadMusicSource, openMusicApp } from '../utils/musicApp'

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
  discardDraft,
  refreshPosition
} = useTracker()

const { recent, loading: loadingRoutes, load: loadRecent } = useRouteHistory(8)
const {
  state: locateState,
  center: previewCenter,
  label: locateLabel,
  probe: locatePreview
} = useLocateProbe()

const sportOptions = SPORTS.filter((item) => ['跑步', '快走', '骑行'].includes(item.name))
const queryType = typeof route.query.type === 'string' ? route.query.type : ''
const selectedSport = ref(
  sportOptions.some((item) => item.name === queryType) ? queryType : '跑步'
)

const profile = ref(null)
const mapMissing = ref(false)
const saving = ref(false)
const saveError = ref('')
const mapRef = ref(null)
const refreshing = ref(false)
const refreshNote = ref('')
let refreshNoteTimer = null

/* Keep 式启动页：音源、弹层与开始倒计时 */
const musicSource = ref(loadMusicSource())
const musicOpen = ref(false)
const recentOpen = ref(false)
const countdownActive = ref(false)
const musicLabel = computed(() => ({
  netease: '网易云音乐',
  qq: 'QQ 音乐'
}[musicSource.value] || '未选择音源'))

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
const gpsWaitingLong = computed(() => (
  status.value === 'tracking'
  && points.value.length === 0
  && duration.value >= 12
))
const gpsText = computed(() => {
  if (refreshing.value) return '正在刷新定位…'
  if (refreshNote.value) return refreshNote.value
  if (gpsWaitingLong.value) return 'GPS 待定位 · 请到户外'
  return gpsLabel.value
})
const gpsChipState = computed(() => {
  if (refreshNote.value.startsWith('已刷新 · GPS 良好')) return 'good'
  if (refreshNote.value.startsWith('已刷新 · GPS 一般')) return 'fair'
  if (refreshing.value || refreshNote.value || gpsWaitingLong.value) return 'waiting'
  return gpsState.value
})
const canSaveSummary = computed(() => (
  points.value.length >= 2 && distance.value >= 50 && duration.value >= 60
))
const draftText = computed(() => {
  if (!draft.value) return ''
  return `${draft.value.sport || '跑步'} · ${formatDistance(draft.value.distance || 0)} · ${formatDuration(draft.value.duration || 0)}`
})

async function loadProfile() {
  try {
    profile.value = await getProfile()
  } catch {
    /* 热量估算使用默认体重 */
  }
}

function setRefreshNote(text) {
  refreshNote.value = text
  clearTimeout(refreshNoteTimer)
  refreshNoteTimer = setTimeout(() => {
    refreshNote.value = ''
  }, 5000)
}

async function refreshLocation() {
  if (refreshing.value) return
  refreshing.value = true
  mapRef.value?.recenter()
  try {
    const result = isLive.value
      ? await refreshPosition(10000)
      : await locatePreview()
    if (!result) {
      toast('暂未获取到新的定位，请到开阔处重试')
      return
    }
    mapRef.value?.recenter()
    const acc = Number(result.acc)
    if (Number.isFinite(acc) && acc > 50) {
      setRefreshNote('室内定位 · 精度较低')
      toast(`已获取位置，室内精度约 ±${Math.round(acc)}m，到户外会自动变准`)
    } else if (Number.isFinite(acc) && acc > 0 && acc <= 15) {
      setRefreshNote('已刷新 · GPS 良好')
    } else {
      setRefreshNote('已刷新 · GPS 一般')
    }
  } catch {
    toast('暂未获取到新的定位，请到开阔处重试')
  } finally {
    refreshing.value = false
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
  /* 倒计时这几秒正好用来预热 GPS，减少开头的漂移点 */
  countdownActive.value = true
  refreshLocation()
}

function handleCountdownDone() {
  countdownActive.value = false
  if (!start(selectedSport.value)) {
    toast('当前已有记录在进行')
  }
}

function onMusicTap() {
  // 已选音源时也先打开选择面板，方便随时更换；选中后才会唤起对应音乐 App
  musicOpen.value = true
}

function onMusicPicked(value) {
  musicSource.value = value
  if (value) openMusicApp(value)
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
  clearTimeout(refreshNoteTimer)
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
          ref="mapRef"
          :points="points"
          mode="live"
          :follow="true"
          :center="previewCenter"
          class="track-live__map"
          @missing-key="mapMissing = true"
          @ready="mapMissing = false"
        />

        <button
          v-if="!mapMissing"
          type="button"
          class="track-refresh track-refresh--live"
          :disabled="refreshing"
          @click="refreshLocation"
        >
          <Navigation :size="14" />
          {{ refreshing ? '正在刷新' : '刷新定位' }}
        </button>

        <div class="track-live__top">
          <button type="button" class="track-round" aria-label="返回" @click="router.back()">
            <ArrowLeft :size="19" />
          </button>
          <div class="track-live__title">
            <strong>{{ sport }}</strong>
            <span>{{ status === 'paused' ? '已暂停' : '正在记录' }}</span>
          </div>
          <span class="gps-chip" :class="'gps-chip--' + gpsChipState">{{ gpsText }}</span>
        </div>

        <div class="track-live__bottom">
          <button type="button" class="live-music" @click="onMusicTap">
            <span class="live-music__icon"><Music :size="16" /></span>
            <span class="live-music__text">
              <strong>{{ musicLabel }}</strong>
              <small>{{ musicSource ? '点按唤起音乐 App' : '选择音源，运动时来点音乐' }}</small>
            </span>
            <ChevronRight :size="14" />
          </button>

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
          <p v-if="pointLimitReached" class="track-tip track-tip--dark">轨迹点已达上限，后续点位不再记录。</p>

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

        <MusicSheet v-model="musicOpen" :source="musicSource" @picked="onMusicPicked" />
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
      <div class="track-launcher">
        <RouteMap
          ref="mapRef"
          :points="[]"
          mode="detail"
          :follow="false"
          :center="previewCenter"
          class="track-launcher__map"
          @missing-key="mapMissing = true"
          @ready="mapMissing = false"
        />

        <div class="track-launcher__top">
          <button type="button" class="track-round" aria-label="返回" @click="router.back()">
            <ArrowLeft :size="19" />
          </button>
          <span class="track-launcher__badge">
            <Navigation :size="13" />
            {{ locateLabel }}
          </span>
          <button
            v-if="!mapMissing"
            type="button"
            class="track-launcher__refresh"
            :disabled="refreshing"
            @click="refreshLocation"
          >
            <Navigation :size="13" />
            {{ refreshing ? '正在刷新' : '刷新定位' }}
          </button>
        </div>

        <div class="track-launcher__stack">
        <div v-if="mapMissing" class="track-launcher__notice">
          <AlertTriangle :size="15" />
          <span>
            地图未配置：在 <code>.env</code> 填写 <code>VITE_AMAP_JS_KEY</code> 和
            <code>VITE_AMAP_SECURITY_CODE</code> 后重启。
          </span>
        </div>

        <div v-if="locateState === 'failed'" class="track-launcher__notice">
          <AlertTriangle :size="15" />
          <span>
            未能获取当前位置：请允许浏览器定位权限；手机用局域网 HTTP 地址访问时，浏览器会禁用定位，需要 HTTPS。
          </span>
        </div>

        <div v-if="draft" class="track-launcher__draft">
          <div>
            <strong>上次记录未完成</strong>
            <span>{{ draftText }}</span>
          </div>
          <div class="draft-actions">
            <button type="button" @click="discardDraftRun">丢弃</button>
            <button type="button" class="draft-primary" @click="resumeDraftRun">继续</button>
          </div>
        </div>
        </div>

        <div class="track-launcher__drawer">
          <div class="launcher-head">
            <strong>{{ selectedSport }}</strong>
            <span>自由记录</span>
          </div>

          <div class="launcher-sports">
            <button
              v-for="item in sportOptions"
              :key="item.name"
              type="button"
              class="launcher-sport"
              :class="{ active: selectedSport === item.name }"
              :aria-pressed="selectedSport === item.name"
              @click="selectedSport = item.name"
            >
              {{ item.name }}
            </button>
          </div>

          <div class="launcher-go">
            <button type="button" class="launcher-side" aria-label="音乐" @click="onMusicTap">
              <Music :size="18" />
              <span>{{ musicSource ? '音乐' : '选音源' }}</span>
            </button>
            <button type="button" class="launcher-go__main" aria-label="开始记录" @click="startRun">
              GO
            </button>
            <button
              type="button"
              class="launcher-side"
              aria-label="最近轨迹"
              @click="recentOpen = true"
            >
              <History :size="18" />
              <span>轨迹</span>
            </button>
          </div>

          <p class="launcher-tip">页面保持在前台时记录轨迹；锁屏或切后台可能暂停定位。</p>
        </div>

        <StartCountdown
          v-if="countdownActive"
          :sport="selectedSport"
          @done="handleCountdownDone"
        />
      </div>

      <transition name="ui">
        <div v-if="recentOpen" class="sheet-overlay" @click.self="recentOpen = false">
          <div class="sheet-panel" role="dialog" aria-modal="true" aria-label="最近轨迹">
            <div class="sheet-panel__head">
              <strong>最近轨迹</strong>
              <span>{{ recent.length }} 条</span>
              <button type="button" class="sheet-panel__close" @click="recentOpen = false">
                关闭
              </button>
            </div>
            <div class="sheet-panel__body">
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
            </div>
          </div>
        </div>
      </transition>

      <MusicSheet v-model="musicOpen" :source="musicSource" @picked="onMusicPicked" />
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

.track-refresh {
  position: absolute;
  z-index: 20;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid rgba(47, 107, 255, 0.18);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.96);
  color: #2f6bff;
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 6px 16px rgba(15, 35, 95, 0.16);
}

.track-refresh:disabled {
  opacity: 0.72;
}

.track-refresh--live {
  top: 84px;
  right: 16px;
}

.track-refresh--idle {
  top: 12px;
  right: 12px;
  height: 30px;
  padding: 0 10px;
  font-size: 11px;
}

.track-live__bottom {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 4;
  padding: 14px 20px calc(18px + env(safe-area-inset-bottom));
  border-radius: 26px 26px 0 0;
  background: #0f172a;
  box-shadow: 0 -12px 40px rgba(2, 8, 23, 0.5);
}

.live-music {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-bottom: 12px;
  border: 0;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.07);
  padding: 10px 12px;
  color: #fff;
  text-align: left;
  cursor: pointer;
}

.live-music__icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.12);
  color: #9db8ff;
}

.live-music__text {
  flex: 1;
  min-width: 0;
}

.live-music__text strong {
  display: block;
  font-size: 13px;
  line-height: 17px;
}

.live-music__text small {
  display: block;
  margin-top: 1px;
  font-size: 11px;
  line-height: 15px;
  color: rgba(255, 255, 255, 0.55);
}

.live-music > svg {
  flex: 0 0 auto;
  color: rgba(255, 255, 255, 0.4);
}

.track-distance {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.track-distance strong {
  color: #fff;
  font-size: 56px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.04em;
  font-variant-numeric: tabular-nums;
}

.track-distance span {
  color: rgba(255, 255, 255, 0.55);
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
  color: rgba(255, 255, 255, 0.5);
  font-size: 11px;
}

.track-metrics strong {
  color: #fff;
  font-size: 18px;
  line-height: 22px;
  font-variant-numeric: tabular-nums;
}

.track-metrics small {
  margin-left: 2px;
  color: rgba(255, 255, 255, 0.5);
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
  border: 1px solid rgba(255, 255, 255, 0.22);
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
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
  background: rgba(239, 68, 68, 0.14);
  padding: 8px 10px;
  color: #fca5a5;
  font-size: 11px;
  line-height: 16px;
}

.track-tip--dark {
  color: rgba(255, 255, 255, 0.6);
}

.track-launcher {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 50%;
  z-index: 110;
  width: 100%;
  max-width: 480px;
  transform: translateX(-50%);
  background: #0f172a;
  overflow: hidden;
}

.track-launcher__map {
  position: absolute;
  inset: 0;
}

.track-launcher__top {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 4;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: calc(14px + env(safe-area-inset-top)) 16px 12px;
}

.track-launcher__badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--blue);
  font-size: 11px;
  font-weight: 600;
}

.track-launcher__refresh {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 11px;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--blue);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.track-launcher__refresh:disabled {
  opacity: 0.7;
}

.track-launcher__stack {
  position: absolute;
  top: calc(58px + env(safe-area-inset-top));
  left: 16px;
  right: 16px;
  z-index: 5;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.track-launcher__notice {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  border: 1px solid #fed7aa;
  border-radius: 14px;
  background: rgba(255, 247, 237, 0.95);
  padding: 10px 12px;
  color: #9a3412;
  font-size: 11px;
  line-height: 17px;
}

.track-launcher__notice code {
  border-radius: 4px;
  background: rgba(154, 52, 18, 0.08);
  padding: 1px 3px;
  font-size: 10px;
}

.track-launcher__draft {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid rgba(47, 107, 255, 0.18);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.95);
  padding: 11px 13px;
}

.track-launcher__draft > div:first-child {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.track-launcher__draft strong {
  color: var(--text);
  font-size: 13px;
}

.track-launcher__draft span {
  color: var(--muted);
  font-size: 11px;
}




.track-launcher__drawer {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 6;
  border-radius: 26px 26px 0 0;
  background: #fff;
  padding: 16px 20px calc(14px + env(safe-area-inset-bottom));
  box-shadow: 0 -12px 40px rgba(2, 8, 23, 0.18);
}

.launcher-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.launcher-head strong {
  color: #0f172a;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.launcher-head span {
  margin-left: auto;
  color: var(--muted);
  font-size: 11px;
}

.launcher-sports {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.launcher-sport {
  flex: 1;
  height: 40px;
  border: 1px solid var(--line-2);
  border-radius: 999px;
  background: var(--surface-2);
  color: var(--text-2);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.launcher-sport.active {
  border-color: rgba(47, 107, 255, 0.5);
  background: var(--blue-soft);
  color: var(--blue);
}

.launcher-go {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
}

.launcher-side {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  width: 56px;
  border: 0;
  background: transparent;
  color: var(--text-2);
  font-size: 10px;
  cursor: pointer;
}

.launcher-side svg {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--surface-2);
  color: var(--text);
  padding: 12px;
}

.launcher-go__main {
  width: 78px;
  height: 78px;
  border: 0;
  border-radius: 50%;
  background: linear-gradient(135deg, #2f6bff, #5b8cff);
  color: #fff;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.04em;
  box-shadow: 0 14px 30px rgba(47, 107, 255, 0.35);
  cursor: pointer;
}

.launcher-go__main:active {
  transform: scale(0.95);
}

.launcher-tip {
  margin: 12px 0 0;
  text-align: center;
  color: var(--muted);
  font-size: 10px;
  line-height: 14px;
}

.sheet-overlay {
  position: fixed;
  inset: 0;
  z-index: 230;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(15, 23, 42, 0.45);
}

.sheet-panel {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 480px;
  max-height: 72vh;
  border-radius: 24px 24px 0 0;
  background: var(--surface);
  padding-bottom: env(safe-area-inset-bottom);
}

.sheet-panel__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 20px 10px;
}

.sheet-panel__head strong {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

.sheet-panel__head span {
  flex: 1;
  color: var(--muted);
  font-size: 11px;
}

.sheet-panel__close {
  border: 0;
  background: transparent;
  padding: 4px 0;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
}

.sheet-panel__body {
  flex: 1;
  overflow-y: auto;
  padding: 0 20px 20px;
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
