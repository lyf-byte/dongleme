const { supabase, json, noContent, requireUser } = require('./db')

exports.handler = async (event) => {
  if (event.httpMethod === 'OPTIONS') return noContent()
  if (event.httpMethod !== 'GET') return json(405, { message: 'Method Not Allowed' })

  const { user, error: authError } = await requireUser(event)
  if (authError) return authError

  const { data, error } = await supabase
    .from('weekly_rank')
    .select('id, nickname, weekly_minutes')
    .order('weekly_minutes', { ascending: false })

  if (error) return json(500, { message: '读取排行榜失败' })

  const rows = (data || []).map((row) => ({
    id: row.id,
    nickname: row.nickname || '运动新人',
    weeklyMinutes: Number(row.weekly_minutes) || 0
  }))

  const index = rows.findIndex((row) => row.id === user.id)
  const me =
    index >= 0
      ? {
          rank: index + 1,
          minutes: rows[index].weeklyMinutes,
          gap: index > 0 ? rows[index - 1].weeklyMinutes - rows[index].weeklyMinutes : 0
        }
      : null

  const list = rows.slice(0, 20).map((row) => ({
    ...row,
    isMe: row.id === user.id
  }))

  return json(200, { list, me })
}
