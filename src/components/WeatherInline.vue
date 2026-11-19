<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { MapPin, RefreshCw } from 'lucide-vue-next'
import { getWeather, errorMessage } from '../utils/api'

const CACHE_KEY = 'dlm_weather_cache'
const CACHE_TTL = 15 * 60 * 1000

const router = useRouter()
const emit = defineEmits(['theme'])
const state = ref('loading')
const weather = ref(null)
const errorText = ref('')

const current = computed(() => weather.value?.current || {})
const air = computed(() => weather.value?.air || {})
const advice = computed(() => weather.value?.advice || {})
const daily = computed(() => weather.value?.daily || [])
const sports = computed(() => advice.value.sports || [])

const type = computed(() => current.value.weatherType || 'cloudy')
const isDay = computed(() => current.value.isDay !== false)
const temperature = computed(() => Math.round(Number(current.value.temperature) || 0))
const humidity = computed(() =>
  current.value.humidity == null ? null : Number(current.value.humidity)
)
const windText = computed(() => {
  if (current.value.windPower) return `风力 ${current.value.windPower} 级`
  if (current.value.windSpeed != null) {
    return `风速 ${Math.round(Number(current.value.windSpeed))} km/h`
  }
  return ''
})

const uvValue = computed(() => {
  const value = current.value.uvIndex ?? air.value.uvIndex
  return value == null ? null : Number(value)
})
const uvText = computed(() => {
  if (uvValue.value == null) return '暂无'
  if (uvValue.value <= 2) return '弱'
  if (uvValue.value <= 5) return '中等'
  if (uvValue.value <= 7) return '强'
  if (uvValue.value <= 10) return '很强'
  return '极强'
})

const aqiClass = computed(() => {
  const aqi = Number(air.value.aqi)
  if (!Number.isFinite(aqi)) return 'badge--unknown'
  if (aqi <= 50) return 'badge--good'
  if (aqi <= 100) return 'badge--moderate'
  if (aqi <= 150) return 'badge--light'
  if (aqi <= 200) return 'badge--heavy'
  if (aqi <= 300) return 'badge--severe'
  return 'badge--hazard'
})
const uvClass = computed(() => {
  if (uvValue.value == null) return 'badge--unknown'
  if (uvValue.value <= 2) return 'badge--good'
  if (uvValue.value <= 5) return 'badge--moderate'
  if (uvValue.value <= 7) return 'badge--light'
  if (uvValue.value <= 10) return 'badge--heavy'
  return 'badge--severe'
})
const adviceClass = computed(() => {
  if (advice.value.level === '适宜') return 'level--good'
  if (advice.value.level === '不建议户外') return 'level--bad'
  return 'level--mid'
})

function dayLabel(date) {
  const value = new Date(`${date}T00:00:00Z`)
  const weeks = ['日', '一', '二', '三', '四', '五', '六']
  return `周${weeks[value.getUTCDay()]}`
}

function goCheckin(sport) {
  router.push({ path: '/checkin', query: { type: sport } })
}

function emitTheme() {
  if (state.value === 'ready' && weather.value) {
    emit('theme', { type: type.value, isDay: isDay.value })
    return
  }
  emit('theme', { type: 'default', isDay: true })
}

function loadFromCache() {
  try {
    const raw = sessionStorage.getItem(CACHE_KEY)
    if (!raw) return false
    const cached = JSON.parse(raw)
    if (Date.now() - cached.at > CACHE_TTL) return false
    weather.value = cached.data
    state.value = 'ready'
    emitTheme()
    return true
  } catch {
    return false
  }
}

function saveCache(data) {
  try {
    sessionStorage.setItem(CACHE_KEY, JSON.stringify({ at: Date.now(), data }))
  } catch {
    /* 忽略存储异常 */
  }
}

function load() {
  if (!navigator.geolocation) {
    state.value = 'denied'
    errorText.value = '当前浏览器不支持定位'
    emitTheme()
    return
  }

  state.value = 'loading'
  errorText.value = ''

  navigator.geolocation.getCurrentPosition(
    async (position) => {
      try {
        const data = await getWeather(position.coords.latitude, position.coords.longitude)
        weather.value = data
        saveCache(data)
        state.value = 'ready'
        emitTheme()
      } catch (error) {
        errorText.value = errorMessage(error, '天气加载失败')
        state.value = 'error'
        emitTheme()
      }
    },
    () => {
      state.value = 'denied'
      errorText.value = '开启定位后查看实时天气'
      emitTheme()
    },
    { timeout: 8000, maximumAge: 10 * 60 * 1000, enableHighAccuracy: false }
  )
}

onMounted(() => {
  if (!loadFromCache()) load()
})
</script>

