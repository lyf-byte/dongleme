const { pool, json, noContent, requireUser, getToday, addDays } = require('./db')

const RANGES = ['7d', '30d', 'month']

exports.handler = async (event) => {
  if (event.httpMethod === 'OPTIONS') return noContent()
  if (event.httpMethod !== 'GET') return json(405, { message: 'Method Not Allowed' })

  const { user, error: authError } = await requireUser(event)
  if (authError) return authError

  const range = event.queryStringParameters?.range || '7d'
  if (!RANGES.includes(range)) return json(400, { message: 'range 参数不合法' })

  const today = getToday()
  let start = addDays(today, -6)
  if (range === '30d') start = addDays(today, -29)
  if (range === 'month') start = `${today.slice(0, 7)}-01`

  const [rows] = await pool.query(
    `select date, type, duration, calories from records
     where user_id = ? and date >= ? and date <= ?`,
    [user.id, start, today]
  )

  const dayMap = new Map()
  const typeMap = {}
  let count = 0
  let minutes = 0
  let calories = 0

  for (const row of rows) {
    const duration = Number(row.duration) || 0
    const kcal = Number(row.calories) || 0
    count += 1
    minutes += duration
    calories += kcal
    typeMap[row.type] = (typeMap[row.type] || 0) + duration

    const day = dayMap.get(row.date) || { minutes: 0, calories: 0, types: {} }
    day.minutes += duration
    day.calories += kcal
    day.types[row.type] = (day.types[row.type] || 0) + duration
    dayMap.set(row.date, day)
  }

  const daily = []
  for (let d = start; d <= today; d = addDays(d, 1)) {
    const day = dayMap.get(d)
    daily.push({
      date: d,
      minutes: day ? day.minutes : 0,
      calories: day ? day.calories : 0,
      types: day ? day.types : {}
    })
  }

  return json(200, {
    range,
    start,
    end: today,
    daily,
    types: typeMap,
    total: { count, minutes, calories }
  })
}
