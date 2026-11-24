import {
  CalendarCheck,
  Clock3,
  Crown,
  Flame,
  Medal,
  Sparkles,
  Target,
  Zap
} from 'lucide-vue-next'

/** 勋章档位视觉：颜色 + 浅底 + 标签 */
export const TIERS = {
  bronze: { label: '铜', color: '#b45309', soft: '#fdf0e0' },
  silver: { label: '银', color: '#64748b', soft: '#eef2f7' },
  gold: { label: '金', color: '#d97706', soft: '#fff5d9' },
  violet: { label: '钻', color: '#8b5cf6', soft: '#f1ecff' }
}

/** 从 ProfileView 归一出勋章引擎需要的统计口径 */
export function statsFromProfile(profile) {
  const total = profile?.total || {}
  return {
    count: total.count || 0,
    minutes: total.minutes || 0,
    calories: total.calories || 0,
    streak: profile?.streak || 0,
    weekMinutes: profile?.weekMinutes || 0,
    weekDaysCount: (profile?.weekDays || []).length,
    weeklyGoal: profile?.weeklyGoal || 150
  }
}

/**
 * 勋章规则表：levels 多档位（铜/银/金），metric 从统计口径取当前值。
 * special: 'weekGoal' 表示"本周时长达成本周目标"，目标随用户设置变化。
 */
const DEFS = [
  {
    id: 'first-checkin',
    name: '初次打卡',
    desc: '完成第 1 次运动打卡',
    icon: Sparkles,
    unit: '次',
    levels: [{ value: 1, tier: 'bronze' }],
    metric: (s) => s.count
  },
  {
    id: 'count-master',
    name: '百炼成钢',
    desc: '累计打卡 10 / 50 / 200 次',
    icon: CalendarCheck,
    unit: '次',
    levels: [
      { value: 10, tier: 'bronze' },
      { value: 50, tier: 'silver' },
      { value: 200, tier: 'gold' }
    ],
    metric: (s) => s.count
  },
  {
    id: 'streak-fire',
    name: '星火燎原',
    desc: '连续打卡 3 / 7 / 30 天',
    icon: Flame,
    unit: '天',
    levels: [
      { value: 3, tier: 'bronze' },
      { value: 7, tier: 'silver' },
      { value: 30, tier: 'gold' }
    ],
    metric: (s) => s.streak
  },
  {
    id: 'time-keeper',
    name: '持之以恒',
    desc: '累计运动 100 / 500 / 2000 分钟',
    icon: Clock3,
    unit: '分钟',
    levels: [
      { value: 100, tier: 'bronze' },
      { value: 500, tier: 'silver' },
      { value: 2000, tier: 'gold' }
    ],
    metric: (s) => s.minutes
  },
  {
    id: 'calorie-burner',
    name: '热力全开',
    desc: '累计消耗 1000 / 5000 / 20000 千卡',
    icon: Zap,
    unit: '千卡',
    levels: [
      { value: 1000, tier: 'bronze' },
      { value: 5000, tier: 'silver' },
      { value: 20000, tier: 'gold' }
    ],
    metric: (s) => s.calories
  },
  {
    id: 'week-goal',
    name: '本周达标',
    desc: '本周运动时长达成周目标',
    icon: Target,
    unit: '分钟',
    special: 'weekGoal'
  },
  {
    id: 'week-full',
    name: '本周全勤',
    desc: '本周 7 天全部完成打卡',
    icon: Medal,
    unit: '天',
    levels: [{ value: 7, tier: 'gold' }],
    metric: (s) => s.weekDaysCount
  },
  {
    id: 'ambition',
    name: '雄心壮志',
    desc: '把周目标定到 300 分钟以上',
    icon: Crown,
    unit: '分钟',
    levels: [{ value: 300, tier: 'violet' }],
    metric: (s) => s.weeklyGoal
  }
]

function resolveBadge(def, stats) {
  if (def.special === 'weekGoal') {
    const goal = stats.weeklyGoal
    const current = Math.min(stats.weekMinutes, goal)
    const earned = goal > 0 && stats.weekMinutes >= goal
    return {
      id: def.id,
      name: def.name,
      desc: def.desc,
      icon: def.icon,
      unit: '分钟',
      earned,
      current,
      target: goal,
      percent: goal > 0 ? Math.min(100, Math.round((stats.weekMinutes / goal) * 100)) : 0,
      tierStyle: earned ? TIERS.violet : null
    }
  }

  const current = Math.max(0, def.metric(stats) || 0)
  let tierIndex = -1
  def.levels.forEach((level, index) => {
    if (current >= level.value) tierIndex = index
  })
  const achieved = tierIndex >= 0 ? def.levels[tierIndex] : null
  const isMax = tierIndex === def.levels.length - 1
  const next = def.levels[Math.min(tierIndex + 1, def.levels.length - 1)]
  const target = achieved && isMax ? achieved.value : next.value
  const percent = Math.min(100, Math.round((current / target) * 100))

  return {
    id: def.id,
    name: def.name,
    desc: def.desc,
    icon: def.icon,
    unit: def.unit,
    earned: Boolean(achieved),
    current: Math.min(current, target),
    target,
    percent,
    tierStyle: achieved ? TIERS[achieved.tier] : null
  }
}

/** 引擎入口：传入统计口径，返回带进度与档位样式的勋章列表 */
export function evaluateBadges(stats) {
  return DEFS.map((def) => resolveBadge(def, stats))
}

const STORE_KEY = 'dongleme.badges.unlocked'

function loadUnlockedIds() {
  try {
    const raw = JSON.parse(localStorage.getItem(STORE_KEY))
    return Array.isArray(raw) ? raw : []
  } catch {
    return []
  }
}

/**
 * 对比本地已解锁记录，返回本次新解锁的勋章并写入记录。
 * 在首页每次加载 profile 后调用。
 */
export function consumeNewBadges(list) {
  const known = new Set(loadUnlockedIds())
  const fresh = list.filter((badge) => badge.earned && !known.has(badge.id))
  list.forEach((badge) => {
    if (badge.earned) known.add(badge.id)
  })
  try {
    localStorage.setItem(STORE_KEY, JSON.stringify([...known]))
  } catch {
    /* 隐身模式等场景忽略 */
  }
  return fresh
}
