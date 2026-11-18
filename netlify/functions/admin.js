const {
  supabase,
  json,
  noContent,
  requireUser,
  getToday,
  getWeekStart
} = require('./db')

const nicknameOf = (row) => {
  const users = row.users
  if (Array.isArray(users)) return users[0]?.nickname || '—'
  return users?.nickname || '—'
}

exports.handler = async (event) => {
  if (event.httpMethod === 'OPTIONS') return noContent()

  const { user, error: authError } = await requireUser(event)
  if (authError) return authError

  const { data: me, error: meError } = await supabase
    .from('users')
    .select('is_admin')
    .eq('id', user.id)
    .maybeSingle()

  if (meError) return json(500, { message: '读取管理员信息失败' })
  if (!me?.is_admin) return json(403, { message: '没有管理员权限' })

  const method = event.httpMethod
  const action = event.queryStringParameters?.action || 'overview'

  if (method === 'GET') {
    if (action === 'overview') {
      const today = getToday()
      const weekStart = getWeekStart(today)

      const [usersCount, recordsCount, todayRows, weekRows, recent] = await Promise.all([
        supabase.from('users').select('id', { count: 'exact', head: true }),
        supabase.from('records').select('id', { count: 'exact', head: true }),
        supabase.from('records').select('user_id').eq('date', today),
        supabase.from('records').select('duration').gte('date', weekStart),
        supabase
          .from('records')
          .select('id,date,type,duration,calories,user_id,users(nickname)')
          .order('created_at', { ascending: false })
          .limit(10)
      ])

      if (usersCount.error || recordsCount.error || todayRows.error || weekRows.error || recent.error) {
        return json(500, { message: '读取概览数据失败' })
      }

      return json(200, {
        totalUsers: usersCount.count || 0,
        totalRecords: recordsCount.count || 0,
        activeToday: new Set((todayRows.data || []).map((row) => row.user_id)).size,
        weekMinutes: (weekRows.data || []).reduce(
          (sum, row) => sum + (Number(row.duration) || 0),
          0
        ),
        recent: (recent.data || []).map((row) => ({
          id: row.id,
          date: row.date,
          type: row.type,
          duration: row.duration,
          calories: row.calories,
          nickname: nicknameOf(row)
        }))
      })
    }

    if (action === 'users') {
      const query = String(event.queryStringParameters?.query || '').trim().toLowerCase()

      const [rowsResult, authResult, recordRows] = await Promise.all([
        supabase
          .from('users')
          .select('id,nickname,weight,weekly_goal,streak,is_admin,created_at')
          .order('created_at', { ascending: false })
          .limit(500),
        supabase.auth.admin.listUsers({ page: 1, perPage: 1000 }),
        supabase.from('records').select('user_id')
      ])

      if (rowsResult.error) return json(500, { message: '读取用户列表失败' })

      const authMap = new Map((authResult.data?.users || []).map((item) => [item.id, item]))
      const countMap = new Map()
      for (const row of recordRows.data || []) {
        countMap.set(row.user_id, (countMap.get(row.user_id) || 0) + 1)
      }

      let users = (rowsResult.data || []).map((row) => {
        const authUser = authMap.get(row.id)
        return {
          id: row.id,
          nickname: row.nickname,
          weight: Number(row.weight),
          weeklyGoal: row.weekly_goal,
          streak: row.streak,
          isAdmin: row.is_admin,
          createdAt: row.created_at,
          account: authUser?.email || authUser?.phone || '—',
          recordCount: countMap.get(row.id) || 0
        }
      })

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

      let builder = supabase
        .from('records')
        .select('id,date,type,duration,calories,user_id,users(nickname)')
        .order('date', { ascending: false })
        .order('created_at', { ascending: false })
        .limit(200)

      if (type) builder = builder.eq('type', type)
      if (from) builder = builder.gte('date', from)
      if (to) builder = builder.lte('date', to)

      const { data, error } = await builder
      if (error) return json(500, { message: '读取打卡记录失败' })

      return json(200, {
        records: (data || []).map((row) => ({
          id: row.id,
          date: row.date,
          type: row.type,
          duration: row.duration,
          calories: row.calories,
          userId: row.user_id,
          nickname: nicknameOf(row)
        }))
      })
    }

    if (action === 'rank') {
      const { data, error } = await supabase
        .from('weekly_rank')
        .select('id,nickname,weekly_minutes')
        .order('weekly_minutes', { ascending: false })
        .limit(100)

      if (error) return json(500, { message: '读取排行榜失败' })

      return json(200, {
        list: (data || []).map((row) => ({
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

      const { error } = await supabase.auth.admin.deleteUser(id)
      if (error) return json(500, { message: '删除用户失败' })

      await supabase.from('users').delete().eq('id', id)
      return json(200, { ok: true })
    }

    if (action === 'record') {
      const { error } = await supabase.from('records').delete().eq('id', id)
      if (error) return json(500, { message: '删除记录失败' })
      return json(200, { ok: true })
    }

    return json(404, { message: '未知的后台操作' })
  }

  return json(405, { message: 'Method Not Allowed' })
}
