<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ClipboardList, LayoutDashboard, LogOut, Trophy, Users } from 'lucide-vue-next'
import { getAdminSession, adminSignOut } from '../../utils/supabase'
import { confirmDialog } from '../../utils/ui'
import '../../styles/admin.css'

const route = useRoute()
const router = useRouter()
const account = ref('')

const tabs = [
  { label: '数据概览', path: '/admin', icon: LayoutDashboard },
  { label: '用户管理', path: '/admin/users', icon: Users },
  { label: '打卡记录', path: '/admin/records', icon: ClipboardList },
  { label: '排行榜', path: '/admin/rank', icon: Trophy }
]

function isActive(item) {
  if (item.path === '/admin') return route.path === '/admin'
  return route.path.startsWith(item.path)
}

async function logout() {
  try {
    await confirmDialog({
      title: '退出后台',
      message: '退出后需要重新登录管理员账号。',
      confirmText: '退出',
      cancelText: '取消'
    })
  } catch {
    return
  }
  await adminSignOut()
  router.replace('/admin/login')
}

onMounted(async () => {
  const { data } = await getAdminSession()
  const user = data.session?.user
  account.value = user?.email || user?.user_metadata?.phone || ''
})
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-side">
      <div class="admin-brand">
        <img class="admin-brand__logo" src="/logo.svg" alt="" aria-hidden="true" />
        <div>
          动了没
          <span>ADMIN CONSOLE</span>
        </div>
      </div>

      <nav class="admin-nav">
        <router-link
          v-for="item in tabs"
          :key="item.path"
          :to="item.path"
          :class="{ active: isActive(item) }"
        >
          <component :is="item.icon" :size="18" />
          {{ item.label }}
        </router-link>
      </nav>

      <div class="admin-side__footer">
        <div class="admin-account">{{ account }}</div>
        <button type="button" class="admin-logout" @click="logout">
          <LogOut :size="16" />
          退出登录
        </button>
      </div>
    </aside>

    <main class="admin-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.admin-account {
  padding: 0 12px 8px;
  color: var(--muted);
  font-size: 11px;
  line-height: 16px;
  word-break: break-all;
}
</style>
