import {
  Bike,
  Dumbbell,
  Feather,
  Footprints,
  Sparkles,
  Waves,
  Wind,
  Zap
} from 'lucide-vue-next'

export const SPORTS = [
  { name: '跑步', met: 8.0, icon: Footprints },
  { name: '快走', met: 4.3, icon: Wind },
  { name: '骑行', met: 7.5, icon: Bike },
  { name: '跳绳', met: 12.3, icon: Zap },
  { name: '游泳', met: 6.0, icon: Waves },
  { name: '力量训练', met: 5.5, icon: Dumbbell },
  { name: '羽毛球', met: 5.5, icon: Feather }
]

export const INTENSITY_PRESETS = [
  { label: '轻松', met: 3.0, hint: '散步、拉伸' },
  { label: '中等', met: 5.5, hint: '日常锻炼' },
  { label: '高强度', met: 8.0, hint: '有氧出汗' },
  { label: '很激烈', met: 11.0, hint: '冲刺、对抗' }
]

export const BUILTIN_NAMES = new Set(SPORTS.map((sport) => sport.name))

export const toSportView = (sport) => ({
  name: sport.name,
  met: Number(sport.met),
  icon: Sparkles,
  custom: true,
  id: sport.id
})

export const mergeSports = (customSports = []) => [
  ...SPORTS,
  ...customSports.map(toSportView)
]

export const genderFactor = (gender) => (gender === 'female' ? 0.9 : 1)

export const calcCalories = (met, weight, minutes, gender = 'secret') =>
  Math.round((met * 3.5 * weight * minutes) / 200 * genderFactor(gender))

const TAG_VARIANTS = ['', 'green', 'cyan', 'gold', 'violet', 'orange', 'red']

export const sportTagClass = (name) => {
  const variant = TAG_VARIANTS[SPORTS.findIndex((sport) => sport.name === name)]
  return variant ? `admin-tag admin-tag--${variant}` : 'admin-tag'
}

export const todayShanghai = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
