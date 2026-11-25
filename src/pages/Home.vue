<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  BellRing,
  Check,
  Clock3,
  Flame,
  Navigation,
  Plus,
  RefreshCw,
  Settings2,
  Target,
  Timer
} from 'lucide-vue-next'
import { getProfile, getCustomSports, getMakeupStatus } from '../utils/api'
import { mergeSports } from '../utils/sport'
import { avatarFor } from '../utils/avatar'
import { useWeather } from '../composables/useWeather'
import { useCountUp } from '../composables/useCountUp'
import { syncBadges } from '../composables/useBadges'
import WeatherInline from '../components/WeatherInline.vue'
import WeatherScene from '../components/WeatherScene.vue'
import WeatherCanvas from '../components/WeatherCanvas.vue'
import CustomSportManager from '../components/CustomSportManager.vue'
import HomeSportGrid from '../components/HomeSportGrid.vue'
import HomeRecentRecords from '../components/HomeRecentRecords.vue'
import GoalRing from '../components/GoalRing.vue'
import GoalSheet from '../components/GoalSheet.vue'
import MakeupSheet from '../components/MakeupSheet.vue'
import BadgeUnlock from '../components/BadgeUnlock.vue'

const router = useRouter()
const loading = ref(true)
const profile = ref(null)
const {
  state: weatherState,
  weather,
  errorText: weatherError,
  theme: weatherTheme,
  load: loadWeather
} = useWeather()
const canvasFallback = ref(false)
const customSports = ref([])
const managerOpen = ref(false)
const allSports = computed(() => mergeSports(customSports.value))
const homeSports = computed(() => allSports.value.slice(0, 6))
const hasMoreSports = computed(() => allSports.value.length > 6)

const todayRef = ref(new Date())
const todayStr = computed(() =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(todayRef.value)
)
const dateText = computed(() =>
  new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
    timeZone: 'Asia/Shanghai'
  })
    .format(todayRef.value)
    .replace('日', '日 ')
)

const weekDays = ['一', '二', '三', '四', '五', '六', '日']
const todayIndex = computed(
  () => (new Date(`${todayStr.value}T00:00:00Z`).getUTCDay() + 6) % 7
)

const weekDates = computed(() => {
  const monday = new Date(`${todayStr.value}T00:00:00Z`)
  monday.setUTCDate(monday.getUTCDate() - todayIndex.value)
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(monday)
    d.setUTCDate(d.getUTCDate() + i)
    return d.toISOString().slice(0, 10)
  })
})
const checkedDays = computed(() => new Set(profile.value?.weekDays || []))

