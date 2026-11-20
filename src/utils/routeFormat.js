import { SPORTS } from './sport'
import { formatDistance, formatDuration, formatPace, formatSpeed } from './geo'

/** 后端返回的日期没有时区后缀，统一按 Asia/Shanghai 解析 */
export function parseApiDate(value) {
  if (value == null) return null
  if (value instanceof Date) return value
  if (typeof value === 'number') return new Date(value)
  if (/[zZ]|[+-]\d{2}:\d{2}$/.test(value)) return new Date(value)
  return new Date(`${value}+08:00`)
}

export function formatWhen(value) {
  const date = parseApiDate(value)
  if (!date || Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'Asia/Shanghai'
  }).format(date)
}

export function sportMeta(name) {
  return SPORTS.find((item) => item.name === name)
}

/** 骑行看时速，其余看配速 */
export function routePace(item) {
  return item.type === '骑行'
    ? `${formatSpeed(item.durationSec, item.distanceM)} km/h`
    : formatPace(item.durationSec, item.distanceM)
}

export function routeSummaryText(item) {
  return `${formatWhen(item.startedAt)} · ${formatDuration(item.durationSec)} · ${routePace(item)}`
}

export function routeTitle(item) {
  return `${item.type} · ${formatDistance(item.distanceM)}`
}
