<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { MapPin } from 'lucide-vue-next'
import { hasAmapKey, loadAmap } from '../utils/amap'
import { cumulativeDistances } from '../utils/routeSegments'

const props = defineProps({
  points: { type: Array, default: () => [] },
  mode: { type: String, default: 'live' },
  follow: { type: Boolean, default: true },
  sport: { type: String, default: '跑步' },
  center: { type: Array, default: null },
  /* 按配速切好的分段，传入后替代单色线渲染 */
  segments: { type: Array, default: () => [] }
})

const emit = defineEmits(['ready', 'missing-key', 'error'])

const el = ref(null)
const state = ref('loading')
const manualMove = ref(false)

let map = null
let AMapRef = null
let polyline = null
let startMarker = null
let endMarker = null
let currentMarker = null
let accuracyCircle = null
let previewMarker = null
let disposed = false

let segmentLines = []
let maskLine = null
let progressMarker = null
let progressPoints = []
let progressDists = []
let progressTotal = 0

function validPath() {
  return (props.points || [])
    .filter((point) => Number.isFinite(point?.lat) && Number.isFinite(point?.lng))
    .map((point) => [point.lng, point.lat])
}

function ensureMarker(kind, position, color, radius) {
  const options = {
    center: position,
    radius,
    strokeColor: '#ffffff',
    strokeWeight: 3,
    fillColor: color,
    fillOpacity: 1,
    zIndex: kind === 'current' ? 120 : 100
  }
  if (kind === 'start') {
    if (!startMarker) {
      startMarker = new AMapRef.CircleMarker(options)
      map.add(startMarker)
    } else {
      startMarker.setCenter(position)
    }
    return
  }
  if (kind === 'end') {
    if (!endMarker) {
      endMarker = new AMapRef.CircleMarker(options)
      map.add(endMarker)
    } else {
      endMarker.setCenter(position)
    }
    return
  }
  if (!currentMarker) {
    currentMarker = new AMapRef.CircleMarker(options)
    map.add(currentMarker)
  } else {
    currentMarker.setCenter(position)
  }
}

function drawPreview() {
  if (state.value !== 'ready' || !map) return
  if (!props.center || props.points.length) {
    if (previewMarker) {
      map.remove(previewMarker)
      previewMarker = null
    }
    return
  }
  const position = props.center
  if (!previewMarker) {
    previewMarker = new AMapRef.CircleMarker({
      center: position,
      radius: 8,
      strokeColor: '#ffffff',
      strokeWeight: 3,
      fillColor: '#2f6bff',
      fillOpacity: 1,
      zIndex: 120
    })
    map.add(previewMarker)
  } else {
    previewMarker.setCenter(position)
  }
}

function draw(fit = false) {
  if (state.value !== 'ready' || !map) return
  drawPreview()
  const path = validPath()
  if (!path.length) {
    if (polyline) {
      map.remove(polyline)
      polyline = null
    }
    clearSegmentLines()
    resetProgress()
    if (startMarker) {
      map.remove(startMarker)
      startMarker = null
    }
    if (endMarker) {
      map.remove(endMarker)
      endMarker = null
    }
    if (currentMarker) {
      map.remove(currentMarker)
      currentMarker = null
    }
    if (accuracyCircle) {
      map.remove(accuracyCircle)
      accuracyCircle = null
    }
    return
  }

  if (props.segments.length) {
    if (polyline) {
      map.remove(polyline)
      polyline = null
    }
    drawSegments()
  } else {
    clearSegmentLines()
    if (!polyline) {
      polyline = new AMapRef.Polyline({
        path,
        strokeColor: '#2f6bff',
        strokeWeight: 6,
        strokeOpacity: 0.92,
        lineJoin: 'round',
        lineCap: 'round',
        showDir: true,
        zIndex: 60
      })
      map.add(polyline)
    } else {
      polyline.setPath(path)
    }
  }

  ensureMarker('start', path[0], '#16a34a', 7)

  if (props.mode === 'detail') {
    ensureMarker('end', path[path.length - 1], '#f97316', 8)
    if (currentMarker) {
      map.remove(currentMarker)
      currentMarker = null
    }
    if (accuracyCircle) {
      map.remove(accuracyCircle)
      accuracyCircle = null
    }
    if (fit) focusTrack()
    return
  }

  if (endMarker) {
    map.remove(endMarker)
    endMarker = null
  }
  const lastPoint = props.points[props.points.length - 1]
  ensureMarker('current', path[path.length - 1], '#2f6bff', 8)

  if (Number.isFinite(lastPoint?.acc) && lastPoint.acc > 0) {
    if (!accuracyCircle) {
      accuracyCircle = new AMapRef.Circle({
        center: path[path.length - 1],
        radius: lastPoint.acc,
        strokeColor: '#2f6bff',
        strokeOpacity: 0.25,
        strokeWeight: 1,
        fillColor: '#2f6bff',
        fillOpacity: 0.08,
        zIndex: 50
      })
      map.add(accuracyCircle)
    } else {
      accuracyCircle.setCenter(path[path.length - 1])
      accuracyCircle.setRadius(lastPoint.acc)
    }
  } else if (accuracyCircle) {
    map.remove(accuracyCircle)
    accuracyCircle = null
  }

  if (props.follow && !manualMove.value) {
    if (path.length === 1) {
      map.setZoomAndCenter(17, path[0])
    } else {
      map.setCenter(path[path.length - 1])
    }
  }
}

