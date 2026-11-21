<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Download, Route as RouteIcon, Trash2 } from 'lucide-vue-next'
import { deleteRoute, errorMessage, getRoute } from '../utils/api'
import { SPORTS } from '../utils/sport'
import { distanceParts, formatDuration, formatPace, formatSpeed } from '../utils/geo'
import { toast, confirmDialog } from '../utils/ui'
import { saveBlob } from '../utils/native'
import posterTrack from '../assets/poster-track.jpg'
import {
  POSTER_FONT,
  canvasToBlob,
  clipText,
  drawBandChrome,
  drawStatColumns,
  loadPosterImage
} from '../utils/poster'
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

const POSTER_WIDTH = 1024
const POSTER_IMAGE_HEIGHT = 1024
const POSTER_BAND_HEIGHT = 288

async function savePoster() {
  if (!detail.value) return

  let image
  try {
    image = await loadPosterImage(posterTrack)
  } catch {
    toast('海报底图加载失败，请重试')
    return
  }

  const canvas = document.createElement('canvas')
  const bandTop = POSTER_IMAGE_HEIGHT
  canvas.width = POSTER_WIDTH
  canvas.height = bandTop + POSTER_BAND_HEIGHT
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    toast('当前浏览器不支持生成海报')
    return
  }

  ctx.drawImage(image, 0, 0, POSTER_WIDTH, POSTER_IMAGE_HEIGHT)
  drawBandChrome(ctx, POSTER_WIDTH, bandTop, POSTER_BAND_HEIGHT)

  ctx.fillStyle = 'rgba(255,255,255,0.5)'
  ctx.font = `24px ${POSTER_FONT}`
  ctx.fillText('动了么 · 运动轨迹', 64, bandTop + 56)

  ctx.fillStyle = '#ffffff'
  ctx.font = `bold 42px ${POSTER_FONT}`
  const title = `${detail.value.type || '运动'} · ${formatWhen(detail.value.startedAt)}`
  ctx.fillText(clipText(ctx, title, 700), 64, bandTop + 116)

  drawStatColumns(ctx, [
    { label: '距离', value: distanceView.value.value, unit: distanceView.value.unit },
    { label: '时长', value: formatDuration(detail.value.durationSec), unit: '' },
    { label: paceLabel.value, value: paceValue.value, unit: '' },
    { label: '热量', value: detail.value.calories, unit: '千卡' }
  ], {
    left: 64,
    right: POSTER_WIDTH - 64,
    labelY: bandTop + 182,
    valueY: bandTop + 240,
    valueSize: 42,
    spread: true
  })

  try {
    const blob = await canvasToBlob(canvas)
    const result = await saveBlob(blob, `dongleme-route-${detail.value.id}.png`)
    toast(result === 'ok' ? '轨迹海报已保存' : '保存失败，请重试')
  } catch {
    toast('海报生成失败')
  }
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
