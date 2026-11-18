<script setup>
import { onMounted, ref } from 'vue'
import { RefreshCw, Trash2 } from 'lucide-vue-next'
import { adminRecords, adminDeleteRecord, errorMessage } from '../../utils/api'
import { SPORTS } from '../../utils/sport'
import { confirmDialog, toast } from '../../utils/ui'

const records = ref([])
const loading = ref(true)
const errorText = ref('')
const filters = ref({ type: '', from: '', to: '' })

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await adminRecords(filters.value)
    records.value = data.records || []
  } catch (error) {
    errorText.value = errorMessage(error, '加载打卡记录失败')
  } finally {
    loading.value = false
  }
}

async function remove(row) {
  try {
    await confirmDialog({
      title: '删除打卡记录',
      message: `确定删除「${row.nickname}」在 ${row.date} 的「${row.type}」记录吗？`,
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
    await load()
  } catch (error) {
    toast(errorMessage(error, '删除失败'))
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="admin-page-head">
      <div>
        <h1>打卡记录</h1>
        <p>最多展示最近 200 条，可按类型和日期筛选</p>
      </div>
      <div class="admin-actions">
        <button type="button" class="admin-btn" :disabled="loading" @click="load">
          <RefreshCw :size="15" />
          刷新
        </button>
      </div>
    </div>

    <div class="admin-actions admin-filters">
      <select v-model="filters.type" class="admin-select" @change="load">
        <option value="">全部类型</option>
        <option v-for="sport in SPORTS" :key="sport.name" :value="sport.name">
          {{ sport.name }}
        </option>
      </select>
      <input v-model="filters.from" class="admin-input" type="date" @change="load" />
      <span style="color: var(--muted); font-size: 12px">至</span>
      <input v-model="filters.to" class="admin-input" type="date" @change="load" />
    </div>

    <p v-if="errorText" class="admin-error">{{ errorText }}</p>

    <section class="admin-card" style="margin-top: 14px">
      <div class="admin-card__head">
        <h2>记录列表</h2>
        <span class="admin-tag">{{ records.length }} 条</span>
      </div>

      <div v-if="loading" class="admin-loading">加载中…</div>
      <div v-else-if="!records.length" class="admin-empty">没有符合条件的记录</div>
      <div v-else class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
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
              <td class="num">{{ row.date }}</td>
              <td>{{ row.nickname }}</td>
              <td>{{ row.type }}</td>
              <td class="num">{{ row.duration }} 分钟</td>
              <td class="num">{{ row.calories }} 千卡</td>
              <td>
                <button type="button" class="admin-btn admin-btn--danger" @click="remove(row)">
                  <Trash2 :size="14" />
                  删除
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
