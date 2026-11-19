<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Eye, EyeOff, Lock, ShieldCheck, UserRound } from 'lucide-vue-next'
import { signIn, signUp } from '../utils/auth'
import authHero from '../assets/auth-hero.svg'

const route = useRoute()
const router = useRouter()

const mode = ref('login')
const form = ref({ account: '', password: '', confirm: '' })
const agreed = ref(false)
const loading = ref(false)
const errorText = ref('')
const showPassword = ref(false)

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
      <div class="auth-brand">
        <img class="auth-logo" src="/logo.svg" alt="动了没" />
        <div>
          <h1>动了没</h1>
          <p>每天动一点，连续看得见</p>
        </div>
      </div>
      <img class="auth-illustration" :src="authHero" alt="" aria-hidden="true" />
      <div class="auth-tags">
        <span class="chip chip--blue">连续打卡</span>
        <span class="chip chip--yellow">校园排行</span>
        <span class="chip chip--green">AI 小结</span>
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
          <span>我已阅读并同意《用户协议》与《隐私说明》</span>
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

    <p class="auth-foot">校园运动打卡 · 让坚持有迹可循</p>
  </div>
</template>

<style scoped>
.auth {
  min-height: 100vh;
  padding: 0 20px calc(28px + env(safe-area-inset-bottom));
  background: var(--bg);
  overflow: hidden;
}

.auth-hero {
  padding: 40px 4px 20px;
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.auth-logo {
  width: 52px;
  height: 52px;
  flex: 0 0 auto;
  border-radius: 16px;
  box-shadow: 0 10px 24px rgba(47, 107, 255, 0.28);
}

.auth-illustration {
  display: block;
  width: 100%;
  margin: 20px 0 4px;
}

.auth-hero h1 {
  margin: 0;
  font-size: 28px;
  line-height: 34px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.auth-hero p {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--text-2);
}

.auth-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.auth-card {
  position: relative;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 24px;
  background: var(--surface);
  box-shadow: var(--shadow);
}

.auth-tabs {
  margin-bottom: 16px;
}

.auth-row {
  padding: 13px 14px;
}

.auth-row__icon {
  flex: 0 0 auto;
  color: var(--muted);
}

.auth-row input {
  text-align: left;
  font-weight: 400;
}

.auth-eye {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border: 0;
  background: transparent;
  padding: 0;
  color: var(--muted);
  cursor: pointer;
}

.auth-hint {
  margin: 10px 4px 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--muted);
}

.auth-submit {
  margin-top: 18px;
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
  margin: 22px 0 0;
  text-align: center;
  font-size: 11px;
  color: var(--muted);
}
</style>
