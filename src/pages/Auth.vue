<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, CalendarCheck, Eye, EyeOff, Footprints, Leaf, Lock, ShieldCheck, Trophy, UserRound } from 'lucide-vue-next'
import { signIn, signUp } from '../utils/auth'
import authHero from '../assets/auth-hero.jpg'

const route = useRoute()
const router = useRouter()

const mode = ref('login')
const form = ref({ account: '', password: '', confirm: '' })
const agreed = ref(false)
const loading = ref(false)
const errorText = ref('')
const showPassword = ref(false)
const docOpen = ref(false)
const docType = ref('terms')

function openDoc(type) {
  docType.value = type
  docOpen.value = true
}

const PHONE_RE = /^1[3-9]\d{9}$/
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const accountType = computed(() => {
  const value = form.value.account.trim()
  if (PHONE_RE.test(value)) return 'phone'
  if (EMAIL_RE.test(value)) return 'email'
  return ''
})

const accountHint = computed(() => {
  if (accountType.value === 'phone') return '手机号仅作为账号标识，不发送短信验证码'
  if (accountType.value === 'email') return '将使用邮箱作为登录账号'
  return '支持手机号或邮箱，输入后自动识别'
})

const submitText = computed(() => {
  if (loading.value) return '请稍候…'
  const label =
    accountType.value === 'phone' ? '手机号' : accountType.value === 'email' ? '邮箱' : ''
  return mode.value === 'login' ? `${label}登录` : `${label}注册`
})

function setMode(value) {
  mode.value = value
  errorText.value = ''
}

function mapError(error) {
  const message = error?.message || ''
  if (message.toLowerCase().includes('failed to fetch')) return '网络异常，请检查网络后重试'
  return message || '操作失败，请稍后再试'
}

function validate() {
  const account = form.value.account.trim()
  if (!account) return '请输入手机号或邮箱'
  if (!accountType.value) return '请输入正确的手机号或邮箱地址'
  if (form.value.password.length < 6) return '密码至少 6 位'
  if (mode.value === 'register' && form.value.password !== form.value.confirm) {
    return '两次输入的密码不一致'
  }
  if (mode.value === 'register' && !agreed.value) return '请先勾选用户协议'
  return ''
}

async function afterAuth(user) {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  if (!user?.onboarded) {
    router.replace({ path: '/onboarding', query: redirect ? { redirect } : {} })
    return
  }
  router.replace(redirect || '/')
}

