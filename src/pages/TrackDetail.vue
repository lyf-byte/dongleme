<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Download, Route as RouteIcon, Trash2 } from 'lucide-vue-next'
import { deleteRoute, errorMessage, getRoute } from '../utils/api'
import { SPORTS } from '../utils/sport'
import { distanceParts, formatDuration, formatPace, formatSpeed } from '../utils/geo'
import { toast, confirmDialog } from '../utils/ui'
import RouteMap from '../components/RouteMap.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const detail = ref(null)
const errorText = ref('')
const deleting = ref(false)

const distanceView = computed(() => distanceParts(detail.value?.distanceM || 0))
const currentSport = computed(
  () => SPORTS.find((item) => item.name === detail.value?.type) || null
)
const paceLabel = computed(() => (detail.value?.type === '骑行' ? '时速' : '配速'))
const paceValue = computed(() => {
  if (!detail.value) return '--'
  return detail.value.type === '骑行'
    ? `${formatSpeed(detail.value.durationSec, detail.value.distanceM)} km/h`
    : formatPace(detail.value.durationSec, detail.value.distanceM)
})

function parseApiDate(value) {
  if (!value) return null
  if (/[zZ]|[+-]\d{2}:\d{2}$/.test(value)) return new Date(value)
  return new Date(`${value}+08:00`)
}

function formatWhen(value) {
  const date = parseApiDate(value)
  if (!date || Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'Asia/Shanghai'
  }).format(date)
}

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    detail.value = await getRoute(route.params.id)
  } catch (error) {
    errorText.value = errorMessage(error, '轨迹加载失败')
  } finally {
    loading.value = false
  }
}

async function removeRoute() {
  if (!detail.value || deleting.value) return
  try {
    await confirmDialog({
      title: '删除轨迹',
      message: '删除后无法恢复，对应的打卡记录会保留。',
      confirmText: '删除',
      cancelText: '取消',
      danger: true
    })
  } catch {
    return
  }
  deleting.value = true
  try {
    await deleteRoute(detail.value.id)
    toast('轨迹已删除')
    router.replace('/track')
  } catch (error) {
    toast(errorMessage(error, '删除失败'))
  } finally {
    deleting.value = false
  }
}

function drawTrack(ctx, points, x, y, width, height) {
  const valid = (points || []).filter(
    (point) => Number.isFinite(point?.lat) && Number.isFinite(point?.lng)
  )
  if (valid.length < 2) return

  const lats = valid.map((point) => point.lat)
  const lngs = valid.map((point) => point.lng)
  const minLat = Math.min(...lats)
  const maxLat = Math.max(...lats)
  const minLng = Math.min(...lngs)
  const maxLng = Math.max(...lngs)
  const midLat = (minLat + maxLat) / 2
  const latSpan = Math.max(maxLat - minLat, 1e-9)
  const lngSpan = Math.max((maxLng - minLng) * Math.cos((midLat * Math.PI) / 180), 1e-9)
  const pad = 36
  const scale = Math.min((width - pad * 2) / lngSpan, (height - pad * 2) / latSpan)
  const trackW = lngSpan * scale
  const trackH = latSpan * scale
  const offsetX = x + (width - trackW) / 2
  const offsetY = y + (height - trackH) / 2

  const toPoint = (point) => [
    offsetX + (point.lng - minLng) * Math.cos((midLat * Math.PI) / 180) * scale,
    offsetY + (maxLat - point.lat) * scale
  ]

  ctx.save()
  ctx.lineJoin = 'round'
  ctx.lineCap = 'round'
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.18)'
  ctx.lineWidth = 14
  ctx.beginPath()
  valid.forEach((point, index) => {
    const [px, py] = toPoint(point)
    if (index === 0) ctx.moveTo(px, py)
    else ctx.lineTo(px, py)
  })
  ctx.stroke()

  ctx.strokeStyle = '#ffffff'
  ctx.lineWidth = 6
  ctx.shadowColor = 'rgba(47, 107, 255, 0.55)'
  ctx.shadowBlur = 16
  ctx.beginPath()
  valid.forEach((point, index) => {
    const [px, py] = toPoint(point)
    if (index === 0) ctx.moveTo(px, py)
    else ctx.lineTo(px, py)
  })
  ctx.stroke()
  ctx.shadowBlur = 0

  const [sx, sy] = toPoint(valid[0])
  const [ex, ey] = toPoint(valid[valid.length - 1])
  ctx.fillStyle = '#4ade80'
  ctx.beginPath()
  ctx.arc(sx, sy, 8, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = '#fb923c'
  ctx.beginPath()
  ctx.arc(ex, ey, 8, 0, Math.PI * 2)
  ctx.fill()
  ctx.restore()
}

