const EARTH_RADIUS_METERS = 6371000

export const MAX_ACCURACY_METERS = 50
export const MAX_POINTS = 5000

export function haversineMeters(lat1, lng1, lat2, lng2) {
  const rad = Math.PI / 180
  const deltaLat = (lat2 - lat1) * rad
  const deltaLng = (lng2 - lng1) * rad
  const a = Math.sin(deltaLat / 2) ** 2
    + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(deltaLng / 2) ** 2
  return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

export function maxSpeedFor(sport) {
  if (sport === '骑行') return 25
  if (sport === '快走') return 8
  return 12
}

export function shouldAcceptPoint(point, previous, sport) {
  if (!point
    || !Number.isFinite(point.lat)
    || !Number.isFinite(point.lng)
    || !Number.isFinite(point.t)) {
    return false
  }
  if (Number.isFinite(point.acc) && point.acc > MAX_ACCURACY_METERS) return false
  if (!previous) return true

  const gap = haversineMeters(previous.lat, previous.lng, point.lat, point.lng)
  if (gap < 2) return false

  const deltaTime = point.t - previous.t
  if (deltaTime <= 0) return false
  return gap / deltaTime <= maxSpeedFor(sport)
}

export function distanceParts(meters) {
  if (!Number.isFinite(meters) || meters < 0) return { value: '0', unit: 'm' }
  if (meters < 1000) return { value: String(Math.round(meters)), unit: 'm' }
  return { value: (meters / 1000).toFixed(2), unit: 'km' }
}

export function formatDistance(meters) {
  const parts = distanceParts(meters)
  return `${parts.value} ${parts.unit}`
}

export function formatDuration(totalSeconds) {
  const value = Math.max(0, Math.floor(Number(totalSeconds) || 0))
  const hours = Math.floor(value / 3600)
  const minutes = Math.floor((value % 3600) / 60)
  const seconds = value % 60
  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  }
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

export function formatPace(durationSec, distanceM) {
  if (!durationSec || !distanceM || distanceM < 10) return '--'
  const pace = durationSec / (distanceM / 1000)
  if (!Number.isFinite(pace) || pace <= 0 || pace > 3600) return '--'
  const minutes = Math.floor(pace / 60)
  const seconds = Math.round(pace % 60)
  return `${minutes}'${String(seconds).padStart(2, '0')}"`
}

export function formatSpeed(durationSec, distanceM) {
  if (!durationSec || !distanceM) return '0.0'
  const speed = (distanceM / 1000) / (durationSec / 3600)
  if (!Number.isFinite(speed) || speed < 0) return '0.0'
  return speed.toFixed(1)
}

export function limitPoints(points, max = MAX_POINTS) {
  if (!Array.isArray(points) || points.length <= max) return points
  const step = Math.ceil(points.length / max)
  const result = []
  for (let i = 0; i < points.length; i += step) result.push(points[i])
  if (result[result.length - 1] !== points[points.length - 1]) {
    result.push(points[points.length - 1])
  }
  return result
}
