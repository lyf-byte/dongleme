<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Ban,
  Camera,
  ChevronLeft,
  Flag,
  Images,
  MapPin,
  MoreHorizontal,
  Pencil,
  Plus,
  Send,
  Share2,
  X
} from 'lucide-vue-next'
import {
  getChat,
  getProfile,
  sendMessage,
  uploadImage,
  markChatRead,
  setContactRemark,
  setBlocked,
  reportUser,
  recallMessage,
  followUser,
  errorMessage
} from '../utils/api'
import { onRealtime } from '../utils/realtime'
import { toast } from '../utils/ui'

const route = useRoute()
const router = useRouter()
const peerId = route.params.userId
const peer = ref(null)
const messages = ref([])
const mutual = ref(false)
const draft = ref('')
const sending = ref(false)
const listRef = ref(null)
const remarkOpen = ref(false)
const remarkDraft = ref('')
const savingRemark = ref(false)
const moreOpen = ref(false)
const blocked = ref(false)
const blockedByPeer = ref(false)
const fileInput = ref(null)
const cameraInput = ref(null)
const panelOpen = ref(false)
const uploading = ref(false)
const sportOpen = ref(false)
const sportLoading = ref(false)
const records = ref([])
const previewUrl = ref('')
let timer = 0
let offs = []
let lastId = 0

const displayName = computed(
  () => peer.value?.remark || peer.value?.nickname || '聊天'
)

function formatTime(value) {
  if (!value) return ''
  const text = String(value).replace('T', ' ')
  return text.slice(5, 16)
}

function recordCard(message) {
  try {
    const data = JSON.parse(message.payloadJson || '{}')
    return {
      type: data.type || '运动记录',
      date: data.date || '',
      duration: data.duration || 0,
      calories: data.calories || 0
    }
  } catch {
    return { type: '运动记录', date: '', duration: 0, calories: 0 }
  }
}

function locationCard(message) {
  try {
    const data = JSON.parse(message.payloadJson || '{}')
    return {
      lat: Number(data.lat),
      lng: Number(data.lng),
      name: data.name || '共享位置'
    }
  } catch {
    return null
  }
}

function openLocation(location) {
  if (!location || !Number.isFinite(location.lat) || !Number.isFinite(location.lng)) {
    return
  }
  window.open(
    `https://uri.amap.com/marker?position=${location.lng},${location.lat}&name=${encodeURIComponent(location.name)}`,
    '_blank'
  )
}

const timeline = computed(() => {
  const rows = []
  let lastAt = 0
  for (const message of messages.value) {
    const stamp = Date.parse(String(message.createdAt).replace(' ', 'T')) || 0
    if (!lastAt || stamp - lastAt > 5 * 60 * 1000) {
      rows.push({ key: `time-${message.id}`, type: 'time', text: formatTime(message.createdAt) })
    }
    rows.push({
      key: message.id,
      type: 'message',
      message,
      record: message.type === 'record' ? recordCard(message) : null,
      location: message.type === 'location' ? locationCard(message) : null
    })
    lastAt = stamp
  }
  return rows
})

