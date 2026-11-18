const {
  supabase,
  json,
  noContent,
  requireUser,
  parseBody,
  getToday,
  diffDays
} = require('./db')

const MET = {
  跑步: 8.0,
  快走: 4.3,
  骑行: 7.5,
  跳绳: 12.3,
  游泳: 6.0,
  力量训练: 5.5
}

exports.handler = async (event) => {
  if (event.httpMethod === 'OPTIONS') return noContent()
  if (event.httpMethod !== 'POST') return json(405, { message: 'Method Not Allowed' })

  const { user, error: authError } = await requireUser(event)
  if (authError) return authError

  const body = parseBody(event)
  if (!body) return json(400, { message: '请求格式不正确' })

  const type = String(body.type || '')
  const met = MET[type]
  if (!met) return json(400, { message: '运动类型不在可选范围内' })

  const duration = Number(body.duration)
  if (!Number.isInteger(duration) || duration < 1 || duration > 600) {
    return json(400, { message: '时长必须是 1–600 的整数' })
  }

  const { data: profile, error: profileError } = await supabase
    .from('users')
    .select('weight, streak, last_check_date')
    .eq('id', user.id)
    .single()

  if (profileError || !profile) return json(500, { message: '读取用户信息失败' })

  const weight = Number(body.weight ?? profile.weight ?? 60)
  if (!Number.isFinite(weight) || weight < 20 || weight > 300) {
    return json(400, { message: '体重需在 20–300 kg 之间' })
  }

  const today = getToday()
  const calories = Math.round(met * weight * (duration / 60))
  const record = { user_id: user.id, date: today, type, duration, calories }

  if (body.overwrite === true) {
    const { error } = await supabase
      .from('records')
      .upsert(record, { onConflict: 'user_id,date,type' })
    if (error) return json(500, { message: '保存打卡记录失败' })
  } else {
    const { error } = await supabase.from('records').insert(record)
    if (error) {
      if (error.code === '23505') {
        return json(409, { message: '今日已打卡该类型' })
      }
      return json(500, { message: '保存打卡记录失败' })
    }
  }

  const last = profile.last_check_date
  let streak = 1
  if (last === today) {
    streak = profile.streak || 1
  } else if (last && diffDays(today, last) === 1) {
    streak = (profile.streak || 0) + 1
  }

  const { error: streakError } = await supabase
    .from('users')
    .update({ streak, last_check_date: today })
    .eq('id', user.id)

  if (streakError) return json(500, { message: '更新连续打卡失败' })

  return json(200, { date: today, duration, calories, streak })
}
