const bcrypt = require('bcryptjs')
const { randomUUID } = require('crypto')
const {
  pool,
  json,
  noContent,
  parseBody,
  requireUser,
  mapUser,
  signToken
} = require('./db')

const PHONE_RE = /^1[3-9]\d{9}$/
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const findUser = async (account) => {
  const value = String(account || '').trim()
  if (PHONE_RE.test(value)) {
    const [rows] = await pool.query('select * from users where phone = ? limit 1', [value])
    return { row: rows[0] || null, type: 'phone', value }
  }
  if (EMAIL_RE.test(value)) {
    const email = value.toLowerCase()
    const [rows] = await pool.query('select * from users where email = ? limit 1', [email])
    return { row: rows[0] || null, type: 'email', value: email }
  }
  return { row: null, type: '', value }
}

const register = async (body) => {
  const account = String(body.account || '').trim()
  const password = String(body.password || '')
  const { row: existing, type, value } = await findUser(account)

  if (!type) return json(400, { message: '请输入正确的手机号或邮箱' })
  if (password.length < 6) return json(400, { message: '密码至少 6 位' })
  if (existing) {
    return json(409, {
      message: type === 'phone' ? '该手机号已注册，请直接登录' : '该邮箱已注册，请直接登录'
    })
  }

  const id = randomUUID()
  const passwordHash = await bcrypt.hash(password, 10)

  try {
    await pool.query(
      `insert into users (id, email, phone, password_hash, nickname, weight, weekly_goal)
       values (?, ?, ?, ?, '运动新人', 60, 150)`,
      [id, type === 'email' ? value : null, type === 'phone' ? value : null, passwordHash]
    )
  } catch (error) {
    if (error.code === 'ER_DUP_ENTRY') {
      return json(409, { message: '该账号已注册，请直接登录' })
    }
    throw error
  }

  const [rows] = await pool.query('select * from users where id = ? limit 1', [id])
  const user = mapUser(rows[0])
  return json(200, { token: signToken(rows[0]), user })
}

const login = async (body) => {
  const account = String(body.account || '').trim()
  const password = String(body.password || '')
  const { row } = await findUser(account)

  if (!row) return json(401, { message: '账号或密码错误' })

  const ok = await bcrypt.compare(password, row.password_hash)
  if (!ok) return json(401, { message: '账号或密码错误' })

  return json(200, { token: signToken(row), user: mapUser(row) })
}

const setOnboarded = async (body, user) => {
  const onboarded = body.onboarded === true
  await pool.query('update users set onboarded = ? where id = ?', [onboarded ? 1 : 0, user.id])
  const [rows] = await pool.query('select * from users where id = ? limit 1', [user.id])
  return json(200, { user: mapUser(rows[0]) })
}

const changePassword = async (body, user) => {
  const oldPassword = String(body.oldPassword || '')
  const newPassword = String(body.newPassword || '')
  if (newPassword.length < 6) return json(400, { message: '新密码至少 6 位' })

  const [rows] = await pool.query('select password_hash from users where id = ? limit 1', [user.id])
  if (!rows[0]) return json(401, { message: '登录已过期，请重新登录' })

  const ok = await bcrypt.compare(oldPassword, rows[0].password_hash)
  if (!ok) return json(400, { message: '原密码不正确' })

  const hash = await bcrypt.hash(newPassword, 10)
  await pool.query('update users set password_hash = ? where id = ?', [hash, user.id])
  return json(200, { ok: true })
}

exports.handler = async (event) => {
  if (event.httpMethod === 'OPTIONS') return noContent()

  const { user, error } = await requireUser(event)

  if (event.httpMethod === 'GET') {
    if (error) return error
    return json(200, { user })
  }

  const body = parseBody(event)
  if (!body) return json(400, { message: '请求格式不正确' })
  const action = String(body.action || '')

  if (event.httpMethod === 'POST') {
    if (action === 'register') return register(body)
    if (action === 'login') return login(body)
    return json(400, { message: '未知的认证操作' })
  }

  if (event.httpMethod === 'PUT') {
    if (error) return error
    if (action === 'onboarded') return setOnboarded(body, user)
    if (action === 'password') return changePassword(body, user)
    return json(400, { message: '未知的认证操作' })
  }

  return json(405, { message: 'Method Not Allowed' })
}
