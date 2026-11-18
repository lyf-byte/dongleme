const { createClient } = require('@supabase/supabase-js')

const secretKey =
  process.env.SUPABASE_SECRET_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY

const supabase = createClient(process.env.SUPABASE_URL, secretKey, {
  auth: { persistSession: false, autoRefreshToken: false }
})

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS'
}

const json = (statusCode, body) => ({
  statusCode,
  headers: { 'Content-Type': 'application/json; charset=utf-8', ...CORS },
  body: JSON.stringify(body)
})

const noContent = () => ({ statusCode: 204, headers: CORS, body: '' })

const userCache = new Map()
const USER_CACHE_TTL = 60 * 1000

const getUser = async (event) => {
  const header = event.headers?.authorization || event.headers?.Authorization || ''
  const token = header.startsWith('Bearer ') ? header.slice(7) : ''
  if (!token) return null

  const now = Date.now()
  const cached = userCache.get(token)
  if (cached && cached.expiresAt > now) return cached.user

  const { data, error } = await supabase.auth.getUser(token)
  if (error || !data?.user) return null

  userCache.set(token, { user: data.user, expiresAt: now + USER_CACHE_TTL })
  if (userCache.size > 500) {
    for (const [key, value] of userCache) {
      if (value.expiresAt <= now) userCache.delete(key)
    }
  }

  return data.user
}

const requireUser = async (event) => {
  const user = await getUser(event)
  if (!user) return { error: json(401, { message: '登录已过期，请重新登录' }) }
  return { user }
}

const parseBody = (event) => {
  try {
    return JSON.parse(event.body || '{}')
  } catch {
    return null
  }
}

const pad = (n) => String(n).padStart(2, '0')

const getToday = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())

const toUtc = (dateStr) => new Date(`${dateStr}T00:00:00Z`)
const fromUtc = (d) => `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`

const addDays = (dateStr, days) => {
  const d = toUtc(dateStr)
  d.setUTCDate(d.getUTCDate() + days)
  return fromUtc(d)
}

const diffDays = (a, b) => Math.round((toUtc(a) - toUtc(b)) / 86400000)

const getWeekStart = (dateStr = getToday()) => {
  const d = toUtc(dateStr)
  const diff = (d.getUTCDay() + 6) % 7
  d.setUTCDate(d.getUTCDate() - diff)
  return fromUtc(d)
}

module.exports = {
  supabase,
  json,
  noContent,
  getUser,
  requireUser,
  parseBody,
  getToday,
  addDays,
  diffDays,
  getWeekStart
}
