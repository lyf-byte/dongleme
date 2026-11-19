import { Bike, Dumbbell, Footprints, Waves, Wind, Zap } from 'lucide-vue-next'

export const SPORTS = [
  { name: '跑步', met: 8.0, icon: Footprints },
  { name: '快走', met: 4.3, icon: Wind },
  { name: '骑行', met: 7.5, icon: Bike },
  { name: '跳绳', met: 12.3, icon: Zap },
  { name: '游泳', met: 6.0, icon: Waves },
  { name: '力量训练', met: 5.5, icon: Dumbbell }
]

export const calcCalories = (met, weight, minutes) =>
  Math.round(met * weight * (minutes / 60))

const TAG_VARIANTS = ['', 'green', 'cyan', 'gold', 'violet', 'orange']

export const sportTagClass = (name) => {
  const variant = TAG_VARIANTS[SPORTS.findIndex((sport) => sport.name === name)]
  return variant ? `admin-tag admin-tag--${variant}` : 'admin-tag'
}

export const todayShanghai = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
