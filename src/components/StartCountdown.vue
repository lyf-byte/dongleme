<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

/**
 * 开始前的全屏 3-2-1 倒计时。
 * 这几秒同时给 GPS 预热留时间（由父页面在倒计时开始时刷新定位）。
 */
const props = defineProps({
  sport: { type: String, default: '跑步' },
  from: { type: Number, default: 3 }
})

const emit = defineEmits(['done'])

const left = ref(props.from)
let timer = null

function clearTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function tick() {
  left.value -= 1
  if ('vibrate' in navigator) {
    try {
      navigator.vibrate(60)
    } catch {
      /* 不支持就算了 */
    }
  }
  if (left.value <= 0) {
    clearTimer()
    emit('done')
  }
}

function skip() {
  clearTimer()
  emit('done')
}

onMounted(() => {
  timer = setInterval(tick, 1000)
})

onUnmounted(clearTimer)
</script>

<template>
  <div class="countdown" role="alert" aria-label="即将开始运动记录">
    <button type="button" class="countdown__skip" @click="skip">跳过</button>

    <transition name="cd" mode="out-in">
      <strong :key="left" class="countdown__num">{{ left > 0 ? left : 'GO' }}</strong>
    </transition>
    <p class="countdown__sub">即将开始 · {{ sport }}</p>

    <div class="countdown__bar">
      <div>
        <strong>0.00</strong>
        <span>总距离</span>
      </div>
      <div>
        <strong>00:00</strong>
        <span>总时长</span>
      </div>
      <div>
        <strong>--'--"</strong>
        <span>实时配速</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.countdown {
  position: absolute;
  inset: 0;
  z-index: 30;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(2, 8, 23, 0.55);
  color: #fff;
}

.countdown__skip {
  position: absolute;
  top: calc(14px + env(safe-area-inset-top));
  right: 16px;
  height: 30px;
  padding: 0 14px;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: #0f172a;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.countdown__num {
  font-size: 96px;
  line-height: 1;
  font-weight: 700;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
  text-shadow: 0 10px 30px rgba(2, 8, 23, 0.4);
}

.countdown__sub {
  margin: 18px 0 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
}

.countdown__bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 20px 20px calc(22px + env(safe-area-inset-bottom));
  border-radius: 26px 26px 0 0;
  background: rgba(15, 23, 42, 0.92);
}

.countdown__bar > div {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.countdown__bar strong {
  font-size: 20px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.countdown__bar span {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.55);
}

.cd-enter-active,
.cd-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.cd-enter-from {
  opacity: 0;
  transform: scale(1.6);
}

.cd-leave-to {
  opacity: 0;
  transform: scale(0.6);
}

@media (prefers-reduced-motion: reduce) {
  .cd-enter-active,
  .cd-leave-active {
    transition: none;
  }
}
</style>
