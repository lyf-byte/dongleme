<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { BellRing, X } from 'lucide-vue-next'
import { getProfile } from '../utils/api'
import { getToken } from '../utils/auth'
import {
  dismissReminderToday,
  isDismissedToday,
  reminderDue
} from '../utils/reminder'

const route = useRoute()
const router = useRouter()

const visible = ref(false)
const text = ref('')

const HIDDEN = ['/auth', '/onboarding', '/checkin', '/track', '/timer', '/settings']
const hidden = computed(
  () =>
    route.path.startsWith('/admin')
    || HIDDEN.some((path) => route.path === path || route.path.startsWith(`${path}/`))
)

async function check() {
  if (hidden.value || !getToken() || !reminderDue() || isDismissedToday()) {
    visible.value = false
    return
  }
  try {
    const profile = await getProfile()
    if ((profile?.today?.minutes || 0) > 0) {
      visible.value = false
      return
    }
    const streak = profile?.streak || 0
    text.value = streak > 0
      ? `别断了 ${streak} 天连续打卡，动 10 分钟也算完成`
      : '今天还没有运动记录，动 10 分钟也算完成'
    visible.value = true
  } catch {
    visible.value = false
  }
}

function close() {
  dismissReminderToday()
  visible.value = false
}

function go() {
  dismissReminderToday()
  visible.value = false
  router.push('/checkin')
}

function onVisibility() {
  if (document.visibilityState === 'visible') check()
}

let timer = 0

onMounted(() => {
  check()
  document.addEventListener('visibilitychange', onVisibility)
  timer = window.setInterval(check, 60000)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibility)
  window.clearInterval(timer)
})

watch(() => route.path, check)
</script>

<template>
  <transition name="ui">
    <div v-if="visible" class="inapp-reminder">
      <span class="inapp-reminder__icon"><BellRing :size="17" /></span>
      <div class="inapp-reminder__text">
        <strong>该动一动啦</strong>
        <span>{{ text }}</span>
      </div>
      <button type="button" class="inapp-reminder__go" @click="go">去打卡</button>
      <button
        type="button"
        class="inapp-reminder__close"
        aria-label="关闭提醒"
        @click="close"
      >
        <X :size="15" />
      </button>
    </div>
  </transition>
</template>

<style scoped>
.inapp-reminder {
  position: fixed;
  left: 12px;
  right: 12px;
  bottom: calc(76px + env(safe-area-inset-bottom));
  z-index: 150;
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 456px;
  margin: 0 auto;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--surface);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.16);
}

.inapp-reminder__icon {
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: var(--orange-soft);
  color: var(--orange);
}

.inapp-reminder__text {
  flex: 1;
  min-width: 0;
}

.inapp-reminder__text strong {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.inapp-reminder__text span {
  display: block;
  margin-top: 1px;
  font-size: 11px;
  line-height: 15px;
  color: var(--muted);
}

.inapp-reminder__go {
  flex: 0 0 auto;
  height: 30px;
  padding: 0 12px;
  border: 0;
  border-radius: 999px;
  background: var(--blue);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.inapp-reminder__close {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
</style>
