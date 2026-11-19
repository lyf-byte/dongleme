/**
 * 本地全栈启动器（Netlify Dev 的替代方案）
 *
 * - 加载项目根目录的 .env
 * - 托管 dist 静态文件（SPA fallback）
 * - 直接在 Node 进程里调用 netlify/functions/*.js
 *
 * 用法：
 *   npm run dev:local    # 先 build，再启动
 *   npm run start:local  # 已有 dist 时直接启动
 *
 * 仅用于本地调试，不要用于生产部署。
 */
const http = require('http')
const fs = require('fs')
const path = require('path')

const PROJECT = path.resolve(__dirname, '..')
const ROOT = path.join(PROJECT, 'dist')
const PORT = Number(process.env.PORT) || 8888

const envFile = path.join(PROJECT, '.env')
if (!fs.existsSync(envFile)) {
  console.error('缺少 .env，请先复制 .env.example 并填写 MySQL 与 JWT 配置')
  process.exit(1)
}

for (const line of fs.readFileSync(envFile, 'utf8').split(/\r?\n/)) {
  const match = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/)
  if (match) process.env[match[1]] = match[2].trim()
}

const handlers = {}
const allowed = new Set(['profile', 'checkin', 'stats', 'rank', 'summary', 'admin', 'auth'])

function getHandler(name) {
  if (!handlers[name]) {
    handlers[name] = require(path.join(PROJECT, 'netlify/functions', `${name}.js`)).handler
  }
  return handlers[name]
}

const mime = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.map': 'application/json',
  '.woff2': 'font/woff2'
}

function serveIndex(res) {
  let html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8')
  // 本地调试时跳过 Service Worker，避免缓存干扰；生产环境保留 PWA 注册
  if (process.env.NODE_ENV !== 'production') {
    html = html.replace(/<script id="vite-plugin-pwa:register-sw"[^>]*><\/script>/, '')
  }
  res.writeHead(200, {
    'Content-Type': 'text/html; charset=utf-8',
    'Cache-Control': 'no-store'
  })
  res.end(html)
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost')
  const pathname = decodeURIComponent(url.pathname)

  if (pathname.startsWith('/.netlify/functions/')) {
    const rest = pathname.slice('/.netlify/functions/'.length)
    const name = rest.split('/')[0]
    if (!allowed.has(name)) {
      res.writeHead(404)
      res.end('Not Found')
      return
    }

    let body = ''
    req.on('data', (chunk) => {
      body += chunk
    })
    req.on('end', async () => {
      const event = {
        httpMethod: req.method,
        headers: req.headers,
        queryStringParameters: Object.fromEntries(url.searchParams.entries()),
        body: body || null,
        path: pathname,
        isBase64Encoded: false
      }
      try {
        const result = await getHandler(name)(event, {})
        res.writeHead(result.statusCode || 200, result.headers || {})
        const body = result.isBase64Encoded
          ? Buffer.from(result.body || '', 'base64')
          : result.body || ''
        res.end(body)
      } catch (error) {
        console.error(`[${name}]`, error)
        res.writeHead(500, { 'Content-Type': 'application/json; charset=utf-8' })
        res.end(JSON.stringify({ message: error.message }))
      }
    })
    return
  }

  if (pathname === '/' || !path.extname(pathname)) {
    serveIndex(res)
    return
  }

  const filePath = path.resolve(ROOT, '.' + pathname)
  if (!filePath.startsWith(path.resolve(ROOT))) {
    res.writeHead(403)
    res.end('Forbidden')
    return
  }

  fs.readFile(filePath, (error, content) => {
    if (error) {
      res.writeHead(404)
      res.end('Not Found')
      return
    }
    res.writeHead(200, {
      'Content-Type': mime[path.extname(filePath)] || 'application/octet-stream',
      'Cache-Control': pathname.startsWith('/assets/')
        ? 'public, max-age=31536000, immutable'
        : 'public, max-age=86400'
    })
    res.end(content)
  })
})

server.listen(PORT, '127.0.0.1', () => {
  console.log(`\n  动了没 · 本地全栈已启动`)
  console.log(`  http://127.0.0.1:${PORT}/\n`)
  console.log('  前端：dist 构建产物')
  console.log('  接口：/.netlify/functions/*（MySQL）\n')
})
