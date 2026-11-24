import { todayShanghai } from './sport'

const KEY = 'dongleme.reminder'
const DISMISS_KEY = 'dongleme.reminder.dismissed'
const DEFAULT = { enabled: true, time: '20:00' }

/** 读取应用内提醒设置，缺省每天 20:00 提醒 */
export function getReminder() {
  try {
    const raw = JSON.parse(localStorage.getItem(KEY))
    if (!raw || typeof raw !== 'object') return { ...DEFAULT }
    return {
      enabled: raw.enabled !== false,
      time: /^\d{2}:\d{2}$/.test(raw.time) ? raw.time : DEFAULT.time
    }
  } catch {
    return { ...DEFAULT }
  }
}

export function saveReminder(next) {
  try {
    localStorage.setItem(KEY, JSON.stringify({
      enabled: Boolean(next.enabled),
      time: /^\d{2}:\d{2}$/.test(next.time) ? next.time : DEFAULT.time
    }))
  } catch {
    /* 隐身模式等场景忽略 */
  }
}

function shanghaiTime() {
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Shanghai',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23'
  }).format(new Date())
}

/** 是否已到提醒时间 */
export function reminderDue() {
  const reminder = getReminder()
  if (!reminder.enabled) return false
  return shanghaiTime() >= reminder.time
}

export function isDismissedToday() {
  try {
    return localStorage.getItem(DISMISS_KEY) === todayShanghai()
  } catch {
    return false
  }
}

export function dismissReminderToday() {
  try {
    localStorage.setItem(DISMISS_KEY, todayShanghai())
  } catch {
    /* 忽略存储异常 */
  }
}
