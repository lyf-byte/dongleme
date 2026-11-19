<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Lock, Mail } from 'lucide-vue-next'
import { adminSignIn, adminSignOut } from '../../utils/auth'
import { adminOverview, errorMessage } from '../../utils/api'
import '../../styles/admin.css'

const router = useRouter()

const form = ref({ email: '', password: '' })
const loading = ref(false)
const errorText = ref('')

function mapError(error) {
  const message = (error?.message || '').toLowerCase()
  if (message.includes('invalid login credentials')) return '邮箱或密码错误'
  if (message.includes('email not confirmed')) return '邮箱尚未验证'
  return errorMessage(error, '登录失败，请稍后再试')
}

async function submit() {
  errorText.value = ''
  if (!form.value.email.trim() || !form.value.password) {
    errorText.value = '请输入管理员邮箱和密码'
    return
  }

  loading.value = true
  try {
    const data = await adminSignIn(form.value.email.trim(), form.value.password)
    if (!data.user?.isAdmin) {
      await adminSignOut()
      errorText.value = '该账号不是管理员'
      return
    }

    await adminOverview()
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
    <div class="admin-login__card">
      <img class="admin-login__logo" src="/logo.svg" alt="动了没" />
      <h1>后台管理</h1>
      <p>动了没 · 管理员登录</p>

      <form @submit.prevent="submit">
        <div class="form-card">
          <div class="form-row admin-login__row">
            <Mail :size="18" class="admin-login__icon" />
            <input v-model="form.email" type="email" placeholder="管理员邮箱" autocomplete="username" />
          </div>
          <div class="form-row admin-login__row">
            <Lock :size="18" class="admin-login__icon" />
            <input
              v-model="form.password"
              type="password"
              placeholder="密码"
              autocomplete="current-password"
            />
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

.admin-login__submit {
  margin-top: 18px;
}
</style>
