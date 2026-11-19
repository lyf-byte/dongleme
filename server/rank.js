const { pool, json, noContent, requireUser, getWeekStart } = require('./db')

exports.handler = async (event) => {
  if (event.httpMethod === 'OPTIONS') return noContent()
  if (event.httpMethod !== 'GET') return json(405, { message: 'Method Not Allowed' })

  const { user, error: authError } = await requireUser(event)
  if (authError) return authError

  const weekStart = getWeekStart()
  const [rows] = await pool.query(
    `select u.id, u.nickname, coalesce(sum(r.duration), 0) as weekly_minutes
     from users u
     join records r on r.user_id = u.id and r.date >= ?
     group by u.id, u.nickname
     order by weekly_minutes desc, u.nickname asc`,
    [weekStart]
  )

  const list = rows.map((row) => ({
    id: row.id,
    nickname: row.nickname || '运动新人',
    weeklyMinutes: Number(row.weekly_minutes) || 0
  }))

  const index = list.findIndex((row) => row.id === user.id)
  const me =
    index >= 0
      ? {
          rank: index + 1,
          minutes: list[index].weeklyMinutes,
          gap: index > 0 ? list[index - 1].weeklyMinutes - list[index].weeklyMinutes : 0
        }
      : null

  return json(200, {
    list: list.slice(0, 20).map((row) => ({ ...row, isMe: row.id === user.id })),
    me
  })
}
