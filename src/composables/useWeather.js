import { computed, onMounted, ref } from 'vue'
import { getWeather, errorMessage } from '../utils/api'
import { wgs84ToGcj02 } from '../utils/coord'
import { DEFAULT_THEME } from '../utils/weatherTheme'

const CACHE_KEY = 'dlm_weather_cache'
const CACHE_TTL = 15 * 60 * 1000

/**
 * 天气数据层：定位、请求、会话缓存、主题推导。
 * 组件只消费这里返回的 state / weather / theme，不再自己发请求或算主题。
 */
export function useWeather() {
  const state = ref('loading')
  const weather = ref(null)
  const errorText = ref('')

  const current = computed(() => weather.value?.current || {})
  const air = computed(() => weather.value?.air || {})
  const advice = computed(() => weather.value?.advice || {})
  const daily = computed(() => weather.value?.daily || [])
  const sports = computed(() => advice.value.sports || [])

  // 天气类型与昼夜由后端决定，这里只做兜底，不做任何气象判断
  const theme = computed(() => {
    if (state.value !== 'ready' || !weather.value) return DEFAULT_THEME
    return {
      type: current.value.weatherType || 'cloudy',
      isDay: current.value.isDay !== false
    }
  })

  function loadFromCache() {
    try {
      const raw = sessionStorage.getItem(CACHE_KEY)
      if (!raw) return false
      const cached = JSON.parse(raw)
      if (Date.now() - cached.at > CACHE_TTL) return false
      weather.value = cached.data
      state.value = 'ready'
      return true
    } catch {
      return false
    }
  }

  function saveCache(data) {
    try {
      sessionStorage.setItem(CACHE_KEY, JSON.stringify({ at: Date.now(), data }))
    } catch {
      /* 隐私模式下 sessionStorage 可能不可写，忽略即可 */
    }
  }

  function load() {
    if (!navigator.geolocation) {
      state.value = 'denied'
      errorText.value = '当前浏览器不支持定位'
      return
    }

    state.value = 'loading'
    errorText.value = ''

    navigator.geolocation.getCurrentPosition(
      async (position) => {
        try {
          const gcj = wgs84ToGcj02(position.coords.longitude, position.coords.latitude)
          const data = await getWeather(gcj.lat, gcj.lng)
          weather.value = data
          saveCache(data)
          state.value = 'ready'
        } catch (error) {
          errorText.value = errorMessage(error, '天气加载失败')
          state.value = 'error'
        }
      },
      () => {
        state.value = 'denied'
        errorText.value = '开启定位后查看实时天气'
      },
      { timeout: 8000, maximumAge: 10 * 60 * 1000, enableHighAccuracy: false }
    )
  }

  onMounted(() => {
    if (!loadFromCache()) load()
  })

  return {
    state,
    weather,
    errorText,
    current,
    air,
    advice,
    daily,
    sports,
    theme,
    load
  }
}
