const {
  pool,
  json,
  noContent,
  requireUser,
  parseBody,
  getToday,
  getWeekStart,
  effectiveStreak
} = require('./db')

const NICKNAME_RE = /^[\u4e00-\u9fa5A-Za-z0-9_-]{1,12}$/

const mapProfile = (row) => ({
  nickname: row.nickname,
  weight: Number(row.weight),
  weeklyGoal: row.weekly_goal,
  streak: effectiveStreak(row.streak, row.last_check_date),
  createdAt: row.created_at
})

const sumRows = (rows) =>
  rows.reduce(
    (acc, row) => {
      acc.count += 1
      acc.minutes += Number(row.duration) || 0
      acc.calories += Number(row.calories) || 0
      return acc
    },
    { count: 0, minutes: 0, calories: 0 }
  )

exports.handler = async (event) => {
  if (event.httpMethod === 'OPTIONS') return noContent()

  const { user, error: authError } = await requireUser(event)
  if (authError) return authError

  if (event.httpMethod === 'GET') {
    const [profileRows] = await pool.query(
      `select nickname, weight, weekly_goal, streak, last_check_date, created_at
       from users where id = ? limit 1`,
      [user.id]
    )
    if (!profileRows[0]) return json(404, { message: '用户不存在' })

    const [rows] = await pool.query(
      'select date, type, duration, calories from records where user_id = ? order by date asc',
      [user.id]
    )

    const profileRow = profileRows[0]

    if (event.queryStringParameters?.export === '1') {
      const [summaries] = await pool.query(
        'select week_start, content, created_at from summaries where user_id = ? order by week_start asc',
        [user.id]
      )

      return json(200, {
        profile: mapProfile(profileRow),
        records: rows,
        summaries
      })
    }

    const today = getToday()
    const weekStart = getWeekStart(today)

    const todayTotals = sumRows(rows.filter((row) => row.date === today))
    const weekTotals = sumRows(rows.filter((row) => row.date >= weekStart))
    const totalTotals = sumRows(rows)
    const weekDays = [...new Set(rows.filter((row) => row.date >= weekStart).map((row) => row.date))]

    return json(200, {
      ...mapProfile(profileRow),
      weekMinutes: weekTotals.minutes,
      weekDays,
      today: { minutes: todayTotals.minutes, calories: todayTotals.calories },
      total: totalTotals
    })
  }

  if (event.httpMethod === 'PUT') {
    const body = parseBody(event)
    if (!body) return json(400, { message: '请求格式不正确' })

    const nickname = String(body.nickname || '').trim()
    if (!NICKNAME_RE.test(nickname)) {
      return json(400, { message: '昵称需为 1–12 位中英文、数字、下划线或连字符' })
    }

    const weight = Number(body.weight)
    if (!Number.isFinite(weight) || weight < 20 || weight > 300) {
      return json(400, { message: '体重需在 20–300 kg 之间' })
    }

    const weeklyGoal = Number(body.weeklyGoal)
    if (!Number.isInteger(weeklyGoal) || weeklyGoal < 1 || weeklyGoal > 2000) {
      return json(400, { message: '周目标需为 1–2000 的整数' })
    }

    await pool.query(
      'update users set nickname = ?, weight = ?, weekly_goal = ? where id = ?',
      [nickname, weight, weeklyGoal, user.id]
    )

    return json(200, { nickname, weight, weeklyGoal })
  }

  if (event.httpMethod === 'DELETE') {
    const conn = await pool.getConnection()
    try {
      await conn.beginTransaction()
      await conn.query('delete from records where user_id = ?', [user.id])
      await conn.query('delete from summaries where user_id = ?', [user.id])
      await conn.query(
        'update users set streak = 0, last_check_date = null where id = ?',
        [user.id]
      )
      await conn.commit()
      return json(200, { ok: true })
    } catch {
      await conn.rollback()
      return json(500, { message: '清空数据失败' })
    } finally {
      conn.release()
    }
  }

  return json(405, { message: 'Method Not Allowed' })
}
