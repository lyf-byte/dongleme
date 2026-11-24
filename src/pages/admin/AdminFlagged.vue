<script setup>
import { onMounted, ref } from 'vue'
import { RefreshCw, ShieldCheck, Trash2 } from 'lucide-vue-next'
import {
  adminApproveRecord,
  adminDeleteRecord,
  adminFlagged,
  errorMessage
} from '../../utils/api'
import { sportTagClass } from '../../utils/sport'
import { confirmDialog, toast } from '../../utils/ui'
import AdminPager from './AdminPager.vue'

const records = ref([])
const loading = ref(true)
const errorText = ref('')
const page = ref(1)
const pageSize = 20
const total = ref(0)

async function load(force = false) {
  loading.value = true
  errorText.value = ''
  try {
    const data = await adminFlagged({ page: page.value, pageSize }, force)
    records.value = data.records || []
    total.value = data.total || 0
    page.value = data.page || page.value
  } catch (error) {
    errorText.value = errorMessage(error, '加载异常记录失败')
  } finally {
    loading.value = false
  }
}

function changePage(next) {
  page.value = next
  load()
}

async function approve(row) {
  try {
    await confirmDialog({
      title: '通过打卡记录',
      message: `确认「${row.nickname}」在 ${row.date} 的「${row.type}」${row.duration} 分钟记录真实有效？通过后将按完整时长计入排行榜。`,
      confirmText: '通过',
      cancelText: '取消'
    })
  } catch {
    return
  }

  try {
    await adminApproveRecord(row.id)
    toast('已通过')
    if (records.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await load()
  } catch (error) {
    toast(errorMessage(error, '操作失败'))
  }
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

onMounted(() => {
  load()
})
</script>

<template>
  <div>
    <div class="admin-page-head">
      <div>
        <h1>异常打卡</h1>
        <p>手动打卡单日累计超过 180 分钟的记录会进入这里，审核前不计入排行榜</p>
      </div>
      <div class="admin-actions">
        <button type="button" class="admin-btn" :disabled="loading" @click="load(true)">
          <RefreshCw :size="15" />
          刷新
        </button>
      </div>
    </div>

    <p v-if="errorText" class="admin-error">{{ errorText }}</p>

    <section class="admin-card">
      <div class="admin-card__head">
        <h2>待审核记录</h2>
        <span class="admin-tag">共 {{ total }} 条</span>
      </div>

      <div v-if="loading" class="admin-loading">加载中…</div>
      <div v-else-if="!records.length" class="admin-empty">
        <strong>没有待审核的记录</strong>
        <span>手动打卡时长异常时会自动出现在这里</span>
      </div>
      <div v-else class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th class="col-lg">日期</th>
              <th>用户</th>
              <th>类型</th>
              <th>时长</th>
              <th class="col-lg">卡路里</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in records" :key="row.id">
              <td class="num col-lg">{{ row.date }}</td>
              <td>
                {{ row.nickname }}
                <span class="admin-table-sub admin-mobile-only">{{ row.date.slice(5) }}</span>
              </td>
              <td><span :class="sportTagClass(row.type)">{{ row.type }}</span></td>
              <td class="num">{{ row.duration }} 分钟</td>
              <td class="num col-lg">{{ row.calories }} 千卡</td>
              <td>
                <button
                  type="button"
                  class="admin-btn admin-btn--sm admin-btn--primary"
                  @click="approve(row)"
                >
                  <ShieldCheck :size="13" />
                  通过
                </button>
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
