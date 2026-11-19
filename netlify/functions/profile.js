const {
  supabase,
  json,
  noContent,
  requireUser,
  parseBody,
  getToday,
  getWeekStart
} = require('./db')

const NICKNAME_RE = /^[\u4e00-\u9fa5A-Za-z0-9_-]{1,12}$/

const mapProfile = (row) => ({
  nickname: row.nickname,
  weight: Number(row.weight),
  weeklyGoal: row.weekly_goal,
  streak: row.streak,
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
    const [profileResult, recordsResult] = await Promise.all([
      supabase
        .from('users')
        .select('nickname, weight, weekly_goal, streak, created_at')
        .eq('id', user.id)
        .maybeSingle(),
      supabase
        .from('records')
        .select('date, type, duration, calories')
        .eq('user_id', user.id)
        .order('date', { ascending: true })
    ])

    let profileRow = profileResult.data
    if (profileResult.error) return json(500, { message: '读取用户信息失败' })

    if (!profileRow) {
      const { data: created, error: createError } = await supabase
        .from('users')
        .insert({ id: user.id })
        .select('nickname, weight, weekly_goal, streak, created_at')
        .single()

      if (createError) return json(500, { message: '初始化用户失败' })
      profileRow = created
    }

    if (recordsResult.error) return json(500, { message: '读取打卡记录失败' })
    const rows = recordsResult.data

    if (event.queryStringParameters?.export === '1') {
      const { data: summaries } = await supabase
        .from('summaries')
        .select('week_start, content, created_at')
        .eq('user_id', user.id)
        .order('week_start', { ascending: true })

      return json(200, {
        profile: mapProfile(profileRow),
        records: rows || [],
        summaries: summaries || []
      })
    }

    const today = getToday()
    const weekStart = getWeekStart(today)
    const allRows = rows || []

    const todayTotals = sumRows(allRows.filter((row) => row.date === today))
    const weekTotals = sumRows(allRows.filter((row) => row.date >= weekStart))
    const totalTotals = sumRows(allRows)
    const weekDays = [...new Set(allRows.filter((row) => row.date >= weekStart).map((row) => row.date))]

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

    const { error } = await supabase.from('users').upsert(
      {
        id: user.id,
        nickname,
        weight,
        weekly_goal: weeklyGoal
      },
      { onConflict: 'id' }
    )

    if (error) return json(500, { message: '保存资料失败' })

    return json(200, { nickname, weight, weeklyGoal })
  }

  if (event.httpMethod === 'DELETE') {
    const { error: recordsError } = await supabase
      .from('records')
      .delete()
      .eq('user_id', user.id)

    const { error: summariesError } = await supabase
      .from('summaries')
      .delete()
      .eq('user_id', user.id)

    if (recordsError || summariesError) {
      return json(500, { message: '清空数据失败' })
    }

    const { error: userError } = await supabase
      .from('users')
      .update({ streak: 0, last_check_date: null })
      .eq('id', user.id)

    if (userError) return json(500, { message: '重置连续打卡失败' })

    return json(200, { ok: true })
  }

  return json(405, { message: 'Method Not Allowed' })
}
