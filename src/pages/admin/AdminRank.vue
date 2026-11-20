<script setup>
import { computed, onMounted, ref } from 'vue'
import { RefreshCw, Trophy } from 'lucide-vue-next'
import { adminRank, errorMessage } from '../../utils/api'

const scopes = [
  { value: 'week', label: '本周' },
  { value: 'total', label: '累计' }
]

const list = ref([])
const loading = ref(true)
const errorText = ref('')
const scope = ref('week')

const topList = computed(() => list.value.slice(0, 3))
const barList = computed(() => list.value.slice(0, 10))
const maxMinutes = computed(() => barList.value[0]?.weeklyMinutes || 1)

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await adminRank(scope.value)
    list.value = data.list || []
  } catch (error) {
    errorText.value = errorMessage(error, '加载排行榜失败')
  } finally {
    loading.value = false
  }
}

function setScope(value) {
  if (scope.value === value) return
  scope.value = value
  load()
}

function medalClass(index) {
  return ['gold', 'silver', 'bronze'][index] || ''
}

onMounted(load)
</script>

<template>
  <div>
    <div class="admin-page-head">
      <div>
        <h1>{{ scope === 'week' ? '本周排行榜' : '累计排行榜' }}</h1>
        <p>{{ scope === 'week' ? '按本周累计运动时长排序' : '按历史累计运动时长排序' }}</p>
      </div>
      <div class="admin-actions">
        <button type="button" class="admin-btn" :disabled="loading" @click="load">
          <RefreshCw :size="15" />
          刷新
        </button>
      </div>
    </div>

    <div class="admin-toolbar">
      <div class="admin-chips">
        <button
          v-for="item in scopes"
          :key="item.value"
          type="button"
          class="admin-chip"
          :class="{ active: scope === item.value }"
          @click="setScope(item.value)"
        >
          {{ item.label }}
        </button>
      </div>
      <div class="admin-toolbar__spacer"></div>
      <span class="admin-tag">{{ list.length }} 人上榜</span>
    </div>

    <p v-if="errorText" class="admin-error">{{ errorText }}</p>

    <div v-if="loading" class="admin-loading">加载中…</div>

    <template v-else-if="list.length">
      <div class="admin-podium">
        <div
          v-for="(item, index) in topList"
          :key="item.id"
          class="admin-podium__item"
          :class="`admin-podium__item--${medalClass(index)}`"
        >
          <span class="admin-podium__medal">{{ index + 1 }}</span>
          <span class="admin-podium__name">{{ item.nickname }}</span>
          <span class="admin-podium__minutes">
            <strong>{{ item.weeklyMinutes }}</strong> 分钟
          </span>
        </div>
      </div>

      <div class="admin-grid">
        <section class="admin-card">
          <div class="admin-card__head">
            <h2>Top 10 时长</h2>
            <span class="admin-tag">分钟</span>
          </div>
          <div class="admin-bars">
            <div v-for="item in barList" :key="item.id" class="admin-bar-row">
              <span class="admin-bar-label">{{ item.nickname }}</span>
              <span class="admin-bar-track">
                <i
                  class="admin-bar-fill"
                  :style="{ width: `${Math.max(4, (item.weeklyMinutes / maxMinutes) * 100)}%` }"
                ></i>
              </span>
              <span class="admin-bar-value">{{ item.weeklyMinutes }}</span>
            </div>
          </div>
        </section>

        <section class="admin-card">
          <div class="admin-card__head">
            <h2>完整榜单</h2>
            <span class="admin-tag">TOP 100</span>
          </div>
          <div class="admin-table-wrap">
            <table class="admin-table">
              <thead>
                <tr>
                  <th>排名</th>
                  <th>用户</th>
                  <th>{{ scope === 'week' ? '本周' : '累计' }}时长</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in list" :key="item.id">
                  <td class="num">
                    <span
                      :class="[
                        'admin-tag',
                        index === 0
                          ? 'admin-tag--gold'
                          : index === 1
                            ? 'admin-tag--blue'
                            : index === 2
                              ? 'admin-tag--orange'
                              : ''
                      ]"
                    >
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

    <div v-else class="admin-card admin-empty">
      <Trophy :size="26" />
      <strong>{{ scope === 'week' ? '本周还没有人打卡' : '还没有累计数据' }}</strong>
      <span>用户完成打卡后会出现在榜单中</span>
    </div>
  </div>
</template>
