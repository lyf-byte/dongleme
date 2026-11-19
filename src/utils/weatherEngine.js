const SKY = {
  sunny: { day: ['#f6b21b', '#f98a1b', '#f97316'], night: ['#1e3a8a', '#16255f', '#0f172a'] },
  cloudy: { day: ['#5b7c99', '#3f5a75', '#233446'], night: ['#33465e', '#243447', '#141e2b'] },
  overcast: { day: ['#5c6b7a', '#46525e', '#2f3a45'], night: ['#3a4450', '#2b333c', '#1b2129'] },
  rain: { day: ['#2c5364', '#1d3a47', '#0f2027'], night: ['#1b3540', '#12242e', '#08131a'] },
  thunder: { day: ['#414345', '#2b2d2f', '#1a1b1c'], night: ['#26282a', '#1c1e20', '#101112'] },
  snow: { day: ['#4a6fa5', '#3a5a8c', '#274060'], night: ['#2c4269', '#22334f', '#141f33'] },
  fog: { day: ['#606c88', '#4e5a75', '#3f4c6b'], night: ['#3b4257', '#2e3444', '#20242f'] },
  haze: { day: ['#7a6a53', '#5c5040', '#3e3529'], night: ['#4a4136', '#37302a', '#241f1a'] }
}

const CLOUD_TINTS = {
  light: { highlight: '#ffffff', core: '#ffffff', mid: '#e8f1fb', bottom: '#c9d9ec' },
  gray: { highlight: '#ffffff', core: '#eef2f7', mid: '#c3cfdd', bottom: '#9fb0c2' },
  dark: { highlight: '#d5e0ee', core: '#a9b8cb', mid: '#7c8da3', bottom: '#4e5f75' }
}

const rand = (min, max) => min + Math.random() * (max - min)
const clamp = (value, min, max) => Math.min(max, Math.max(min, value))

function makeCanvas(width, height) {
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  return canvas
}

function cloudSprite(tint, variant = 0) {
  const width = 320
  const height = 180
  const layouts = [
    [[74, 118, 36], [118, 84, 48], [168, 92, 42], [214, 118, 30], [46, 128, 24], [146, 120, 42]],
    [[94, 104, 40], [138, 70, 50], [188, 82, 44], [232, 112, 30], [58, 118, 26], [168, 118, 38]],
    [[112, 116, 34], [150, 86, 44], [194, 100, 36], [228, 124, 26], [82, 126, 24], [172, 124, 32]]
  ]
  const blobs = layouts[variant % layouts.length]
  const mask = makeCanvas(width, height)
  const mctx = mask.getContext('2d')
  mctx.fillStyle = '#fff'
  for (const [x, y, r] of blobs) {
    mctx.beginPath()
    mctx.arc(x, y, r, 0, Math.PI * 2)
    mctx.fill()
  }
  mctx.fillRect(48, 118, 208, 26)

  const canvas = makeCanvas(width, height)
  const ctx = canvas.getContext('2d')
  if ('filter' in ctx) ctx.filter = 'blur(6px)'
  ctx.drawImage(mask, 0, 0)
  ctx.filter = 'none'

  ctx.globalCompositeOperation = 'source-in'
  const grad = ctx.createLinearGradient(0, 20, 0, 168)
  grad.addColorStop(0, tint.highlight)
  grad.addColorStop(0.5, tint.core)
  grad.addColorStop(0.78, tint.mid)
  grad.addColorStop(1, tint.bottom)
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, width, height)

  ctx.globalCompositeOperation = 'source-atop'
  const light = ctx.createRadialGradient(112, 54, 8, 126, 76, 138)
  light.addColorStop(0, 'rgba(255,255,255,.5)')
  light.addColorStop(0.45, 'rgba(255,255,255,.1)')
  light.addColorStop(1, 'rgba(255,255,255,0)')
  ctx.fillStyle = light
  ctx.fillRect(0, 0, width, height)

  const shade = ctx.createLinearGradient(0, 108, 0, 178)
  shade.addColorStop(0, 'rgba(28,48,78,0)')
  shade.addColorStop(1, 'rgba(28,48,78,.26)')
  ctx.fillStyle = shade
  ctx.fillRect(0, 0, width, height)
  return canvas
}

