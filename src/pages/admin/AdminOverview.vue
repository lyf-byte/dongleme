<script setup>
import { onMounted, ref } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import { adminOverview, errorMessage } from '../../utils/api'

const data = ref(null)
const loading = ref(true)
const errorText = ref('')

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    data.value = await adminOverview()
  } catch (error) {
    errorText.value = errorMessage(error, '加载概览失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="admin-page-head">
      <div>
        <h1>数据概览</h1>
        <p>用户、打卡与本周活跃情况</p>
      </div>
      <div class="admin-actions">
        <button type="button" class="admin-btn" :disabled="loading" @click="load">
          <RefreshCw :size="15" />
          刷新
        </button>
      </div>
    </div>

    <p v-if="errorText" class="admin-error">{{ errorText }}</p>

    <div class="admin-metrics">
      <div class="admin-metric">
        <span>总用户</span>
        <strong>{{ data?.totalUsers ?? '—' }}</strong>
      </div>
      <div class="admin-metric">
        <span>总打卡</span>
        <strong>{{ data?.totalRecords ?? '—' }}</strong>
      </div>
      <div class="admin-metric">
        <span>今日活跃</span>
        <strong>{{ data?.activeToday ?? '—' }}</strong>
      </div>
      <div class="admin-metric">
        <span>本周时长 / 分钟</span>
        <strong>{{ data?.weekMinutes ?? '—' }}</strong>
      </div>
    </div>

    <section class="admin-card">
      <div class="admin-card__head">
        <h2>最近打卡</h2>
        <span class="admin-tag">最近 10 条</span>
      </div>

      <div v-if="loading" class="admin-loading">加载中…</div>
      <div v-else-if="!data?.recent?.length" class="admin-empty">还没有打卡记录</div>
      <div v-else class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>日期</th>
              <th>用户</th>
              <th>类型</th>
              <th>时长</th>
              <th>卡路里</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in data.recent" :key="row.id">
              <td class="num">{{ row.date }}</td>
              <td>{{ row.nickname }}</td>
              <td>{{ row.type }}</td>
              <td class="num">{{ row.duration }} 分钟</td>
              <td class="num">{{ row.calories }} 千卡</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
