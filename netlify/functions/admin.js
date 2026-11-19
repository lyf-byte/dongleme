const {
  pool,
  json,
  noContent,
  requireUser,
  getToday,
  getWeekStart
} = require('./db')

const accountOf = (row) => row.email || row.phone || '—'

exports.handler = async (event) => {
  if (event.httpMethod === 'OPTIONS') return noContent()

  const { user, error: authError } = await requireUser(event)
  if (authError) return authError
  if (!user.isAdmin) return json(403, { message: '没有管理员权限' })

  const method = event.httpMethod
  const action = event.queryStringParameters?.action || 'overview'

  if (method === 'GET') {
    if (action === 'overview') {
      const today = getToday()
      const weekStart = getWeekStart(today)

      const [
        [usersRows],
        [recordsRows],
        [activeRows],
        [weekRows],
        [recent]
      ] = await Promise.all([
        pool.query('select count(*) as total from users'),
        pool.query('select count(*) as total from records'),
        pool.query('select count(distinct user_id) as total from records where date = ?', [today]),
        pool.query('select coalesce(sum(duration), 0) as total from records where date >= ?', [weekStart]),
        pool.query(
          `select r.id, r.date, r.type, r.duration, r.calories, u.nickname
           from records r join users u on u.id = r.user_id
           order by r.created_at desc
           limit 10`
        )
      ])

      return json(200, {
        totalUsers: Number(usersRows[0]?.total || 0),
        totalRecords: Number(recordsRows[0]?.total || 0),
        activeToday: Number(activeRows[0]?.total || 0),
        weekMinutes: Number(weekRows[0]?.total || 0),
        recent: recent.map((row) => ({
          id: row.id,
          date: row.date,
          type: row.type,
          duration: row.duration,
          calories: row.calories,
          nickname: row.nickname
        }))
      })
    }

    if (action === 'users') {
      const query = String(event.queryStringParameters?.query || '').trim().toLowerCase()

      const [rows] = await pool.query(
        `select u.id, u.nickname, u.weight, u.weekly_goal, u.streak, u.is_admin,
                u.created_at, u.email, u.phone, count(r.id) as record_count
         from users u
         left join records r on r.user_id = u.id
         group by u.id, u.nickname, u.weight, u.weekly_goal, u.streak, u.is_admin,
                  u.created_at, u.email, u.phone
         order by u.created_at desc
         limit 500`
      )

      let users = rows.map((row) => ({
        id: row.id,
        nickname: row.nickname,
        weight: Number(row.weight),
        weeklyGoal: row.weekly_goal,
        streak: row.streak,
        isAdmin: Boolean(row.is_admin),
        createdAt: row.created_at,
        account: accountOf(row),
        recordCount: Number(row.record_count) || 0
      }))

      if (query) {
        users = users.filter(
          (row) =>
            row.nickname.toLowerCase().includes(query) ||
            row.account.toLowerCase().includes(query)
        )
      }

      return json(200, { users })
    }

    if (action === 'records') {
      const type = event.queryStringParameters?.type
      const from = event.queryStringParameters?.from
      const to = event.queryStringParameters?.to

      let sql = `select r.id, r.date, r.type, r.duration, r.calories, r.user_id, u.nickname
                 from records r join users u on u.id = r.user_id
                 where 1 = 1`
      const params = []

      if (type) {
        sql += ' and r.type = ?'
        params.push(type)
      }
      if (from) {
        sql += ' and r.date >= ?'
        params.push(from)
      }
      if (to) {
        sql += ' and r.date <= ?'
        params.push(to)
      }

      sql += ' order by r.date desc, r.created_at desc limit 200'
      const [rows] = await pool.query(sql, params)

      return json(200, {
        records: rows.map((row) => ({
          id: row.id,
          date: row.date,
          type: row.type,
          duration: row.duration,
          calories: row.calories,
          userId: row.user_id,
          nickname: row.nickname
        }))
      })
    }

    if (action === 'rank') {
      const weekStart = getWeekStart()
      const [rows] = await pool.query(
        `select u.id, u.nickname, coalesce(sum(r.duration), 0) as weekly_minutes
         from users u
         join records r on r.user_id = u.id and r.date >= ?
         group by u.id, u.nickname
         order by weekly_minutes desc, u.nickname asc
         limit 100`,
        [weekStart]
      )

      return json(200, {
        list: rows.map((row) => ({
          id: row.id,
          nickname: row.nickname,
          weeklyMinutes: Number(row.weekly_minutes) || 0
        }))
      })
    }

    return json(404, { message: '未知的后台操作' })
  }

  if (method === 'DELETE') {
    const id = event.queryStringParameters?.id
    if (!id) return json(400, { message: '缺少 id' })

    if (action === 'user') {
      if (id === user.id) return json(400, { message: '不能删除当前管理员账号' })
      const [result] = await pool.query('delete from users where id = ?', [id])
      if (!result.affectedRows) return json(404, { message: '用户不存在' })
      return json(200, { ok: true })
    }

    if (action === 'record') {
      const [result] = await pool.query('delete from records where id = ?', [id])
      if (!result.affectedRows) return json(404, { message: '记录不存在' })
      return json(200, { ok: true })
    }

    return json(404, { message: '未知的后台操作' })
  }

  return json(405, { message: 'Method Not Allowed' })
}
