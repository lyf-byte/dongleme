<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Pause, Play, RotateCcw, Square } from 'lucide-vue-next'
import { getCustomSports } from '../utils/api'
import { mergeSports } from '../utils/sport'
import { toast } from '../utils/ui'

const STORAGE_KEY = 'dlm_timer_state'
const COUNTDOWN_PRESETS = [3, 5, 10, 15, 20, 30]
const RING_RADIUS = 136
const RING_LENGTH = 2 * Math.PI * RING_RADIUS
const RESTORE_TTL_MS = 12 * 60 * 60 * 1000
// 单次手动/计时器打卡上限（分钟），与后端一致；更长时间走轨迹记录
const MAX_MANUAL_MINUTES = 240

const route = useRoute()
const router = useRouter()

const routeType = typeof route.query.type === 'string' ? route.query.type : ''
const customSports = ref([])
const allSports = computed(() => mergeSports(customSports.value))
const sport = ref(routeType || '跑步')

const mode = ref('up')
const status = ref('idle')
const totalMs = ref(5 * 60 * 1000)
const elapsedMs = ref(0)
const accumulatedMs = ref(0)

let runningSince = 0
let ticker = null
let wakeLock = null
// 已经交给打卡页了，卸载时不要再把状态写回存储
let submitted = false

const isRunning = computed(() => status.value === 'running')
const isActive = computed(() => status.value === 'running' || status.value === 'paused')
// 满 1 分钟才会计入打卡，按钮文案跟着变，避免「结束打卡」名不副实
const reachedOneMinute = computed(() => elapsedMs.value >= 60 * 1000)

const displayMs = computed(() => (
  mode.value === 'down'
    ? Math.max(0, totalMs.value - elapsedMs.value)
    : elapsedMs.value
))

