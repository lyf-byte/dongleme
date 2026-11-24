<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft,
  ChevronRight,
  Download,
  FileText,
  Info,
  Lock,
  LogOut,
  RefreshCw,
  ShieldCheck,
  Trash2,
  UserRound
} from 'lucide-vue-next'
import { clearRecords, errorMessage, getProfile } from '../utils/api'
import { changePassword, getUser, signOut } from '../utils/auth'
import { todayShanghai } from '../utils/sport'
import { saveBlob } from '../utils/native'
import { confirmDialog, toast } from '../utils/ui'

const router = useRouter()

const account = ref('')
const pwdOpen = ref(false)
const pwdSaving = ref(false)
const pwdError = ref('')
const pwdForm = ref({ oldPassword: '', newPassword: '', confirm: '' })
const docType = ref('')

const adminUrl = computed(() => `${window.location.origin}/admin`)

async function copyAdminUrl() {
  try {
    await navigator.clipboard.writeText(adminUrl.value)
    toast('后台地址已复制')
  } catch {
    toast('复制失败，请手动复制')
  }
}

const docTitle = computed(() => ({
  terms: '用户协议',
  privacy: '隐私说明',
  about: '关于动了么'
}[docType.value] || ''))

onMounted(() => {
  const user = getUser()
  account.value = user?.username || user?.phone || user?.email || '未绑定账号'
})

function togglePassword() {
  pwdOpen.value = !pwdOpen.value
  pwdError.value = ''
}

async function savePassword() {
  pwdError.value = ''
  if (!pwdForm.value.oldPassword) {
    pwdError.value = '请输入原密码'
    return
  }
  if (pwdForm.value.newPassword.length < 6) {
    pwdError.value = '新密码至少 6 位'
    return
  }
  if (pwdForm.value.newPassword !== pwdForm.value.confirm) {
    pwdError.value = '两次输入的新密码不一致'
    return
  }

  pwdSaving.value = true
  try {
    await changePassword(pwdForm.value.oldPassword, pwdForm.value.newPassword)
    pwdForm.value = { oldPassword: '', newPassword: '', confirm: '' }
    pwdOpen.value = false
    toast('密码已修改')
  } catch (error) {
    pwdError.value = errorMessage(error, '修改密码失败')
  } finally {
    pwdSaving.value = false
  }
}

async function exportData() {
  try {
    const data = await getProfile({ export: 1 })
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const result = await saveBlob(blob, `dongleme-${todayShanghai()}.json`)
    toast(result === 'ok' ? '已导出' : '导出失败，请重试')
  } catch (error) {
    toast(errorMessage(error, '导出失败'))
  }
}

async function clearAll() {
  try {
    await confirmDialog({
      title: '清空所有数据',
      message: '将删除全部打卡记录、运动轨迹与本周小结，连续打卡也会归零，且无法恢复。',
      confirmText: '清空',
      cancelText: '取消',
      danger: true
    })
  } catch {
    return
  }

  try {
    await clearRecords()
    toast('已清空')
  } catch (error) {
    toast(errorMessage(error, '清空失败'))
  }
}

async function clearLocalCache() {
  try {
    await confirmDialog({
      title: '清除本地缓存',
      message: '会清除未完成的轨迹草稿和天气缓存，不影响账号与打卡记录。',
      confirmText: '清除',
      cancelText: '取消'
    })
  } catch {
    return
  }
  try {
    localStorage.removeItem('dlm_route_draft')
    sessionStorage.removeItem('dlm_weather_cache')
    toast('本地缓存已清除')
  } catch {
    toast('清除失败，请稍后再试')
  }
}

async function logout() {
  try {
    await confirmDialog({
      title: '退出登录',
      message: '退出后需要重新输入账号密码。',
      confirmText: '退出',
      cancelText: '取消'
    })
  } catch {
    return
  }
  try {
    await signOut()
  } finally {
    router.replace('/auth')
  }
}
</script>