const greet = computed(() => {
  const hour = todayRef.value.getHours()
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const reminderText = computed(() => {
  const hour = todayRef.value.getHours()
  if (hour < 11) return '今天还没动，早上适合来一组拉伸'
  if (hour < 18) return '今天还没动，抽 10 分钟走一走'
  return '今天还没动，睡前动一动也算打卡'
})

const avatarText = computed(() => (profile.value?.nickname || '动').slice(0, 1))
const avatarSrc = computed(() => avatarFor(profile.value?.gender))
const today = computed(
  () => profile.value?.today || { minutes: 0, calories: 0, pendingMinutes: 0, pendingCalories: 0, types: [] }
)
const weekMinutes = computed(() => profile.value?.weekMinutes || 0)
const weeklyGoal = computed(() => profile.value?.weeklyGoal || 150)
const progress = computed(() =>
  Math.min(100, Math.round((weekMinutes.value / weeklyGoal.value) * 100))
)
const streakView = useCountUp(computed(() => profile.value?.streak || 0))
const goalOpen = ref(false)
const makeupOpen = ref(false)
const makeup = ref(null)
const unlocked = ref([])

const recentRecords = computed(() => (profile.value?.recent || []).slice(0, 3))
const recentRows = computed(() => recentRecords.value.map((item) => ({
  key: `${item.date}-${item.type}-${item.duration}`,
  type: item.type,
  icon: recordIcon(item.type),
  dateText: formatRecordDate(item.date),
  duration: item.duration,
  calories: item.calories,
  flagged: item.flagged === 1
})))

function recordIcon(type) {
  return allSports.value.find((sport) => sport.name === type)?.icon || Clock3
}

function formatRecordDate(value) {
  if (!value) return ''
  if (value === todayStr.value) return '今天'
  const date = new Date(`${value}T00:00:00Z`)
  const todayDate = new Date(`${todayStr.value}T00:00:00Z`)
  const diff = Math.round((todayDate - date) / 86400000)
  if (diff === 1) return '昨天'
  return `${date.getUTCMonth() + 1}月${date.getUTCDate()}日`
}

const weekTip = computed(() => {
  if (!profile.value) return ''
  if (progress.value >= 100) return '本周目标已完成，给自己一点奖励'
  const rest = weeklyGoal.value - weekMinutes.value
  if (weekMinutes.value === 0) return '本周还没开始，先来 10 分钟'
  return `还差 ${rest} 分钟完成本周目标`
})

async function load() {
  loading.value = true
  try {
    const [profileResult, sportsResult, makeupResult] = await Promise.allSettled([
      getProfile(),
      getCustomSports(),
      getMakeupStatus()
    ])
    profile.value = profileResult.status === 'fulfilled' ? profileResult.value : null
    customSports.value = sportsResult.status === 'fulfilled' ? sportsResult.value : []
    makeup.value = makeupResult.status === 'fulfilled' ? makeupResult.value : null
    if (profile.value) {
      syncBadges(profile.value).then(({ fresh }) => {
        if (fresh.length) unlocked.value = fresh
      })
    }
  } finally {
    loading.value = false
  }
}

function handleGoalSaved(next) {
  if (profile.value) profile.value.weeklyGoal = next
}

async function handleMakeupDone(next) {
  makeup.value = next
  try {
    profile.value = await getProfile()
  } catch {
    /* 补签成功但资料刷新失败时保持当前数据 */
  }
}

function handleSportsChanged(list) {
  customSports.value = list
}

function goCheckin(type) {
  if (type) {
    router.push({ path: '/checkin', query: { type } })
    return
  }
  router.push('/checkin')
}

function refreshDate() {
  if (document.visibilityState === 'visible') {
    todayRef.value = new Date()
  }
}

onMounted(() => {
  load()
  document.addEventListener('visibilitychange', refreshDate)
  window.addEventListener('focus', refreshDate)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', refreshDate)
  window.removeEventListener('focus', refreshDate)
})
</script>

<template>
  <div class="page home">
    <header class="nav-large">
      <div>
        <h1>{{ greet }}，{{ profile?.nickname || '运动新人' }}</h1>
      </div>
      <img v-if="avatarSrc" :src="avatarSrc" alt="" class="avatar" />
      <div v-else class="avatar">{{ avatarText }}</div>
    </header>

    <template v-if="loading">
      <div class="card loading-card">
        <div class="loading-line"><span></span></div>
      </div>
    </template>

    <template v-else-if="profile">
      <section
        class="streak-card"
        :class="[
          `streak-card--${weatherTheme.type}`,
          weatherTheme.isDay ? 'is-day' : 'is-night'
        ]"
      >
        <WeatherCanvas
          v-if="weatherTheme.type !== 'default' && !canvasFallback"
          :type="weatherTheme.type"
          :is-day="weatherTheme.isDay"
          class="streak-weather-canvas"
          @fallback="canvasFallback = true"
        />
        <div v-else-if="weatherTheme.type !== 'default'" class="streak-weather-bg">
          <WeatherScene :type="weatherTheme.type" :is-day="weatherTheme.isDay" />
        </div>

        <svg
          v-if="weatherTheme.type === 'default'"
          class="streak-mark"
          viewBox="0 0 64 64"
          aria-hidden="true"
        >
          <path
            d="M13 40H24L32 47.5L46 28"
            fill="none"
            stroke="rgba(255,255,255,0.14)"
            stroke-width="6"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <circle cx="48.5" cy="21.5" r="5.5" fill="rgba(246,178,27,0.9)" />
        </svg>
        <p class="streak-date">{{ dateText }}</p>
        <div class="streak-top">
          <span class="streak-label">
            <Flame :size="16" />
            连续打卡
          </span>
          <span class="streak-target">目标 {{ weeklyGoal }} 分钟</span>
        </div>

        <div class="streak-main">
          <strong class="streak-num">{{ streakView }}</strong>
          <span>天</span>
          <button type="button" class="streak-btn" @click="goCheckin()">
            去打卡
            <ArrowRight :size="14" />
          </button>
        </div>

        <WeatherInline
          :state="weatherState"
          :weather="weather"
          :error-text="weatherError"
          @retry="loadWeather"
        />

        <div class="week-strip">
          <div
            v-for="(day, index) in weekDays"
            :key="day"
            class="week-strip__day"
            :class="{
              done: checkedDays.has(weekDates[index]),
              today: index === todayIndex
            }"
          >
            <Check v-if="checkedDays.has(weekDates[index])" :size="13" />
            <template v-else>{{ day }}</template>
          </div>
        </div>

        <button
          v-if="makeup && makeup.remaining > 0"
          type="button"
          class="streak-makeup"
          @click="makeupOpen = true"
        >
          漏打卡了？补签卡剩余 {{ makeup.remaining }} 张
        </button>
      </section>

      <div class="today-grid">
        <div class="card today-card">
          <span class="today-icon today-icon--blue"><Clock3 :size="17" /></span>
          <p>今日运动</p>
          <strong>{{ today.minutes }}<span>分钟</span></strong>
          <small v-if="today.pendingMinutes" class="today-pending">
            超出上限 {{ today.pendingMinutes }} 分钟
          </small>
        </div>
        <div class="card today-card">
          <span class="today-icon today-icon--orange"><Flame :size="17" /></span>
          <p>今日消耗</p>
          <strong>{{ today.calories }}<span>千卡</span></strong>
        </div>
        <div class="card today-card">
          <span class="today-icon today-icon--violet"><Target :size="17" /></span>
          <p>本周时长</p>
          <strong>{{ weekMinutes }}<span>分钟</span></strong>
        </div>
      </div>

      <div v-if="today.types && today.types.length" class="today-done">
        <span class="today-done__label">今日完成</span>
        <span v-for="type in today.types" :key="type" class="chip chip--blue">{{ type }}</span>
      </div>

      <button v-else type="button" class="remind-card" @click="goCheckin()">
        <span class="remind-icon"><BellRing :size="17" /></span>
        <span class="remind-text">
          <strong>{{ reminderText }}</strong>
          <small>动 10 分钟也算完成，别断了连续打卡</small>
        </span>
        <ArrowRight :size="16" />
      </button>

      <section class="card week-card">
        <GoalRing :value="weekMinutes" :goal="weeklyGoal" :size="94" :thickness="9">
          <template #default="{ percent }">
            <strong>{{ percent }}<em>%</em></strong>
          </template>
        </GoalRing>
        <div class="week-body">
          <div class="week-head">
            <h2>本周进度</h2>
            <button type="button" class="goal-edit" @click="goalOpen = true">
              调整目标
            </button>
          </div>
          <p class="week-value">
            <strong>{{ weekMinutes }}</strong>
            <span>/ {{ weeklyGoal }} 分钟</span>
          </p>
          <p class="week-tip">{{ weekTip }}</p>
        </div>
      </section>

      <template v-if="recentRecords.length">
        <div class="section-title">
          <h2>最近记录</h2>
          <button type="button" class="link-btn" @click="router.push('/stats')">
            查看统计
          </button>
        </div>

        <HomeRecentRecords :rows="recentRows" />
      </template>

      <div class="section-title">
        <h2>选择运动</h2>
        <button type="button" class="manage-btn" @click="managerOpen = true">
          <Settings2 :size="13" />
          自定义
        </button>
      </div>

      <HomeSportGrid
        :sports="homeSports"
        :total="allSports.length"
        :has-more="hasMoreSports"
        @pick="goCheckin"
      />

      <div class="home-actions">
        <button type="button" class="btn-primary home-cta" @click="goCheckin()">
          <Plus :size="18" />
          记录一次运动
        </button>
        <button type="button" class="home-secondary" @click="router.push('/track')">
          <Navigation :size="18" />
          轨迹运动
        </button>
        <button type="button" class="home-secondary" @click="router.push('/timer')">
          <Timer :size="18" />
          运动计时
        </button>
      </div>

      <CustomSportManager v-model="managerOpen" @changed="handleSportsChanged" />

      <GoalSheet
        v-model="goalOpen"
        :goal="weeklyGoal"
        :profile="profile"
        @saved="handleGoalSaved"
      />

      <MakeupSheet v-model="makeupOpen" :status="makeup" @done="handleMakeupDone" />

      <BadgeUnlock :badges="unlocked" @close="unlocked = []" />
    </template>

    <div v-else class="card empty-state">
      <span class="empty-state__icon"><RefreshCw :size="24" /></span>
      <h3>加载失败</h3>
      <p>数据没有加载出来，请重试。</p>
      <button type="button" class="btn-primary btn-sm empty-btn" @click="load">重新加载</button>
    </div>
  </div>
