const API_BASE = import.meta.env.VITE_API_BASE || '/.netlify/functions'

const TOKEN_KEY = 'dlm_token'
const USER_KEY = 'dlm_user'
const ADMIN_TOKEN_KEY = 'dlm_admin_token'
const ADMIN_USER_KEY = 'dlm_admin_user'

const readJson = (key) => {
  try {
    return JSON.parse(localStorage.getItem(key) || 'null')
  } catch {
    return null
  }
}

const decodeToken = (token) => {
  try {
    const base = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(base))
  } catch {
    return null
  }
}

const isValid = (token) => {
  const payload = decodeToken(token)
  if (!payload) return false
  return !payload.exp || payload.exp * 1000 > Date.now()
}

export const getToken = () => localStorage.getItem(TOKEN_KEY) || ''
export const getUser = () => readJson(USER_KEY)
export const getAdminToken = () => localStorage.getItem(ADMIN_TOKEN_KEY) || ''
export const getAdminUser = () => readJson(ADMIN_USER_KEY)

export const getAuthState = () => {
  const token = getToken()
  if (!token || !isValid(token)) {
    if (token) clearSession()
    return { token: '', user: null }
  }
  return { token, user: getUser() }
}

export const getAdminAuthState = () => {
  const token = getAdminToken()
  if (!token || !isValid(token)) {
    if (token) clearAdminSession()
    return { token: '', user: null }
  }
  return { token, user: getAdminUser() }
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function clearAdminSession() {
  localStorage.removeItem(ADMIN_TOKEN_KEY)
  localStorage.removeItem(ADMIN_USER_KEY)
}

const saveSession = (data, admin = false) => {
  localStorage.setItem(admin ? ADMIN_TOKEN_KEY : TOKEN_KEY, data.token)
  localStorage.setItem(admin ? ADMIN_USER_KEY : USER_KEY, JSON.stringify(data.user))
}

const requestAuth = async (action, payload, token, method = 'POST') => {
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  if (payload !== undefined) headers['Content-Type'] = 'application/json'

  const response = await fetch(`${API_BASE}/auth`, {
    method,
    headers,
    body: payload !== undefined ? JSON.stringify({ ...payload, action }) : undefined
  })

  const data = await response.json().catch(() => ({}))
  if (!response.ok) {
    const error = new Error(data.message || '请求失败，请稍后再试')
    error.status = response.status
    throw error
  }
  return data
}

export async function signIn(account, password) {
  const data = await requestAuth('login', { account, password })
  saveSession(data)
  return data
}

export async function signUp(account, password) {
  const data = await requestAuth('register', { account, password })
  saveSession(data)
  return data
}

export async function adminSignIn(account, password) {
  const data = await requestAuth('login', { account, password })
  if (!data.user?.isAdmin) {
    const error = new Error('该账号不是管理员')
    error.status = 403
    throw error
  }
  saveSession(data, true)
  return data
}

export async function signOut() {
  clearSession()
}

export async function adminSignOut() {
  clearAdminSession()
}

export async function refreshMe() {
  const { token } = getAuthState()
  if (!token) return null
  const data = await requestAuth('me', undefined, token, 'GET')
  localStorage.setItem(USER_KEY, JSON.stringify(data.user))
  return data.user
}

export async function completeOnboarding() {
  const { token } = getAuthState()
  const data = await requestAuth('onboarded', { onboarded: true }, token, 'PUT')
  localStorage.setItem(USER_KEY, JSON.stringify(data.user))
  return data.user
}

export async function changePassword(oldPassword, newPassword) {
  const { token } = getAuthState()
  return requestAuth('password', { oldPassword, newPassword }, token, 'PUT')
}
