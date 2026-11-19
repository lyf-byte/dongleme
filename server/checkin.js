const {
  pool,
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

  const [profileRows] = await pool.query(
    'select weight, streak, last_check_date from users where id = ? limit 1',
    [user.id]
  )
  const profile = profileRows[0]
  if (!profile) return json(404, { message: '用户不存在' })

  const weight = Number(body.weight ?? profile.weight ?? 60)
  if (!Number.isFinite(weight) || weight < 20 || weight > 300) {
    return json(400, { message: '体重需在 20–300 kg 之间' })
  }

  const today = getToday()
  const calories = Math.round(met * weight * (duration / 60))
  const overwrite = body.overwrite === true

  const conn = await pool.getConnection()
  try {
    await conn.beginTransaction()

    if (overwrite) {
      await conn.query(
        `insert into records (user_id, date, type, duration, calories)
         values (?, ?, ?, ?, ?)
         on duplicate key update duration = values(duration), calories = values(calories)`,
        [user.id, today, type, duration, calories]
      )
    } else {
      try {
        await conn.query(
          'insert into records (user_id, date, type, duration, calories) values (?, ?, ?, ?, ?)',
          [user.id, today, type, duration, calories]
        )
      } catch (error) {
        await conn.rollback()
        if (error.code === 'ER_DUP_ENTRY') {
          return json(409, { message: '今日已打卡该类型' })
        }
        throw error
      }
    }

    const last = profile.last_check_date
    let streak = 1
    if (last === today) {
      streak = profile.streak || 1
    } else if (last && diffDays(today, String(last).slice(0, 10)) === 1) {
      streak = (profile.streak || 0) + 1
    }

    await conn.query('update users set streak = ?, last_check_date = ? where id = ?', [
      streak,
      today,
      user.id
    ])
    await conn.commit()

    return json(200, { date: today, duration, calories, streak })
  } catch {
    await conn.rollback()
    return json(500, { message: '保存打卡记录失败' })
  } finally {
    conn.release()
  }
}
