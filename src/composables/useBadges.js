import { getBadges, unlockBadges } from '../utils/api'
import { evaluateBadges, statsFromProfile } from '../utils/badges'

/**
 * 以服务端为准同步勋章：先拉取已解锁列表，再把本次新达成的勋章上报。
 * 返回服务端已解锁 id 集合，以及本次真正新解锁的勋章（用于解锁动画）。
 */
export async function syncBadges(profile) {
  const earned = evaluateBadges(statsFromProfile(profile)).filter((badge) => badge.earned)

  let ids
  try {
    ids = await getBadges()
  } catch {
    return { ids: new Set(), fresh: [] }
  }

  const known = new Set(ids)
  const fresh = earned.filter((badge) => !known.has(badge.id))
  if (!fresh.length) {
    return { ids: known, fresh: [] }
  }

  try {
    ids = await unlockBadges(fresh.map((badge) => badge.id))
  } catch {
    return { ids: known, fresh: [] }
  }
  return { ids: new Set(ids), fresh }
}
