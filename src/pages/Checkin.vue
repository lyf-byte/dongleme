<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Check, Download, Minus, Navigation, Plus, Settings2 } from 'lucide-vue-next'
import { getProfile, getCustomSports, submitCheckin, errorMessage } from '../utils/api'
import { SPORTS, calcCalories, mergeSports } from '../utils/sport'
import { toast, confirmDialog } from '../utils/ui'
import { saveBlob } from '../utils/native'
import posterCheckin from '../assets/poster-checkin.jpg'
import {
  POSTER_FONT,
  canvasToBlob,
  clipText,
  drawBandChrome,
  drawStatColumns,
  loadPosterImage
} from '../utils/poster'
import CustomSportManager from '../components/CustomSportManager.vue'

const route = useRoute()
const router = useRouter()

const customSports = ref([])
const managerOpen = ref(false)
const allSports = computed(() => mergeSports(customSports.value))

const routeType = typeof route.query.type === 'string' ? route.query.type : ''
const initialSport = allSports.value.find((sport) => sport.name === routeType)

// 单次手动/计时器打卡上限（分钟），与后端一致；更长时间走轨迹记录
const MAX_MANUAL_MINUTES = 240
// 运动计时器跳过来时会带上 duration（分钟），这里直接回填
const routeDuration = Number(route.query.duration)
const initialDuration = Number.isInteger(routeDuration) && routeDuration >= 1
  ? Math.min(MAX_MANUAL_MINUTES, routeDuration)
  : 30
const checkinSource = route.query.source === 'timer' ? 'timer' : 'manual'

const form = ref({
  type: initialSport ? initialSport.name : '跑步',
  duration: initialDuration,
  weight: 60
})
const gender = ref('secret')
const todayTypes = ref([])
const submitting = ref(false)
const success = ref(null)
const posterUrl = ref('')
const posterBlob = ref(null)
const posterLoading = ref(false)
const posterError = ref('')
const savingPoster = ref(false)
let posterTask = 0

const currentSport = computed(
  () => allSports.value.find((sport) => sport.name === form.value.type) || allSports.value[0]
)
const met = computed(() => currentSport.value.met)
const calories = computed(() =>
  calcCalories(
    met.value,
    Number(form.value.weight) || 60,
    Number(form.value.duration) || 0,
    gender.value
  )
)

function adjust(delta) {
  const next = Number(form.value.duration) + delta
  form.value.duration = Math.min(MAX_MANUAL_MINUTES, Math.max(1, Number.isFinite(next) ? next : 1))
}

async function loadProfile() {
  try {
    const profile = await getProfile()
    if (profile?.weight) form.value.weight = profile.weight
    gender.value = profile?.gender || 'secret'
    todayTypes.value = profile?.today?.types || []
  } catch {
    /* 保留默认体重 */
  }
}

async function loadCustomSports() {
  try {
    const list = await getCustomSports()
    customSports.value = list
    if (!SPORTS.some((sport) => sport.name === form.value.type)) {
      const matched = list.find((sport) => sport.name === routeType)
      if (matched) form.value.type = matched.name
    }
  } catch {
    /* 自定义运动加载失败时仍可使用内置运动 */
  }
}

function handleSportsChanged(list) {
  customSports.value = list
  if (!allSports.value.some((sport) => sport.name === form.value.type)) {
    form.value.type = '跑步'
  }
}

async function submit(overwrite = false) {
  const duration = Number(form.value.duration)
  const weight = Number(form.value.weight)

  if (!Number.isInteger(duration) || duration < 1 || duration > MAX_MANUAL_MINUTES) {
    toast(`单次时长需为 1–${MAX_MANUAL_MINUTES} 的整数分钟，更长时间请使用轨迹记录`)
    return
  }
  if (!Number.isFinite(weight) || weight < 20 || weight > 300) {
    toast('体重需在 20–300 kg 之间')
    return
  }

  submitting.value = true
  try {
    const result = await submitCheckin({
      type: form.value.type,
      duration,
      weight,
      overwrite,
      source: checkinSource
    })
    todayTypes.value = Array.from(new Set([...todayTypes.value, form.value.type]))
    success.value = {
      type: form.value.type,
      duration,
      calories: result.calories,
      streak: result.streak,
      date: result.date
    }
    preparePoster(success.value)
  } catch (error) {
    if (error?.response?.status === 409 && !overwrite) {
      try {
        await confirmDialog({
          title: '今日已打卡',
          message: `今天已经记录过「${form.value.type}」，要用这次的数据覆盖吗？`,
          confirmText: '覆盖',
          cancelText: '取消'
        })
        await submit(true)
        return
      } catch {
        return
      }
    }
    toast(errorMessage(error))
  } finally {
    submitting.value = false
  }
}

