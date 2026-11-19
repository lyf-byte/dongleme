<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Check, Minus, Plus, Settings2 } from 'lucide-vue-next'
import { getProfile, getCustomSports, submitCheckin, errorMessage } from '../utils/api'
import { SPORTS, calcCalories, mergeSports } from '../utils/sport'
import { toast, confirmDialog } from '../utils/ui'
import CustomSportManager from '../components/CustomSportManager.vue'

const route = useRoute()
const router = useRouter()

const customSports = ref([])
const managerOpen = ref(false)
const allSports = computed(() => mergeSports(customSports.value))

const routeType = typeof route.query.type === 'string' ? route.query.type : ''
const initialSport = allSports.value.find((sport) => sport.name === routeType)

const form = ref({
  type: initialSport ? initialSport.name : '跑步',
  duration: 30,
  weight: 60
})
const gender = ref('secret')
const submitting = ref(false)

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
  form.value.duration = Math.min(600, Math.max(1, Number.isFinite(next) ? next : 1))
}

async function loadProfile() {
  try {
    const profile = await getProfile()
    if (profile?.weight) form.value.weight = profile.weight
    gender.value = profile?.gender || 'secret'
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

  if (!Number.isInteger(duration) || duration < 1 || duration > 600) {
    toast('时长需为 1–600 的整数')
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
      overwrite
    })
    toast(`打卡成功 · ${result.calories} 千卡`)
    setTimeout(() => router.replace('/'), 500)
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
        :class="{ active: form.type === sport.name }"
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
        <span class="type-met">MET {{ sport.met.toFixed(1) }}</span>
        <span v-if="form.type === sport.name" class="type-check">
          <Check :size="12" />
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
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.type-grid button {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 16px;
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

.type-grid button:active {
  transform: scale(0.98);
}

.type-icon {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 10px;
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
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.type-met {
  font-size: 10px;
  color: var(--muted);
}

.type-check {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--blue);
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
</style>