async function scrollBottom() {
  await nextTick()
  const el = listRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function load(silent = false) {
  try {
    const data = await getChat(peerId)
    peer.value = data
    messages.value = data.messages || []
    mutual.value = Boolean(data.mutual)
    blocked.value = Boolean(data.blocked)
    blockedByPeer.value = Boolean(data.blockedByPeer)
    if (!silent) markChatRead(peerId).catch(() => {})
    const newest = messages.value.length
      ? messages.value[messages.value.length - 1].id
      : 0
    if (newest !== lastId || !silent) {
      lastId = newest
      await scrollBottom()
    }
  } catch (error) {
    if (!silent) toast(errorMessage(error, '加载聊天失败'))
  }
}

async function send() {
  const text = draft.value.trim()
  if (!text || sending.value || !mutual.value) return
  sending.value = true
  try {
    const message = await sendMessage(peerId, text)
    draft.value = ''
    messages.value.push(message)
    markChatRead(peerId).catch(() => {})
    await scrollBottom()
  } catch (error) {
    toast(errorMessage(error, '发送失败'))
  } finally {
    sending.value = false
  }
}

function openRemark() {
  remarkDraft.value = peer.value?.remark || ''
  remarkOpen.value = true
}

async function saveRemark() {
  if (savingRemark.value) return
  savingRemark.value = true
  try {
    const result = await setContactRemark(peerId, remarkDraft.value)
    if (peer.value) peer.value.remark = result?.remark || ''
    remarkOpen.value = false
    toast(result?.remark ? '备注已更新' : '已恢复昵称')
  } catch (error) {
    toast(errorMessage(error, '保存备注失败'))
  } finally {
    savingRemark.value = false
  }
}

async function clearRemark() {
  remarkDraft.value = ''
  await saveRemark()
}

function canRecall(message) {
  return Boolean(message.recallable) && !message.recalled
}

async function recall(message) {
  if (!window.confirm('撤回这条消息？')) return
  try {
    await recallMessage(message.id)
    toast('已撤回')
    await load(true)
  } catch (error) {
    toast(errorMessage(error, '撤回失败'))
  }
}

async function toggleBlock() {
  moreOpen.value = false
  const next = !blocked.value
  if (next && !window.confirm('拉黑后双方将无法继续聊天，确定拉黑？')) return
  try {
    await setBlocked(peerId, next)
    toast(next ? '已拉黑对方' : '已解除拉黑')
    await load(true)
  } catch (error) {
    toast(errorMessage(error, '操作失败'))
  }
}

async function submitReport() {
  moreOpen.value = false
  const reason = window.prompt('请输入举报原因（最多 200 字）')
  if (!reason || !reason.trim()) return
  try {
    await reportUser(peerId, reason.trim())
    toast('举报已提交')
  } catch (error) {
    toast(errorMessage(error, '举报失败'))
  }
}

function pushOutgoing(message) {
  messages.value.push(message)
  scrollBottom()
}

async function togglePanel() {
  panelOpen.value = !panelOpen.value
  if (panelOpen.value) {
    // 面板展开会压缩消息区高度，重新贴底避免最新消息被挡住
    await nextTick()
    await scrollBottom()
  }
}

function pickImage() {
  panelOpen.value = false
  fileInput.value?.click()
}

function pickCamera() {
  panelOpen.value = false
  cameraInput.value?.click()
}

function shareLocation() {
  panelOpen.value = false
  if (!navigator.geolocation) {
    toast('当前设备不支持定位')
    return
  }
  navigator.geolocation.getCurrentPosition(
    async (position) => {
      try {
        const payloadJson = JSON.stringify({
          lat: position.coords.latitude,
          lng: position.coords.longitude,
          name: '我的位置'
        })
        const message = await sendMessage(peerId, '', {
          type: 'location',
          payloadJson
        })
        pushOutgoing(message)
      } catch (error) {
        toast(errorMessage(error, '位置发送失败'))
      }
    },
    () => toast('定位失败，请检查定位权限'),
    { enableHighAccuracy: true, timeout: 10000 }
  )
}

async function compressImage(file) {
  if (file.type === 'image/gif') return file
  try {
    const bitmap = await createImageBitmap(file)
    const max = 1600
    const scale = Math.min(1, max / Math.max(bitmap.width, bitmap.height))
    if (scale === 1 && file.size <= 900 * 1024) {
      bitmap.close?.()
      return file
    }
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(bitmap.width * scale)
    canvas.height = Math.round(bitmap.height * scale)
    const context = canvas.getContext('2d')
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    bitmap.close?.()
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.82))
    if (!blob) return file
    return new File([blob], file.name.replace(/\.[^.]+$/, '') + '.jpg', {
      type: 'image/jpeg'
    })
  } catch {
    return file
  }
}

async function onImagePicked(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    toast('请选择图片文件')
    return
  }
  const prepared = await compressImage(file)
  if (prepared.size > 5 * 1024 * 1024) {
    toast('图片不能超过 5MB')
    return
  }
  uploading.value = true
  try {
    const result = await uploadImage(prepared)
    const message = await sendMessage(peerId, '', {
      type: 'image',
      mediaUrl: result.url
    })
    pushOutgoing(message)
  } catch (error) {
    toast(errorMessage(error, '图片发送失败'))
  } finally {
    uploading.value = false
  }
}

