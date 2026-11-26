<script setup>
import { computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { House, ChartBar, Trophy, Sparkles, User } from 'lucide-vue-next'
import UiHost from './components/UiHost.vue'
import ReminderBanner from './components/ReminderBanner.vue'
import { connectRealtime, disconnectRealtime } from './utils/realtime'

const route = useRoute()

function syncRealtime(path) {
  if (path.startsWith('/auth') || path.startsWith('/admin')) {
    disconnectRealtime()
  } else {
    connectRealtime()
  }
}

onMounted(() => syncRealtime(route.path))
onUnmounted(disconnectRealtime)
watch(() => route.path, syncRealtime)

const tabs = [
  { label: '首页', path: '/', icon: House },
  { label: '统计', path: '/stats', icon: ChartBar },
  { label: '排行', path: '/rank', icon: Trophy },
  { label: '小结', path: '/summary', icon: Sparkles },
  { label: '我的', path: '/mine', icon: User }
]

const hiddenPrefixes = [
  '/checkin',
  '/auth',
  '/onboarding',
  '/track',
  '/timer',
  '/settings',
  '/messages',
  '/chat'
]
const isAdmin = computed(() => route.path.startsWith('/admin'))
const showTabbar = computed(() => (
  !hiddenPrefixes.some((path) => route.path === path || route.path.startsWith(`${path}/`))
  && !isAdmin.value
))
</script>

<template>
  <div class="app-shell">
    <router-view v-slot="{ Component }">
      <transition name="page" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>

    <nav v-if="showTabbar" class="tabbar">
      <router-link
        v-for="item in tabs"
        :key="item.path"
        :to="item.path"
        class="tabbar__item"
        :class="{ active: route.path === item.path }"
        replace
      >
        <component
          :is="item.icon"
          :size="22"
          :stroke-width="route.path === item.path ? 2.1 : 1.8"
        />
        <span>{{ item.label }}</span>
      </router-link>
    </nav>

    <ReminderBanner />

    <UiHost />
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
}
</style>