function dropSprite() {
  const canvas = makeCanvas(8, 64)
  const ctx = canvas.getContext('2d')
  const grad = ctx.createLinearGradient(0, 0, 0, 64)
  grad.addColorStop(0, 'rgba(210,240,255,0)')
  grad.addColorStop(0.35, 'rgba(210,240,255,.9)')
  grad.addColorStop(1, 'rgba(120,190,250,.25)')
  ctx.fillStyle = grad
  ctx.fillRect(2, 0, 4, 64)
  return canvas
}

function flakeSprite() {
  const canvas = makeCanvas(20, 20)
  const ctx = canvas.getContext('2d')
  const grad = ctx.createRadialGradient(10, 10, 0, 10, 10, 10)
  grad.addColorStop(0, 'rgba(255,255,255,.95)')
  grad.addColorStop(0.55, 'rgba(255,255,255,.8)')
  grad.addColorStop(1, 'rgba(255,255,255,0)')
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, 20, 20)
  return canvas
}

function crystalSprite() {
  const canvas = makeCanvas(28, 28)
  const ctx = canvas.getContext('2d')
  ctx.strokeStyle = 'rgba(255,255,255,.95)'
  ctx.lineWidth = 1.8
  ctx.lineCap = 'round'
  ctx.shadowColor = 'rgba(255,255,255,.8)'
  ctx.shadowBlur = 3
  ctx.translate(14, 14)
  for (let arm = 0; arm < 6; arm++) {
    ctx.rotate(Math.PI / 3)
    ctx.beginPath()
    ctx.moveTo(0, 0)
    ctx.lineTo(0, -11)
    ctx.moveTo(0, -6)
    ctx.lineTo(-3, -9)
    ctx.moveTo(0, -6)
    ctx.lineTo(3, -9)
    ctx.stroke()
  }
  return canvas
}

function moonSprite() {
  const crescent = makeCanvas(160, 160)
  const cctx = crescent.getContext('2d')
  cctx.fillStyle = '#fdf6c9'
  cctx.beginPath()
  cctx.arc(80, 80, 34, 0, Math.PI * 2)
  cctx.fill()
  cctx.globalCompositeOperation = 'destination-out'
  cctx.beginPath()
  cctx.arc(95, 69, 30, 0, Math.PI * 2)
  cctx.fill()
  cctx.globalCompositeOperation = 'source-atop'
  cctx.fillStyle = 'rgba(190,170,90,.3)'
  for (const [x, y, r] of [[70, 88, 5], [78, 102, 3], [66, 68, 2.5]]) {
    cctx.beginPath()
    cctx.arc(x, y, r, 0, Math.PI * 2)
    cctx.fill()
  }
  const canvas = makeCanvas(160, 160)
  const ctx = canvas.getContext('2d')
  const glow = ctx.createRadialGradient(80, 80, 10, 80, 80, 80)
  glow.addColorStop(0, 'rgba(255,250,214,.45)')
  glow.addColorStop(0.55, 'rgba(255,250,214,.12)')
  glow.addColorStop(1, 'rgba(255,250,214,0)')
  ctx.fillStyle = glow
  ctx.fillRect(0, 0, 160, 160)
  ctx.drawImage(crescent, 0, 0)
  return canvas
}

function glowSprite(color) {
  const canvas = makeCanvas(128, 128)
  const ctx = canvas.getContext('2d')
  const grad = ctx.createRadialGradient(64, 64, 4, 64, 64, 64)
  grad.addColorStop(0, color)
  grad.addColorStop(1, 'rgba(255,200,90,0)')
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, 128, 128)
  return canvas
}

export class WeatherEngine {
  constructor(canvas, ctx, type, isDay) {
    this.canvas = canvas
    this.ctx = ctx
    this.w = 0
    this.h = 0
    this.dpr = 1
    this.unit = 1
    this.t = 0
    this.last = 0
    this.raf = 0
    this.running = false
    this.fade = 0
    this.snap = makeCanvas(1, 1)
    this.sprites = {
      cloudLight: [0, 1, 2].map((variant) => cloudSprite(CLOUD_TINTS.light, variant)),
      cloudGray: [0, 1, 2].map((variant) => cloudSprite(CLOUD_TINTS.gray, variant)),
      cloudDark: [0, 1, 2].map((variant) => cloudSprite(CLOUD_TINTS.dark, variant)),
      drop: dropSprite(),
      flake: flakeSprite(),
      crystal: crystalSprite(),
      moon: moonSprite(),
      glow: glowSprite('rgba(255,214,110,.55)')
    }
    this.loop = this.loop.bind(this)
    this.setScene(type, isDay, true)
  }