async function openSportPicker() {
  panelOpen.value = false
  sportOpen.value = true
  sportLoading.value = true
  try {
    const profile = await getProfile()
    records.value = (profile?.recent || []).slice(0, 8)
  } catch (error) {
    records.value = []
    toast(errorMessage(error, '加载运动记录失败'))
  } finally {
    sportLoading.value = false
  }
}

async function shareRecord(record) {
  try {
    const payloadJson = JSON.stringify({
      type: record.type,
      date: record.date,
      duration: record.duration,
      calories: record.calories
    })
    const message = await sendMessage(peerId, '', { type: 'record', payloadJson })
    sportOpen.value = false
    pushOutgoing(message)
  } catch (error) {
    toast(errorMessage(error, '分享失败'))
  }
}

async function follow() {
  try {
    await followUser(peerId)
    toast('已关注，等对方回关后即可聊天')
    await load(true)
  } catch (error) {
    toast(errorMessage(error, '关注失败'))
  }
}

onMounted(() => {
  load()
  offs = [
    onRealtime('message', (event) => {
      if (event.peerId === peerId) load(true)
    }),
    onRealtime('recall', (event) => {
      if (event.peerId === peerId) load(true)
    })
  ]
  timer = window.setInterval(() => {
    if (document.visibilityState === 'visible') load(true)
  }, 30000)
})

onUnmounted(() => {
  window.clearInterval(timer)
  offs.forEach((off) => off())
})
</script>

