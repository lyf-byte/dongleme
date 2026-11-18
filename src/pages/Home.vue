<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Clock3, Flame, Plus, RefreshCw } from 'lucide-vue-next'
import { getProfile } from '../utils/api'
import { SPORTS } from '../utils/sport'

const router = useRouter()
const loading = ref(true)
const profile = ref(null)

const now = new Date()
const todayStr = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(now)
const dateText = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'short',
  timeZone: 'Asia/Shanghai'
})
  .format(now)
  .replace('日', '日 ')

const weekDays = ['一', '二', '三', '四', '五', '六', '日']
const todayIndex = (new Date(`${todayStr}T00:00:00Z`).getUTCDay() + 6) % 7

const greet = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const avatarText = computed(() => (profile.value?.nickname || '动').slice(0, 1))
const today = computed(() => profile.value?.today || { minutes: 0, calories: 0 })
const weekMinutes = computed(() => profile.value?.weekMinutes || 0)
const weeklyGoal = computed(() => profile.value?.weeklyGoal || 150)
const progress = computed(() =>
  Math.min(100, Math.round((weekMinutes.value / weeklyGoal.value) * 100))
)

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
    profile.value = await getProfile()
  } catch {
    profile.value = null
  } finally {
    loading.value = false
  }
}

function goCheckin(type) {
  if (type) {
    router.push({ path: '/checkin', query: { type } })
    return
  }
  router.push('/checkin')
}

onMounted(load)
</script>

<template>
  <div class="page home">
    <header class="nav-large">
      <div>
        <p class="eyebrow">{{ dateText }}</p>
        <h1>{{ greet }}，{{ profile?.nickname || '运动新人' }}</h1>
      </div>
      <div class="avatar">{{ avatarText }}</div>
    </header>

    <template v-if="loading">
      <div class="card loading-card">
        <div class="loading-line"><span></span></div>
      </div>
    </template>

    <template v-else-if="profile">
      <section class="streak-card">
        <span class="streak-orb"></span>
        <div class="streak-top">
          <span class="streak-label">
            <Flame :size="16" />
            连续打卡
          </span>
          <span class="streak-target">目标 {{ weeklyGoal }} 分钟</span>
        </div>

        <div class="streak-main">
          <strong>{{ profile.streak }}</strong>
          <span>天</span>
          <button type="button" class="streak-btn" @click="goCheckin()">
            去打卡
            <ArrowRight :size="14" />
          </button>
        </div>

        <div class="week-strip">
          <div
            v-for="(day, index) in weekDays"
            :key="day"
            class="week-strip__day"
            :class="{ active: index === todayIndex }"
          >
            {{ day }}
          </div>
        </div>
      </section>

      <div class="today-grid">
        <div class="card today-card">
          <span class="today-icon today-icon--blue"><Clock3 :size="18" /></span>
          <p>今日运动</p>
          <strong>{{ today.minutes }}<span>分钟</span></strong>
        </div>
        <div class="card today-card">
          <span class="today-icon today-icon--orange"><Flame :size="18" /></span>
          <p>今日消耗</p>
          <strong>{{ today.calories }}<span>千卡</span></strong>
        </div>
      </div>

      <section class="card week-card">
        <div class="week-head">
          <h2>本周进度</h2>
          <span>{{ weekMinutes }} / {{ weeklyGoal }} 分钟</span>
        </div>
        <div class="bar"><i :style="{ width: progress + '%' }"></i></div>
        <p>{{ weekTip }}</p>
      </section>

      <div class="section-title">
        <h2>选择运动</h2>
        <span>MET 越高，消耗越大</span>
      </div>

      <div class="sport-grid">
        <button
          v-for="(sport, index) in SPORTS"
          :key="sport.name"
          type="button"
          class="sport-card"
          @click="goCheckin(sport.name)"
        >
          <span class="sport-icon" :class="'sport-icon--' + index">
            {{ sport.name.slice(0, 1) }}
          </span>
          <span class="sport-name">{{ sport.name }}</span>
          <span class="sport-met">MET {{ sport.met.toFixed(1) }}</span>
        </button>
      </div>

      <button type="button" class="btn-primary home-cta" @click="goCheckin()">
        <Plus :size="18" />
        记录一次运动
      </button>
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
}

.streak-orb {
  position: absolute;
  width: 180px;
  height: 180px;
  right: -54px;
  top: -74px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  pointer-events: none;
}

.streak-top {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
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
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.18);
}

.week-strip__day {
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

.week-strip__day.active {
  background: #fff;
  color: var(--blue);
  font-weight: 700;
}

.today-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 14px;
}

.today-card {
  padding: 14px;
  border-radius: 18px;
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

.today-card p {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--muted);
}

.today-card strong {
  display: block;
  margin-top: 2px;
  font-size: 24px;
  line-height: 30px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.today-card strong span {
  margin-left: 3px;
  font-size: 11px;
  font-weight: 400;
  color: var(--muted);
}

.week-card {
  margin-top: 14px;
  padding: 16px;
  border-radius: 18px;
}

.week-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.week-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.week-head span {
  font-size: 12px;
  color: var(--muted);
}

.bar {
  height: 8px;
  margin-top: 12px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--blue-soft);
}

.bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--blue);
  transition: width 0.3s ease;
}

.week-card p {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--muted);
}

.sport-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.sport-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  padding: 14px 8px 12px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--surface);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: transform 0.14s ease;
}

.sport-card:active {
  transform: scale(0.97);
}

.sport-icon {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  font-size: 17px;
  font-weight: 700;
}

.sport-icon--0 {
  background: var(--blue-soft);
  color: var(--blue);
}

.sport-icon--1 {
  background: var(--green-soft);
  color: var(--green);
}

.sport-icon--2 {
  background: var(--cyan-soft);
  color: var(--cyan);
}

.sport-icon--3 {
  background: var(--yellow-soft);
  color: #b45309;
}

.sport-icon--4 {
  background: var(--violet-soft);
  color: var(--violet);
}

.sport-icon--5 {
  background: var(--orange-soft);
  color: var(--orange);
}

.sport-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.sport-met {
  font-size: 10px;
  color: var(--muted);
}

.home-cta {
  margin-top: 18px;
}

.empty-btn {
  margin: 16px auto 0;
}
</style>
