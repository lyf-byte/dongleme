<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft,
  BellOff,
  ChevronRight,
  MoreHorizontal,
  Pin,
  Search,
  X
} from 'lucide-vue-next'
import {
  getConversations,
  getMutualFollows,
  getNotifications,
  getUnread,
  markNotificationsRead,
  updateConversationSetting,
  followUser,
  searchUsers,
  errorMessage
} from '../utils/api'
import { avatarFor } from '../utils/avatar'
import { onRealtime } from '../utils/realtime'
import { toast } from '../utils/ui'

const router = useRouter()
const loading = ref(true)
const conversations = ref([])
const mutuals = ref([])
const notices = ref([])
const unread = ref({ messages: 0, notifications: 0 })
const convMenu = ref(null)
const searchKeyword = ref('')
const searchResults = ref([])
const searching = ref(false)
let timer = 0
let offs = []
let searchTimer = 0
let searchSeq = 0

const showSearch = computed(() => searchKeyword.value.trim().length > 0)
const mutualIds = computed(() => new Set(mutuals.value.map((item) => item.id)))
const mutualChats = computed(() => {
  const chatting = new Set(conversations.value.map((item) => item.userId))
  return mutuals.value.filter((item) => !chatting.has(item.id))
})
const visibleNotices = computed(() =>
  notices.value.filter((item) => !mutualIds.value.has(item.actorId))
)

function displayName(item) {
  return item.remark || item.nickname || '运动新人'
}

function formatTime(value) {
  if (!value) return ''
  const text = String(value).replace('T', ' ')
  return `${text.slice(5, 10)} ${text.slice(11, 16)}`
}

function isRecentFollow(item) {
  if (!item.createdAt) return true
  const stamp = Date.parse(String(item.createdAt).replace(' ', 'T'))
  return !Number.isFinite(stamp) || Date.now() - stamp < 7 * 86400000
}

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const [conv, notice, count, mutual] = await Promise.all([
      getConversations(),
      getNotifications(),
      getUnread(),
      getMutualFollows().catch(() => [])
    ])
    conversations.value = conv || []
    mutuals.value = mutual || []
    notices.value = (notice || [])
      .filter((item) => item.type === 'follow' && isRecentFollow(item))
      .slice(0, 3)
    unread.value = count || { messages: 0, notifications: 0 }
  } catch (error) {
    if (!silent) toast(errorMessage(error, '加载消息失败'))
  } finally {
    loading.value = false
  }
}

async function handleFollow(notice) {
  try {
    await followUser(notice.actorId)
    toast('已回关')
    load(true)
  } catch (error) {
    toast(errorMessage(error, '回关失败'))
  }
}

function searchHint(item) {
  if (item.isFollowing && item.followsMe) return '已互关，可以聊天'
  if (item.isFollowing) return '已关注，等待对方回关'
  if (item.followsMe) return '关注了你，回关后可以聊天'
  return '还没有关注'
}

async function runSearch() {
  const keyword = searchKeyword.value.trim()
  if (!keyword) {
    searchResults.value = []
    searching.value = false
    return
  }
  const seq = ++searchSeq
  searching.value = true
  try {
    const rows = await searchUsers(keyword)
    if (seq !== searchSeq) return
    searchResults.value = rows || []
  } catch (error) {
    if (seq === searchSeq) {
      searchResults.value = []
      toast(errorMessage(error, '搜索失败'))
    }
  } finally {
    if (seq === searchSeq) searching.value = false
  }
}

watch(searchKeyword, () => {
  window.clearTimeout(searchTimer)
  searchSeq++
  const keyword = searchKeyword.value.trim()
  if (!keyword) {
    searchResults.value = []
    searching.value = false
    return
  }
  searchTimer = window.setTimeout(runSearch, 350)
})

function clearSearch() {
  searchKeyword.value = ''
}

async function followFromSearch(item) {
  try {
    await followUser(item.id)
    item.isFollowing = true
    toast(item.followsMe ? '已回关' : '已关注')
    if (item.followsMe) load(true)
  } catch (error) {
    toast(errorMessage(error, '关注失败'))
  }
}

async function togglePin(item) {
  try {
    await updateConversationSetting(item.userId, { pinned: !item.pinned })
    convMenu.value = null
    toast(item.pinned ? '已取消置顶' : '已置顶')
    load(true)
  } catch (error) {
    toast(errorMessage(error, '设置失败'))
  }
}

async function toggleMute(item) {
  try {
    await updateConversationSetting(item.userId, { muted: !item.muted })
    convMenu.value = null
    toast(item.muted ? '已取消免打扰' : '已开启免打扰')
    load(true)
  } catch (error) {
    toast(errorMessage(error, '设置失败'))
  }
}

onMounted(() => {
  load().then(() => markNotificationsRead().catch(() => {}))
  offs = [
    onRealtime('message', () => load(true)),
    onRealtime('notification', () => load(true)),
    onRealtime('recall', () => load(true))
  ]
  timer = window.setInterval(() => {
    if (document.visibilityState === 'visible') load(true)
  }, 60000)
})

