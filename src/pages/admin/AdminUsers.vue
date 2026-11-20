<script setup>
import { onMounted, ref } from 'vue'
import { Eye, RefreshCw, Search, Trash2, X } from 'lucide-vue-next'
import {
  adminDeleteUser,
  adminUserDetail,
  adminUsers,
  errorMessage
} from '../../utils/api'
import { getAdminUser } from '../../utils/auth'
import { avatarFor } from '../../utils/avatar'
import { sportTagClass } from '../../utils/sport'
import { confirmDialog, toast } from '../../utils/ui'
import AdminPager from './AdminPager.vue'

const filters = [
  { value: 'all', label: '全部用户' },
  { value: 'activeToday', label: '今日活跃' },
  { value: 'hasRecords', label: '有记录' },
  { value: 'noRecords', label: '未打卡' },
  { value: 'admin', label: '管理员' }
]

const sorts = [
  { value: 'createdAt', label: '按注册时间' },
  { value: 'lastCheck', label: '按最近打卡' },
  { value: 'streak', label: '按连续天数' },
  { value: 'records', label: '按打卡次数' },
  { value: 'weekMinutes', label: '按本周时长' }
]

const users = ref([])
const loading = ref(true)
const errorText = ref('')
const query = ref('')
const filter = ref('all')
const sort = ref('createdAt')
const currentUserId = ref('')
const page = ref(1)
const pageSize = 10
const total = ref(0)

const detail = ref(null)
const detailOpen = ref(false)
const detailLoading = ref(false)

