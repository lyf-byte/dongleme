/**
 * 天气场景主题：全项目唯一的「天气类型 → 视觉配置」映射表。
 *
 * 约定：
 * - 后端 WeatherService 负责「算」：天气类型、昼夜、温度、空气质量都在服务端确定。
 * - 本文件负责「翻译」：把 weatherType 翻译成天空色板与要显示的图层。
 * - weatherEngine.js（Canvas 动画）与 WeatherScene.vue（SVG 兜底）只负责「画」，
 *   两者都从这里取配置，不再各自维护一份类型判断。
 */

export const WEATHER_TYPES = [
  'sunny',
  'cloudy',
  'overcast',
  'rain',
  'thunder',
  'snow',
  'fog',
  'haze'
]

/** 天空渐变色板，从上到下三个色标 */
export const SKY = {
  sunny: { day: ['#3d78d8', '#5a8fd8', '#7aa6cf'], night: ['#1e3a8a', '#16255f', '#0f172a'] },
  cloudy: { day: ['#5b7c99', '#3f5a75', '#233446'], night: ['#33465e', '#243447', '#141e2b'] },
  overcast: { day: ['#5c6b7a', '#46525e', '#2f3a45'], night: ['#3a4450', '#2b333c', '#1b2129'] },
  rain: { day: ['#2c5364', '#1d3a47', '#0f2027'], night: ['#1b3540', '#12242e', '#08131a'] },
  thunder: { day: ['#414345', '#2b2d2f', '#1a1b1c'], night: ['#26282a', '#1c1e20', '#101112'] },
  snow: { day: ['#4a6fa5', '#3a5a8c', '#274060'], night: ['#2c4269', '#22334f', '#141f33'] },
  fog: { day: ['#606c88', '#4e5a75', '#3f4c6b'], night: ['#3b4257', '#2e3444', '#20242f'] },
  haze: { day: ['#7a6a53', '#5c5040', '#3e3529'], night: ['#4a4136', '#37302a', '#241f1a'] }
}

/** 定位失败或未取到天气时的中性主题 */
export const DEFAULT_THEME = Object.freeze({ type: 'default', isDay: true })

const DARK_CLOUD_TYPES = ['rain', 'thunder']
const STARLESS_TYPES = ['rain', 'thunder', 'snow']

/** 未知类型统一回落到 cloudy，避免脏数据把画面打空 */
export function normalizeType(type) {
  return Object.prototype.hasOwnProperty.call(SKY, type) ? type : 'cloudy'
}

/** 云层贴图色调：雨/雷用暗云，阴天用灰云，其余用亮云 */
export function cloudKindOf(type) {
  if (DARK_CLOUD_TYPES.includes(type)) return 'cloudDark'
  return type === 'overcast' ? 'cloudGray' : 'cloudLight'
}

/**
 * 把后端返回的 weatherType + isDay 翻译成一份完整的场景描述。
 * Canvas 引擎按需取 sky / cloudKind，SVG 场景按需取 show* 开关。
 */
export function sceneOf(type, isDay) {
  const normalized = normalizeType(type)
  const daytime = isDay !== false

  return {
    type: normalized,
    isDay: daytime,
    sky: SKY[normalized][daytime ? 'day' : 'night'],
    cloudKind: cloudKindOf(normalized),
    darkClouds: DARK_CLOUD_TYPES.includes(normalized),
    showSun: normalized === 'sunny' && daytime,
    showDimSun: daytime && ['fog', 'haze'].includes(normalized),
    showClouds: ['cloudy', 'overcast', 'rain', 'snow', 'thunder', 'fog', 'haze'].includes(normalized),
    showRain: ['rain', 'thunder'].includes(normalized),
    showSnow: normalized === 'snow',
    showThunder: normalized === 'thunder',
    showFog: normalized === 'fog',
    showHaze: normalized === 'haze',
    showStars: !daytime && !STARLESS_TYPES.includes(normalized)
  }
}
