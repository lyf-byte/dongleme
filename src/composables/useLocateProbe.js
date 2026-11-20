import { computed, ref } from 'vue'
import { wgs84ToGcj02 } from '../utils/coord'

const LABELS = {
  idle: 'GPS 轨迹',
  locating: '正在定位…',
  ready: '已定位到你附近',
  failed: 'GPS 轨迹'
}

/**
 * 轨迹页的「预览定位」：只取一次当前位置，用于把地图移到用户附近。
 * 持续轨迹记录由 useTracker 负责，两者互不干扰。
 */
export function useLocateProbe() {
  const state = ref('idle')
  const center = ref(null)

  const label = computed(() => LABELS[state.value] || LABELS.idle)

  function probe() {
    if (!('geolocation' in navigator)) {
      state.value = 'failed'
      return
    }
    state.value = 'locating'
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const gcj = wgs84ToGcj02(position.coords.longitude, position.coords.latitude)
        center.value = [gcj.lng, gcj.lat]
        state.value = 'ready'
      },
      () => {
        state.value = 'failed'
      },
      { enableHighAccuracy: true, maximumAge: 30000, timeout: 10000 }
    )
  }

  return { state, center, label, probe }
}
