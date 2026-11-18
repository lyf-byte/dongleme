const {
  supabase,
  json,
  noContent,
  requireUser,
  parseBody,
  getToday,
  getWeekStart
} = require('./db')

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

  const { data: cached, error: cacheError } = await supabase
    .from('summaries')
    .select('content')
    .eq('user_id', user.id)
    .eq('week_start', weekStart)
    .maybeSingle()

  if (cacheError) return json(500, { message: '读取小结缓存失败' })

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

  const { data: rows, error } = await supabase
    .from('records')
    .select('date, type, duration, calories')
    .eq('user_id', user.id)
    .gte('date', weekStart)
    .lte('date', today)

  if (error) return json(500, { message: '读取本周数据失败' })

  const stats = {
    count: 0,
    minutes: 0,
    calories: 0,
    types: {}
  }

  for (const row of rows || []) {
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

    await supabase
      .from('summaries')
      .upsert(
        { user_id: user.id, week_start: weekStart, content },
        { onConflict: 'user_id,week_start' }
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