function releasePoster() {
  // 递增任务号，让还未返回的生成任务作废
  posterTask += 1
  if (posterUrl.value) URL.revokeObjectURL(posterUrl.value)
  posterUrl.value = ''
  posterBlob.value = null
  posterLoading.value = false
  posterError.value = ''
}

function finishSuccess() {
  releasePoster()
  success.value = null
  router.replace('/')
}

// 海报底图 1024×1536，底部再拼一条 176px 的信息条，避免遮挡画面上的标题与文案
const POSTER_WIDTH = 1024
const POSTER_IMAGE_HEIGHT = 1536
const POSTER_BAND_HEIGHT = 176


function posterDateText(value) {
  const raw = String(value || '')
  const matched = raw.match(/^\d{4}-(\d{2})-(\d{2})$/)
  if (matched) {
    return `${Number(matched[1])}月${Number(matched[2])}日`
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    timeZone: 'Asia/Shanghai'
  }).format(new Date())
}

async function buildPoster(data) {
  const image = await loadPosterImage(posterCheckin)

  const canvas = document.createElement('canvas')
  const bandTop = POSTER_IMAGE_HEIGHT
  canvas.width = POSTER_WIDTH
  canvas.height = bandTop + POSTER_BAND_HEIGHT
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    throw new Error('canvas unsupported')
  }

  // 底图按原始分辨率 1:1 铺满，文字与照片都不失真
  ctx.drawImage(image, 0, 0, POSTER_WIDTH, POSTER_IMAGE_HEIGHT)

  drawBandChrome(ctx, POSTER_WIDTH, bandTop, POSTER_BAND_HEIGHT)

  ctx.fillStyle = 'rgba(255,255,255,0.5)'
  ctx.font = `24px ${POSTER_FONT}`
  ctx.fillText('动了么 · 每日打卡', 64, bandTop + 60)

  ctx.fillStyle = '#ffffff'
  ctx.font = `bold 42px ${POSTER_FONT}`
  const title = `${posterDateText(data.date)} · ${data.type || '运动'}`
  ctx.fillText(clipText(ctx, title, 440), 64, bandTop + 120)

  drawStatColumns(ctx, [
    { label: '时长', value: data.duration, unit: '分钟' },
    { label: '热量', value: data.calories, unit: '千卡' },
    { label: '连续', value: data.streak, unit: '天' }
  ], {
    right: POSTER_WIDTH - 64,
    labelY: bandTop + 60,
    valueY: bandTop + 122
  })

  return canvasToBlob(canvas)
}

async function preparePoster(data) {
  const task = ++posterTask
  posterLoading.value = true
  posterError.value = ''
  try {
    const blob = await buildPoster(data)
    if (task !== posterTask) return
    posterBlob.value = blob
    posterUrl.value = URL.createObjectURL(blob)
  } catch {
    if (task === posterTask) {
      posterError.value = '海报生成失败，可以直接完成打卡'
    }
  } finally {
    if (task === posterTask) posterLoading.value = false
  }
}

async function savePoster() {
  if (!posterBlob.value || savingPoster.value) return
  savingPoster.value = true
  try {
    const result = await saveBlob(
      posterBlob.value,
      `dongleme-checkin-${success.value?.date || 'today'}.png`
    )
    toast(result === 'ok' ? '打卡海报已保存' : '保存失败，请重试')
    if (result === 'ok') finishSuccess()
  } finally {
    savingPoster.value = false
  }
}

onMounted(() => {
  loadProfile()
  loadCustomSports()
})
</script>