function clearSegmentLines() {
  segmentLines.forEach((line) => map.remove(line))
  segmentLines = []
}

function drawSegments() {
  clearSegmentLines()
  if (!map || !props.segments.length) return
  props.segments.forEach((segment) => {
    const line = new AMapRef.Polyline({
      path: segment.path,
      strokeColor: segment.color,
      strokeWeight: 6,
      strokeOpacity: 0.95,
      lineJoin: 'round',
      lineCap: 'round',
      showDir: false,
      zIndex: 60
    })
    map.add(line)
    segmentLines.push(line)
  })
}

function prepareProgress() {
  progressPoints = (props.points || []).filter(
    (point) => Number.isFinite(point?.lat) && Number.isFinite(point?.lng)
  )
  progressDists = cumulativeDistances(progressPoints)
  progressTotal = progressDists[progressDists.length - 1] || 0
}

/** 回放：用灰色遮罩盖住未走过的部分，当前位置用圆点标记 */
function setProgress(ratio) {
  if (state.value !== 'ready' || !map) return
  if (!progressPoints.length) prepareProgress()
  if (!progressPoints.length) return

  const clamped = Math.max(0, Math.min(1, ratio))
  const target = clamped * progressTotal
  let index = 0
  while (index < progressDists.length - 1 && progressDists[index] < target) index += 1

  const head = [progressPoints[index].lng, progressPoints[index].lat]
  const remain = progressPoints.slice(index).map((point) => [point.lng, point.lat])

  if (!progressMarker) {
    progressMarker = new AMapRef.CircleMarker({
      center: head,
      radius: 8,
      strokeColor: '#ffffff',
      strokeWeight: 3,
      fillColor: '#2f6bff',
      fillOpacity: 1,
      zIndex: 130
    })
    map.add(progressMarker)
  } else {
    progressMarker.setCenter(head)
  }

  if (clamped >= 1) {
    if (maskLine) {
      map.remove(maskLine)
      maskLine = null
    }
  } else if (remain.length > 1) {
    if (!maskLine) {
      maskLine = new AMapRef.Polyline({
        path: remain,
        strokeColor: '#cbd5e1',
        strokeWeight: 6,
        strokeOpacity: 0.92,
        lineJoin: 'round',
        lineCap: 'round',
        zIndex: 70
      })
      map.add(maskLine)
    } else {
      maskLine.setPath(remain)
    }
  }

  map.setCenter(head)
}

function resetProgress() {
  if (maskLine && map) {
    map.remove(maskLine)
    maskLine = null
  }
  if (progressMarker && map) {
    map.remove(progressMarker)
    progressMarker = null
  }
}

function focusTrack() {
  if (!map) return
  const targets = segmentLines.length ? segmentLines : polyline ? [polyline] : []
  if (targets.length) map.setFitView(targets, false, [60, 60, 60, 60])
}