<template>
  <div class="page chat-page">
    <header class="chat-top">
      <button type="button" class="chat-back" aria-label="返回" @click="router.back()">
        <ChevronLeft :size="20" />
      </button>
      <div class="chat-peer">
        <strong>{{ displayName }}</strong>
        <small>{{ mutual ? '相互关注 · 可以聊天' : '相互关注后才能聊天' }}</small>
      </div>
      <button type="button" class="chat-remark" aria-label="设置备注" @click="openRemark">
        <Pencil :size="16" />
      </button>
      <button type="button" class="chat-remark" aria-label="更多操作" @click="moreOpen = true">
        <MoreHorizontal :size="16" />
      </button>
    </header>

    <div ref="listRef" class="chat-messages">
      <template v-for="row in timeline" :key="row.key">
        <p v-if="row.type === 'time'" class="chat-time">{{ row.text }}</p>
        <div v-else class="bubble-row" :class="{ 'is-mine': row.message.mine }">
          <div
            class="bubble"
            :class="{
              'is-recalled': row.message.recalled,
              'bubble--image': !row.message.recalled && row.message.type === 'image',
              'bubble--record': !row.message.recalled && row.message.type === 'record',
              'bubble--location': !row.message.recalled && row.message.type === 'location'
            }"
          >
            <template v-if="row.message.recalled">消息已撤回</template>
            <template v-else-if="row.message.type === 'image'">
              <img
                :src="row.message.mediaUrl"
                alt="图片消息"
                class="bubble-image"
                loading="lazy"
                decoding="async"
                @click="previewUrl = row.message.mediaUrl"
              />
            </template>
            <template v-else-if="row.message.type === 'record' && row.record">
              <div class="record-card">
                <strong>{{ row.record.type }}</strong>
                <small>{{ row.record.date }}</small>
                <div class="record-stats">
                  <span>{{ row.record.duration }} 分钟</span>
                  <span>{{ row.record.calories }} 千卡</span>
                </div>
              </div>
            </template>
            <template v-else-if="row.message.type === 'location'">
              <div class="location-card" @click="openLocation(row.location)">
                <span class="location-icon"><MapPin :size="18" /></span>
                <span class="location-text">
                  <strong>{{ row.location?.name || '共享位置' }}</strong>
                  <small v-if="row.location">
                    {{ row.location.lat.toFixed(5) }}, {{ row.location.lng.toFixed(5) }}
                  </small>
                </span>
              </div>
            </template>
            <template v-else>{{ row.message.content }}</template>
          </div>
          <button
            v-if="canRecall(row.message)"
            type="button"
            class="recall-link"
            @click="recall(row.message)"
          >
            撤回
          </button>
        </div>
      </template>
      <p v-if="!messages.length" class="chat-empty">还没有消息，打个招呼吧</p>
    </div>

    <transition name="ui">
      <div v-if="remarkOpen" class="remark-overlay" @click.self="remarkOpen = false">
        <div class="remark-sheet" role="dialog" aria-modal="true" aria-label="设置备注">
          <h3>设置备注</h3>
          <p>仅自己可见，用来区分同名好友</p>
          <input
            v-model="remarkDraft"
            type="text"
            maxlength="20"
            placeholder="留空则恢复昵称"
            @keyup.enter="saveRemark"
          />
          <div class="remark-actions">
            <button type="button" class="btn-ghost btn-sm" @click="clearRemark">清除</button>
            <button
              type="button"
              class="btn-primary btn-sm"
              :disabled="savingRemark"
              @click="saveRemark"
            >
              {{ savingRemark ? '保存中…' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </transition>

    <div class="chat-input" :class="{ 'chat-input--panel': panelOpen }">
      <template v-if="blocked">
        <p class="chat-locked">已拉黑对方，解除后可继续聊天</p>
        <button type="button" class="btn-ghost btn-sm" @click="toggleBlock">解除拉黑</button>
      </template>
      <template v-else-if="blockedByPeer">
        <p class="chat-locked">对方已将你拉黑，暂时无法发消息</p>
      </template>
      <template v-else-if="mutual">
        <input
          ref="fileInput"
          type="file"
          accept="image/*"
          class="chat-file"
          @change="onImagePicked"
        />
        <input
          ref="cameraInput"
          type="file"
          accept="image/*"
          capture="environment"
          class="chat-file"
          @change="onImagePicked"
        />
        <input
          v-model="draft"
          type="text"
          maxlength="500"
          placeholder="发条消息…"
          @focus="panelOpen = false"
          @keyup.enter="send"
        />
        <button
          v-if="!draft.trim()"
          type="button"
          class="chat-tool"
          aria-label="更多功能"
          @click="togglePanel"
        >
          <Plus :size="22" />
        </button>
        <button
          v-else
          type="button"
          class="btn-primary btn-sm chat-send"
          :disabled="sending"
          aria-label="发送"
          @click="send"
        >
          <Send :size="16" />
        </button>
      </template>
      <template v-else>
        <p class="chat-locked">相互关注后才能聊天</p>
        <button type="button" class="btn-primary btn-sm" @click="follow">关注 TA</button>
      </template>
    </div>

    <div v-if="mutual && panelOpen" class="chat-panel">
      <button type="button" class="panel-item" @click="pickImage">
        <span class="panel-icon"><Images :size="22" /></span>
        <span>相册</span>
      </button>
      <button type="button" class="panel-item" @click="pickCamera">
        <span class="panel-icon"><Camera :size="22" /></span>
        <span>拍摄</span>
      </button>
      <button type="button" class="panel-item" @click="shareLocation">
        <span class="panel-icon"><MapPin :size="22" /></span>
        <span>位置</span>
      </button>
      <button type="button" class="panel-item" @click="openSportPicker">
        <span class="panel-icon"><Share2 :size="21" /></span>
        <span>分享海报</span>
      </button>
    </div>

    <transition name="ui">
      <div v-if="sportOpen" class="remark-overlay" @click.self="sportOpen = false">
        <div class="remark-sheet sport-sheet" role="dialog" aria-modal="true" aria-label="分享海报">
          <h3>分享海报</h3>
          <p>选择一条运动记录生成分享卡片</p>
          <p v-if="sportLoading" class="sport-empty">加载中…</p>
          <p v-else-if="!records.length" class="sport-empty">还没有可分享的运动记录</p>
          <div v-else class="sport-list">
            <button
              v-for="(record, index) in records"
              :key="`${record.date}-${record.type}-${index}`"
              type="button"
              class="sport-row"
              @click="shareRecord(record)"
            >
              <span class="sport-main">
                <strong>{{ record.type }}</strong>
                <small>{{ record.date }} · {{ record.duration }} 分钟</small>
              </span>
              <span class="sport-cal">{{ record.calories }} 千卡</span>
            </button>
          </div>
        </div>
      </div>
    </transition>

    <div v-if="previewUrl" class="image-preview" @click.self="previewUrl = ''">
      <button type="button" class="image-preview__close" aria-label="关闭" @click="previewUrl = ''">
        <X :size="20" />
      </button>
      <img :src="previewUrl" alt="图片预览" />
    </div>

    <transition name="ui">
      <div v-if="moreOpen" class="more-overlay" @click.self="moreOpen = false">
        <div class="more-sheet" role="dialog" aria-modal="true" aria-label="更多操作">
          <button type="button" class="more-action" @click="toggleBlock">
            <Ban :size="16" />
            {{ blocked ? '解除拉黑' : '拉黑对方' }}
          </button>
          <button type="button" class="more-action" @click="submitReport">
            <Flag :size="16" />
            举报
          </button>
          <button type="button" class="more-action more-action--cancel" @click="moreOpen = false">
            取消
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding: 0 14px;
  background: var(--bg);
}

.chat-top {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0 10px;
}

.chat-back {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: 1px solid var(--line-2);
  background: var(--surface);
  color: var(--text);
  display: grid;
  place-items: center;
  cursor: pointer;
}

.chat-peer {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.chat-peer strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-remark {
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1px solid var(--line-2);
  background: var(--surface);
  color: var(--text-2);
  display: grid;
  place-items: center;
  cursor: pointer;
}

.chat-peer strong {
  font-size: 16px;
}

.chat-peer small {
  font-size: 11px;
  color: var(--muted);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 8px 2px 12px;
}

.bubble-row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: 10px;
}

.bubble-row.is-mine {
  align-items: flex-end;
}

.bubble {
  max-width: 78%;
  padding: 9px 13px;
  border-radius: 14px 14px 14px 4px;
  background: var(--surface);
  border: 1px solid var(--line);
  font-size: 14px;
  line-height: 1.55;
  word-break: break-word;
}

.bubble-row.is-mine .bubble {
  background: var(--blue);
  border-color: transparent;
  color: #fff;
  border-radius: 14px 14px 4px 14px;
}

.bubble.is-recalled {
  background: var(--surface-2);
  border-color: var(--line);
  color: var(--muted);
  font-size: 12px;
}

.bubble-row.is-mine .bubble.is-recalled {
  background: var(--surface-2);
  color: var(--muted);
}

.recall-link {
  margin-top: 3px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 10px;
  cursor: pointer;
}

.chat-time {
  margin: 10px 0;
  text-align: center;
  font-size: 10px;
  color: var(--muted);
}

.chat-empty {
  text-align: center;
  color: var(--muted);
  font-size: 12px;
  margin-top: 40px;
}

.chat-input {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0 calc(14px + env(safe-area-inset-bottom));
  border-top: 1px solid var(--line);
}

.chat-input--panel {
  padding-bottom: 8px;
}

.chat-input input {
  flex: 1;
  height: 40px;
  border-radius: 20px;
  border: 1px solid var(--line-2);
  background: var(--surface);
  padding: 0 14px;
  font-size: 14px;
  outline: none;
}

.chat-send {
  flex: 0 0 auto;
  width: 44px;
  height: 40px;
  padding: 0;
  display: grid;
  place-items: center;
}

.chat-tool {
  flex: 0 0 auto;
  width: 34px;
  height: 40px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-2);
  display: grid;
  place-items: center;
  cursor: pointer;
}

