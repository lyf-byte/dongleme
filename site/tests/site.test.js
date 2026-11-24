const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

const siteDir = path.resolve(__dirname, '..')
const html = fs.readFileSync(path.join(siteDir, 'index.html'), 'utf8')
const css = fs.readFileSync(path.join(siteDir, 'styles.css'), 'utf8')
const script = fs.readFileSync(path.join(siteDir, 'app.js'), 'utf8')

function classList() {
  const values = new Set()
  return {
    add: (...names) => names.forEach((name) => values.add(name)),
    remove: (...names) => names.forEach((name) => values.delete(name)),
    contains: (name) => values.has(name),
    toggle: (name, force) => {
      if (force) values.add(name)
      else values.delete(name)
    }
  }
}

function element(id, classes = '') {
  const attributes = {}
  return {
    id,
    textContent: '',
    classList: classList(),
    classes: classes.split(' ').filter(Boolean),
    setAttribute: (name, value) => { attributes[name] = value },
    getAttribute: (name) => attributes[name] ?? null,
    removeAttribute: (name) => { delete attributes[name] },
    attributes
  }
}

function loadApp({ protocol = 'https:', fetchImpl, observer = true } = {}) {
  const root = element('html')
  root.classList.add('no-js')
  const reveals = [element('hero'), element('feature')]
  reveals.forEach((item) => item.classList.add('reveal'))
  const nav = element('nav')
  const apk = element('apk')
  apk.setAttribute('href', 'download/dongleme.apk')
  apk.setAttribute('download', '')
  const apkLabel = element('apk-label')
  const mobileLinks = ['#features', '#showcase', '#download'].map((href) => {
    const link = element('')
    link.setAttribute('href', href)
    return link
  })
  const sections = mobileLinks.map((link, index) => element(['features', 'showcase', 'download'][index]))

  const context = {
    document: {
      documentElement: root,
      getElementById: (id) => ({ nav, apk, 'apk-label': apkLabel })[id] || null,
      querySelectorAll: (selector) => selector === '.reveal' ? reveals : mobileLinks,
      querySelector: (selector) => sections[mobileLinks.findIndex((link) => link.getAttribute('href') === selector)]
    },
    window: {
      scrollY: 0,
      innerHeight: 844,
      location: { protocol },
      matchMedia: () => ({ matches: false }),
      addEventListener: () => {}
    },
    fetch: fetchImpl || (() => Promise.resolve({ ok: true })),
    setTimeout: (callback) => callback()
  }
  if (observer) {
    context.IntersectionObserver = class {
      constructor(callback) { this.callback = callback }
      observe() {}
      unobserve() {}
    }
  }

  vm.runInNewContext(script, context)
  return { root, reveals, apk, apkLabel }
}

const flushPromises = () => new Promise((resolve) => setImmediate(resolve))

test('手机章节导航的每个锚点都有对应区块', () => {
  for (const id of ['features', 'showcase', 'download']) {
    assert.match(html, new RegExp(`id=["']${id}["']`))
    assert.ok(html.includes(`href="#${id}"`))
  }
  assert.match(html, /class="nav__mobile" aria-label="章节导航"/)
})

test('首屏标题和主按钮不依赖滚动入场动画', () => {
  assert.match(html, /<h1>\s*每天动一点/)
  assert.match(html, /<div class="hero__actions">/)
  assert.doesNotMatch(html, /<h1 class="reveal"/)
})

test('无 JavaScript 时正文默认可见，动画由 JS 启用', () => {
  assert.match(html, /<html lang="zh-CN" class="no-js">/)
  assert.match(css, /\.reveal\s*\{\s*opacity: 1;\s*transform: none;/)
  assert.match(css, /\.js \.reveal\s*\{\s*opacity: 0;/)
})

test('IntersectionObserver 不可用时正文可见且 APK 检查仍执行', async () => {
  let requested = false
  const app = loadApp({
    observer: false,
    fetchImpl: () => {
      requested = true
      return Promise.resolve({ ok: true })
    }
  })
  await flushPromises()
  assert.ok(app.root.classList.contains('js'))
  assert.ok(app.reveals.every((item) => item.classList.contains('is-in')))
  assert.equal(requested, true)
  assert.equal(app.apk.getAttribute('download'), '')
})

test('APK 不存在时降级为网页版入口', async () => {
  const app = loadApp({ fetchImpl: () => Promise.resolve({ ok: false }) })
  await flushPromises()
  assert.equal(app.apkLabel.textContent, 'Android 版准备中')
  assert.equal(app.apk.getAttribute('href'), 'https://dongleme.xyz')
  assert.equal(app.apk.getAttribute('download'), null)
  assert.ok(app.apk.classList.contains('is-pending'))
})

test('HTTP 网络异常时不会保留死链', async () => {
  const app = loadApp({ fetchImpl: () => Promise.reject(new Error('offline')) })
  await flushPromises()
  assert.equal(app.apk.getAttribute('href'), 'https://dongleme.xyz')
  assert.equal(app.apkLabel.textContent, 'Android 版准备中')
})

test('file:// 本地预览网络异常时保留 APK 链接', async () => {
  const app = loadApp({
    protocol: 'file:',
    fetchImpl: () => Promise.reject(new Error('cors'))
  })
  await flushPromises()
  assert.equal(app.apk.getAttribute('href'), 'download/dongleme.apk')
  assert.equal(app.apk.getAttribute('download'), '')
})

test('产品截图和协议页面资源完整', () => {
  for (const file of ['assets/ui-checkin.webp', 'assets/ui-timer.webp', 'privacy.html', 'terms.html']) {
    assert.ok(fs.existsSync(path.join(siteDir, file)), `${file} should exist`)
  }
  assert.match(html, /href="privacy\.html"/)
  assert.match(html, /href="terms\.html"/)
  assert.match(html, /alt="运动打卡完成页面/)
  assert.match(html, /alt="运动计时页面/)
  assert.match(html, /poster-checkin\.jpg" width="760" height="1140"/)
  assert.match(html, /poster-track\.jpg" width="760" height="760"/)
  assert.match(html, /poster-summary\.jpg" width="760" height="760"/)
})

test('协议与实现一致，不包含过时的免保存轨迹承诺', () => {
  const privacy = fs.readFileSync(path.join(siteDir, 'privacy.html'), 'utf8')
  const terms = fs.readFileSync(path.join(siteDir, 'terms.html'), 'utf8')
  const auth = fs.readFileSync(path.join(siteDir, '../src/pages/Auth.vue'), 'utf8')
  const settings = fs.readFileSync(path.join(siteDir, '../src/pages/Settings.vue'), 'utf8')
  assert.match(privacy, /尚未提供网页端自助注销功能/)
  assert.match(privacy, /不发送你的昵称、手机号、邮箱或完整轨迹点位/)
  assert.match(terms, /《隐私政策》/)
  assert.doesNotMatch(auth + settings, /不会保存位置轨迹|轨迹仅你本人可见/)
})