<template>
  <div class="page page--plain checkin">
    <header class="topbar">
      <button type="button" class="topbar__btn" aria-label="返回" @click="router.back()">
        <ArrowLeft :size="20" />
      </button>
      <h1>记录运动</h1>
    </header>

    <section class="selected-card">
      <span class="selected-icon"><component :is="currentSport.icon" :size="22" /></span>
      <div class="selected-main">
        <h2>{{ form.type }}</h2>
        <span>MET {{ met.toFixed(1) }} · 自动估算</span>
      </div>
      <div class="selected-cal">
        <strong>{{ calories }}</strong>
        <span>千卡</span>
      </div>
    </section>

    <button
      type="button"
      class="track-entry"
      @click="router.push({ path: '/track', query: { type: form.type } })"
    >
      <Navigation :size="16" />
      用 GPS 记录轨迹
    </button>

    <div class="section-title">
      <h2>运动类型</h2>
      <button type="button" class="manage-btn" @click="managerOpen = true">
        <Settings2 :size="13" />
        自定义
      </button>
    </div>

    <div class="type-grid">
      <button
        v-for="(sport, index) in allSports"
        :key="sport.custom ? `custom-${sport.id}` : sport.name"
        type="button"
        :class="{ active: form.type === sport.name, 'is-done': todayTypes.includes(sport.name) }"
        :aria-pressed="form.type === sport.name"
        @click="form.type = sport.name"
      >
        <span
          class="type-icon"
          :class="sport.custom ? 'type-icon--custom' : 'type-icon--' + index"
        >
          <component :is="sport.icon" :size="16" />
        </span>
        <span class="type-name">{{ sport.name }}</span>
        <span class="type-met" :class="{ done: todayTypes.includes(sport.name) }">
          {{ todayTypes.includes(sport.name) ? '今日已打卡' : `MET ${sport.met.toFixed(1)}` }}
        </span>
        <span v-if="form.type === sport.name" class="type-check">
          <Check :size="12" />
        </span>
        <span v-else-if="todayTypes.includes(sport.name)" class="type-done">
          <Check :size="10" />
        </span>
      </button>
    </div>

    <div class="section-title">
      <h2>运动时长</h2>
      <span>分钟</span>
    </div>

    <div class="form-card">
      <div class="stepper">
        <button type="button" @click="adjust(-5)"><Minus :size="18" /></button>
        <div class="stepper__value">
          <input v-model.number="form.duration" type="number" inputmode="numeric" />
          <span>分钟</span>
        </div>
        <button type="button" @click="adjust(5)"><Plus :size="18" /></button>
      </div>
    </div>

    <div class="form-card weight-card">
      <div class="form-row">
        <span class="form-row__label">体重</span>
        <input v-model="form.weight" type="number" inputmode="decimal" placeholder="60" />
        <span class="form-row__extra">kg</span>
      </div>
    </div>

    <p class="tip">
      同一天同一类型只保留一条记录，重复提交时会询问是否覆盖。热量按 ACSM 公式估算：
      MET × 3.5 × 体重 ÷ 200 × 时长，女性按 0.9 系数，男性/保密按 1.0。
    </p>

    <button type="button" class="btn-primary checkin-submit" :disabled="submitting" @click="submit()">
      <span>{{ submitting ? '保存中…' : '完成打卡' }}</span>
      <Check :size="18" />
    </button>

    <CustomSportManager v-model="managerOpen" @changed="handleSportsChanged" />

    <transition name="ui">
      <div v-if="success" class="checkin-overlay" @click.self="finishSuccess">
        <div
          class="checkin-sheet checkin-sheet--poster"
          role="dialog"
          aria-modal="true"
          aria-label="打卡成功"
        >
          <div class="poster-head">
            <span class="success-check"><Check :size="18" /></span>
            <div>
              <h2>打卡成功</h2>
              <p>{{ success.type }} · {{ success.duration }} 分钟 · {{ success.calories }} 千卡</p>
            </div>
          </div>

          <div class="poster-frame">
            <img v-if="posterUrl" :src="posterUrl" alt="打卡海报预览" />
            <div v-else class="poster-placeholder">
              {{ posterError || '正在生成海报…' }}
            </div>
          </div>

          <button
            type="button"
            class="btn-primary success-poster"
            :disabled="!posterBlob || savingPoster"
            @click="savePoster"
          >
            <Download :size="16" />
            {{ savingPoster ? '保存中…' : '保存海报' }}
          </button>
          <button type="button" class="success-done" @click="finishSuccess">
            {{ posterBlob ? '不保存，直接完成' : '完成' }}
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.checkin {
  padding-top: 0;
}

.selected-card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 6px;
  padding: 16px;
  border: 0;
  border-radius: 20px;
  background: var(--blue-soft);
}

.track-entry {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 44px;
  margin-top: 10px;
  border: 1px dashed rgba(47, 107, 255, 0.35);
  border-radius: 14px;
  background: rgba(47, 107, 255, 0.06);
  color: var(--blue);
  font-size: 13px;
  font-weight: 600;
}

.selected-icon {
  width: 48px;
  height: 48px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 16px;
  background: #fff;
  color: var(--blue);
  font-size: 20px;
  font-weight: 700;
}

.selected-main {
  flex: 1;
  min-width: 0;
}

.selected-main h2 {
  margin: 0;
  font-size: 18px;
  line-height: 24px;
  font-weight: 700;
}

.selected-main span {
  display: block;
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-2);
}