.chat-tool:disabled {
  opacity: 0.4;
}

.chat-file {
  display: none;
}

.chat-panel {
  flex: 0 0 auto;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  padding: 14px 4px calc(14px + env(safe-area-inset-bottom));
  border-radius: 16px 16px 0 0;
  background: var(--surface-2);
}

.panel-item {
  border: 0;
  background: transparent;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 0;
  color: var(--text-2);
  font-size: 11px;
  cursor: pointer;
}

.panel-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: var(--surface);
  display: grid;
  place-items: center;
  color: var(--text);
  box-shadow: 0 2px 8px rgba(17, 24, 39, 0.06);
}

.bubble--image {
  padding: 3px;
  background: var(--surface);
  border: 1px solid var(--line);
}

.bubble-row.is-mine .bubble--image {
  background: var(--surface);
  border-color: var(--line);
}

.bubble-image {
  display: block;
  width: 210px;
  max-width: 60vw;
  border-radius: 11px;
  cursor: pointer;
}

.bubble--record {
  min-width: 190px;
  padding: 12px 14px;
  background: var(--surface);
  border: 1px solid var(--line);
}

.bubble-row.is-mine .bubble--record {
  background: var(--blue-soft);
  border-color: transparent;
  color: var(--text);
}

.bubble--location {
  min-width: 190px;
  padding: 10px 12px;
  background: var(--surface);
  border: 1px solid var(--line);
}

