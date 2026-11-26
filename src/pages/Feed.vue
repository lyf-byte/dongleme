<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Users } from 'lucide-vue-next'
import { getFeed, getFollowing, errorMessage } from '../utils/api'
import { avatarFor } from '../utils/avatar'
import { todayShanghai } from '../utils/sport'
import { toast } from '../utils/ui'
import emptyRank from '../assets/empty-rank.svg'

const router = useRouter()

const loading = ref(true)
const items = ref([])
const followingCount = ref(0)

function displayName(item) {
  return item.remark || item.nickname || '运动新人'
}

function formatDate(value) {
  if (!value) return ''
  const today = todayShanghai()
  if (value === today) return '今天'
  const date = new Date(`${value}T00:00:00Z`)
  const todayDate = new Date(`${today}T00:00:00Z`)
  const diff = Math.round((todayDate - date) / 86400000)
  if (diff === 1) return '昨天'
  return `${date.getUTCMonth() + 1}月${date.getUTCDate()}日`
}

async function load() {
  loading.value = true
  try {
    const [feedResult, followingResult] = await Promise.allSettled([
      getFeed(),
      getFollowing()
    ])
    items.value = feedResult.status === 'fulfilled' ? feedResult.value || [] : []
    followingCount.value = followingResult.status === 'fulfilled'
      ? (followingResult.value || []).length
      : 0
    if (feedResult.status === 'rejected') {
      toast(errorMessage(feedResult.reason, '加载好友动态失败'))
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page feed-page">
    <header class="nav-large">
      <div>
        <p class="eyebrow">已关注 {{ followingCount }} 人</p>
        <h1>好友动态</h1>
      </div>
      <button
        type="button"
        class="nav-large__action"
        aria-label="去排行榜关注好友"
        @click="router.push('/rank')"
      >
        <Users :size="20" />
      </button>
    </header>

    <template v-if="loading">
      <div class="card loading-card">
        <div class="loading-line"><span></span></div>
      </div>
    </template>

    <template v-else-if="items.length">
      <div class="card feed-list">
        <button
          v-for="item in items"
          :key="item.id"
          type="button"
          class="feed-row"
          @click="router.push(`/chat/${item.userId}`)"
        >
          <span class="feed-avatar">
            <img
              v-if="avatarFor(item.gender)"
              :src="avatarFor(item.gender)"
              alt=""
              class="feed-avatar__img"
            />
            <template v-else>{{ displayName(item).slice(0, 1) }}</template>
          </span>
          <div class="feed-body">
            <div class="feed-head">
              <strong>{{ displayName(item) }}</strong>
              <span>{{ formatDate(item.date) }}</span>
            </div>
            <div class="feed-meta">
              <span class="chip chip--blue">{{ item.type }}</span>
              <span>{{ item.duration }} 分钟</span>
              <span v-if="item.flagged === 1" class="feed-pending">审核中</span>
              <span>{{ item.calories }} 千卡</span>
            </div>
          </div>
        </button>
      </div>
    </template>

    <div v-else class="card empty-state">
      <img class="empty-state__art" :src="emptyRank" alt="" aria-hidden="true" />
      <h3>{{ followingCount ? '好友最近还没运动' : '还没有关注好友' }}</h3>
      <p>
        {{ followingCount
          ? '好友最近 14 天没有打卡，过两天再来看看。'
          : '去排行榜关注同学，这里就会显示他们的运动动态。' }}
      </p>
      <button type="button" class="btn-primary btn-sm empty-btn" @click="router.push('/rank')">
        {{ followingCount ? '看看排行榜' : '去排行榜关注' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.feed-page {
  padding-top: 0;
}

.loading-card {
  padding: 18px;
  margin-top: 4px;
}

.feed-list {
  overflow: hidden;
  padding: 0 14px;
  margin-top: 4px;
}

.feed-row {
  width: 100%;
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border: 0;
  border-top: 1px solid var(--line);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.feed-row:active {
  background: var(--surface-2);
}

.feed-row:first-child {
  border-top: 0;
}

.feed-avatar {
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 50%;
  overflow: hidden;
  background: var(--blue-soft);
  color: var(--blue);
  font-size: 14px;
  font-weight: 600;
}

.feed-avatar__img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.feed-body {
  flex: 1;
  min-width: 0;
}

.feed-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.feed-head strong {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed-head span {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--muted);
}

.feed-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-2);
}

.feed-pending {
  color: var(--muted);
}

.empty-btn {
  margin: 16px auto 0;
}
</style>
