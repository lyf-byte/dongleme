<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronRight, Download, Flame, LogOut, Trash2 } from 'lucide-vue-next'
import {
  getProfile,
  updateProfile,
  clearRecords,
  clearApiCache,
  errorMessage
} from '../utils/api'
import { getSession, signOut } from '../utils/supabase'
import { todayShanghai } from '../utils/sport'
import { toast, confirmDialog } from '../utils/ui'

const router = useRouter()

const loading = ref(true)
const saving = ref(false)
const profile = ref(null)
const account = ref('')
const form = ref({ nickname: '', weight: 60, weeklyGoal: 150 })
const errorText = ref('')

const avatarText = computed(() => (profile.value?.nickname || '动').slice(0, 1))

const totals = computed(() => [
  { label: '总打卡', value: profile.value?.total?.count || 0, unit: '次' },
  { label: '总时长', value: profile.value?.total?.minutes || 0, unit: '分钟' },
  { label: '总消耗', value: profile.value?.total?.calories || 0, unit: '千卡' }
])

async function load() {
  loading.value = true
  try {
    const [data, sessionResult] = await Promise.all([getProfile(), getSession()])
    profile.value = data
    form.value = {
      nickname: data.nickname,
      weight: data.weight,
      weeklyGoal: data.weeklyGoal
    }

    const user = sessionResult.data.session?.user
    const email = user?.email || ''
    const phone = user?.user_metadata?.phone
    account.value =
      phone || (email.endsWith('@phone.dlm.app') ? email.split('@')[0] : email) || '未绑定账号'
  } catch (error) {
    toast(errorMessage(error, '加载资料失败'))
  } finally {
    loading.value = false
  }
}

async function save() {
  errorText.value = ''
  const nickname = form.value.nickname.trim()
  if (!/^[\u4e00-\u9fa5A-Za-z0-9_-]{1,12}$/.test(nickname)) {
    errorText.value = '昵称需为 1–12 位中英文、数字、下划线或连字符'
    return
  }
  const weight = Number(form.value.weight)
  if (!Number.isFinite(weight) || weight < 20 || weight > 300) {
    errorText.value = '体重需在 20–300 kg 之间'
    return
  }
  const weeklyGoal = Number(form.value.weeklyGoal)
  if (!Number.isInteger(weeklyGoal) || weeklyGoal < 1 || weeklyGoal > 2000) {
    errorText.value = '周目标需为 1–2000 的整数'
    return
  }

  saving.value = true
  try {
    await updateProfile({ nickname, weight, weeklyGoal })
    if (profile.value) {
      profile.value.nickname = nickname
      profile.value.weight = weight
      profile.value.weeklyGoal = weeklyGoal
    }
    toast('已保存')
  } catch (error) {
    errorText.value = errorMessage(error, '保存失败')
  } finally {
    saving.value = false
  }
}

async function exportData() {
  try {
    const data = await getProfile({ export: 1 })
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `donglemei-${todayShanghai()}.json`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    toast('已导出')
  } catch (error) {
    toast(errorMessage(error, '导出失败'))
  }
}

async function clearAll() {
  try {
    await confirmDialog({
      title: '清空所有数据',
      message: '将删除全部打卡记录与 AI 小结，连续打卡也会归零，且无法恢复。',
      confirmText: '清空',
      cancelText: '取消',
      danger: true
    })
  } catch {
    return
  }

  try {
    await clearRecords()
    await load()
    toast('已清空')
  } catch (error) {
    toast(errorMessage(error, '清空失败'))
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
    clearApiCache()
    await signOut()
  } finally {
    router.replace('/auth')
  }
}

onMounted(load)
</script>

<template>
  <div class="page mine">
    <header class="nav-large">
      <div>
        <p class="eyebrow">账号与设置</p>
        <h1>我的</h1>
      </div>
      <div class="avatar">{{ avatarText }}</div>
    </header>

    <template v-if="loading">
      <div class="card loading-card">
        <div class="loading-line"><span></span></div>
      </div>
    </template>

    <template v-else>
      <section class="card profile-card">
        <div class="profile-main">
          <div class="avatar profile-avatar">{{ avatarText }}</div>
          <div class="profile-text">
            <h2>{{ profile?.nickname || '运动新人' }}</h2>
            <p>{{ account }}</p>
          </div>
        </div>
        <span class="streak-chip">
          <Flame :size="14" />
          连续 {{ profile?.streak || 0 }} 天
        </span>
      </section>

      <div class="card stats-card">
        <div v-for="item in totals" :key="item.label" class="stat">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}<em>{{ item.unit }}</em></strong>
        </div>
      </div>

      <div class="section-title">
        <h2>基础资料</h2>
        <span>用于计算卡路里</span>
      </div>

      <div class="form-card">
        <div class="form-row">
          <span class="form-row__label">昵称</span>
          <input v-model="form.nickname" type="text" maxlength="12" placeholder="1–12 位" />
        </div>
        <div class="form-row">
          <span class="form-row__label">体重</span>
          <input v-model="form.weight" type="number" inputmode="decimal" placeholder="60" />
          <span class="form-row__extra">kg</span>
        </div>
        <div class="form-row">
          <span class="form-row__label">周目标</span>
          <input v-model="form.weeklyGoal" type="number" inputmode="numeric" placeholder="150" />
          <span class="form-row__extra">分钟</span>
        </div>
      </div>

      <p v-if="errorText" class="form-error">{{ errorText }}</p>

      <button type="button" class="btn-primary save-btn" :disabled="saving" @click="save">
        {{ saving ? '保存中…' : '保存修改' }}
      </button>

      <div class="section-title">
        <h2>数据管理</h2>
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

      <button type="button" class="logout" @click="logout">
        <LogOut :size="16" />
        退出登录
      </button>

      <p class="version">动了没 · v1.0.0</p>
    </template>
  </div>
</template>

<style scoped>
.mine {
  padding-top: 0;
}

.loading-card {
  padding: 18px;
  margin-top: 4px;
}

.profile-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-radius: 20px;
}

.profile-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.profile-avatar {
  width: 52px;
  height: 52px;
  font-size: 20px;
  background: var(--blue);
  color: #fff;
}

.profile-text {
  min-width: 0;
}

.profile-text h2 {
  margin: 0;
  font-size: 18px;
  line-height: 24px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-text p {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.streak-chip {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--yellow-soft);
  color: #9a6700;
  font-size: 11px;
  font-weight: 500;
}

.stats-card {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-top: 12px;
  padding: 14px 0;
  border-radius: 18px;
}

.stat {
  padding: 0 14px;
}

.stat + .stat {
  border-left: 1px solid var(--line);
}

.stat span {
  display: block;
  font-size: 12px;
  color: var(--muted);
}

.stat strong {
  display: block;
  margin-top: 3px;
  font-size: 20px;
  line-height: 26px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.stat em {
  margin-left: 2px;
  font-size: 10px;
  font-weight: 400;
  font-style: normal;
  color: var(--muted);
}

.save-btn {
  margin-top: 12px;
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
}

.chev {
  color: var(--muted);
}

.logout {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 48px;
  margin-top: 20px;
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
</style>