<template>
  <div class="settings-page">
    <header class="topbar">
      <button type="button" class="topbar__btn" aria-label="返回" @click="router.back()">
        <ArrowLeft :size="20" />
      </button>
      <h1>设置</h1>
    </header>

    <div class="section-title">
      <h2>账号</h2>
    </div>

    <div class="list-card">
      <div class="list-row list-row--static">
        <span class="list-icon list-icon--blue"><UserRound :size="18" /></span>
        <span class="list-text">
          <strong>登录账号</strong>
          <small>{{ account }}</small>
        </span>
      </div>
      <button type="button" class="list-row" @click="togglePassword">
        <span class="list-icon list-icon--blue"><Lock :size="18" /></span>
        <span class="list-text">
          <strong>修改密码</strong>
          <small>定期更换更安全</small>
        </span>
        <ChevronRight :size="18" class="chev" :class="{ open: pwdOpen }" />
      </button>
    </div>

    <form v-if="pwdOpen" class="form-card pwd-card" @submit.prevent="savePassword">
      <div class="form-row">
        <span class="form-row__label">原密码</span>
        <input
          v-model="pwdForm.oldPassword"
          type="password"
          autocomplete="current-password"
          placeholder="请输入原密码"
        />
      </div>
      <div class="form-row">
        <span class="form-row__label">新密码</span>
        <input
          v-model="pwdForm.newPassword"
          type="password"
          autocomplete="new-password"
          placeholder="至少 6 位"
        />
      </div>
      <div class="form-row">
        <span class="form-row__label">确认</span>
        <input
          v-model="pwdForm.confirm"
          type="password"
          autocomplete="new-password"
          placeholder="再次输入新密码"
        />
      </div>
    </form>

    <p v-if="pwdError" class="form-error">{{ pwdError }}</p>
    <button
      v-if="pwdOpen"
      type="button"
      class="btn-primary settings-save"
      :disabled="pwdSaving"
      @click="savePassword"
    >
      {{ pwdSaving ? '保存中…' : '确认修改' }}
    </button>

    <div class="section-title">
      <h2>数据</h2>
    </div>

    <div class="list-card">
      <button type="button" class="list-row" @click="exportData">
        <span class="list-icon list-icon--blue"><Download :size="18" /></span>
        <span class="list-text">
          <strong>导出数据</strong>
          <small>JSON 格式，包含打卡记录与小结</small>
        </span>
        <ChevronRight :size="18" class="chev" />
      </button>
      <button type="button" class="list-row" @click="clearAll">
        <span class="list-icon list-icon--red"><Trash2 :size="18" /></span>
        <span class="list-text">
          <strong class="red">清空数据</strong>
          <small>删除全部记录并重置连续天数</small>
        </span>
        <ChevronRight :size="18" class="chev" />
      </button>
    </div>

    <div class="section-title">
      <h2>通用</h2>
    </div>

    <div class="list-card">
      <button type="button" class="list-row" @click="clearLocalCache">
        <span class="list-icon list-icon--blue"><RefreshCw :size="18" /></span>
        <span class="list-text">
          <strong>清除本地缓存</strong>
          <small>清理草稿与天气缓存，不影响打卡数据</small>
        </span>
        <ChevronRight :size="18" class="chev" />
      </button>
      <button type="button" class="list-row" @click="docType = 'terms'">
        <span class="list-icon list-icon--blue"><FileText :size="18" /></span>
        <span class="list-text">
          <strong>用户协议</strong>
          <small>使用本应用前请先阅读</small>
        </span>
        <ChevronRight :size="18" class="chev" />
      </button>
      <button type="button" class="list-row" @click="docType = 'privacy'">
        <span class="list-icon list-icon--blue"><ShieldCheck :size="18" /></span>
        <span class="list-text">
          <strong>隐私说明</strong>
          <small>了解我们如何收集和使用数据</small>
        </span>
        <ChevronRight :size="18" class="chev" />
      </button>
      <button type="button" class="list-row" @click="docType = 'about'">
        <span class="list-icon list-icon--blue"><Info :size="18" /></span>
        <span class="list-text">
          <strong>关于动了么</strong>
          <small>版本信息与产品介绍</small>
        </span>
        <ChevronRight :size="18" class="chev" />
      </button>
    </div>

    <button type="button" class="logout" @click="logout">
      <LogOut :size="16" />
      退出登录
    </button>

    <p class="version">动了么 · v1.0.0</p>

    <transition name="ui">
      <div v-if="docType" class="doc-overlay" @click.self="docType = ''">
        <div class="doc-sheet" role="dialog" aria-modal="true" :aria-label="docTitle">
          <h2>{{ docTitle }}</h2>

          <div v-if="docType === 'terms'" class="doc-body">
            <p>1. 本应用提供运动打卡、统计、排行与 AI 小结功能，内容仅供参考，不构成医疗建议。</p>
            <p>2. 请如实记录运动数据，禁止刷量、作弊等影响排行榜公平的行为。</p>
            <p>3. 账号与密码由你自行保管，请勿转借他人使用。</p>
            <p>4. 我们可能根据功能需要更新本协议，更新后会在应用内提示。</p>
          </div>

          <div v-else-if="docType === 'privacy'" class="doc-body">
            <p>1. 我们仅收集手机号或邮箱用于登录，昵称、体重和周目标用于计算卡路里与目标进度。</p>
            <p>2. 打卡记录与运动轨迹用于统计、排行和周小结；管理员可在后台查看轨迹缩略预览，AI 小结仅向模型发送聚合后的运动数据。</p>
            <p>3. 定位信息仅在你授权后用于获取当地天气和记录运动轨迹；轨迹保存在你的账号下，可随时删除。</p>
            <p>4. 你可以在「设置」中导出或清空自己的数据。</p>
          </div>

          <div v-else class="doc-body about-body">
            <span class="about-logo">动</span>
            <strong>动了么</strong>
            <p>校园运动打卡 · 每天动一点，连续看得见</p>
            <p class="about-version">版本 v1.0.0</p>

            <div class="about-admin">
              <div class="about-admin__info">
                <span>管理后台</span>
                <a :href="adminUrl" target="_blank" rel="noopener">{{ adminUrl }}</a>
              </div>
              <button type="button" class="about-admin__copy" @click="copyAdminUrl">
                复制
              </button>
            </div>
          </div>

          <button type="button" class="btn-primary doc-close" @click="docType = ''">
            知道了
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.settings-page {
  min-height: 100vh;
  padding: 0 20px calc(28px + env(safe-area-inset-bottom));
  background: var(--bg);
}

