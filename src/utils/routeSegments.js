import { haversineMeters } from './geo'

/**
 * 配速分段着色：把轨迹点按速度/配速区间拆成多段，
 * 每段一个颜色，供 RouteMap 渲染多段 Polyline。
 */

export const SEGMENT_ZONES = [
  { key: 'fast', label: '快', color: '#f43f5e' },
  { key: 'mid', label: '中', color: '#f59e0b' },
  { key: 'slow', label: '慢', color: '#22c55e' },
  { key: 'stop', label: '停留', color: '#94a3b8' }
]

/* 阈值为速度上界（km/h）：高于第一档为"快"，依此类推，低于末档算停留 */
const THRESHOLDS = {
  跑步: [10.9, 7.5, 2.0],
  快走: [8.0, 5.4, 1.8],
  骑行: [25, 15, 5]
}
const DEFAULT_THRESHOLDS = THRESHOLDS.跑步

const ZONE_MAP = Object.fromEntries(SEGMENT_ZONES.map((zone) => [zone.key, zone]))

/** 逐点累计距离（米），返回与 points 等长的数组 */
export function cumulativeDistances(points) {
  const dists = [0]
  for (let i = 1; i < points.length; i += 1) {
    const prev = points[i - 1]
    const cur = points[i]
    dists.push(
      dists[i - 1] +
        (Number.isFinite(prev?.lat) && Number.isFinite(cur?.lat)
          ? haversineMeters(prev.lat, prev.lng, cur.lat, cur.lng)
          : 0)
    )
  }
  return dists
}

function zoneFor(speedKmh, thresholds) {
  if (speedKmh > thresholds[0]) return 'fast'
  if (speedKmh > thresholds[1]) return 'mid'
  if (speedKmh > thresholds[2]) return 'slow'
  return 'stop'
}

/**
 * 主入口。
 * points: [{lat, lng, t(秒), acc}]；sportType: '跑步' | '快走' | '骑行' …
 * 返回 { segments: [{ zone, color, path }], legend: [{ label, color }], hasPace }
 */
export function buildSegments(points, sportType = '跑步') {
  const clean = (points || [])
    .filter(
      (point) =>
        Number.isFinite(point?.lat) &&
        Number.isFinite(point?.lng) &&
        Number.isFinite(Number(point?.t))
    )
    .map((point) => ({ lat: point.lat, lng: point.lng, t: Number(point.t) }))
    .sort((a, b) => a.t - b.t)

  if (clean.length < 3) {
    return { segments: [], legend: [], hasPace: false }
  }

  const thresholds = THRESHOLDS[sportType] || DEFAULT_THRESHOLDS
  const dists = cumulativeDistances(clean)

  /* 逐段瞬时速度（i 段 = 点 i-1 → 点 i），并做一次平滑去抖 */
  const speeds = [0]
  for (let i = 1; i < clean.length; i += 1) {
    const dt = clean[i].t - clean[i - 1].t
    const dd = dists[i] - dists[i - 1]
    const kmh = dt > 0 ? (dd / dt) * 3.6 : 0
    speeds.push(Math.max(0, kmh))
  }
  for (let i = 1; i < speeds.length - 1; i += 1) {
    speeds[i] = (speeds[i - 1] + speeds[i] * 2 + speeds[i + 1]) / 4
  }

  /* 每个点继承"到达它那一段"的速度区间 */
  const zones = clean.map((_, i) =>
    i === 0 ? zoneFor(speeds[1] ?? 0, thresholds) : zoneFor(speeds[i], thresholds)
  )

  /* 孤立区间回填：与前后都不同则并入前一段，避免地图上碎色 */
  for (let i = 1; i < zones.length - 1; i += 1) {
    if (zones[i] !== zones[i - 1] && zones[i] !== zones[i + 1]) {
      zones[i] = zones[i - 1]
    }
  }

  /* 合并相邻同色区间；新段以"上一个点"开头，保证段与段首尾相接 */
  const segments = []
  let start = 0
  for (let i = 1; i < clean.length; i += 1) {
    if (zones[i] !== zones[start]) {
      segments.push({ zone: zones[start], from: start, to: i })
      start = i
    }
  }
  segments.push({ zone: zones[start], from: start, to: clean.length - 1 })

  const paths = segments
    .filter((segment) => segment.to > segment.from)
    .map((segment) => ({
      zone: segment.zone,
      color: ZONE_MAP[segment.zone].color,
      path: clean
        .slice(segment.from, segment.to + 1)
        .map((point) => [point.lng, point.lat])
    }))

  const presentZones = SEGMENT_ZONES.filter((zone) =>
    paths.some((segment) => segment.zone === zone.key)
  )

  return {
    segments: paths,
    legend: presentZones.map((zone) => ({ label: zone.label, color: zone.color })),
    hasPace: new Set(paths.map((segment) => segment.zone)).size > 1
  }
}
