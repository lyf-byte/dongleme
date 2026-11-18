<script setup>
import { onMounted, ref } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import { adminRank, errorMessage } from '../../utils/api'

const list = ref([])
const loading = ref(true)
const errorText = ref('')

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await adminRank()
    list.value = data.list || []
  } catch (error) {
    errorText.value = errorMessage(error, '加载排行榜失败')
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
        <h1>本周排行榜</h1>
        <p>按本周累计运动时长排序</p>
      </div>
      <div class="admin-actions">
        <button type="button" class="admin-btn" :disabled="loading" @click="load">
          <RefreshCw :size="15" />
          刷新
        </button>
      </div>
    </div>

    <p v-if="errorText" class="admin-error">{{ errorText }}</p>

    <section class="admin-card">
      <div class="admin-card__head">
        <h2>榜单</h2>
        <span class="admin-tag">{{ list.length }} 人</span>
      </div>

      <div v-if="loading" class="admin-loading">加载中…</div>
      <div v-else-if="!list.length" class="admin-empty">本周还没有人打卡</div>
      <div v-else class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>排名</th>
              <th>用户</th>
              <th>本周时长</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in list" :key="item.id">
              <td class="num">
                <span :class="['admin-tag', index === 0 ? 'admin-tag--gold' : '']">
                  #{{ index + 1 }}
                </span>
              </td>
              <td>{{ item.nickname }}</td>
              <td class="num">{{ item.weeklyMinutes }} 分钟</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
