<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft,
  ArrowRight,
  Eye,
  EyeOff,
  LayoutDashboard,
  Lock,
  Route as RouteIcon,
  Users,
  UserRound
} from 'lucide-vue-next'
import { adminSignIn, adminSignOut } from '../../utils/auth'
import { adminOverview, errorMessage } from '../../utils/api'
import '../../styles/admin.css'

const router = useRouter()

const form = ref({ account: '', password: '' })
const loading = ref(false)
const errorText = ref('')
const showPassword = ref(false)

function mapError(error) {
  return errorMessage(error, '登录失败，请稍后再试')
}

async function submit() {
  errorText.value = ''
  if (!form.value.account.trim() || !form.value.password) {
    errorText.value = '请输入管理员账号和密码'
    return
  }

  loading.value = true
  try {
    const data = await adminSignIn(form.value.account.trim(), form.value.password)
    if (!data.user?.isAdmin) {
      await adminSignOut()
      errorText.value = '该账号不是管理员'
      return
    }

    await adminOverview(true)
    router.replace('/admin')
  } catch (error) {
    if (error?.response?.status === 403 || error?.status === 403) {
      await adminSignOut()
      errorText.value = '该账号没有管理员权限'
      return
    }
    errorText.value = mapError(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="admin-login">
    <aside class="admin-login__brand">
      <div class="admin-login__brand-top">
        <img class="admin-login__logo" src="/logo.svg" alt="" aria-hidden="true" />
        <div>
          动了么
          <span>ADMIN CONSOLE</span>
        </div>
      </div>

      <div class="admin-login__brand-copy">
        <h2>校园运动<br />运营管理后台</h2>
        <p>集中管理用户、打卡、运动轨迹与榜单数据，掌握平台每天的运动动态。</p>
        <ul>
          <li><LayoutDashboard :size="16" /> 数据概览与 14 天趋势</li>
          <li><Users :size="16" /> 用户、打卡与活跃筛选</li>
          <li><RouteIcon :size="16" /> GPS 运动轨迹审核</li>
        </ul>
      </div>

      <span class="admin-login__brand-foot">动了么 · 让每一次运动都被看见</span>
    </aside>

    <main class="admin-login__main">
      <div class="admin-login__card">
        <h1>管理员登录</h1>
        <p>请使用管理员账号进入后台</p>

        <form @submit.prevent="submit">
          <div class="form-card">
            <div class="form-row admin-login__row">
              <UserRound :size="18" class="admin-login__icon" />
              <input
                v-model="form.account"
                type="text"
                placeholder="管理员账号"
                autocomplete="username"
              />
            </div>
            <div class="form-row admin-login__row">
              <Lock :size="18" class="admin-login__icon" />
              <input
                v-model="form.password"
                :type="showPassword ? 'text' : 'password'"
                placeholder="密码"
                autocomplete="current-password"
              />
              <button
                type="button"
                class="admin-login__eye"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                <EyeOff v-if="showPassword" :size="18" />
                <Eye v-else :size="18" />
              </button>
            </div>
          </div>

          <p v-if="errorText" class="form-error">{{ errorText }}</p>

          <button class="btn-primary admin-login__submit" type="submit" :disabled="loading">
            <span>{{ loading ? '登录中…' : '登录后台' }}</span>
            <ArrowRight :size="18" />
          </button>
        </form>

        <button type="button" class="admin-login__back" @click="router.push('/')">
          <ArrowLeft :size="14" />
          返回应用
        </button>
      </div>
    </main>
  </div>
</template>

<style scoped>
.admin-login__row input {
  text-align: left;
  font-weight: 400;
}

.admin-login__icon {
  flex: 0 0 auto;
  color: var(--muted);
}

.admin-login__eye {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border: 0;
  background: transparent;
  padding: 0;
  color: #94a3b8;
  cursor: pointer;
}

.admin-login__submit {
  margin-top: 18px;
}
</style>
