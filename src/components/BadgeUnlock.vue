<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  /** 新解锁勋章队列，展示完最后一条后触发 close */
  badges: { type: Array, default: () => [] }
})

const emit = defineEmits(['close'])

const index = ref(0)

watch(
  () => props.badges,
  () => {
    index.value = 0
  }
)

const current = computed(() => props.badges[index.value] || null)
const isLast = computed(() => index.value >= props.badges.length - 1)

function next() {
  if (isLast.value) {
    emit('close')
    return
  }
  index.value += 1
}
</script>

<template>
  <transition name="badge-pop">
    <div v-if="current" class="unlock-overlay" @click.self="next">
      <div class="unlock-card" role="dialog" aria-modal="true" :aria-label="current.name">
        <span class="unlock-card__rays" :style="{ color: current.tierStyle?.color || '#2f6bff' }"></span>
        <span
          class="unlock-card__icon"
          :style="{
            background: current.tierStyle?.soft || '#eef2f7',
            color: current.tierStyle?.color || 'var(--blue)'
          }"
        >
          <component :is="current.icon" :size="34" />
        </span>
        <small class="unlock-card__tag">解锁新徽章</small>
        <strong>{{ current.name }}</strong>
        <p>{{ current.desc }}</p>
        <button type="button" class="btn-primary btn-sm unlock-card__btn" @click="next">
          {{ isLast ? '收下' : '下一个' }}
        </button>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.unlock-overlay {
  position: fixed;
  inset: 0;
  z-index: 230;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at 50% 34%, rgba(139, 92, 246, 0.16), transparent 46%),
    rgba(15, 23, 42, 0.5);
}

.unlock-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 100%;
  max-width: 320px;
  border-radius: 24px;
  background: #fff;
  padding: 30px 24px 20px;
  text-align: center;
  box-shadow: 0 28px 70px rgba(15, 23, 42, 0.3);
}

.unlock-card__rays {
  position: absolute;
  top: 22px;
  width: 120px;
  height: 120px;
  background: radial-gradient(circle, currentColor 0%, transparent 62%);
  opacity: 0.14;
  pointer-events: none;
}

.unlock-card__icon {
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  border-radius: 24px;
  box-shadow: inset 0 0 0 1px rgba(17, 24, 39, 0.06);
  animation: badgeBounce 0.6s cubic-bezier(0.2, 1.4, 0.4, 1) both;
}

.unlock-card__tag {
  margin-top: 8px;
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--muted);
}

.unlock-card strong {
  font-size: 19px;
  color: var(--text);
}

.unlock-card p {
  margin: 0;
  font-size: 12px;
  line-height: 17px;
  color: var(--muted);
}

.unlock-card__btn {
  width: 100%;
  margin-top: 12px;
}

@keyframes badgeBounce {
  0% {
    opacity: 0;
    transform: scale(0.5) rotate(-8deg);
  }
  70% {
    transform: scale(1.08) rotate(2deg);
  }
  100% {
    opacity: 1;
    transform: none;
  }
}

.badge-pop-enter-active {
  transition: opacity 0.24s ease;
}

.badge-pop-enter-active .unlock-card {
  transition: transform 0.3s cubic-bezier(0.2, 1.2, 0.4, 1);
}

.badge-pop-enter-from {
  opacity: 0;
}

.badge-pop-enter-from .unlock-card {
  transform: translateY(18px) scale(0.92);
}

@media (prefers-reduced-motion: reduce) {
  .unlock-card__icon {
    animation: none;
  }
}
</style>