  resize(width, height) {
    if (width < 4 || height < 4) return
    this.dpr = clamp(window.devicePixelRatio || 1, 1, 2)
    this.w = width
    this.h = height
    this.canvas.width = Math.round(width * this.dpr)
    this.canvas.height = Math.round(height * this.dpr)
    this.ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0)
    this.unit = clamp((width * height) / (360 * 420), 0.5, 1.8)
    this.cloudScale = clamp(height / 420, 0.6, 1.15)
    this.initParticles()
    if (!this.running) this.draw(0)
  }

  setScene(type, isDay, first = false) {
    if (!first && this.running && this.w > 0) {
      this.snap.width = this.canvas.width
      this.snap.height = this.canvas.height
      this.snap.getContext('2d').drawImage(this.canvas, 0, 0)
      this.fade = 1
    }
    this.type = SKY[type] ? type : 'cloudy'
    this.isDay = isDay
    this.sky = SKY[this.type][isDay ? 'day' : 'night']
    this.cloudKind = ['rain', 'thunder'].includes(this.type)
      ? 'cloudDark'
      : this.type === 'overcast' ? 'cloudGray' : 'cloudLight'
    this.flash = null
    this.flashIn = rand(1.5, 4)
    this.shoot = null
    this.shootIn = rand(5, 10)
    this.initParticles()
    if (!this.running) this.draw(0)
  }

  initParticles() {
    const { w, h, unit } = this
    if (!w || !h) return
    const scale = h / 420
    this.sunX = w * 0.7
    this.sunY = h * 0.23

    this.stars = !this.isDay && !['rain', 'thunder', 'snow'].includes(this.type)
      ? Array.from({ length: Math.round(30 * unit) }, () => ({
        x: rand(0, w), y: rand(0, h * 0.7), r: rand(0.6, 1.7),
        phase: rand(0, Math.PI * 2), speed: rand(0.5, 1.6)
      }))
      : []

    const cloudLayers = [
      { n: 2, size: 0.32, speed: 5, alpha: 0.22, yMin: 0.02, yMax: 0.08 },
      { n: 2, size: 0.4, speed: 9, alpha: 0.34, yMin: 0.06, yMax: 0.14 },
      { n: 2, size: 0.48, speed: 14, alpha: 0.5, yMin: 0.1, yMax: 0.18 }
    ]
    if (this.type === 'overcast') cloudLayers.push({ n: 2, size: 0.4, speed: 7, alpha: 0.42, yMin: 0, yMax: 0.06 })
    if (['fog', 'haze'].includes(this.type)) cloudLayers.splice(1, 2)
    this.clouds = cloudLayers.flatMap((layer, layerIndex) =>
      Array.from({ length: layer.n }, (_, index) => ({
        layer: layerIndex,
        x: rand(-0.3, 1) * w,
        y: rand(layer.yMin, layer.yMax) * h,
        size: layer.size * rand(0.85, 1.15),
        speed: layer.speed * rand(0.8, 1.2),
        alpha: layer.alpha * rand(0.85, 1),
        variant: Math.floor(rand(0, 3))
      }))
    )

    this.rain = ['rain', 'thunder'].includes(this.type)
      ? [
        { n: 40, vy: [520, 640], len: [18, 26], w: 2.6, alpha: 0.7 },
        { n: 28, vy: [380, 460], len: [12, 17], w: 1.8, alpha: 0.45 },
        { n: 20, vy: [260, 320], len: [9, 12], w: 1.3, alpha: 0.25 }
      ].flatMap((layer, layerIndex) =>
        Array.from({ length: Math.round(layer.n * unit) }, () => ({
          layer: layerIndex,
          x: rand(0, w), y: rand(-h * 0.2, h),
          vy: rand(...layer.vy) * scale,
          vx: -rand(40, 70) * scale,
          len: rand(...layer.len) * scale,
          w: layer.w, alpha: layer.alpha
        }))
      )
      : []
    this.splashes = []

    this.snow = this.type === 'snow'      ? [
        { n: 24, vy: [46, 70], r: [2.4, 3.6], alpha: 0.9, amp: 14 },
        { n: 20, vy: [30, 46], r: [1.7, 2.5], alpha: 0.6, amp: 10 },
        { n: 14, vy: [18, 28], r: [1.1, 1.8], alpha: 0.35, amp: 7 }
      ].flatMap((layer, layerIndex) =>
        Array.from({ length: Math.round(layer.n * unit) }, (_, index) => ({
          layer: layerIndex,
          x0: rand(0, w), y: rand(-h * 0.15, h),
          vy: rand(...layer.vy) * scale,
          r: rand(...layer.r) * scale,
          alpha: layer.alpha,
          amp: layer.amp * scale,
          freq: rand(0.6, 1.3),
          phase: rand(0, Math.PI * 2),
          crystal: layerIndex === 0 && index % 6 === 0,
          rot: rand(0, Math.PI * 2),
          vr: rand(-0.8, 0.8)
        }))
      )
      : []

    const fogCount = 4 + Math.floor(rand(0, 3))
    this.fogBanks = this.type === 'fog'
      ? Array.from({ length: fogCount }, (_, index) => ({
        variant: Math.floor(rand(0, 3)),
        x: rand(-0.8, 0.8) * w,
        y: h * (0.08 + ((index + 0.5) / fogCount) * 0.8) + rand(-h * 0.03, h * 0.03),
        scale: rand(1.15, 2.05),
        speed: (index % 2 === 0 ? 1 : -1) * rand(3.5, 9),
        alpha: rand(0.09, 0.16)
      }))
      : []

    this.dust = this.type === 'haze'
      ? Array.from({ length: Math.round(36 * unit) }, () => ({
        x: rand(0, w), y: rand(h * 0.15, h),
        vx: rand(5, 14), vy: -rand(3, 9),
        r: rand(1.2, 3.2), phase: rand(0, Math.PI * 2)
      }))
      : []
  }

  start() {
    if (this.running) return
    this.running = true
    this.last = performance.now()
    this.raf = requestAnimationFrame(this.loop)
  }

  stop() {
    this.running = false
    cancelAnimationFrame(this.raf)
  }

  destroy() {
    this.stop()
  }

  loop(now) {
    if (!this.running) return
    const dt = clamp((now - this.last) / 1000, 0, 0.05)
    this.last = now
    this.t += dt
    this.draw(dt)
    this.raf = requestAnimationFrame(this.loop)
  }

  draw(dt) {
    const { ctx, w, h } = this
    if (!w || !h) return
    const sky = ctx.createLinearGradient(0, 0, 0, h)
    sky.addColorStop(0, this.sky[0])
    sky.addColorStop(0.55, this.sky[1])
    sky.addColorStop(1, this.sky[2])
    ctx.fillStyle = sky
    ctx.fillRect(0, 0, w, h)

    if (this.isDay) {
      const horizon = ctx.createLinearGradient(0, h * 0.55, 0, h)
      horizon.addColorStop(0, 'rgba(255,220,150,0)')
      horizon.addColorStop(1, 'rgba(255,220,150,.1)')
      ctx.fillStyle = horizon
      ctx.fillRect(0, h * 0.55, w, h * 0.45)

      const glow = ctx.createRadialGradient(this.sunX, this.sunY, 0, this.sunX, this.sunY, w * 0.72)
      glow.addColorStop(0, 'rgba(255,228,160,.18)')
      glow.addColorStop(0.45, 'rgba(255,200,110,.06)')
      glow.addColorStop(1, 'rgba(255,200,110,0)')
      ctx.fillStyle = glow
      ctx.fillRect(0, 0, w, h)
    } else {
      const moonGlow = ctx.createRadialGradient(this.sunX, this.sunY, 0, this.sunX, this.sunY, w * 0.7)
      moonGlow.addColorStop(0, 'rgba(210,225,255,.14)')
      moonGlow.addColorStop(0.5, 'rgba(180,200,255,.05)')
      moonGlow.addColorStop(1, 'rgba(180,200,255,0)')
      ctx.fillStyle = moonGlow
      ctx.fillRect(0, 0, w, h)
    }

    this.drawStars(dt)
    this.drawSunMoon()
    this.drawClouds(dt, 0, 1)
    this.drawRain(dt)
    this.drawSnow(dt)
    this.drawClouds(dt, 2, 9)
    this.drawFog(dt)
    this.drawHaze(dt)
    this.drawThunder(dt)

    const vignette = ctx.createRadialGradient(w / 2, h * 0.42, h * 0.3, w / 2, h * 0.5, h * 0.95)
    vignette.addColorStop(0, 'rgba(0,0,0,0)')
    vignette.addColorStop(1, 'rgba(0,0,0,.14)')
    ctx.fillStyle = vignette
    ctx.fillRect(0, 0, w, h)

    if (this.fade > 0) {
      ctx.globalAlpha = this.fade
      ctx.drawImage(this.snap, 0, 0, w, h)
      ctx.globalAlpha = 1
      this.fade = Math.max(0, this.fade - dt / 0.7)
    }
  }

  drawStars(dt) {
    const { ctx, w, h } = this
    for (const star of this.stars) {
      const alpha = 0.25 + 0.75 * (Math.sin(this.t * star.speed + star.phase) + 1) / 2
      ctx.globalAlpha = alpha
      ctx.fillStyle = '#fff'
      ctx.beginPath()
      ctx.arc(star.x, star.y, star.r, 0, Math.PI * 2)
      ctx.fill()
    }
    ctx.globalAlpha = 1

    if (this.stars.length) {
      this.shootIn -= dt
      if (this.shootIn <= 0 && !this.shoot) {
        this.shoot = {
          age: 0, life: 0.8,
          x: w * rand(0.5, 0.9), y: h * rand(0.06, 0.22),
          vx: -w * 0.55, vy: h * 0.3
        }
        this.shootIn = rand(8, 14)
      }
      if (this.shoot) {
        this.shoot.age += dt
        const p = this.shoot.age / this.shoot.life
        if (p >= 1) {
          this.shoot = null
        } else {
          const x = this.shoot.x + this.shoot.vx * this.shoot.age
          const y = this.shoot.y + this.shoot.vy * this.shoot.age
          const tailX = x - this.shoot.vx * 0.14
          const tailY = y - this.shoot.vy * 0.14
          const grad = ctx.createLinearGradient(tailX, tailY, x, y)
          grad.addColorStop(0, 'rgba(255,255,255,0)')
          grad.addColorStop(1, 'rgba(255,255,255,.95)')
          ctx.globalAlpha = Math.sin(p * Math.PI)
          ctx.strokeStyle = grad
          ctx.lineWidth = 1.8
          ctx.lineCap = 'round'
          ctx.beginPath()
          ctx.moveTo(tailX, tailY)
          ctx.lineTo(x, y)
          ctx.stroke()
          ctx.fillStyle = '#fff'
          ctx.beginPath()
          ctx.arc(x, y, 1.6, 0, Math.PI * 2)
          ctx.fill()
          ctx.globalAlpha = 1
        }
      }
    }
  }

  drawSunMoon() {
    const { ctx, h } = this
    const x = this.sunX
    const y = this.sunY
    if (!this.isDay) {
      const size = h * 0.38
      ctx.drawImage(this.sprites.moon, x - size / 2, y - size / 2 + Math.sin(this.t * 0.5) * 3, size, size)
      return
    }
    if (this.type !== 'sunny' && !['fog', 'haze'].includes(this.type)) return
    const dim = this.type === 'fog' ? 0.4 : this.type === 'haze' ? 0.6 : 1
    const radius = h * 0.14

    ctx.globalCompositeOperation = 'lighter'
    ctx.globalAlpha = (0.45 + 0.15 * Math.sin(this.t * 0.9)) * dim
    const haloSize = radius * 4.4
    ctx.drawImage(this.sprites.glow, x - haloSize / 2, y - haloSize / 2, haloSize, haloSize)

    if (this.type === 'sunny') {
      ctx.fillStyle = 'rgba(255,240,200,.05)'
      for (let beam = 0; beam < 12; beam++) {
        const angle = beam * Math.PI / 6 + this.t * 0.06
        const inner = radius * 1.15
        const outer = radius * 2.6
        const spread = 0.055
        ctx.beginPath()
        ctx.moveTo(x + Math.cos(angle - spread) * inner, y + Math.sin(angle - spread) * inner)
        ctx.lineTo(x + Math.cos(angle) * outer, y + Math.sin(angle) * outer)
        ctx.lineTo(x + Math.cos(angle + spread) * inner, y + Math.sin(angle + spread) * inner)
        ctx.closePath()
        ctx.fill()
      }
      const flareDirX = (this.w * 0.3 - x)
      const flareDirY = (this.h * 0.65 - y)
      const flares = [[0.35, 9, 0.06], [0.62, 5, 0.05], [0.9, 13, 0.035]]
      for (const [k, r, alpha] of flares) {
        ctx.globalAlpha = alpha
        ctx.fillStyle = 'rgba(255,236,180,1)'
        ctx.beginPath()
        ctx.arc(x + flareDirX * k, y + flareDirY * k, r, 0, Math.PI * 2)
        ctx.fill()
      }
    }
    ctx.globalCompositeOperation = 'source-over'

    ctx.globalAlpha = dim
    const core = ctx.createRadialGradient(x - radius * 0.25, y - radius * 0.25, radius * 0.1, x, y, radius)
    core.addColorStop(0, '#fffef2')
    core.addColorStop(0.45, '#ffe9a0')
    core.addColorStop(0.8, '#ffc84a')
    core.addColorStop(1, 'rgba(255,157,46,0)')
    ctx.fillStyle = core
    ctx.beginPath()
    ctx.arc(x, y, radius, 0, Math.PI * 2)
    ctx.fill()
    ctx.globalAlpha = 1
  }

  drawClouds(dt, fromLayer, toLayer) {
    const { ctx, w } = this
    const variants = this.sprites[this.cloudKind]
    for (const cloud of this.clouds) {
      if (cloud.layer < fromLayer || cloud.layer > toLayer) continue
      cloud.x += cloud.speed * dt
      const cw = w * cloud.size * this.cloudScale
      const ch = cw * (180 / 320)
      if (cloud.x > w + cw * 0.2) cloud.x = -cw
      ctx.globalAlpha = cloud.alpha
      ctx.drawImage(variants[cloud.variant % variants.length], cloud.x, cloud.y, cw, ch)
    }
    ctx.globalAlpha = 1
  }

  drawRain(dt) {
    const { ctx, w, h } = this
    if (!this.rain.length) return
    for (const drop of this.rain) {
      drop.y += drop.vy * dt
      drop.x += drop.vx * dt
      if (drop.y > h - 6 && drop.layer === 0 && this.splashes.length < 40) {
        this.splashes.push({ x: drop.x, y: h - 4, age: 0, life: 0.45 })
      }
      if (drop.y > h + 24 || drop.x < -24) {
        drop.y = rand(-h * 0.25, -10)
        drop.x = rand(0, w + 40)
      }
      ctx.globalAlpha = drop.alpha
      ctx.save()
      ctx.translate(drop.x, drop.y)
      ctx.rotate(Math.atan2(drop.vx, drop.vy))
      ctx.drawImage(this.sprites.drop, -drop.w / 2, 0, drop.w, drop.len)
      ctx.restore()
    }
    ctx.globalAlpha = 1
    ctx.strokeStyle = 'rgba(210,240,255,.8)'
    ctx.lineWidth = 1.2
    for (let index = this.splashes.length - 1; index >= 0; index--) {
      const splash = this.splashes[index]
      splash.age += dt
      const p = splash.age / splash.life
      if (p >= 1) {
        this.splashes.splice(index, 1)
        continue
      }
      ctx.globalAlpha = 0.45 * (1 - p)
      ctx.beginPath()
      ctx.arc(splash.x, splash.y, 2 + 6 * p, Math.PI, 0)
      ctx.stroke()
    }
    ctx.globalAlpha = 1
    const mist = ctx.createLinearGradient(0, h * 0.72, 0, h)
    mist.addColorStop(0, 'rgba(180,210,240,0)')
    mist.addColorStop(1, 'rgba(180,210,240,.09)')
    ctx.fillStyle = mist
    ctx.fillRect(0, h * 0.72, w, h * 0.28)
  }

  drawSnow(dt) {
    const { ctx, w, h } = this
    if (!this.snow.length) return
    for (const flake of this.snow) {
      flake.y += flake.vy * dt
      flake.x0 += 6 * dt
      flake.rot += flake.vr * dt
      if (flake.y > h + 12) {
        flake.y = rand(-h * 0.2, -8)
        flake.x0 = rand(-20, w)
      }
      const x = flake.x0 + Math.sin(this.t * flake.freq + flake.phase) * flake.amp
      ctx.globalAlpha = flake.alpha
      if (flake.crystal) {
        const size = flake.r * 5
        ctx.save()
        ctx.translate(x, flake.y)
        ctx.rotate(flake.rot)
        ctx.drawImage(this.sprites.crystal, -size / 2, -size / 2, size, size)
        ctx.restore()
      } else {
        const size = flake.r * 2.6
        ctx.drawImage(this.sprites.flake, x - size / 2, flake.y - size / 2, size, size)
      }
    }
    ctx.globalAlpha = 1
  }

  drawFog(dt) {
    const { ctx, w, h } = this
    if (!this.fogBanks.length) return
    const variants = this.sprites.cloudGray
    for (const bank of this.fogBanks) {
      bank.x += bank.speed * dt
      const bw = w * bank.scale
      const bh = bw * (180 / 320)
      if (bank.x > w + bw * 0.15) bank.x = -bw
      if (bank.x < -bw * 1.15) bank.x = w
      ctx.globalAlpha = bank.alpha
      ctx.drawImage(variants[bank.variant % variants.length], bank.x, bank.y, bw, bh)
    }
    ctx.globalAlpha = 1
    const veil = ctx.createLinearGradient(0, 0, 0, h)
    veil.addColorStop(0, 'rgba(220,228,240,.03)')
    veil.addColorStop(1, 'rgba(220,228,240,.1)')
    ctx.fillStyle = veil
    ctx.fillRect(0, 0, w, h)
  }

  drawHaze(dt) {
    const { ctx, w, h } = this
    if (!this.dust.length) return
    ctx.fillStyle = 'rgba(255,232,180,.8)'
    for (const dot of this.dust) {
      dot.x += dot.vx * dt
      dot.y += dot.vy * dt
      if (dot.x > w + 6) dot.x = -6
      if (dot.y < -6) dot.y = h + 6
      ctx.globalAlpha = 0.2 + 0.35 * (Math.sin(this.t * 0.8 + dot.phase) + 1) / 2
      ctx.beginPath()
      ctx.arc(dot.x, dot.y, dot.r, 0, Math.PI * 2)
      ctx.fill()
    }
    ctx.globalAlpha = 1
    ctx.fillStyle = 'rgba(120,100,60,.08)'
    ctx.fillRect(0, 0, w, h)
  }

  drawThunder(dt) {
    const { ctx, w, h } = this
    if (this.type !== 'thunder') return
    this.flashIn -= dt
    if (this.flashIn <= 0 && !this.flash) {
      this.flash = { age: 0, bolt: this.makeBolt() }
      this.flashIn = rand(3, 7)
    }
    if (!this.flash) return
    this.flash.age += dt
    const age = this.flash.age
    let env = 0
    if (age < 0.06) env = 1
    else if (age < 0.12) env = 0.25
    else if (age < 0.2) env = 0.9
    else if (age < 0.5) env = 0.5 * (1 - (age - 0.2) / 0.3)
    if (age >= 0.5) {
      this.flash = null
      return
    }
    const bolt = this.flash.bolt
    ctx.globalCompositeOperation = 'lighter'
    ctx.globalAlpha = env * 0.35
    const glowSize = h * 0.7
    ctx.drawImage(this.sprites.glow, bolt[0].x - glowSize / 2, bolt[0].y - glowSize / 2, glowSize, glowSize)
    ctx.globalCompositeOperation = 'source-over'

    ctx.globalAlpha = env
    ctx.strokeStyle = '#fff6cc'
    ctx.lineWidth = 2.6
    ctx.lineJoin = 'round'
    ctx.shadowColor = '#ffe066'
    ctx.shadowBlur = 16
    ctx.beginPath()
    ctx.moveTo(bolt[0].x, bolt[0].y)
    for (const point of bolt.slice(1)) ctx.lineTo(point.x, point.y)
    ctx.stroke()
    ctx.shadowBlur = 0
    ctx.globalAlpha = 1

    ctx.fillStyle = `rgba(255,250,230,${(env * 0.15).toFixed(3)})`
    ctx.fillRect(0, 0, w, h)
  }

  makeBolt() {
    const { w, h } = this
    const points = [{ x: w * rand(0.45, 0.72), y: h * 0.3 }]
    const segments = 7
    for (let index = 1; index <= segments; index++) {
      const prev = points[index - 1]
      points.push({
        x: prev.x + w * rand(-0.06, 0.06),
        y: h * 0.3 + (h * 0.45 * index) / segments
      })
    }
    return points
  }
}