<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { WeatherEngine } from '../utils/weatherEngine'

const props = defineProps({
  type: { type: String, default: 'cloudy' },
  isDay: { type: Boolean, default: true }
})
const emit = defineEmits(['fallback'])

const canvas = ref(null)
let engine = null
let resizeObserver = null
let intersectionObserver = null
let inView = true

function syncRunning() {
  if (!engine) return
  if (document.hidden || !inView) engine.stop()
  else engine.start()
}

function onVisibility() {
  syncRunning()
}

onMounted(() => {
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const ctx = reduceMotion ? null : canvas.value.getContext('2d', { alpha: false })
  if (!ctx) {
    emit('fallback')
    return
  }

  engine = new WeatherEngine(canvas.value, ctx, props.type, props.isDay)
  const rect = canvas.value.getBoundingClientRect()
  engine.resize(rect.width, rect.height)

  resizeObserver = new ResizeObserver((entries) => {
    const { width, height } = entries[0].contentRect
    engine.resize(width, height)
  })
  resizeObserver.observe(canvas.value)

  intersectionObserver = new IntersectionObserver((entries) => {
    inView = entries[0].isIntersecting
    syncRunning()
  })
  intersectionObserver.observe(canvas.value)

  document.addEventListener('visibilitychange', onVisibility)
  syncRunning()
})

watch(
  () => [props.type, props.isDay],
  ([type, isDay]) => engine?.setScene(type, isDay)
)

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', onVisibility)
  resizeObserver?.disconnect()
  intersectionObserver?.disconnect()
  engine?.destroy()
  engine = null
})
</script>

<template>
  <canvas ref="canvas" class="weather-canvas" aria-hidden="true" />
</template>

<style scoped>
.weather-canvas {
  display: block;
  width: 100%;
  height: 100%;
}
</style>