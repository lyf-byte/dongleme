import fs from 'fs'
import path from 'path'
import mysql from 'mysql2/promise'
import bcrypt from 'bcryptjs'

const env = {}
for (const line of fs.readFileSync(path.resolve('.env'), 'utf8').split(/\r?\n/)) {
  const match = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/)
  if (match) env[match[1]] = match[2].trim()
}

const backupPath = process.argv[2] || '/opt/donglemei/backup-supabase-2026-11-19.json'
const backup = JSON.parse(fs.readFileSync(backupPath, 'utf8'))

const authMap = new Map((backup.authUsers || []).map((user) => [user.id, user]))
const TEMP_PASSWORD = 'Dlm@123456'
const passwordHash = await bcrypt.hash(TEMP_PASSWORD, 10)

const toDate = (value) => (value ? String(value).slice(0, 10) : null)
const toDateTime = (value) => {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return null
  return date.toISOString().slice(0, 19).replace('T', ' ')
}

const conn = await mysql.createConnection({
  host: env.DB_HOST || '127.0.0.1',
  port: Number(env.DB_PORT || 3306),
  user: env.DB_USER,
  password: env.DB_PASSWORD,
  database: env.DB_NAME,
  charset: 'utf8mb4',
  dateStrings: true
})

try {
  let users = 0
  for (const row of backup.users || []) {
    const authUser = authMap.get(row.id)
    const email = authUser?.email && !authUser.email.endsWith('@phone.dlm.app')
      ? authUser.email
      : null
    const phone = authUser?.user_metadata?.phone
      || (authUser?.email?.endsWith('@phone.dlm.app')
        ? authUser.email.split('@')[0]
        : null)

    await conn.query(
      `insert into users
        (id, email, phone, password_hash, nickname, weight, weekly_goal, is_admin,
         onboarded, streak, last_check_date, created_at)
       values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       on duplicate key update
         email = values(email), phone = values(phone), nickname = values(nickname),
         weight = values(weight), weekly_goal = values(weekly_goal),
         is_admin = values(is_admin), onboarded = values(onboarded),
         streak = values(streak), last_check_date = values(last_check_date)`,
      [
        row.id,
        email,
        phone,
        passwordHash,
        row.nickname || '运动新人',
        Number(row.weight) || 60,
        Number(row.weekly_goal) || 150,
        row.is_admin ? 1 : 0,
        authUser?.user_metadata?.onboarded === true ? 1 : 0,
        Number(row.streak) || 0,
        toDate(row.last_check_date),
        toDateTime(row.created_at)
      ]
    )
    users += 1
  }

  let records = 0
  for (const row of backup.records || []) {
    await conn.query(
      `insert into records (id, user_id, date, type, duration, calories, created_at)
       values (?, ?, ?, ?, ?, ?, ?)
       on duplicate key update
         duration = values(duration), calories = values(calories)`,
      [
        row.id,
        row.user_id,
        toDate(row.date),
        row.type,
        Number(row.duration) || 0,
        Number(row.calories) || 0,
        toDateTime(row.created_at)
      ]
    )
    records += 1
  }

  let summaries = 0
  for (const row of backup.summaries || []) {
    await conn.query(
      `insert into summaries (id, user_id, week_start, content, regen_count, created_at)
       values (?, ?, ?, ?, ?, ?)
       on duplicate key update content = values(content)`,
      [
        row.id,
        row.user_id,
        toDate(row.week_start),
        row.content,
        Number(row.regen_count) || 0,
        toDateTime(row.created_at)
      ]
    )
    summaries += 1
  }

  console.log(JSON.stringify({ users, records, summaries, tempPassword: TEMP_PASSWORD }))
} finally {
  await conn.end()
}