async function load(force = false) {
  loading.value = true
  errorText.value = ''
  try {
    const data = await adminUsers({
      query: query.value,
      filter: filter.value,
      sort: sort.value,
      page: page.value,
      pageSize
    }, force)
    users.value = data.users || []
    total.value = data.total || 0
    page.value = data.page || page.value
  } catch (error) {
    errorText.value = errorMessage(error, '加载用户失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function setFilter(value) {
  if (filter.value === value) return
  filter.value = value
  page.value = 1
  load()
}

function setSort(value) {
  sort.value = value
  page.value = 1
  load()
}

function changePage(next) {
  page.value = next
  load()
}

async function openDetail(user) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await adminUserDetail(user.id)
  } catch (error) {
    toast(errorMessage(error, '加载用户详情失败'))
    detailOpen.value = false
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  detailOpen.value = false
  detail.value = null
}

async function remove(user) {
  if (user.id === currentUserId.value) {
    toast('不能删除当前管理员账号')
    return
  }
  try {
    await confirmDialog({
      title: '删除用户',
      message: `确定删除「${user.nickname}」及其全部打卡记录、运动轨迹吗？该操作无法恢复。`,
      confirmText: '删除',
      cancelText: '取消',
      danger: true
    })
  } catch {
    return
  }

  try {
    await adminDeleteUser(user.id)
    toast('已删除')
    closeDetail()
    if (users.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await load()
  } catch (error) {
    toast(errorMessage(error, '删除失败'))
  }
}

function formatDate(value) {
  return value ? String(value).slice(0, 10) : '—'
}

function formatDateTime(value) {
  return value ? String(value).slice(0, 16) : '—'
}

function formatDistance(meters) {
  const value = Number(meters) || 0
  return value >= 1000 ? `${(value / 1000).toFixed(2)} km` : `${value} m`
}

onMounted(async () => {
  currentUserId.value = getAdminUser()?.id || ''
  await load()
})
</script>

<template>
  <div>
    <div class="admin-page-head">
      <div>
        <h1>用户管理</h1>
        <p>共 {{ total }} 个用户，可按活跃度和数据量筛选</p>
      </div>
      <div class="admin-actions">
        <button type="button" class="admin-btn" :disabled="loading" @click="load(true)">
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
        placeholder="搜索昵称或账号"
        @keyup.enter="search"
      />
      <button type="button" class="admin-btn admin-btn--primary" @click="search">
        <Search :size="15" />
        搜索
      </button>

      <div class="admin-chips">
        <button
          v-for="item in filters"
          :key="item.value"
          type="button"
          class="admin-chip"
          :class="{ active: filter === item.value }"
          @click="setFilter(item.value)"
        >
          {{ item.label }}
        </button>
      </div>

      <div class="admin-toolbar__spacer"></div>
      <select class="admin-select" :value="sort" @change="setSort($event.target.value)">
        <option v-for="item in sorts" :key="item.value" :value="item.value">
          {{ item.label }}
        </option>
      </select>
    </div>

    <p v-if="errorText" class="admin-error">{{ errorText }}</p>

    <section class="admin-card">
      <div class="admin-card__head">
        <h2>用户列表</h2>
        <span class="admin-tag">{{ total }} 人</span>
      </div>

      <div v-if="loading" class="admin-loading">加载中…</div>
      <div v-else-if="!users.length" class="admin-empty">
        <strong>没有匹配的用户</strong>
        <span>换个关键词或筛选条件试试</span>
      </div>
      <div v-else class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>用户</th>
              <th class="col-md">连续打卡</th>
              <th>总打卡</th>
              <th class="col-md">本周时长</th>
              <th class="col-lg">轨迹</th>
              <th class="col-lg">最后打卡</th>
              <th class="col-lg">注册时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id">
              <td>
                <div class="admin-user-cell">
                  <span class="admin-avatar">
                    <img v-if="avatarFor(user.gender)" :src="avatarFor(user.gender)" alt="" />
                    <template v-else>{{ (user.nickname || '动').slice(0, 1) }}</template>
                  </span>
                  <div>
                    <div class="admin-user-name">
                      {{ user.nickname }}
                      <span v-if="user.isAdmin" class="admin-tag admin-tag--blue">管理员</span>
                    </div>
                    <div class="admin-user-account">{{ user.account }}</div>
                  </div>
                </div>
              </td>
              <td class="num col-md">{{ user.streak }} 天</td>
              <td class="num">{{ user.recordCount }} 次</td>
              <td class="num col-md">{{ user.weekMinutes }} 分钟</td>
              <td class="num col-lg">{{ user.routeCount }} 条</td>
              <td class="num col-lg">{{ formatDate(user.lastCheckDate) }}</td>
              <td class="num col-lg">{{ formatDate(user.createdAt) }}</td>
              <td>
                <div class="admin-table-actions">
                  <button type="button" class="admin-btn admin-btn--sm" @click="openDetail(user)">
                    <Eye :size="13" />
                    查看
                  </button>
                  <button
                    type="button"
                    class="admin-btn admin-btn--sm admin-btn--danger"
                    :disabled="user.id === currentUserId"
                    :title="user.id === currentUserId ? '当前登录账号' : '删除用户'"
                    @click="remove(user)"
                  >
                    <Trash2 :size="13" />
                  </button>
                </div>
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

    <transition name="ui">
      <div v-if="detailOpen" class="admin-drawer-overlay" @click.self="closeDetail">
        <aside class="admin-drawer">
          <header class="admin-drawer__head">
            <span class="admin-avatar">
              <img
                v-if="avatarFor(detail?.user?.gender)"
                :src="avatarFor(detail?.user?.gender)"
                alt=""
              />
              <template v-else>{{ (detail?.user?.nickname || '动').slice(0, 1) }}</template>
            </span>
            <div class="admin-drawer__title">
              <h2>{{ detail?.user?.nickname || '用户详情' }}</h2>
              <p>{{ detail?.user?.account || '加载中…' }}</p>
            </div>
            <button type="button" class="admin-btn admin-btn--sm" @click="closeDetail">
              <X :size="14" />
            </button>
          </header>

          <div class="admin-drawer__body">
            <div v-if="detailLoading" class="admin-loading">加载中…</div>

            <template v-else-if="detail">
              <div class="admin-detail-grid">
                <div class="admin-detail-item">
                  <span>总打卡</span>
                  <strong>{{ detail.user.recordCount }}</strong>
                </div>
                <div class="admin-detail-item">
                  <span>本周时长</span>
                  <strong>{{ detail.user.weekMinutes }}</strong>
                </div>
                <div class="admin-detail-item">
                  <span>连续天数</span>
                  <strong>{{ detail.user.streak }}</strong>
                </div>
                <div class="admin-detail-item">
                  <span>运动轨迹</span>
                  <strong>{{ detail.user.routeCount }}</strong>
                </div>
                <div class="admin-detail-item">
                  <span>体重</span>
                  <strong>{{ detail.user.weight }}<small> kg</small></strong>
                </div>
                <div class="admin-detail-item">
                  <span>最后打卡</span>
                  <strong style="font-size: 13px">{{ formatDate(detail.user.lastCheckDate) }}</strong>
                </div>
              </div>

              <section class="admin-card">
                <div class="admin-card__head">
                  <h2>最近打卡</h2>
                  <span class="admin-tag">{{ detail.records.length }} 条</span>
                </div>
                <div v-if="!detail.records.length" class="admin-empty">还没有打卡记录</div>
                <div v-else class="admin-table-wrap">
                  <table class="admin-table">
                    <thead>
                      <tr>
                        <th>日期</th>
                        <th>类型</th>
                        <th>时长</th>
                        <th>千卡</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="row in detail.records" :key="row.id">
                        <td class="num">{{ row.date }}</td>
                        <td><span :class="sportTagClass(row.type)">{{ row.type }}</span></td>
                        <td class="num">{{ row.duration }} 分钟</td>
                        <td class="num">{{ row.calories }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </section>

              <section class="admin-card">
                <div class="admin-card__head">
                  <h2>最近轨迹</h2>
                  <span class="admin-tag">{{ detail.routes.length }} 条</span>
                </div>
                <div v-if="!detail.routes.length" class="admin-empty">还没有运动轨迹</div>
                <div v-else class="admin-table-wrap">
                  <table class="admin-table">
                    <thead>
                      <tr>
                        <th>日期</th>
                        <th>类型</th>
                        <th>距离</th>
                        <th>时长</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="row in detail.routes" :key="row.id">
                        <td class="num">{{ formatDate(row.date) }}</td>
                        <td><span :class="sportTagClass(row.type)">{{ row.type }}</span></td>
                        <td class="num">{{ formatDistance(row.distanceM) }}</td>
                        <td class="num">{{ Math.round(row.durationSec / 60) }} 分钟</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </section>

              <button
                type="button"
                class="admin-btn admin-btn--danger"
                style="width: 100%"
                :disabled="detail.user.id === currentUserId"
                @click="remove(detail.user)"
              >
                <Trash2 :size="14" />
                删除该用户
              </button>
            </template>
          </div>
        </aside>
      </div>
    </transition>
  </div>
</template>