.list-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  padding: 0 14px;
  box-shadow: var(--shadow-sm);
}

.list-row {
  display: grid;
  grid-template-columns: 36px 1fr 18px;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 12px 0;
  border: 0;
  border-top: 1px solid var(--line);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.list-row:first-child {
  border-top: 0;
}

.list-row:active {
  background: var(--surface-2);
}

.list-row--static {
  grid-template-columns: 36px 1fr;
  cursor: default;
}

.list-row--static:active {
  background: transparent;
}

.list-icon {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 10px;
}

.list-icon--blue {
  background: var(--blue-soft);
  color: var(--blue);
}

.list-icon--red {
  background: var(--red-soft);
  color: var(--red);
}

.list-text {
  min-width: 0;
}

.list-text strong {
  display: block;
  font-size: 15px;
  line-height: 20px;
  font-weight: 500;
}

.list-text strong.red {
  color: var(--red);
}

.list-text small {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  line-height: 16px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chev {
  color: var(--muted);
  transition: transform 0.16s ease;
}

.chev.open {
  transform: rotate(90deg);
}

.pwd-card {
  margin-top: 10px;
}

.settings-save {
  width: 100%;
  margin-top: 10px;
}

.logout {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 48px;
  margin-top: 22px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  color: var(--red);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.version {
  margin: 16px 0 0;
  text-align: center;
  font-size: 11px;
  color: var(--muted);
}

.doc-overlay {
  position: fixed;
  inset: 0;
  z-index: 260;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(17, 24, 39, 0.4);
  backdrop-filter: blur(3px);
}

.doc-sheet {
  width: 100%;
  max-width: 360px;
  max-height: 80vh;
  overflow-y: auto;
  padding: 20px;
  border-radius: 22px;
  background: var(--surface);
  box-shadow: 0 24px 60px rgba(17, 24, 39, 0.25);
}

.doc-sheet h2 {
  margin: 0 0 10px;
  font-size: 18px;
  line-height: 24px;
  font-weight: 700;
}

.doc-body p {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 21px;
  color: var(--text-2);
}

.doc-close {
  width: 100%;
  height: 44px;
  margin-top: 6px;
  font-size: 14px;
}

.about-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 6px 0 2px;
}

.about-logo {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  margin-bottom: 10px;
  border-radius: 18px;
  background: var(--blue);
  color: #fff;
  font-size: 24px;
  font-weight: 700;
}

.about-body strong {
  font-size: 18px;
  color: var(--text);
}

.about-body p {
  margin: 6px 0 0;
}

.about-version {
  color: var(--muted);
  font-size: 11px;
}

.about-admin {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg);
  text-align: left;
}

.about-admin__info {
  flex: 1;
  min-width: 0;
}

.about-admin__info span {
  display: block;
  font-size: 10px;
  line-height: 14px;
  color: var(--muted);
}

.about-admin__info a {
  display: block;
  margin-top: 2px;
  overflow: hidden;
  color: var(--blue);
  font-size: 11px;
  line-height: 16px;
  text-decoration: none;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.about-admin__copy {
  flex: 0 0 auto;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  color: var(--text-2);
  font-size: 11px;
  cursor: pointer;
}
</style>