</template>

<style scoped>
.home {
  padding-top: 0;
}

.loading-card {
  padding: 18px;
  margin-top: 8px;
}

.streak-card {
  position: relative;
  overflow: hidden;
  margin-top: 4px;
  padding: 18px;
  border-radius: 24px;
  background: var(--blue);
  color: #fff;
  box-shadow: 0 14px 30px rgba(47, 107, 255, 0.25);
  transition: box-shadow 0.3s ease;
}

.streak-card::before {
  content: '';
  position: absolute;
  inset: -30%;
  z-index: 0;
  background:
    radial-gradient(circle at 75% 20%, rgba(255, 255, 255, 0.16), transparent 42%),
    radial-gradient(circle at 20% 85%, rgba(255, 255, 255, 0.08), transparent 48%);
  animation: cardAurora 14s ease-in-out infinite alternate;
  pointer-events: none;
}

.streak-card::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 1;
  background:
    linear-gradient(180deg, transparent 55%, rgba(2, 8, 23, 0.18) 100%),
    linear-gradient(
      90deg,
      rgba(2, 8, 23, 0.3),
      rgba(2, 8, 23, 0.08) 54%,
      transparent 76%
    );
  pointer-events: none;
}

.streak-weather-canvas {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
}

.streak-weather-bg {
  position: absolute;
  top: -36px;
  right: -24px;
  z-index: 1;
  width: 252px;
  height: 252px;
  opacity: 0.62;
  pointer-events: none;
  -webkit-mask-image: radial-gradient(closest-side, #000 52%, transparent 96%);
  mask-image: radial-gradient(closest-side, #000 52%, transparent 96%);
}

.streak-card.streak-card--sunny {
  background: linear-gradient(160deg, #3d78d8, #5a8fd8 55%, #7aa6cf);
  box-shadow: 0 14px 30px rgba(61, 120, 216, 0.28);
}

.streak-card.streak-card--sunny.is-night {
  background: linear-gradient(135deg, #1e3a8a, #0f172a);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.35);
}

.streak-card.streak-card--cloudy {
  background: linear-gradient(135deg, #5b7c99, #233446);
  box-shadow: 0 14px 30px rgba(35, 52, 70, 0.3);
}

.streak-card.streak-card--overcast {
  background: linear-gradient(135deg, #5c6b7a, #2f3a45);
  box-shadow: 0 14px 30px rgba(47, 58, 69, 0.3);
}

.streak-card.streak-card--rain {
  background: linear-gradient(135deg, #2c5364, #0f2027);
  box-shadow: 0 14px 30px rgba(15, 32, 39, 0.35);
}

.streak-card.streak-card--thunder {
  background: linear-gradient(135deg, #232526, #414345);
  box-shadow: 0 14px 30px rgba(20, 20, 20, 0.35);
}

.streak-card.streak-card--snow {
  background: linear-gradient(135deg, #4a6fa5, #274060);
  box-shadow: 0 14px 30px rgba(39, 64, 96, 0.3);
}

.streak-card.streak-card--fog {
  background: linear-gradient(135deg, #606c88, #3f4c6b);
  box-shadow: 0 14px 30px rgba(63, 76, 107, 0.3);
}

.streak-card.streak-card--haze {
  background: linear-gradient(135deg, #7a6a53, #3e3529);
  box-shadow: 0 14px 30px rgba(62, 53, 41, 0.35);
}

.streak-mark {
  position: absolute;
  width: 150px;
  height: 150px;
  right: -26px;
  top: -30px;
  pointer-events: none;
}

.streak-top {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.streak-date {
  position: relative;
  z-index: 2;
  margin: 0 0 6px;
  font-size: 11px;
  line-height: 15px;
  color: rgba(255, 255, 255, 0.7);
  letter-spacing: 0.02em;
}

.streak-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.86);
}

.streak-target {
  padding: 4px 9px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  font-size: 11px;
  color: rgba(255, 255, 255, 0.9);
}

.streak-main {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 8px;
}

.streak-main strong {
  font-size: 58px;
  line-height: 64px;
  font-weight: 700;
  letter-spacing: -0.04em;
  font-variant-numeric: tabular-nums;
}

.streak-main > span {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.82);
}

.streak-num {
  display: inline-block;
  animation: streakPop 0.7s cubic-bezier(0.2, 0.8, 0.2, 1) both;
}

.streak-label svg {
  animation: flamePulse 2.6s ease-in-out infinite;
  transform-origin: center;
}

.streak-btn {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  padding: 0 13px;
  border: 0;
  border-radius: 999px;
  background: #fff;
  color: var(--blue);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.week-strip {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
  margin-top: 16px;
}

.streak-makeup {
  position: relative;
  z-index: 2;
  display: block;
  margin: 10px auto 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: rgba(255, 255, 255, 0.78);
  font-size: 11px;
  font-weight: 500;
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.week-strip__day {
  position: relative;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  justify-self: center;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
  color: rgba(255, 255, 255, 0.72);
  font-size: 11px;
  font-weight: 500;
}

.week-strip__day.today {
  background: transparent;
  border: 2px solid #fff;
  color: #fff;
  font-weight: 700;
}

.week-strip__day.done {
  background: #fff;
  color: var(--blue);
  font-weight: 700;
}

.week-strip__day.done.today {
  border: 0;
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.35);
}

.today-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 14px;
}

.today-card {
  padding: 12px 10px 13px;
  border-radius: 16px;
}

.today-icon {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 11px;
}

.today-icon--blue {
  background: var(--blue-soft);
  color: var(--blue);
}

.today-icon--orange {
  background: var(--orange-soft);
  color: var(--orange);
}

.today-icon--violet {
  background: var(--violet-soft);
  color: var(--violet);
}

.today-card p {
  margin: 9px 0 0;
  font-size: 11px;
  color: var(--muted);
}

.today-done {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 16px;
  background: var(--surface);
  border: 1px solid var(--line);
}

.today-done__label {
  margin-right: 2px;
  font-size: 12px;
  color: var(--text-2);
}

.remind-card {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-top: 12px;
  padding: 12px 14px;
  border: 1px dashed var(--blue-soft-2);
  border-radius: 16px;
  background: var(--blue-soft);
  text-align: left;
  cursor: pointer;
}

.remind-icon {
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: #fff;
  color: var(--blue);
}

.remind-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.remind-text strong {
  font-size: 13px;
  line-height: 18px;
  font-weight: 600;
  color: var(--text);
}

.remind-text small {
  font-size: 11px;
  line-height: 15px;
  color: var(--text-2);
}

.remind-card > svg {
  flex: 0 0 auto;
  color: var(--blue);
}

.today-card strong {
  display: block;
  margin-top: 2px;
  font-size: 19px;
  line-height: 25px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.today-card strong span {
  margin-left: 3px;
  font-size: 11px;
  font-weight: 400;
  color: var(--muted);
}

.today-pending {
  display: block;
  margin-top: 2px;
  font-size: 10px;
  line-height: 14px;
  color: var(--muted);
}

.week-card {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 14px;
  padding: 16px;
  border-radius: 20px;
}

.week-body {
  flex: 1;
  min-width: 0;
}

.week-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.week-head h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.goal-edit {
  flex: 0 0 auto;
  padding: 4px 9px;
  border: 1px solid var(--line-2);
  border-radius: 999px;
  background: var(--surface-2);
  color: var(--blue);
  font-family: inherit;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.week-value {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin: 7px 0 0;
}

.week-value strong {
  font-size: 21px;
  font-weight: 700;
  line-height: 24px;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
  color: var(--text);
}

.week-value span {
  font-size: 11px;
  color: var(--muted);
}

.week-tip {
  margin: 5px 0 0;
  font-size: 11px;
  line-height: 16px;
  color: var(--muted);
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


.home-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 18px;
}

.home-cta {
  grid-column: 1 / -1;
  margin-top: 0;
}

.link-btn {
  border: 0;
  background: transparent;
  padding: 0;
  color: var(--blue);
  font-size: 12px;
  font-weight: 600;
}


.home-secondary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 50px;
  border: 1px solid rgba(47, 107, 255, 0.28);
  border-radius: 14px;
  background: #fff;
  color: var(--blue);
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 6px 16px rgba(47, 107, 255, 0.08);
}

.home-secondary:active {
  transform: scale(0.99);
}

@media (max-width: 480px) {
  .streak-weather-bg {
    inset: -4px;
    opacity: 0.3;
  }
}

@keyframes streakPop {
  from {
    opacity: 0;
    transform: translateY(7px) scale(0.94);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes flamePulse {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.88;
  }
  50% {
    transform: scale(1.14);
    opacity: 1;
  }
}

@keyframes cardAurora {
  from {
    transform: translate3d(-2%, -1%, 0) scale(1);
  }
  to {
    transform: translate3d(2%, 1%, 0) scale(1.06);
  }
}

@media (prefers-reduced-motion: reduce) {
  .streak-card::before,
  .streak-num,
  .streak-label svg {
    animation: none;
  }
}

.empty-btn {
  margin: 16px auto 0;
}
</style>