.bubble-row.is-mine .bubble--location {
  background: var(--blue-soft);
  border-color: transparent;
  color: var(--text);
}

.location-card {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.location-icon {
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--blue-soft);
  color: var(--blue);
  display: grid;
  place-items: center;
}

.location-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.location-text strong {
  font-size: 13px;
}

.location-text small {
  font-size: 10px;
  color: var(--muted);
}

.record-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.record-card strong {
  font-size: 14px;
}

.record-card small {
  font-size: 10px;
  color: var(--muted);
}

.record-stats {
  display: flex;
  gap: 12px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--blue);
  font-weight: 600;
}

.sport-sheet {
  max-height: 70vh;
  overflow-y: auto;
}

.sport-empty {
  margin: 16px 0;
  text-align: center;
  font-size: 12px;
  color: var(--muted);
}

.sport-list {
  display: flex;
  flex-direction: column;
}

.sport-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 0;
  border: 0;
  border-top: 1px solid var(--line);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.sport-row:first-child {
  border-top: 0;
}

.sport-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sport-main strong {
  font-size: 14px;
}

.sport-main small {
  font-size: 10px;
  color: var(--muted);
}

.sport-cal {
  flex: 0 0 auto;
  font-size: 12px;
  color: var(--blue);
  font-weight: 600;
}

.image-preview {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(17, 24, 39, 0.86);
}

.image-preview img {
  max-width: 100%;
  max-height: 100%;
  border-radius: 12px;
  object-fit: contain;
}

.image-preview__close {
  position: absolute;
  top: calc(16px + env(safe-area-inset-top));
  right: 16px;
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  display: grid;
  place-items: center;
  cursor: pointer;
}

.chat-locked {
  flex: 1;
  margin: 0;
  font-size: 12px;
  color: var(--muted);
}

.remark-overlay {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(17, 24, 39, 0.42);
  padding: 16px;
}

.remark-sheet {
  width: 100%;
  max-width: 448px;
  padding: 18px 16px calc(18px + env(safe-area-inset-bottom));
  border-radius: 18px;
  background: var(--surface);
}

.remark-sheet h3 {
  margin: 0;
  font-size: 16px;
}

.remark-sheet p {
  margin: 6px 0 12px;
  font-size: 11px;
  color: var(--muted);
}

.remark-sheet input {
  width: 100%;
  height: 44px;
  padding: 0 12px;
  border: 1px solid var(--line-2);
  border-radius: 12px;
  background: var(--surface);
  color: var(--text);
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
}

.remark-actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}

.remark-actions .btn-sm {
  flex: 1;
  height: 40px;
}

.more-overlay {
  position: fixed;
  inset: 0;
  z-index: 41;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 16px;
  background: rgba(17, 24, 39, 0.42);
}

.more-sheet {
  width: 100%;
  max-width: 448px;
  padding: 8px 16px calc(18px + env(safe-area-inset-bottom));
  border-radius: 18px;
  background: var(--surface);
}

.more-action {
  width: 100%;
  height: 48px;
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

.more-action:first-child {
  border-top: 0;
}

.more-action--cancel {
  margin-top: 8px;
  border-top: 0;
  border-radius: 12px;
  background: var(--surface-2);
  color: var(--muted);
}
</style>