const clock = computed(() => {
  const totalSeconds = Math.floor(displayMs.value / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const mm = String(minutes).padStart(2, '0')
  const ss = String(seconds).padStart(2, '0')
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`
})

// 正计时按 60 分钟走满一圈，倒计时按剩余比例回落
const progress = computed(() => {
  if (mode.value === 'down') {
    if (!totalMs.value) return 0
    return Math.min(1, Math.max(0, 1 - elapsedMs.value / totalMs.value))
  }
  return Math.min(1, elapsedMs.value / (60 * 60 * 1000))
})

const dashOffset = computed(() => RING_LENGTH * (1 - progress.value))

const statusText = computed(() => ({
  idle: mode.value === 'down' ? '倒计时准备' : '准备开始',
  running: mode.value === 'down' ? '倒计时中' : '计时中',
  paused: '已暂停',
  done: '已完成'
}[status.value]))

const activePreset = computed(() => Math.round(totalMs.value / 60000))

function update() {
  elapsedMs.value = accumulatedMs.value
    + (runningSince ? Date.now() - runningSince : 0)

  if (mode.value === 'down' && status.value === 'running' && elapsedMs.value >= totalMs.value) {
    completeCountdown()
  }
}

function startTicker() {
  stopTicker()
  ticker = setInterval(update, 200)
  update()
}

function stopTicker() {
  if (ticker !== null) {
    clearInterval(ticker)
    ticker = null
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
    /* 已经释放过就忽略 */
  }
  wakeLock = null
}

function persist() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      mode: mode.value,
      sport: sport.value,
      status: status.value,
      totalMs: totalMs.value,
      accumulatedMs: accumulatedMs.value,
      runningSince,
      savedAt: Date.now()
    }))
  } catch {
    /* 隐私模式下写不进去，不影响计时 */
  }
}

function clearPersist() {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    /* ignore */
  }
}

function restore() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return
    const data = JSON.parse(raw)
    if (!data || Date.now() - Number(data.savedAt) > RESTORE_TTL_MS) {
      clearPersist()
      return
    }
    mode.value = data.mode === 'down' ? 'down' : 'up'
    sport.value = data.sport || sport.value
    totalMs.value = Number(data.totalMs) || totalMs.value
    accumulatedMs.value = Number(data.accumulatedMs) || 0
    runningSince = Number(data.runningSince) || 0
    status.value = ['running', 'paused', 'done'].includes(data.status) ? data.status : 'idle'

    if (status.value === 'running' && !runningSince) {
      status.value = 'paused'
    }
    update()
    if (status.value === 'running') {
      startTicker()
      requestWakeLock()
    }
  } catch {
    clearPersist()
  }
}

function start() {
  if (status.value === 'running') return
  if (status.value === 'done') reset()
  runningSince = Date.now()
  status.value = 'running'
  startTicker()
  requestWakeLock()
  persist()
}

function pause() {
  if (status.value !== 'running') return
  accumulatedMs.value += Date.now() - runningSince
  runningSince = 0
  status.value = 'paused'
  stopTicker()
  releaseWakeLock()
  update()
  persist()
}

function reset() {
  stopTicker()
  releaseWakeLock()
  accumulatedMs.value = 0
  elapsedMs.value = 0
  runningSince = 0
  status.value = 'idle'
  persist()
}

function completeCountdown() {
  accumulatedMs.value = totalMs.value
  runningSince = 0
  status.value = 'done'
  stopTicker()
  releaseWakeLock()
  update()
  persist()
  if ('vibrate' in navigator) {
    try {
      navigator.vibrate([200, 120, 200])
    } catch {
      /* 不支持震动就算了 */
    }
  }
  toast('时间到，可以打卡了')
}

function finish() {
  if (status.value === 'running') {
    pause()
  }

  const seconds = accumulatedMs.value / 1000
  // 按钮不设时间门槛，随时可以结束；只是不足 1 分钟不生成打卡记录
  if (seconds < 60) {
    reset()
    toast('不足 1 分钟，本次不计入打卡')
    return
  }

  const rawMinutes = Math.round(seconds / 60)
  const minutes = Math.min(MAX_MANUAL_MINUTES, rawMinutes)
  if (rawMinutes > MAX_MANUAL_MINUTES) {
    toast(`计时超过 ${MAX_MANUAL_MINUTES} 分钟，按上限记录，更长时间请使用轨迹记录`)
  }
  submitted = true
  clearPersist()
  router.replace({ path: '/checkin', query: { type: sport.value, duration: minutes, source: 'timer' } })
}

function setMode(next) {
  if (isActive.value) return
  mode.value = next
  reset()
}

function setPreset(minutes) {
  if (isActive.value) return
  totalMs.value = minutes * 60 * 1000
  accumulatedMs.value = 0
  elapsedMs.value = 0
  status.value = 'idle'
  persist()
}

function selectSport(name) {
  sport.value = name
  persist()
}

function handleVisibility() {
  if (document.visibilityState !== 'visible') return
  if (status.value === 'running') {
    update()
    requestWakeLock()
  }
}

async function loadSports() {
  try {
    customSports.value = await getCustomSports()
  } catch {
    customSports.value = []
  }
}

onMounted(() => {
  restore()
  loadSports()
  document.addEventListener('visibilitychange', handleVisibility)
})

onUnmounted(() => {
  stopTicker()
  releaseWakeLock()
  document.removeEventListener('visibilitychange', handleVisibility)
  if (!submitted && (status.value === 'running' || status.value === 'paused')) persist()
})
</script>

<template>
  <div class="page page--plain timer">
    <header class="topbar">
      <button type="button" class="topbar__btn" aria-label="返回" @click="router.back()">
        <ArrowLeft :size="20" />
      </button>
      <h1>运动计时</h1>
    </header>

    <div class="timer-modes" :class="{ 'is-locked': isActive }">
      <button
        type="button"
        :class="{ active: mode === 'up' }"
        @click="setMode('up')"
      >
        正计时
      </button>
      <button
        type="button"
        :class="{ active: mode === 'down' }"
        @click="setMode('down')"
      >
        倒计时
      </button>
    </div>

    <section class="timer-dial" :class="[`is-${status}`, `mode-${mode}`]">
      <svg viewBox="0 0 320 320" aria-hidden="true">
        <defs>
          <linearGradient id="timerRing" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#5b8cff" />
            <stop offset="100%" stop-color="#22c3a6" />
          </linearGradient>
        </defs>
        <circle class="dial-track" cx="160" cy="160" :r="RING_RADIUS" />
        <circle
          class="dial-progress"
          cx="160"
          cy="160"
          :r="RING_RADIUS"
          :stroke-dasharray="RING_LENGTH"
          :stroke-dashoffset="dashOffset"
        />
      </svg>

      <div class="dial-face">
        <span class="dial-status">
          <i v-if="isRunning" class="dial-dot"></i>
          {{ statusText }}
        </span>
        <strong class="dial-clock">{{ clock }}</strong>
        <span class="dial-sport">{{ sport }}</span>
      </div>
    </section>

    <div v-if="mode === 'down'" class="timer-presets" :class="{ 'is-locked': isActive }">
      <button
        v-for="minutes in COUNTDOWN_PRESETS"
        :key="minutes"
        type="button"
        :class="{ active: activePreset === minutes }"
        @click="setPreset(minutes)"
      >
        {{ minutes }} 分钟
      </button>
    </div>

    <div class="timer-sports">
      <button
        v-for="item in allSports"
        :key="item.name"
        type="button"
        class="timer-sport"
        :class="{ active: sport === item.name }"
        @click="selectSport(item.name)"
      >
        <component :is="item.icon" :size="15" />
        {{ item.name }}
      </button>
    </div>

    <div class="timer-actions">
      <template v-if="isActive">
        <button type="button" class="btn-primary timer-main" @click="isRunning ? pause() : start()">
          <Pause v-if="isRunning" :size="18" />
          <Play v-else :size="18" />
          {{ isRunning ? '暂停' : '继续' }}
        </button>
        <button type="button" class="timer-finish" @click="finish">
          <Square :size="16" />
          {{ reachedOneMinute ? '结束打卡' : '结束' }}
        </button>
      </template>
      <button v-else type="button" class="btn-primary timer-main" @click="start">
        <Play :size="18" />
        {{ status === 'done' ? '再来一组' : '开始计时' }}
      </button>
    </div>

    <button v-if="status !== 'idle'" type="button" class="timer-reset" @click="reset">
      <RotateCcw :size="13" />
      重置计时
    </button>

    <p class="timer-hint">
      {{
        mode === 'down'
          ? '倒计时结束后会自动震动提醒，可以直接去打卡。'
          : '离开页面计时不会中断，回来会接着走。'
      }}
      <br />
      随时可以结束，但不足 1 分钟不计入打卡。
    </p>
  </div>
</template>

<style scoped>
.timer {
  /* 用文本居中而不是 flex 居中，否则 topbar 这类整宽元素会被压窄 */
  text-align: center;
}

/* 顶栏标题沿用其他页面的左对齐，不跟着页面居中 */
.timer .topbar h1 {
  text-align: left;
}

.timer-modes {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  width: 100%;
  margin-top: 16px;
  border-radius: 999px;
  background: var(--surface-2);
  padding: 4px;
}

.timer-modes button {
  height: 36px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  font-size: 13px;
  font-weight: 600;
  transition: background 0.18s ease, color 0.18s ease;
}

.timer-modes button.active {
  background: var(--surface);
  color: var(--blue);
  box-shadow: var(--shadow-sm);
}

.timer-modes.is-locked {
  opacity: 0.6;
}

.timer-dial {
  position: relative;
  width: 100%;
  max-width: 300px;
  margin: 22px auto 0;
  aspect-ratio: 1;
}

.timer-dial svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.dial-track {
  fill: none;
  stroke: #e7ecf5;
  stroke-width: 12;
}

.dial-progress {
  fill: none;
  stroke: url(#timerRing);
  stroke-width: 12;
  stroke-linecap: round;
  transition: stroke-dashoffset 0.25s linear;
}

.dial-face {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.dial-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.04em;
}

.dial-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--green);
  animation: timerBlink 1.4s ease-in-out infinite;
}

.dial-clock {
  color: var(--text);
  font-size: 56px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.04em;
  font-variant-numeric: tabular-nums;
}

.timer-dial.is-done .dial-clock {
  color: var(--green);
}

.dial-sport {
  color: var(--text-2);
  font-size: 13px;
  font-weight: 600;
}

.timer-presets {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  width: 100%;
  margin-top: 14px;
}

.timer-presets button {
  height: 34px;
  border: 1px solid var(--line-2);
  border-radius: 10px;
  background: var(--surface);
  color: var(--text-2);
  font-size: 12px;
  font-weight: 600;
}

.timer-presets button.active {
  border-color: var(--blue);
  background: var(--blue-soft);
  color: var(--blue);
}

.timer-presets.is-locked {
  opacity: 0.45;
  pointer-events: none;
}

.timer-sports {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  width: 100%;
  margin-top: 18px;
}

.timer-sport {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--line-2);
  border-radius: 12px;
  background: var(--surface);
  color: var(--text-2);
  font-size: 12px;
  font-weight: 600;
}

.timer-sport.active {
  border-color: rgba(47, 107, 255, 0.4);
  background: var(--blue-soft);
  color: var(--blue);
}

.timer-actions {
  display: flex;
  gap: 10px;
  width: 100%;
  margin-top: 22px;
}

.timer-main {
  flex: 1 1 0;
  min-width: 0;
  gap: 6px;
  margin-top: 0;
}

.timer-finish {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex: 1 1 0;
  min-width: 0;
  height: 50px;
  border: 1px solid rgba(239, 68, 68, 0.28);
  border-radius: 14px;
  background: #fff;
  color: var(--red);
  font-size: 14px;
  font-weight: 600;
}

.timer-reset {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 14px;
  border: 0;
  background: transparent;
  padding: 6px 10px;
  color: var(--muted);
  font-size: 12px;
}

.timer-hint {
  margin: 18px 0 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
  text-align: center;
}

@keyframes timerBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.25; }
}

@media (prefers-reduced-motion: reduce) {
  .dial-dot { animation: none; }
  .dial-progress { transition: none; }
}
</style>
