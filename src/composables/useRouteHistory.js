import { ref } from 'vue'
import { getRoutes } from '../utils/api'

/** 轨迹历史列表：只管拉取与加载态，不关心页面怎么展示 */
export function useRouteHistory(limit = 8) {
  const recent = ref([])
  const loading = ref(true)

  async function load() {
    loading.value = true
    try {
      recent.value = await getRoutes(limit)
    } catch {
      recent.value = []
    } finally {
      loading.value = false
    }
  }

  return { recent, loading, load }
}
