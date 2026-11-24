<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: Number, default: 0 },
  goal: { type: Number, default: 150 },
  size: { type: Number, default: 94 },
  thickness: { type: Number, default: 9 }
})

const uid = `ring-${Math.random().toString(36).slice(2, 9)}`

const radius = computed(() => (props.size - props.thickness) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)
const ratio = computed(() => {
  if (!props.goal || props.goal <= 0) return 0
  return Math.max(0, Math.min(1, props.value / props.goal))
})
const percent = computed(() => Math.round(ratio.value * 100))
const dash = computed(
  () => `${circumference.value * ratio.value} ${circumference.value}`
)
const half = computed(() => props.size / 2)
</script>

<template>
  <div class="goal-ring" :style="{ width: `${size}px`, height: `${size}px` }">
    <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`" aria-hidden="true">
      <defs>
        <linearGradient :id="uid" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#4f8bff" />
          <stop offset="100%" stop-color="#8b5cf6" />
        </linearGradient>
      </defs>
      <circle
        class="goal-ring__track"
        :cx="half"
        :cy="half"
        :r="radius"
        :stroke-width="thickness"
      />
      <circle
        class="goal-ring__bar"
        :cx="half"
        :cy="half"
        :r="radius"
        :stroke="`url(#${uid})`"
        :stroke-width="thickness"
        :stroke-dasharray="dash"
        stroke-linecap="round"
        :transform="`rotate(-90 ${half} ${half})`"
      />
    </svg>
    <div class="goal-ring__center">
      <slot :percent="percent">
        <strong>{{ percent }}<em>%</em></strong>
      </slot>
    </div>
  </div>
</template>

<style scoped>
.goal-ring {
  position: relative;
  flex: 0 0 auto;
}

.goal-ring__track {
  fill: none;
  stroke: var(--blue-soft);
}

.goal-ring__bar {
  fill: none;
  filter: drop-shadow(0 2px 6px rgba(79, 139, 255, 0.45));
  transition: stroke-dasharray 0.7s cubic-bezier(0.22, 1, 0.36, 1);
}

.goal-ring__center {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  text-align: center;
}

.goal-ring__center strong {
  font-size: 20px;
  line-height: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
  color: var(--text);
}

.goal-ring__center em {
  margin-left: 1px;
  font-size: 11px;
  font-style: normal;
  font-weight: 600;
  color: var(--muted);
}

@media (prefers-reduced-motion: reduce) {
  .goal-ring__bar {
    transition: none;
  }
}
</style>
