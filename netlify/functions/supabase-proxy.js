/**
 * Supabase HTTP 反向代理
 *
 * 作用：把前端与后端对 Supabase 的 HTTP 请求，统一转发到本 Netlify 站点下的
 * `/.netlify/functions/supabase-proxy/*`，从而不再从用户网络直连 `*.supabase.co`。
 *
 * 用法：把 VITE_SUPABASE_URL 与 SUPABASE_URL 都设为
 *   https://你的站点.netlify.app/.netlify/functions/supabase-proxy
 *
 * 需要环境变量：
 *   SUPABASE_PROJECT_URL=https://你的项目.supabase.co
 */

const FUNCTION_MARKER = '/.netlify/functions/supabase-proxy'
const REWRITE_MARKER = '/supa/'
const ALLOWED_PREFIXES = ['auth/v1', 'rest/v1']

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers':
    'authorization, apikey, content-type, x-client-info, x-supabase-api-version, prefer, range, accept-profile, content-profile',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, PATCH, DELETE, OPTIONS',
  'Access-Control-Expose-Headers': 'content-range, content-profile'
}

const json = (statusCode, message) => ({
  statusCode,
  headers: { ...CORS, 'Content-Type': 'application/json; charset=utf-8' },
  body: JSON.stringify({ message })
})

const getSuffix = (event) => {
  const paths = []
  if (event.path) paths.push(event.path)
  if (event.rawUrl) {
    try {
      paths.push(new URL(event.rawUrl).pathname)
    } catch {
      // 忽略无法解析的 rawUrl
    }
  }

  for (const path of paths) {
    const fnIndex = path.indexOf(FUNCTION_MARKER)
    if (fnIndex >= 0) {
      return path.slice(fnIndex + FUNCTION_MARKER.length).replace(/^\/+/, '')
    }
  }

  for (const path of paths) {
    const rewriteIndex = path.indexOf(REWRITE_MARKER)
    if (rewriteIndex >= 0) {
      return path.slice(rewriteIndex + REWRITE_MARKER.length)
    }
  }

  return ''
}

const getQuery = (event) => {
  if (event.rawQuery) return event.rawQuery
  if (!event.queryStringParameters) return ''
  return new URLSearchParams(event.queryStringParameters).toString()
}

exports.handler = async (event) => {
  if (event.httpMethod === 'OPTIONS') {
    return { statusCode: 204, headers: CORS, body: '' }
  }

  const target = (process.env.SUPABASE_PROJECT_URL || '').replace(/\/+$/, '')
  if (!target) return json(500, '缺少 SUPABASE_PROJECT_URL 环境变量')

  const suffix = getSuffix(event)
  const allowed = ALLOWED_PREFIXES.some(
    (prefix) => suffix === prefix || suffix.startsWith(`${prefix}/`)
  )
  if (!suffix || !allowed) return json(404, '代理路径不合法')

  const query = getQuery(event)
  const url = `${target}/${suffix}${query ? `?${query}` : ''}`

  const headers = {}
  for (const [key, value] of Object.entries(event.headers || {})) {
    const lower = key.toLowerCase()
    if (
      ['host', 'connection', 'content-length', 'accept-encoding', 'origin', 'referer'].includes(
        lower
      )
    ) {
      continue
    }
    headers[lower] = value
  }

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 9000)

  try {
    const hasBody = !['GET', 'HEAD'].includes(event.httpMethod)
    const body = hasBody
      ? event.isBase64Encoded
        ? Buffer.from(event.body || '', 'base64')
        : event.body
      : undefined

    const response = await fetch(url, {
      method: event.httpMethod,
      headers,
      body,
      signal: controller.signal,
      redirect: 'manual'
    })

    const buffer = Buffer.from(await response.arrayBuffer())
    const responseHeaders = { ...CORS }

    for (const key of [
      'content-type',
      'content-range',
      'content-profile',
      'location',
      'cache-control',
      'x-supabase-api-version'
    ]) {
      const value = response.headers.get(key)
      if (value) responseHeaders[key] = value
    }

    return {
      statusCode: response.status,
      headers: responseHeaders,
      body: buffer.toString('base64'),
      isBase64Encoded: true
    }
  } catch (error) {
    if (error.name === 'AbortError') return json(504, 'Supabase 请求超时')
    return json(502, `Supabase 代理请求失败：${error.message}`)
  } finally {
    clearTimeout(timer)
  }
}
