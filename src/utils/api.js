import axios from 'axios'
import { supabase, signOut } from './supabase'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/.netlify/functions',
  timeout: 20000
})

const PROFILE_TTL = 30000
const DATA_TTL = 30000

let profileCache = null
let profileCacheAt = 0
let profileCacheToken = ''
const statsCache = new Map()
let rankCache = null
let summaryCache = null
let summaryCacheAt = 0

export function clearApiCache() {
  profileCache = null
  profileCacheAt = 0
  profileCacheToken = ''
  statsCache.clear()
  rankCache = null
  summaryCache = null
  summaryCacheAt = 0
}

api.interceptors.request.use(async (config) => {
  const { data } = await supabase.auth.getSession()
  const token = data.session?.access_token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      clearApiCache()
      await signOut()
      if (window.location.pathname !== '/auth') window.location.replace('/auth')
    }
    return Promise.reject(error)
  }
)

export async function submitCheckin(payload) {
  const result = await api.post('/checkin', payload).then((res) => res.data)
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

export async function getRank() {
  if (rankCache && Date.now() - rankCache.at < DATA_TTL) return rankCache.data

  const result = await api.get('/rank').then((res) => res.data)
  rankCache = { data: result, at: Date.now() }
  return result
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

  const { data } = await supabase.auth.getSession()
  const token = data.session?.access_token || ''

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

export const adminOverview = () =>
  api.get('/admin', { params: { action: 'overview' } }).then((res) => res.data)

export const adminUsers = (query = '') =>
  api.get('/admin', { params: { action: 'users', query } }).then((res) => res.data)

export const adminRecords = (params = {}) =>
  api.get('/admin', { params: { action: 'records', ...params } }).then((res) => res.data)

export const adminRank = () =>
  api.get('/admin', { params: { action: 'rank' } }).then((res) => res.data)

export const adminDeleteUser = (id) =>
  api.delete('/admin', { params: { action: 'user', id } }).then((res) => res.data)

export const adminDeleteRecord = (id) =>
  api.delete('/admin', { params: { action: 'record', id } }).then((res) => res.data)

export function errorMessage(error, fallback = '操作失败，请稍后再试') {
  return error?.response?.data?.message || error?.message || fallback
}
