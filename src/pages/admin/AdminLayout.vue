<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ClipboardList,
  LayoutDashboard,
  LogOut,
  Route as RouteIcon,
  Trophy,
  UserRound,
  Users
} from 'lucide-vue-next'
import { getAdminUser, adminSignOut } from '../../utils/auth'
import { confirmDialog } from '../../utils/ui'
import '../../styles/admin.css'

const route = useRoute()
const router = useRouter()
const account = ref('')

const groups = [
  {
    label: '运营',
    items: [
      { label: '数据概览', path: '/admin', icon: LayoutDashboard },
      { label: '用户管理', path: '/admin/users', icon: Users },
      { label: '打卡记录', path: '/admin/records', icon: ClipboardList },
      { label: '运动轨迹', path: '/admin/routes', icon: RouteIcon }
    ]
  },
  {
    label: '榜单',
    items: [{ label: '排行榜', path: '/admin/rank', icon: Trophy }]
  }
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

onMounted(() => {
  const user = getAdminUser()
  account.value = user?.username || user?.email || user?.phone || ''
})
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-side">
      <div class="admin-brand">
        <img class="admin-brand__logo" src="/logo.svg" alt="" aria-hidden="true" />
        <div>
          动了么
          <span>ADMIN CONSOLE</span>
        </div>
      </div>

      <nav class="admin-nav">
        <template v-for="group in groups" :key="group.label">
          <div class="admin-nav__label">{{ group.label }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            :class="{ active: isActive(item) }"
          >
            <component :is="item.icon" :size="17" />
            {{ item.label }}
          </router-link>
        </template>
      </nav>

      <div class="admin-side__footer">
        <div class="admin-account">
          <UserRound :size="14" />
          <span>{{ account }}</span>
        </div>
        <button type="button" class="admin-logout" @click="logout">
          <LogOut :size="15" />
          退出登录
        </button>
      </div>
    </aside>

    <main class="admin-main">
      <router-view />
    </main>
  </div>
</template>