<template>
  <div v-if="state === 'ready'" class="weather-inline">
    <div class="wi-top">
      <div class="wi-temp">{{ temperature }}°</div>

      <div class="wi-desc">
        <strong>{{ current.condition }}</strong>
        <span class="wi-loc">
          <MapPin :size="11" />
          {{ weather.location.name }}
        </span>
        <span class="wi-meta">
          <template v-if="humidity !== null">湿度 {{ humidity }}%</template>
          <template v-if="humidity !== null && windText"> · </template>
          <template v-if="windText">{{ windText }}</template>
        </span>
      </div>

      <div class="wi-badges">
        <span
          class="wi-badge"
          :class="aqiClass"
          :title="`AQI ${air.aqi ?? '—'}`"
        >
          空气质量 · {{ air.category || '暂无' }}
        </span>
        <span
          class="wi-badge"
          :class="uvClass"
          :title="`UV ${uvValue ?? '—'}`"
        >
          紫外线 · {{ uvText }}
        </span>
      </div>
    </div>

    <div class="wi-advice">
      <span class="wi-level" :class="adviceClass">{{ advice.level }}</span>
      <span>{{ advice.summary }}</span>
    </div>

    <div class="wi-sports">
      <button
        v-for="sport in sports"
        :key="sport.type"
        type="button"
        :title="sport.reason"
        @click="goCheckin(sport.type)"
      >
        {{ sport.type }}
      </button>
    </div>

    <div v-if="daily.length" class="wi-daily">
      <span v-for="day in daily" :key="day.date">
        {{ dayLabel(day.date) }} {{ day.condition }} {{ day.max }}°/{{ day.min }}°
      </span>
    </div>
  </div>

  <div v-else class="weather-inline weather-inline--state">
    <span class="wi-state-icon">
      <MapPin v-if="state === 'denied'" :size="15" />
      <RefreshCw v-else :size="15" :class="{ spinning: state === 'loading' }" />
    </span>
    <span class="wi-state-text">
      {{
        state === 'denied'
          ? '开启定位查看实时天气'
          : state === 'error'
            ? errorText
            : '正在获取天气…'
      }}
    </span>
    <button v-if="state !== 'loading'" type="button" @click="load">
      {{ state === 'denied' ? '重新定位' : '重试' }}
    </button>
  </div>
</template>

<style scoped>
.weather-inline {
  position: relative;
  z-index: 2;
  margin-top: 14px;
}

.wi-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.wi-temp {
  flex: 0 0 auto;
  font-size: 40px;
  line-height: 42px;
  font-weight: 700;
  letter-spacing: -0.04em;
  font-variant-numeric: tabular-nums;
}

.wi-desc {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.wi-desc strong {
  font-size: 13px;
  font-weight: 600;
}

.wi-loc {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.82);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.wi-meta {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.66);
}

.wi-badges {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 5px;
}

.wi-badge {
  display: inline-flex;
  align-items: center;
  height: 21px;
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  font-size: 10px;
  font-weight: 500;
  white-space: nowrap;
}

.badge--good {
  background: rgba(34, 197, 94, 0.34);
}

.badge--moderate {
  background: rgba(250, 204, 21, 0.3);
}

.badge--light {
  background: rgba(249, 115, 22, 0.38);
}

.badge--heavy {
  background: rgba(239, 68, 68, 0.42);
}

.badge--severe {
  background: rgba(147, 51, 234, 0.44);
}

.badge--hazard {
  background: rgba(120, 20, 30, 0.55);
}

.wi-advice {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 10px;
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 11px;
  line-height: 16px;
  color: rgba(255, 255, 255, 0.9);
}

.wi-level {
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
  white-space: nowrap;
}

.level--good {
  background: rgba(34, 197, 94, 0.6);
}

.level--mid {
  background: rgba(250, 204, 21, 0.55);
  color: #3f2f00;
}

.level--bad {
  background: rgba(239, 68, 68, 0.72);
}

.wi-sports {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.wi-sports button {
  padding: 5px 10px;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--blue);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.14s ease;
}

.wi-sports button:active {
  transform: scale(0.96);
}

.wi-daily {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-top: 10px;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.7);
}

.weather-inline--state {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 30px;
}

.wi-state-icon {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
}

.wi-state-text {
  flex: 1;
  min-width: 0;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.8);
}

.weather-inline--state button {
  flex: 0 0 auto;
  padding: 5px 10px;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  color: var(--blue);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.spinning {
  animation: spin 1.2s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 480px) {
  .wi-top {
    flex-wrap: wrap;
  }

  .wi-temp {
    font-size: 34px;
    line-height: 36px;
  }

  .wi-badges {
    flex-direction: row;
    flex-wrap: wrap;
    width: 100%;
    align-items: flex-start;
  }
}

@media (prefers-reduced-motion: reduce) {
  .weather-inline * {
    animation: none !important;
  }
}
</style>