onUnmounted(() => {
  window.clearInterval(timer)
  window.clearTimeout(searchTimer)
  offs.forEach((off) => off())
})
</script>

<template>
  <div class="page messages-page">
    <header class="topbar">
      <button type="button" class="topbar__btn" aria-label="返回" @click="router.back()">
        <ArrowLeft :size="20" />
      </button>
      <h1>消息</h1>
    </header>

    <div class="user-search">
      <Search :size="16" class="user-search__icon" />
      <input
        v-model="searchKeyword"
        type="search"
        maxlength="50"
        placeholder="搜索昵称、手机号或邮箱"
      />
      <button
        v-if="searchKeyword"
        type="button"
        class="user-search__clear"
        aria-label="清空搜索"
        @click="clearSearch"
      >
        <X :size="15" />
      </button>
    </div>

    <template v-if="!showSearch">
      <p class="messages-tip">相互关注后才能互发消息</p>

    <div v-if="visibleNotices.length" class="card notice-card">
      <div v-for="notice in visibleNotices" :key="notice.id" class="notice-row">
        <span class="msg-avatar">
          <img v-if="avatarFor(notice.gender)" :src="avatarFor(notice.gender)" alt="" class="msg-avatar__img" />
          <template v-else>{{ displayName(notice).slice(0, 1) }}</template>
        </span>
        <div class="notice-text">
          <strong>{{ displayName(notice) }}</strong>
          <small>关注了你</small>
        </div>
        <button
          type="button"
          class="btn-ghost btn-sm"
          @click="router.push(`/chat/${notice.actorId}`)"
        >
          发消息
        </button>
        <span v-if="mutualIds.has(notice.actorId)" class="notice-mutual">已互关</span>
        <button v-else type="button" class="btn-primary btn-sm" @click="handleFollow(notice)">
          回关
        </button>
      </div>
    </div>

    <div v-if="loading" class="card loading-card">
      <div class="loading-line"><span></span></div>
    </div>

    <template v-else>
      <template v-if="mutualChats.length">
        <div class="section-title">
          <h2>相互关注</h2>
          <span>{{ mutualChats.length }} 人</span>
        </div>
        <div class="card msg-list">
          <button
            v-for="item in mutualChats"
            :key="item.id"
            type="button"
            class="msg-row"
            @click="router.push(`/chat/${item.id}`)"
          >
            <span class="msg-avatar">
              <img v-if="avatarFor(item.gender)" :src="avatarFor(item.gender)" alt="" class="msg-avatar__img" />
              <template v-else>{{ displayName(item).slice(0, 1) }}</template>
            </span>
            <span class="msg-body">
              <span class="msg-head">
                <strong>{{ displayName(item) }}</strong>
                <small>可以聊天</small>
              </span>
              <span class="msg-preview">你们已相互关注，打个招呼吧</span>
            </span>
            <span class="chat-cta">发消息</span>
            <ChevronRight :size="16" class="chev" />
          </button>
        </div>
      </template>

      <template v-if="conversations.length">
        <div class="section-title">
          <h2>最近聊天</h2>
          <span>{{ conversations.length }} 个会话</span>
        </div>
        <div class="card msg-list">
          <div
            v-for="item in conversations"
            :key="item.userId"
            class="msg-row"
            role="button"
            tabindex="0"
            @click="router.push(`/chat/${item.userId}`)"
            @keydown.enter="router.push(`/chat/${item.userId}`)"
          >
            <span class="msg-avatar">
              <img v-if="avatarFor(item.gender)" :src="avatarFor(item.gender)" alt="" class="msg-avatar__img" />
              <template v-else>{{ displayName(item).slice(0, 1) }}</template>
            </span>
            <span class="msg-body">
              <span class="msg-head">
                <strong>{{ displayName(item) }}</strong>
                <small class="msg-flags">
                  <Pin v-if="item.pinned" :size="11" />
                  <BellOff v-if="item.muted" :size="11" />
                  {{ formatTime(item.lastAt) }}
                </small>
              </span>
              <span class="msg-preview">{{ item.lastContent }}</span>
            </span>
            <span v-if="item.unread" class="msg-badge">{{ item.unread > 99 ? '99+' : item.unread }}</span>
            <button
              type="button"
              class="conv-more"
              aria-label="会话设置"
              @click.stop="convMenu = item"
            >
              <MoreHorizontal :size="16" />
            </button>
            <ChevronRight :size="16" class="chev" />
          </div>
        </div>
      </template>

      <div v-if="!mutualChats.length && !conversations.length && !visibleNotices.length" class="card empty-state">
        <h3>还没有可以聊天的人</h3>
        <p>相互关注的人会显示在这里，也可以去排行榜关注同学。</p>
        <button type="button" class="btn-primary btn-sm empty-btn" @click="router.push('/rank')">
          去排行榜
        </button>
      </div>
    </template>
    </template>

    <template v-else>
      <div class="section-title">
        <h2>搜索结果</h2>
        <span>{{ searching ? '搜索中…' : `${searchResults.length} 人` }}</span>
      </div>
      <div v-if="searchResults.length" class="card msg-list">
        <div
          v-for="item in searchResults"
          :key="item.id"
          class="msg-row"
          role="button"
          tabindex="0"
          @click="router.push(`/chat/${item.id}`)"
          @keydown.enter="router.push(`/chat/${item.id}`)"
        >
          <span class="msg-avatar">
            <img v-if="avatarFor(item.gender)" :src="avatarFor(item.gender)" alt="" class="msg-avatar__img" />
            <template v-else>{{ displayName(item).slice(0, 1) }}</template>
          </span>
          <span class="msg-body">
            <span class="msg-head">
              <strong>{{ displayName(item) }}</strong>
            </span>
            <span class="msg-preview">{{ searchHint(item) }}</span>
          </span>
          <button
            v-if="item.isFollowing && item.followsMe"
            type="button"
            class="chat-cta"
            @click.stop="router.push(`/chat/${item.id}`)"
          >
            发消息
          </button>
          <span v-else-if="item.isFollowing" class="notice-mutual">已关注</span>
          <button
            v-else
            type="button"
            class="btn-primary btn-sm"
            @click.stop="followFromSearch(item)"
          >
            {{ item.followsMe ? '回关' : '关注' }}
          </button>
          <ChevronRight :size="16" class="chev" />
        </div>
      </div>
      <div v-else-if="!searching" class="card empty-state">
        <h3>没有找到相关用户</h3>
        <p>试试完整手机号、邮箱，或换一个昵称。</p>
      </div>
    </template>

    <transition name="ui">
      <div v-if="convMenu" class="conv-overlay" @click.self="convMenu = null">
        <div class="conv-sheet" role="dialog" aria-modal="true" aria-label="会话设置">
          <h3>{{ displayName(convMenu) }}</h3>
          <button type="button" class="conv-action" @click="togglePin(convMenu)">
            <Pin :size="16" />
            {{ convMenu.pinned ? '取消置顶' : '置顶会话' }}
          </button>
          <button type="button" class="conv-action" @click="toggleMute(convMenu)">
            <BellOff :size="16" />
            {{ convMenu.muted ? '取消免打扰' : '消息免打扰' }}
          </button>
          <button type="button" class="conv-action conv-action--cancel" @click="convMenu = null">
            取消
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.messages-page {
  padding-top: 0;
  padding-bottom: calc(28px + env(safe-area-inset-bottom));
}

