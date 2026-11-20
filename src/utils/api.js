import axios from 'axios'
import { getToken, getAdminToken, signOut, adminSignOut } from './auth'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 20000
})

const adminApi = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 20000
})

const PROFILE_TTL = 30000
const DATA_TTL = 30000
const SPORTS_TTL = 30000
const ADMIN_TTL = 15000
const ADMIN_TYPES_TTL = 60000

const adminCache = new Map()

export function clearAdminCache() {
  adminCache.clear()
}

function adminCached(key, ttl, loader, force = false) {
  if (!force) {
    const hit = adminCache.get(key)
    if (hit && Date.now() - hit.at < ttl) return Promise.resolve(hit.data)
  }
  return loader().then((data) => {
    adminCache.set(key, { data, at: Date.now() })
    return data
  })
}

let profileCache = null
let profileCacheAt = 0
let profileCacheToken = ''
let sportsCache = null
let sportsCacheAt = 0
const statsCache = new Map()
const rankCache = new Map()
let summaryCache = null
let summaryCacheAt = 0

export function clearApiCache() {
  profileCache = null
  profileCacheAt = 0
  profileCacheToken = ''
  sportsCache = null
  sportsCacheAt = 0
  statsCache.clear()
  rankCache.clear()
  summaryCache = null
  summaryCacheAt = 0
}

api.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearApiCache()
      signOut()
      if (window.location.pathname !== '/auth') window.location.replace('/auth')
    }
    return Promise.reject(error)
  }
)

adminApi.interceptors.request.use((config) => {
  const token = getAdminToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

adminApi.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearAdminCache()
      adminSignOut()
      if (window.location.pathname.startsWith('/admin')) {
        window.location.replace('/admin/login')
      }
    }
    return Promise.reject(error)
  }
)

export async function submitCheckin(payload) {
  const result = await api.post('/checkin', payload).then((res) => res.data)
  clearApiCache()
  return result
}

export async function createRoute(payload) {
  const result = await api.post('/routes', payload).then((res) => res.data)
  clearApiCache()
  return result
}

export async function getRoutes(limit = 20) {
  const result = await api
    .get('/routes', { params: { limit } })
    .then((res) => res.data)
  return result.routes || []
}

export async function getRoute(id) {
  return api.get(`/routes/${id}`).then((res) => res.data)
}

export async function deleteRoute(id) {
  const result = await api.delete(`/routes/${id}`).then((res) => res.data)
  clearApiCache()
  return result
}

export async function getStats(range = '7d') {
  const cached = statsCache.get(range)
  if (cached && Date.now() - cached.at < DATA_TTL) return cached.data

  const result = await api.get('/stats', { params: { range } }).then((res) => res.data)
  statsCache.set(range, { data: result, at: Date.now() })
  return result
}

export async function getRank(scope = 'week') {
  const cached = rankCache.get(scope)
  if (cached && Date.now() - cached.at < DATA_TTL) return cached.data

  const result = await api.get('/rank', { params: { scope } }).then((res) => res.data)
  rankCache.set(scope, { data: result, at: Date.now() })
  return result
}

export async function getWeather(lat, lon) {
  return api.get('/weather', { params: { lat, lon } }).then((res) => res.data)
}

export async function getCustomSports(force = false) {
  if (!force && sportsCache && Date.now() - sportsCacheAt < SPORTS_TTL) return sportsCache

  const result = await api.get('/sports').then((res) => res.data.sports || [])
  sportsCache = result
  sportsCacheAt = Date.now()
  return result
}

export async function createCustomSport(payload) {
  const result = await api.post('/sports', payload).then((res) => res.data.sport)
  sportsCache = null
  return result
}

export async function updateCustomSport(payload) {
  const result = await api.put('/sports', payload).then((res) => res.data.sport)
  sportsCache = null
  return result
}

export async function deleteCustomSport(id) {
  await api.delete('/sports', { params: { id } })
  sportsCache = null
}

export async function generateSummary(payload = {}) {
  if (payload.peek && summaryCache && Date.now() - summaryCacheAt < DATA_TTL) {
    return summaryCache
  }

  const result = await api.post('/summary', payload).then((res) => res.data)
  if (payload.peek || result.content) {
    summaryCache = result
    summaryCacheAt = Date.now()
  }
  return result
}

export async function getProfile(params) {
  if (params?.export === 1) {
    return api.get('/profile', { params }).then((res) => res.data)
  }

  const token = getToken()

  if (profileCache && profileCacheToken === token && Date.now() - profileCacheAt < PROFILE_TTL) {
    return profileCache
  }

  const result = await api.get('/profile').then((res) => res.data)
  profileCache = result
  profileCacheAt = Date.now()
  profileCacheToken = token
  return result
}

export async function updateProfile(payload) {
  const result = await api.put('/profile', payload).then((res) => res.data)
  clearApiCache()
  return result
}

export async function clearRecords() {
  const result = await api.delete('/profile').then((res) => res.data)
  clearApiCache()
  return result
}

export const adminOverview = (force = false) =>
  adminCached(
    'overview',
    ADMIN_TTL,
    () => adminApi.get('/admin', { params: { action: 'overview' } }).then((res) => res.data),
    force
  )

export const adminUsers = (params = {}, force = false) =>
  adminCached(
    `users:${JSON.stringify(params)}`,
    ADMIN_TTL,
    () => adminApi.get('/admin', { params: { action: 'users', ...params } }).then((res) => res.data),
    force
  )

export const adminUserDetail = (id, force = false) =>
  adminCached(
    `user:${id}`,
    ADMIN_TTL,
    () => adminApi.get('/admin', { params: { action: 'user', id } }).then((res) => res.data),
    force
  )

export const adminRecords = (params = {}, force = false) =>
  adminCached(
    `records:${JSON.stringify(params)}`,
    ADMIN_TTL,
    () => adminApi.get('/admin', { params: { action: 'records', ...params } }).then((res) => res.data),
    force
  )

export const adminRoutes = (params = {}, force = false) =>
  adminCached(
    `routes:${JSON.stringify(params)}`,
    ADMIN_TTL,
    () => adminApi.get('/admin', { params: { action: 'routes', ...params } }).then((res) => res.data),
    force
  )

export const adminRank = (scope = 'week', force = false) =>
  adminCached(
    `rank:${scope}`,
    ADMIN_TTL,
    () => adminApi.get('/admin', { params: { action: 'rank', scope } }).then((res) => res.data),
    force
  )

export const adminRecordTypes = (force = false) =>
  adminCached(
    'types',
    ADMIN_TYPES_TTL,
    () => adminApi.get('/admin', { params: { action: 'types' } }).then((res) => res.data.types || []),
    force
  )

export const adminDeleteUser = (id) =>
  adminApi.delete('/admin', { params: { action: 'user', id } }).then((res) => {
    clearAdminCache()
    return res.data
  })

export const adminDeleteRecord = (id) =>
  adminApi.delete('/admin', { params: { action: 'record', id } }).then((res) => {
    clearAdminCache()
    return res.data
  })

export const adminDeleteRoute = (id) =>
  adminApi.delete('/admin', { params: { action: 'route', id } }).then((res) => {
    clearAdminCache()
    return res.data
  })

export function errorMessage(error, fallback = '操作失败，请稍后再试') {
  return error?.response?.data?.message || error?.message || fallback
}
