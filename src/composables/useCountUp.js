import { ref, watch } from 'vue'

/**
 * 数字滚动：目标值变化时，从当前显示值平滑过渡到新值。
 * 返回一个只读的 ref，直接渲染即可。
 */
export function useCountUp(source, duration = 900) {
  const view = ref(Number(source.value) || 0)
  let rafId = 0
  let from = 0
  let to = 0
  let startTs = 0

  function stop() {
    if (rafId) {
      cancelAnimationFrame(rafId)
      rafId = 0
    }
  }

  function step(ts) {
    if (!startTs) startTs = ts
    const t = Math.min(1, (ts - startTs) / duration)
    const eased = 1 - Math.pow(1 - t, 3)
    view.value = Math.round(from + (to - from) * eased)
    if (t < 1) {
      rafId = requestAnimationFrame(step)
    } else {
      rafId = 0
    }
  }

  watch(
    source,
    (next) => {
      const target = Number(next) || 0
      stop()
      if (target === view.value) return
      from = view.value
      to = target
      startTs = 0
      rafId = requestAnimationFrame(step)
    },
    { immediate: true }
  )

  return view
}
