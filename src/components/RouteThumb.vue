<script setup>
import { computed } from 'vue'

const props = defineProps({
  points: { type: Array, default: () => [] }
})

const geometry = computed(() => {
  const valid = (props.points || []).filter(
    (point) => Array.isArray(point) && Number.isFinite(point[0]) && Number.isFinite(point[1])
  )
  if (valid.length < 2) return { path: '', start: null, end: null }

  const lats = valid.map((point) => point[0])
  const lngs = valid.map((point) => point[1])
  const minLat = Math.min(...lats)
  const maxLat = Math.max(...lats)
  const minLng = Math.min(...lngs)
  const maxLng = Math.max(...lngs)
  const midLat = (minLat + maxLat) / 2
  const latSpan = Math.max(maxLat - minLat, 1e-9)
  const lngSpan = Math.max(
    (maxLng - minLng) * Math.cos((midLat * Math.PI) / 180),
    1e-9
  )
  const span = Math.max(latSpan, lngSpan)
  const pad = 4.5
  const inner = 40 - pad * 2
  const coords = valid.map((point) => {
    const x = pad + (((point[1] - minLng) * Math.cos((midLat * Math.PI) / 180)) / span) * inner
    const y = pad + ((maxLat - point[0]) / span) * inner
    return [Number(x.toFixed(1)), Number(y.toFixed(1))]
  })

  return {
    path: coords.map(([x, y]) => `${x},${y}`).join(' '),
    start: coords[0],
    end: coords[coords.length - 1]
  }
})
</script>

<template>
  <svg class="route-thumb" viewBox="0 0 40 40" aria-hidden="true">
    <template v-if="geometry.path">
      <polyline
        :points="geometry.path"
        fill="none"
        stroke="currentColor"
        stroke-width="2.4"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
      <circle
        :cx="geometry.start[0]"
        :cy="geometry.start[1]"
        r="2.3"
        fill="#16a34a"
        stroke="#fff"
        stroke-width="1"
      />
      <circle
        :cx="geometry.end[0]"
        :cy="geometry.end[1]"
        r="2.3"
        fill="#f97316"
        stroke="#fff"
        stroke-width="1"
      />
    </template>
  </svg>
</template>

<style scoped>
.route-thumb {
  width: 100%;
  height: 100%;
  display: block;
}
</style>