function roundRect(ctx, x, y, width, height, radius) {
  if (typeof ctx.roundRect === 'function') {
    ctx.beginPath()
    ctx.roundRect(x, y, width, height, radius)
    return
  }
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.arcTo(x + width, y, x + width, y + height, radius)
  ctx.arcTo(x + width, y + height, x, y + height, radius)
  ctx.arcTo(x, y + height, x, y, radius)
  ctx.arcTo(x, y, x + width, y, radius)
  ctx.closePath()
}

function savePoster() {
  if (!detail.value) return
  const canvas = document.createElement('canvas')
  const width = 720
  const height = 1040
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    toast('当前浏览器不支持生成海报')
    return
  }

  const background = ctx.createLinearGradient(0, 0, width, height)
  background.addColorStop(0, '#2f6bff')
  background.addColorStop(1, '#10265f')
  ctx.fillStyle = background
  ctx.fillRect(0, 0, width, height)

  ctx.fillStyle = 'rgba(255,255,255,0.08)'
  ctx.beginPath()
  ctx.arc(620, 170, 190, 0, Math.PI * 2)
  ctx.fill()
  ctx.beginPath()
  ctx.arc(80, 930, 150, 0, Math.PI * 2)
  ctx.fill()

  const font = "'PingFang SC', 'Microsoft YaHei', sans-serif"
  ctx.fillStyle = 'rgba(255,255,255,0.78)'
  ctx.font = `28px ${font}`
  ctx.fillText('动了么 · 运动轨迹', 56, 92)

  ctx.fillStyle = '#ffffff'
  ctx.font = `bold 56px ${font}`
  ctx.fillText(`${detail.value.type}轨迹`, 56, 174)

  ctx.fillStyle = 'rgba(255,255,255,0.82)'
  ctx.font = `28px ${font}`
  ctx.fillText(formatWhen(detail.value.startedAt), 56, 224)

  ctx.fillStyle = 'rgba(255,255,255,0.1)'
  ctx.strokeStyle = 'rgba(255,255,255,0.16)'
  ctx.lineWidth = 2
  roundRect(ctx, 44, 270, width - 88, 470, 32)
  ctx.fill()
  ctx.stroke()
  drawTrack(ctx, detail.value.points, 60, 286, width - 120, 438)

  const metrics = [
    { label: '距离', value: distanceView.value.value, unit: distanceView.value.unit },
    { label: '时长', value: formatDuration(detail.value.durationSec), unit: '' },
    { label: paceLabel.value, value: paceValue.value, unit: '' },
    { label: '热量', value: detail.value.calories, unit: '千卡' }
  ]
  const startX = 56
  const columnWidth = (width - 112) / 4
  metrics.forEach((metric, index) => {
    const x = startX + columnWidth * index
    ctx.fillStyle = 'rgba(255,255,255,0.62)'
    ctx.font = `24px ${font}`
    ctx.fillText(metric.label, x, 810)
    ctx.fillStyle = '#ffffff'
    ctx.font = `bold 42px ${font}`
    ctx.fillText(String(metric.value), x, 864)
    if (metric.unit) {
      ctx.fillStyle = 'rgba(255,255,255,0.7)'
      ctx.font = `22px ${font}`
      ctx.fillText(metric.unit, x, 898)
    }
  })

  ctx.fillStyle = 'rgba(255,255,255,0.62)'
  ctx.font = `24px ${font}`
  ctx.fillText('每天动一点，连续看得见', 56, 970)

  canvas.toBlob((blob) => {
    if (!blob) {
      toast('海报生成失败')
      return
    }
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `dongleme-route-${detail.value.id}.png`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    toast('轨迹海报已保存')
  }, 'image/png')
}

onMounted(load)
</script>

