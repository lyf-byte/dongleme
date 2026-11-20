<script setup>
import { computed, onMounted, ref } from 'vue'
import { Download, RefreshCw, Search, Trash2 } from 'lucide-vue-next'
import {
  adminDeleteRecord,
  adminRecordTypes,
  adminRecords,
  errorMessage
} from '../../utils/api'
import { sportTagClass, todayShanghai } from '../../utils/sport'
import { confirmDialog, toast } from '../../utils/ui'
import AdminPager from './AdminPager.vue'

const records = ref([])
const loading = ref(true)
const errorText = ref('')
const query = ref('')
const filters = ref({ type: '', from: '', to: '' })
const page = ref(1)
const pageSize = 20
const total = ref(0)
const typeOptions = ref([])
const selectedIds = ref([])

const pageMinutes = computed(() =>
  records.value.reduce((sum, row) => sum + (Number(row.duration) || 0), 0)
)
const pageCalories = computed(() =>
  records.value.reduce((sum, row) => sum + (Number(row.calories) || 0), 0)
)
const allChecked = computed(
  () => records.value.length > 0 && records.value.every((row) => selectedIds.value.includes(row.id))
)

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await adminRecords({
      query: query.value,
      type: filters.value.type,
      from: filters.value.from,
      to: filters.value.to,
      page: page.value,
      pageSize
    })
    records.value = data.records || []
    total.value = data.total || 0
    page.value = data.page || page.value
    selectedIds.value = []
  } catch (error) {
    errorText.value = errorMessage(error, '加载打卡记录失败')
  } finally {
    loading.value = false
  }
}

async function loadTypes() {
  try {
    typeOptions.value = await adminRecordTypes()
  } catch {
    /* 类型列表失败时仍可按日期筛选 */
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

function toggleAll(event) {
  selectedIds.value = event.target.checked ? records.value.map((row) => row.id) : []
}

async function remove(row) {
  try {
    await confirmDialog({
      title: '删除打卡记录',
      message: `确定删除「${row.nickname}」在 ${row.date} 的「${row.type}」记录吗？连续天数会重新计算。`,
      confirmText: '删除',
      cancelText: '取消',
      danger: true
    })
  } catch {
    return
  }

  try {
    await adminDeleteRecord(row.id)
    toast('已删除')
    if (records.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await load()
  } catch (error) {
    toast(errorMessage(error, '删除失败'))
  }
}

async function removeSelected() {
  if (!selectedIds.value.length) return
  const count = selectedIds.value.length
  try {
    await confirmDialog({
      title: `删除 ${count} 条记录`,
      message: '删除后无法恢复，相关用户的连续天数会重新计算。',
      confirmText: '批量删除',
      cancelText: '取消',
      danger: true
    })
  } catch {
    return
  }

  try {
    for (const id of selectedIds.value) {
      await adminDeleteRecord(id)
    }
    toast(`已删除 ${count} 条记录`)
    selectedIds.value = []
    page.value = 1
    await load()
  } catch (error) {
    toast(errorMessage(error, '批量删除失败'))
  }
}

function csvCell(value) {
  return `"${String(value ?? '').replace(/"/g, '""')}"`
}

function exportCsv() {
  if (!records.value.length) {
    toast('当前没有可导出的记录')
    return
  }
  const header = ['日期', '用户', '类型', '时长(分钟)', '卡路里(千卡)']
  const lines = records.value.map((row) =>
    [row.date, row.nickname, row.type, row.duration, row.calories].map(csvCell).join(',')
  )
  const csv = `\uFEFF${[header.map(csvCell).join(','), ...lines].join('\r\n')}`
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `dongleme-records-${todayShanghai()}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  toast('已导出当前页记录')
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
        <h1>打卡记录</h1>
        <p>共 {{ total }} 条，支持用户、类型、日期筛选与导出</p>
      </div>
      <div class="admin-actions">
        <button type="button" class="admin-btn" @click="exportCsv">
          <Download :size="15" />
          导出 CSV
        </button>
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

      <div class="admin-toolbar__spacer"></div>

      <button
        v-if="selectedIds.length"
        type="button"
        class="admin-btn admin-btn--danger"
        @click="removeSelected"
      >
        <Trash2 :size="14" />
        删除 {{ selectedIds.length }} 条
      </button>
    </div>

    <p v-if="errorText" class="admin-error">{{ errorText }}</p>

    <section class="admin-card">
      <div class="admin-card__head">
        <h2>记录列表</h2>
        <span class="admin-tag">当前页 {{ pageMinutes }} 分钟 · {{ pageCalories }} 千卡</span>
      </div>

      <div v-if="loading" class="admin-loading">加载中…</div>
      <div v-else-if="!records.length" class="admin-empty">
        <strong>没有符合条件的记录</strong>
        <span>调整筛选条件后再试</span>
      </div>
      <div v-else class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th style="width: 40px">
                <input type="checkbox" :checked="allChecked" @change="toggleAll" />
              </th>
              <th>日期</th>
              <th>用户</th>
              <th>类型</th>
              <th>时长</th>
              <th>卡路里</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in records" :key="row.id">
              <td>
                <input v-model="selectedIds" type="checkbox" :value="row.id" />
              </td>
              <td class="num">{{ row.date }}</td>
              <td>{{ row.nickname }}</td>
              <td><span :class="sportTagClass(row.type)">{{ row.type }}</span></td>
              <td class="num">{{ row.duration }} 分钟</td>
              <td class="num">{{ row.calories }} 千卡</td>
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
