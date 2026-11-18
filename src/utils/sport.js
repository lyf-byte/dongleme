export const SPORTS = [
  { name: '跑步', met: 8.0 },
  { name: '快走', met: 4.3 },
  { name: '骑行', met: 7.5 },
  { name: '跳绳', met: 12.3 },
  { name: '游泳', met: 6.0 },
  { name: '力量训练', met: 5.5 }
]

export const calcCalories = (met, weight, minutes) =>
  Math.round(met * weight * (minutes / 60))

export const todayShanghai = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