.messages-tip {
  margin: 0 0 8px;
  font-size: 11px;
  color: var(--muted);
}

.user-search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  margin-bottom: 10px;
  padding: 0 12px;
  border: 1px solid var(--line-2);
  border-radius: 20px;
  background: var(--surface);
}

.user-search__icon {
  flex: 0 0 auto;
  color: var(--muted);
}

.user-search input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: 0;
  background: transparent;
  outline: none;
  font-size: 13px;
  color: var(--text);
}

.user-search input::-webkit-search-cancel-button {
  display: none;
}

.user-search__clear {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}

.loading-card {
  padding: 18px;
  margin-top: 4px;
}

.notice-card {
  margin-top: 4px;
  padding: 6px 14px;
}

.notice-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-top: 1px solid var(--line);
}

.notice-row:first-child {
  border-top: 0;
}

.notice-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.notice-text strong {
  font-size: 13px;
}

.notice-text small {
  font-size: 11px;
  color: var(--muted);
}

.notice-mutual {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--muted);
}

.chat-cta {
  flex: 0 0 auto;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--blue-soft);
  color: var(--blue);
  font-size: 11px;
  font-weight: 600;
}

.msg-flags {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.conv-more {
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--muted);
  display: grid;
  place-items: center;
  cursor: pointer;
}

.conv-more:active {
  background: var(--surface-2);
}

.conv-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 16px;
  background: rgba(17, 24, 39, 0.42);
}

.conv-sheet {
  width: 100%;
  max-width: 448px;
  padding: 18px 16px calc(18px + env(safe-area-inset-bottom));
  border-radius: 18px;
  background: var(--surface);
}

.conv-sheet h3 {
  margin: 0 0 10px;
  font-size: 15px;
  text-align: center;
}

.conv-action {
  width: 100%;
  height: 46px;
  border: 0;
  border-top: 1px solid var(--line);
  background: transparent;
  color: var(--text);
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
}

.conv-action--cancel {
  margin-top: 8px;
  border-top: 0;
  border-radius: 12px;
  background: var(--surface-2);
  color: var(--muted);
}

.msg-list {
  margin-top: 4px;
  padding: 0 14px;
}

.msg-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px 0;
  border: 0;
  border-top: 1px solid var(--line);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.msg-row:first-child {
  border-top: 0;
}

.msg-avatar {
  width: 40px;
  height: 40px;
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

.msg-avatar__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.msg-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.msg-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.msg-head strong {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg-head small {
  flex: 0 0 auto;
  font-size: 10px;
  color: var(--muted);
}

.msg-preview {
  font-size: 12px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg-badge {
  flex: 0 0 auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--red);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: grid;
  place-items: center;
}

.chev {
  color: var(--muted);
  flex: 0 0 auto;
}

.empty-btn {
  margin: 16px auto 0;
}
</style>
