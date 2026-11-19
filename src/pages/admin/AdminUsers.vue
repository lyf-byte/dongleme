<script setup>
import { onMounted, ref } from 'vue'
import { RefreshCw, Search, Trash2 } from 'lucide-vue-next'
import { adminUsers, adminDeleteUser, errorMessage } from '../../utils/api'
import { getAdminUser } from '../../utils/auth'
import { confirmDialog, toast } from '../../utils/ui'

const users = ref([])
const loading = ref(true)
const errorText = ref('')
const query = ref('')
const currentUserId = ref('')

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await adminUsers(query.value)
    users.value = data.users || []
  } catch (error) {
    errorText.value = errorMessage(error, '加载用户失败')
  } finally {
    loading.value = false
  }
}

async function remove(user) {
  if (user.id === currentUserId.value) {
    toast('不能删除当前管理员账号')
    return
  }

  try {
    await confirmDialog({
      title: '删除用户',
      message: `确定删除「${user.nickname}」及其全部打卡记录吗？该操作无法恢复。`,
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
    await load()
  } catch (error) {
    toast(errorMessage(error, '删除失败'))
  }
}

function formatDate(value) {
  return value ? value.slice(0, 10) : '—'
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
        <p>共 {{ users.length }} 个用户</p>
      </div>
      <div class="admin-actions">
        <input
          v-model="query"
          class="admin-input"
          type="search"
          placeholder="搜索昵称或账号"
          @keyup.enter="load"
        />
        <button type="button" class="admin-btn admin-btn--primary" @click="load">
          <Search :size="15" />
          搜索
        </button>
        <button type="button" class="admin-btn" :disabled="loading" @click="load">
          <RefreshCw :size="15" />
          刷新
        </button>
      </div>
    </div>

    <p v-if="errorText" class="admin-error">{{ errorText }}</p>

    <section class="admin-card">
      <div class="admin-card__head">
        <h2>用户列表</h2>
        <span class="admin-tag">{{ users.length }} 人</span>
      </div>

      <div v-if="loading" class="admin-loading">加载中…</div>
      <div v-else-if="!users.length" class="admin-empty">没有匹配的用户</div>
      <div v-else class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>用户</th>
              <th>体重</th>
              <th>周目标</th>
              <th>连续</th>
              <th>总打卡</th>
              <th>注册时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id">
              <td>
                <div class="admin-user-cell">
                  <span class="admin-user-avatar">{{ user.nickname.slice(0, 1) }}</span>
                  <div>
                    <div class="admin-user-name">
                      {{ user.nickname }}
                      <span v-if="user.isAdmin" class="admin-tag">管理员</span>
                    </div>
                    <div class="admin-user-account">{{ user.account }}</div>
                  </div>
                </div>
              </td>
              <td class="num">{{ user.weight }} kg</td>
              <td class="num">{{ user.weeklyGoal }} 分钟</td>
              <td class="num">{{ user.streak }} 天</td>
              <td class="num">{{ user.recordCount }}</td>
              <td class="num">{{ formatDate(user.createdAt) }}</td>
              <td>
                <button
                  type="button"
                  class="admin-btn admin-btn--danger"
                  :disabled="user.id === currentUserId"
                  :title="user.id === currentUserId ? '当前登录账号' : '删除用户'"
                  @click="remove(user)"
                >
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