<template>
  <div class="track-detail">
    <header class="topbar">
      <button type="button" class="topbar__btn" aria-label="返回" @click="router.back()">
        <ArrowLeft :size="20" />
      </button>
      <h1>轨迹详情</h1>
      <button
        v-if="detail"
        type="button"
        class="topbar__btn"
        aria-label="保存轨迹海报"
        @click="savePoster"
      >
        <Download :size="18" />
      </button>
      <button
        v-if="detail"
        type="button"
        class="topbar__btn topbar__btn--danger"
        aria-label="删除轨迹"
        :disabled="deleting"
        @click="removeRoute"
      >
        <Trash2 :size="18" />
      </button>
    </header>

    <template v-if="loading">
      <div class="card loading-card">
        <div class="loading-line"><span></span></div>
      </div>
    </template>

    <template v-else-if="detail">
      <section class="detail-map">
        <RouteMap :points="detail.points" mode="detail" :follow="false" />
      </section>

      <section class="card detail-card">
        <div class="detail-head">
          <span class="detail-icon">
            <component :is="currentSport?.icon || RouteIcon" :size="20" />
          </span>
          <div>
            <h2>{{ detail.type }}</h2>
            <p>{{ formatWhen(detail.startedAt) }}</p>
          </div>
        </div>

        <div class="detail-stats">
          <div>
            <span>距离</span>
            <strong>{{ distanceView.value }}<small>{{ distanceView.unit }}</small></strong>
          </div>
          <div>
            <span>时长</span>
            <strong>{{ formatDuration(detail.durationSec) }}</strong>
          </div>
          <div>
            <span>{{ paceLabel }}</span>
            <strong>{{ paceValue }}</strong>
          </div>
          <div>
            <span>热量</span>
            <strong>{{ detail.calories }}<small>千卡</small></strong>
          </div>
        </div>

        <p class="detail-foot">
          共 {{ detail.pointCount }} 个轨迹点 · 已自动计入当天打卡
        </p>
      </section>
    </template>

    <div v-else class="card empty-state">
      <RouteIcon :size="24" />
      <h3>轨迹不存在</h3>
      <p>{{ errorText || '这条轨迹可能已被删除。' }}</p>
      <button type="button" class="btn-primary btn-sm" @click="router.replace('/track')">
        返回运动轨迹
      </button>
    </div>
  </div>
</template>

<style scoped>
.track-detail {
  min-height: 100vh;
  padding: 0 20px calc(28px + env(safe-area-inset-bottom));
  background: var(--bg);
}

.topbar__btn--danger {
  border-color: rgba(239, 68, 68, 0.22);
  color: var(--red);
}

.topbar__btn:disabled {
  opacity: 0.5;
}

.detail-map {
  height: 300px;
  margin-top: 8px;
  border-radius: 24px;
  overflow: hidden;
  background: #dbeafe;
  box-shadow: 0 14px 30px rgba(47, 107, 255, 0.12);
}

.detail-card {
  margin-top: 14px;
  padding: 18px;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: rgba(47, 107, 255, 0.08);
  color: var(--blue);
}

.detail-head h2 {
  margin: 0;
  color: var(--text);
  font-size: 18px;
}

.detail-head p {
  margin: 2px 0 0;
  color: var(--muted);
  font-size: 11px;
}

.detail-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-top: 16px;
}

.detail-stats > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
  border-radius: 14px;
  background: #f8fafc;
  padding: 10px;
}

.detail-stats span {
  color: var(--muted);
  font-size: 10px;
}

.detail-stats strong {
  color: var(--text);
  font-size: 16px;
  white-space: nowrap;
}

.detail-stats small {
  margin-left: 2px;
  color: var(--muted);
  font-size: 9px;
  font-weight: 500;
}

.detail-foot {
  margin: 14px 0 0;
  color: var(--muted);
  font-size: 11px;
}

.loading-card {
  margin-top: 14px;
  padding: 18px;
}

.loading-line {
  height: 14px;
  border-radius: 999px;
  background: #eef2f7;
  overflow: hidden;
}

.loading-line span {
  display: block;
  width: 42%;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #e2e8f0, #f8fafc, #e2e8f0);
  animation: detailLoading 1.2s ease-in-out infinite;
}

@keyframes detailLoading {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(260%); }
}
</style>
