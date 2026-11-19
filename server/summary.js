const {
  pool,
  json,
  noContent,
  requireUser,
  parseBody,
  getToday,
  getWeekStart
} = require('./db')

const MAX_REGEN_PER_WEEK = 5

const buildFallback = ({ count, minutes, calories }) =>
  `本周你完成了 ${count} 次打卡，累计 ${minutes} 分钟，约消耗 ${calories} 千卡。` +
  (minutes >= 150
    ? '已经达到每周 150 分钟的基础运动量，节奏保持得不错。'
    : '距离每周 150 分钟还差一点，下周可以安排两次快走或骑行补上。') +
  '下周建议固定两个时间段运动，把打卡变成习惯。'

exports.handler = async (event) => {
  if (event.httpMethod === 'OPTIONS') return noContent()
  if (event.httpMethod !== 'POST') return json(405, { message: 'Method Not Allowed' })

  const { user, error: authError } = await requireUser(event)
  if (authError) return authError

  const body = parseBody(event)
  if (!body) return json(400, { message: '请求格式不正确' })

  const weekStart = getWeekStart()
  const today = getToday()

  const [cachedRows] = await pool.query(
    'select content, regen_count from summaries where user_id = ? and week_start = ? limit 1',
    [user.id, weekStart]
  )
  const cached = cachedRows[0]

  if (body.peek === true) {
    return json(200, {
      weekStart,
      content: cached?.content || null,
      cached: Boolean(cached)
    })
  }

  if (cached && body.force !== true) {
    return json(200, { weekStart, content: cached.content, cached: true })
  }

  if (cached && body.force === true && cached.regen_count >= MAX_REGEN_PER_WEEK) {
    return json(429, {
      message: `本周重新生成次数已用完（最多 ${MAX_REGEN_PER_WEEK} 次），下周一自动刷新`
    })
  }

  const [rows] = await pool.query(
    `select date, type, duration, calories from records
     where user_id = ? and date >= ? and date <= ?`,
    [user.id, weekStart, today]
  )

  const stats = { count: 0, minutes: 0, calories: 0, types: {} }
  for (const row of rows) {
    stats.count += 1
    stats.minutes += Number(row.duration) || 0
    stats.calories += Number(row.calories) || 0
    stats.types[row.type] = (stats.types[row.type] || 0) + (Number(row.duration) || 0)
  }

  if (stats.count === 0) {
    return json(200, {
      weekStart,
      content:
        '本周还没有打卡记录。先从 10 分钟快走开始吧，动起来比动得多更重要。下周给自己定一个小目标：完成 3 次打卡。',
      cached: false,
      empty: true
    })
  }

  if (!process.env.LLM_API_URL || !process.env.LLM_API_KEY) {
    return json(200, {
      weekStart,
      content: buildFallback(stats),
      cached: false,
      degraded: true
    })
  }

  const prompt =
    `你是运动教练。用户本周数据：${JSON.stringify(stats)}。` +
    '写150字以内中文小结，含数据回顾、亮点、不足、下周建议。语气轻松鼓励。'

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 8000)

  try {
    const response = await fetch(process.env.LLM_API_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${process.env.LLM_API_KEY}`
      },
      body: JSON.stringify({
        model: process.env.LLM_MODEL || 'Qwen/Qwen2.5-7B-Instruct',
        messages: [{ role: 'user', content: prompt }],
        temperature: 0.7,
        max_tokens: 400
      }),
      signal: controller.signal
    })

    if (!response.ok) throw new Error(`LLM ${response.status}`)

    const payload = await response.json()
    const content = payload?.choices?.[0]?.message?.content?.trim()
    if (!content) throw new Error('LLM empty content')

    const regenCount = cached && body.force === true ? cached.regen_count + 1 : 0
    await pool.query(
      `insert into summaries (user_id, week_start, content, regen_count)
       values (?, ?, ?, ?)
       on duplicate key update content = values(content), regen_count = values(regen_count)`,
      [user.id, weekStart, content, regenCount]
    )

    return json(200, { weekStart, content, cached: false })
  } catch {
    return json(200, {
      weekStart,
      content: buildFallback(stats),
      cached: false,
      degraded: true
    })
  } finally {
    clearTimeout(timer)
  }
}