.selected-cal {
  flex: 0 0 auto;
  text-align: right;
}

.selected-cal strong {
  display: block;
  font-size: 30px;
  line-height: 32px;
  font-weight: 700;
  color: var(--blue);
  font-variant-numeric: tabular-nums;
}

.selected-cal span {
  display: block;
  margin-top: 2px;
  font-size: 10px;
  color: var(--muted);
}

.type-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.type-grid button {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  padding: 10px 9px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.14s ease, background 0.14s ease;
}

.type-grid button.active {
  border-color: var(--blue);
  background: var(--blue-soft);
  box-shadow: inset 0 0 0 1px var(--blue);
}

.type-grid button.is-done:not(.active) {
  border-color: rgba(22, 163, 74, 0.28);
  background: #f4fbf6;
}

.type-grid button:active {
  transform: scale(0.98);
}

.type-icon {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 700;
}

.type-icon--0 {
  background: var(--blue-soft);
  color: var(--blue);
}

.type-icon--1 {
  background: var(--green-soft);
  color: var(--green);
}

.type-icon--2 {
  background: var(--cyan-soft);
  color: var(--cyan);
}

.type-icon--3 {
  background: var(--yellow-soft);
  color: #b45309;
}

.type-icon--4 {
  background: var(--violet-soft);
  color: var(--violet);
}

.type-icon--5 {
  background: var(--orange-soft);
  color: var(--orange);
}

.type-icon--6 {
  background: var(--red-soft);
  color: var(--red);
}

.type-icon--custom {
  background: var(--violet-soft);
  color: var(--violet);
}

.manage-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border: 1px solid var(--line-2);
  border-radius: 999px;
  background: var(--surface);
  color: var(--blue);
  font-size: 12px;
  line-height: 16px;
  cursor: pointer;
}

.type-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.type-met {
  font-size: 10px;
  color: var(--muted);
}

.type-met.done {
  color: var(--green);
  font-weight: 600;
}

.type-check {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 16px;
  height: 16px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--blue);
  color: #fff;
}

.type-done {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 16px;
  height: 16px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--green);
  color: #fff;
}

.stepper {
  display: grid;
  grid-template-columns: 48px 1fr 48px;
  align-items: center;
  padding: 12px;
}

.stepper button {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: var(--blue-soft);
  color: var(--blue);
  cursor: pointer;
}

.stepper__value {
  text-align: center;
}

.stepper__value input {
  width: 100px;
  border: 0;
  background: transparent;
  text-align: center;
  font-size: 36px;
  line-height: 40px;
  font-weight: 700;
  color: var(--text);
  outline: none;
  font-variant-numeric: tabular-nums;
}

.stepper__value span {
  display: block;
  font-size: 11px;
  color: var(--muted);
}

.weight-card {
  margin-top: 12px;
}

.tip {
  margin: 12px 4px 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--muted);
}

.checkin-submit {
  margin-top: 20px;
}

.checkin-overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: grid;
  place-items: center;
  background: rgba(15, 23, 42, 0.48);
  padding: 24px;
}

.checkin-sheet {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-width: 330px;
  border-radius: 24px;
  background: #fff;
  padding: 26px 20px 16px;
  text-align: center;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.26);
}

.success-check {
  display: grid;
  place-items: center;
  width: 62px;
  height: 62px;
  border-radius: 50%;
  background: rgba(22, 163, 74, 0.12);
  color: var(--green);
}

.checkin-sheet h2 {
  margin: 12px 0 0;
  color: var(--text);
  font-size: 21px;
}

.checkin-sheet > p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.checkin-sheet--poster {
  align-items: stretch;
  max-width: 320px;
  padding: 16px 14px 12px;
}

.poster-head {
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
}

.poster-head .success-check {
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
}

.poster-head h2 {
  margin: 0;
  font-size: 16px;
}

.poster-head p {
  margin: 2px 0 0;
  color: var(--muted);
  font-size: 11px;
}

.poster-frame {
  display: grid;
  place-items: center;
  width: 100%;
  margin-top: 12px;
}

.poster-frame img {
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: 52vh;
  border-radius: 12px;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.2);
}

.poster-placeholder {
  display: grid;
  place-items: center;
  width: 100%;
  min-height: 220px;
  padding: 20px;
  border-radius: 12px;
  background: #f4f6fa;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}

.success-poster:disabled {
  opacity: 0.6;
}

.success-poster {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  margin-top: 14px;
}

.success-done {
  width: 100%;
  margin-top: 6px;
  border: 0;
  background: transparent;
  padding: 10px;
  color: var(--muted);
  font-size: 13px;
}
</style>
