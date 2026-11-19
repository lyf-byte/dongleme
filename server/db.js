const mysql = require('mysql2/promise')
const jwt = require('jsonwebtoken')

const JWT_SECRET = process.env.JWT_SECRET
if (!JWT_SECRET) throw new Error('缺少 JWT_SECRET 环境变量')

const pool = mysql.createPool({
  host: process.env.DB_HOST || '127.0.0.1',
  port: Number(process.env.DB_PORT || 3306),
  user: process.env.DB_USER || 'dlm',
  password: process.env.DB_PASSWORD,
  database: process.env.DB_NAME || 'donglemei',
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0,
  charset: 'utf8mb4',
  dateStrings: true,
  decimalNumbers: true
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

const mapUser = (row) => ({
  id: row.id,
  email: row.email || null,
  phone: row.phone || null,
  account: row.email || row.phone || '',
  nickname: row.nickname,
  weight: Number(row.weight),
  weeklyGoal: row.weekly_goal,
  streak: row.streak,
  isAdmin: Boolean(row.is_admin),
  onboarded: Boolean(row.onboarded),
  createdAt: row.created_at
})

const signToken = (row) =>
  jwt.sign(
    { sub: row.id, isAdmin: Boolean(row.is_admin) },
    JWT_SECRET,
    { expiresIn: '7d' }
  )

const getUser = async (event) => {
  const header = event.headers?.authorization || event.headers?.Authorization || ''
  const token = header.startsWith('Bearer ') ? header.slice(7) : ''
  if (!token) return null

  try {
    const payload = jwt.verify(token, JWT_SECRET)
    const [rows] = await pool.query(
      `select id, email, phone, nickname, weight, weekly_goal, is_admin, onboarded,
              streak, last_check_date, created_at
       from users where id = ? limit 1`,
      [payload.sub]
    )
    return rows[0] ? mapUser(rows[0]) : null
  } catch {
    return null
  }
}

const requireUser = async (event) => {
  const user = await getUser(event)
  if (!user) return { error: json(401, { message: '登录已过期，请重新登录' }) }
  return { user }
}

module.exports = {
  pool,
  JWT_SECRET,
  json,
  noContent,
  parseBody,
  getToday,
  addDays,
  diffDays,
  getWeekStart,
  mapUser,
  signToken,
  getUser,
  requireUser
}
