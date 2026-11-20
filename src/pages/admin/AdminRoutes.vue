<script setup>
import { computed, onMounted, ref } from 'vue'
import { RefreshCw, Search, Trash2 } from 'lucide-vue-next'
import { adminDeleteRoute, adminRecordTypes, adminRoutes, errorMessage } from '../../utils/api'
import { sportTagClass } from '../../utils/sport'
import { confirmDialog, toast } from '../../utils/ui'
import RouteThumb from '../../components/RouteThumb.vue'
import AdminPager from './AdminPager.vue'

const routes = ref([])
const loading = ref(true)
const errorText = ref('')
const query = ref('')
const filters = ref({ type: '', from: '', to: '' })
const page = ref(1)
const pageSize = 20
const total = ref(0)
const typeOptions = ref([])

const pageDistance = computed(() =>
  routes.value.reduce((sum, row) => sum + (Number(row.distanceM) || 0), 0)
)

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await adminRoutes({
      query: query.value,
      type: filters.value.type,
      from: filters.value.from,
      to: filters.value.to,
      page: page.value,
      pageSize
    })
    routes.value = data.routes || []
    total.value = data.total || 0
    page.value = data.page || page.value
  } catch (error) {
    errorText.value = errorMessage(error, '加载轨迹失败')
  } finally {
    loading.value = false
  }
}

async function loadTypes() {
  try {
    typeOptions.value = await adminRecordTypes()
  } catch {
    /* 忽略类型加载失败 */
  }
}

function applyFilters() {
  page.value = 1
  load()
}

function resetFilters() {
  query.value = ''
  filters.value = { type: '', from: '', to: '' }
  page.value = 1
  load()
}

function changePage(next) {
  page.value = next
  load()
}

async function remove(row) {
  try {
    await confirmDialog({
      title: '删除轨迹',
      message: `确定删除「${row.nickname}」在 ${row.date} 的「${row.type}」轨迹吗？打卡记录会保留。`,
      confirmText: '删除',
      cancelText: '取消',
      danger: true
    })
  } catch {
    return
  }

  try {
    await adminDeleteRoute(row.id)
    toast('轨迹已删除')
    if (routes.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await load()
  } catch (error) {
    toast(errorMessage(error, '删除失败'))
  }
}

function formatDistance(meters) {
  const value = Number(meters) || 0
  return value >= 1000 ? `${(value / 1000).toFixed(2)} km` : `${value} m`
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

onMounted(() => {
  load()
  loadTypes()
})
</script>

<template>
  <div>
    <div class="admin-page-head">
      <div>
        <h1>运动轨迹</h1>
        <p>共 {{ total }} 条 GPS 轨迹，可按用户、类型和日期筛选</p>
      </div>
      <div class="admin-actions">
        <button type="button" class="admin-btn" :disabled="loading" @click="load">
          <RefreshCw :size="15" />
          刷新
        </button>
      </div>
    </div>

    <div class="admin-toolbar">
      <input
        v-model="query"
        class="admin-input admin-input--search"
        type="search"
        placeholder="搜索用户昵称"
        @keyup.enter="applyFilters"
      />
      <button type="button" class="admin-btn admin-btn--primary" @click="applyFilters">
        <Search :size="15" />
        筛选
      </button>

      <div class="admin-filters">
        <select v-model="filters.type" class="admin-select" @change="applyFilters">
          <option value="">全部类型</option>
          <option v-for="type in typeOptions" :key="type" :value="type">{{ type }}</option>
        </select>
        <input v-model="filters.from" class="admin-input" type="date" @change="applyFilters" />
        <span class="admin-filters__sep">至</span>
        <input v-model="filters.to" class="admin-input" type="date" @change="applyFilters" />
      </div>

      <button type="button" class="admin-btn" @click="resetFilters">重置</button>
    </div>

    <p v-if="errorText" class="admin-error">{{ errorText }}</p>

    <section class="admin-card">
      <div class="admin-card__head">
        <h2>轨迹列表</h2>
        <span class="admin-tag">当前页 {{ formatDistance(pageDistance) }}</span>
      </div>

      <div v-if="loading" class="admin-loading">加载中…</div>
      <div v-else-if="!routes.length" class="admin-empty">
        <strong>没有符合条件的轨迹</strong>
        <span>用户完成一次轨迹运动后会显示在这里</span>
      </div>
      <div v-else class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>轨迹</th>
              <th>用户</th>
              <th>类型</th>
              <th>日期</th>
              <th>距离</th>
              <th>时长</th>
              <th>轨迹点</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in routes" :key="row.id">
              <td>
                <span class="admin-route-thumb">
                  <RouteThumb v-if="row.preview?.length >= 2" :points="row.preview" />
                </span>
              </td>
              <td>
                <div class="admin-user-name">{{ row.nickname }}</div>
                <span class="admin-table-sub">{{ formatDateTime(row.createdAt) }}</span>
              </td>
              <td><span :class="sportTagClass(row.type)">{{ row.type }}</span></td>
              <td class="num">{{ row.date }}</td>
              <td class="num">{{ formatDistance(row.distanceM) }}</td>
              <td class="num">{{ Math.round(row.durationSec / 60) }} 分钟</td>
              <td class="num">{{ row.pointCount }}</td>
              <td>
                <button
                  type="button"
                  class="admin-btn admin-btn--sm admin-btn--danger"
                  @click="remove(row)"
                >
                  <Trash2 :size="13" />
                  删除
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <AdminPager
        v-if="!loading && total > 0"
        :page="page"
        :page-size="pageSize"
        :total="total"
        @change="changePage"
      />
    </section>
  </div>
</template>