async function onSubmit() {
  errorText.value = ''
  const message = validate()
  if (message) {
    errorText.value = message
    return
  }

  loading.value = true
  try {
    const account = form.value.account.trim()
    if (mode.value === 'register') {
      const data = await signUp(account, form.value.password)
      await afterAuth(data.user)
    } else {
      const data = await signIn(account, form.value.password)
      await afterAuth(data.user)
    }
  } catch (error) {
    errorText.value = mapError(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth">
    <header class="auth-hero">
      <img class="auth-illustration" :src="authHero" alt="" aria-hidden="true" />
      <div class="auth-hero__fade" aria-hidden="true"></div>
      <div class="auth-brand">
        <img class="auth-logo" src="/logo.svg" alt="动了么" />
        <div>
          <h1>动了么</h1>
          <p>每天动一点，<span>连续看得见</span></p>
        </div>
      </div>
      <div class="auth-tags">
        <span class="auth-tag auth-tag--blue">
          <CalendarCheck :size="14" />
          连续打卡
        </span>
        <span class="auth-tag auth-tag--yellow">
          <Trophy :size="14" />
          校园排行
        </span>
        <span class="auth-tag auth-tag--green">
          <Leaf :size="14" />
          AI 小结
        </span>
      </div>
    </header>

    <main class="auth-card">
      <div class="segmented auth-tabs">
        <button
          type="button"
          :class="{ active: mode === 'login' }"
          @click="setMode('login')"
        >
          登录
        </button>
        <button
          type="button"
          :class="{ active: mode === 'register' }"
          @click="setMode('register')"
        >
          注册
        </button>
      </div>

      <form @submit.prevent="onSubmit">
        <div class="form-card">
          <div class="form-row auth-row">
            <UserRound class="auth-row__icon" :size="18" />
            <input
              v-model="form.account"
              type="text"
              placeholder="手机号或邮箱"
              autocomplete="username"
            />
            <span v-if="accountType" class="chip chip--blue">
              {{ accountType === 'phone' ? '手机号' : '邮箱' }}
            </span>
          </div>

          <div class="form-row auth-row">
            <Lock class="auth-row__icon" :size="18" />
            <input
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="密码（至少 6 位）"
              autocomplete="current-password"
            />
            <button
              type="button"
              class="auth-eye"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              @click="showPassword = !showPassword"
            >
              <EyeOff v-if="showPassword" :size="18" />
              <Eye v-else :size="18" />
            </button>
          </div>

          <div v-if="mode === 'register'" class="form-row auth-row">
            <ShieldCheck class="auth-row__icon" :size="18" />
            <input
              v-model="form.confirm"
              :type="showPassword ? 'text' : 'password'"
              placeholder="再次输入密码"
              autocomplete="new-password"
            />
          </div>
        </div>

        <p class="auth-hint">{{ accountHint }}</p>

        <label v-if="mode === 'register'" class="check">
          <input v-model="agreed" type="checkbox" />
          <span>
            我已阅读并同意
            <button type="button" class="doc-link" @click.prevent.stop="openDoc('terms')">
              《用户协议》
            </button>
            与
            <button type="button" class="doc-link" @click.prevent.stop="openDoc('privacy')">
              《隐私说明》
            </button>
          </span>
        </label>

        <p v-if="errorText" class="form-error">{{ errorText }}</p>

        <button class="btn-primary auth-submit" type="submit" :disabled="loading">
          <span>{{ submitText }}</span>
          <ArrowRight :size="18" />
        </button>
      </form>

      <p class="auth-switch">
        {{ mode === 'login' ? '还没有账号？' : '已经有账号了？' }}
        <button type="button" @click="setMode(mode === 'login' ? 'register' : 'login')">
          {{ mode === 'login' ? '立即注册' : '去登录' }}
        </button>
      </p>
    </main>

    <p class="auth-foot">
      <Footprints :size="18" class="auth-foot__icon" aria-hidden="true" />
      校园运动打卡 · 让坚持有迹可循
    </p>

    <transition name="ui">
      <div v-if="docOpen" class="doc-overlay" @click.self="docOpen = false">
        <div class="doc-sheet" role="dialog" aria-modal="true">
          <h2>{{ docType === 'terms' ? '用户协议' : '隐私说明' }}</h2>
          <div v-if="docType === 'terms'" class="doc-body">
            <p>1. 本应用提供运动打卡、统计、排行与 AI 小结功能，内容仅供参考，不构成医疗建议。</p>
            <p>2. 请如实记录运动数据，禁止刷量、作弊等影响排行榜公平的行为。</p>
            <p>3. 账号与密码由你自行保管，请勿转借他人使用。</p>
            <p>4. 我们可能根据功能需要更新本协议，更新后会在应用内提示。</p>
          </div>
          <div v-else class="doc-body">
            <p>1. 我们仅收集手机号或邮箱用于登录，昵称、体重和周目标用于计算卡路里与目标进度。</p>
            <p>2. 打卡记录用于统计、排行榜和周小结；AI 小结仅向模型发送聚合后的运动数据。</p>
            <p>3. 定位信息仅在你授权后用于获取当地天气，不会保存位置轨迹。</p>
            <p>4. 你可以在「我的」中导出或清空自己的数据。</p>
          </div>
          <button type="button" class="btn-primary doc-close" @click="docOpen = false">
            我知道了
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.auth {
  position: relative;
  min-height: 100vh;
  padding: 0 0 calc(28px + env(safe-area-inset-bottom));
  background: linear-gradient(180deg, #e9f4ff 0%, #f3f9ff 38%, #eaf4ff 100%);
  overflow: hidden;
}

.auth-hero {
  position: relative;
}

.auth-illustration {
  display: block;
  width: 100%;
  height: auto;
}

.auth-hero__fade {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1;
  height: 72px;
  background: linear-gradient(to bottom, rgba(243, 249, 255, 0), #f3f9ff 92%);
  pointer-events: none;
}

.auth-brand {
  position: absolute;
  top: calc(26px + env(safe-area-inset-top));
  left: 20px;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: calc(100% - 40px);
}

.auth-logo {
  width: 56px;
  height: 56px;
  flex: 0 0 auto;
  border-radius: 16px;
  box-shadow: 0 10px 22px rgba(47, 107, 255, 0.3);
}

.auth-hero h1 {
  margin: 0;
  font-size: 30px;
  line-height: 36px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: #0f172a;
}

.auth-hero p {
  margin: 2px 0 0;
  font-size: 13px;
  line-height: 18px;
  color: #475569;
}

.auth-hero p span {
  border-bottom: 2px solid rgba(246, 178, 27, 0.85);
  padding-bottom: 1px;
}

.auth-tags {
  position: relative;
  z-index: 2;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: -18px;
  padding: 0 20px;
}

.auth-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 36px;
  padding: 0 13px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 8px 20px rgba(47, 107, 255, 0.14);
  color: #334155;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

.auth-tag--blue {
  background: linear-gradient(135deg, #2f6bff, #5b8cff);
  color: #fff;
  box-shadow: 0 10px 22px rgba(47, 107, 255, 0.32);
}

.auth-tag--yellow svg {
  color: var(--yellow);
}

.auth-tag--green svg {
  color: var(--green);
}

.auth-card {
  position: relative;
  z-index: 1;
  margin: 18px 16px 0;
  padding: 20px;
  border: 0;
  border-radius: 28px;
  background: #fff;
  box-shadow: 0 20px 45px rgba(47, 107, 255, 0.12);
}

.auth-tabs {
  margin-bottom: 18px;
  padding: 4px;
  border-radius: 999px;
  background: #f1f5fb;
}

.auth-tabs button {
  height: 44px;
  border-radius: 999px;
  font-size: 15px;
  font-weight: 600;
  color: #64748b;
}

.auth-tabs button.active {
  background: #fff;
  color: var(--blue);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.08);
}

.auth-card .form-card {
  display: grid;
  gap: 10px;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  overflow: visible;
}

.auth-row {
  min-height: 56px;
  padding: 0 16px;
  border: 1px solid #e8eef7;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.04);
}

.auth-row + .auth-row {
  border-top: 0;
}

.auth-row__icon {
  flex: 0 0 auto;
  color: #94a3b8;
}

.auth-row input {
  text-align: left;
  font-size: 15px;
  font-weight: 400;
}

.auth-eye {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border: 0;
  background: transparent;
  padding: 0;
  color: #94a3b8;
  cursor: pointer;
}

.auth-hint {
  margin: 10px 4px 0;
  font-size: 12px;
  line-height: 18px;
  color: #64748b;
}

.auth-submit {
  height: 52px;
  margin-top: 18px;
  border-radius: 16px;
  background: linear-gradient(135deg, #2f6bff, #5b8cff);
  box-shadow: 0 14px 28px rgba(47, 107, 255, 0.28);
  font-size: 16px;
}

.auth-switch {
  margin: 16px 0 0;
  text-align: center;
  font-size: 13px;
  color: var(--muted);
}

.auth-switch button {
  border: 0;
  background: transparent;
  padding: 0;
  color: var(--blue);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.auth-foot {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  margin: 24px 16px 0;
  text-align: center;
  font-size: 11px;
  color: #64748b;
}

.auth-foot__icon {
  color: #94a3b8;
}

.doc-link {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--blue);
  font-size: inherit;
  font-weight: 600;
  cursor: pointer;
  text-decoration: underline;
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
  height: 44px;
  margin-top: 6px;
  font-size: 14px;
}
</style>