async function init() {
  if (state.value === 'ready') return
  if (!hasAmapKey()) {
    state.value = 'missing'
    emit('missing-key')
    return
  }
  state.value = 'loading'
  try {
    const AMap = await loadAmap()
    if (disposed) return
    AMapRef = AMap
    await nextTick()
    map = new AMap.Map(el.value, {
      zoom: props.center ? 16 : 12,
      center: props.center || [106.5516, 29.563],
      viewMode: '2D',
      mapStyle: 'amap://styles/whitesmoke'
    })
    map.on('dragstart', () => {
      manualMove.value = true
    })
    state.value = 'ready'
    emit('ready')
    draw(props.mode === 'detail')
  } catch (error) {
    if (disposed) return
    state.value = 'error'
    emit('error', error)
  }
}

function recenter() {
  manualMove.value = false
  if (!map) return
  const path = validPath()
  if (path.length) {
    map.setZoomAndCenter(17, path[path.length - 1])
  } else if (props.center) {
    map.setZoomAndCenter(16, props.center)
  }
}

watch(() => props.points, () => draw(false))
watch(() => props.mode, () => draw(true))
watch(
  () => props.segments,
  () => {
    progressPoints = []
    if (state.value === 'ready') draw(false)
  }
)
watch(() => props.follow, (follow) => {
  if (follow) recenter()
})
watch(() => props.center, (center) => {
  if (!map || state.value !== 'ready') return
  if (center && !props.points.length) map.setZoomAndCenter(16, center)
  drawPreview()
})

onMounted(init)

onBeforeUnmount(() => {
  disposed = true
  if (map) {
    map.destroy()
    map = null
  }
})

defineExpose({ recenter, setProgress, resetProgress, focusTrack })
</script>

<template>
  <div class="route-map">
    <div ref="el" class="route-map__canvas" :class="{ 'is-hidden': state !== 'ready' }"></div>

    <div v-if="state !== 'ready'" class="route-map__placeholder">
      <span class="route-map__icon"><MapPin :size="26" /></span>
      <strong v-if="state === 'missing'">地图未配置</strong>
      <strong v-else-if="state === 'error'">地图加载失败</strong>
      <strong v-else>地图加载中…</strong>
      <span v-if="state === 'missing'">在 .env 填写高德 JS API Key</span>
      <span v-else-if="state === 'error'">检查网络或 Key 域名白名单</span>
      <button v-if="state === 'error'" type="button" class="route-map__retry" @click="init">
        重试
      </button>
    </div>

    <button
      v-if="state === 'ready' && mode === 'live' && manualMove"
      type="button"
      class="route-map__recenter"
      @click="recenter"
    >
      回到当前位置
    </button>
  </div>
</template>

<style scoped>
.route-map {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 180px;
  overflow: hidden;
  background: linear-gradient(160deg, #dbeafe, #eff6ff);
}

.route-map__canvas {
  width: 100%;
  height: 100%;
}

.route-map__canvas.is-hidden {
  visibility: hidden;
}

.route-map__placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 20px;
  text-align: center;
  color: #64748b;
  font-size: 12px;
}

.route-map__placeholder strong {
  color: #334155;
  font-size: 14px;
}

.route-map__icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin-bottom: 2px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.75);
  color: var(--blue);
  box-shadow: 0 8px 20px rgba(47, 107, 255, 0.12);
}

.route-map__retry {
  margin-top: 4px;
  border: 1px solid rgba(47, 107, 255, 0.28);
  border-radius: 999px;
  background: #fff;
  padding: 5px 14px;
  color: var(--blue);
  font-size: 12px;
  font-weight: 600;
}

.route-map__recenter {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 3;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.94);
  padding: 8px 12px;
  color: var(--blue);
  font-size: 11px;
  font-weight: 600;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.14);
}

/* 高德 logo 与版权标注：统一移到左下角，层级压低，
   启动页/记录中页的底部面板会自然盖住它，避免与提示文字重叠 */
.route-map :deep(.amap-logo) {
  right: auto !important;
  left: 10px !important;
  bottom: 6px !important;
  z-index: 3 !important;
}

.route-map :deep(.amap-copyright) {
  right: auto !important;
  left: 92px !important;
  bottom: 8px !important;
  z-index: 3 !important;
}
</style>
