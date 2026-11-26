<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ChevronRight,
  Flame,
  MessageCircle,
  Route as RouteIcon,
  Settings,
  Users
} from 'lucide-vue-next'
import { getProfile, updateProfile, getUnread, errorMessage } from '../utils/api'
import { getUser } from '../utils/auth'
import { avatarFor } from '../utils/avatar'
import { evaluateBadges, statsFromProfile } from '../utils/badges'
import { syncBadges } from '../composables/useBadges'
import { toast } from '../utils/ui'

const router = useRouter()

const loading = ref(true)
const saving = ref(false)
const profile = ref(null)
const account = ref('')
const form = ref({ nickname: '', gender: 'secret', weight: 60, weeklyGoal: 150 })
const errorText = ref('')

const genders = [
  { value: 'male', label: '男' },
  { value: 'female', label: '女' },
  { value: 'secret', label: '保密' }
]

const avatarText = computed(() => (profile.value?.nickname || '动').slice(0, 1))
const avatarSrc = computed(() => avatarFor(profile.value?.gender))

const totals = computed(() => [
  { label: '总打卡', value: profile.value?.total?.count || 0, unit: '次' },
  { label: '总时长', value: profile.value?.total?.minutes || 0, unit: '分钟' },
  { label: '总消耗', value: profile.value?.total?.calories || 0, unit: '千卡' }
])

const unread = ref({ messages: 0, notifications: 0 })
const unreadTotal = computed(
  () => (unread.value.messages || 0) + (unread.value.notifications || 0)
)

const unlockedIds = ref(new Set())
const badges = computed(() =>
  evaluateBadges(statsFromProfile(profile.value)).map((badge) => ({
    ...badge,
    earned: badge.earned || unlockedIds.value.has(badge.id)
  }))
)
const earnedCount = computed(() => badges.value.filter((item) => item.earned).length)
const activeBadge = ref(null)

async function load() {
  loading.value = true
  try {
    const [data, count] = await Promise.all([
      getProfile(),
      getUnread().catch(() => null)
    ])
    profile.value = data
    if (count) unread.value = count
    form.value = {
      nickname: data.nickname,
      gender: data.gender || 'secret',
      weight: data.weight,
      weeklyGoal: data.weeklyGoal
    }

    const user = getUser()
    account.value = user?.username || user?.phone || user?.email || '未绑定账号'

    const { ids } = await syncBadges(data)
    unlockedIds.value = ids
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
    await updateProfile({
      nickname,
      gender: form.value.gender,
      weight,
      weeklyGoal
    })
    if (profile.value) {
      profile.value.nickname = nickname
      profile.value.gender = form.value.gender
      profile.value.weight = weight
      profile.value.weeklyGoal = weeklyGoal
      const { ids } = await syncBadges(profile.value)
      unlockedIds.value = ids
    }
    toast('已保存')
  } catch (error) {
    errorText.value = errorMessage(error, '保存失败')
  } finally {
    saving.value = false
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
      <button
        type="button"
        class="nav-large__action"
        aria-label="设置"
        @click="router.push('/settings')"
      >
        <Settings :size="20" />
      </button>
    </header>

    <template v-if="loading">
      <div class="card loading-card">
        <div class="loading-line"><span></span></div>
      </div>
    </template>

    <template v-else>
      <section class="card profile-card">
        <div class="profile-main">
          <img v-if="avatarSrc" :src="avatarSrc" alt="" class="avatar profile-avatar" />
          <div v-else class="avatar profile-avatar">{{ avatarText }}</div>
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

      <div class="list-card route-entry">
        <button type="button" class="list-row" @click="router.push('/track')">
          <span class="list-icon list-icon--blue"><RouteIcon :size="18" /></span>
          <span class="list-text">
            <strong>运动轨迹</strong>
            <small>查看跑步、快走、骑行记录</small>
          </span>
          <ChevronRight :size="18" class="chev" />
        </button>
        <button type="button" class="list-row" @click="router.push('/messages')">
          <span class="list-icon list-icon--green"><MessageCircle :size="18" /></span>
          <span class="list-text">
            <strong>
              消息
              <span v-if="unreadTotal" class="msg-count">{{ unreadTotal > 99 ? '99+' : unreadTotal }}</span>
            </strong>
            <small>聊天与新的关注</small>
          </span>
          <ChevronRight :size="18" class="chev" />
        </button>
        <button type="button" class="list-row" @click="router.push('/feed')">
          <span class="list-icon list-icon--violet"><Users :size="18" /></span>
          <span class="list-text">
            <strong>好友动态</strong>
            <small>看看关注的人最近在运动什么</small>
          </span>
          <ChevronRight :size="18" class="chev" />
        </button>
      </div>

      <div class="section-title">
        <h2>成就徽章</h2>
        <span>已解锁 {{ earnedCount }} / {{ badges.length }}</span>
      </div>

      <div class="badge-grid">
        <button
          v-for="badge in badges"
          :key="badge.id"
          type="button"
          class="badge-card"
          :class="{ earned: badge.earned }"
          @click="activeBadge = badge"
        >
          <span
            class="badge-icon"
            :style="badge.earned ? { background: badge.tierStyle.soft, color: badge.tierStyle.color } : null"
          >
            <component :is="badge.icon" :size="17" />
          </span>
          <strong>{{ badge.name }}</strong>
          <small>
            {{ badge.earned ? badge.tierStyle.label + '章' : badge.current + ' / ' + badge.target }}
          </small>
          <span class="badge-meter">
            <i
              :style="{
                width: badge.percent + '%',
                background: badge.earned ? badge.tierStyle.color : 'var(--blue)'
              }"
            ></i>
          </span>
        </button>
      </div>

      <transition name="ui">
        <div v-if="activeBadge" class="badge-overlay" @click.self="activeBadge = null">
          <div class="badge-sheet" role="dialog" aria-modal="true" :aria-label="activeBadge.name">
            <span
              class="badge-sheet__icon"
              :class="{ earned: activeBadge.earned }"
              :style="activeBadge.earned
                ? { background: activeBadge.tierStyle.soft, color: activeBadge.tierStyle.color }
                : null"
            >
              <component :is="activeBadge.icon" :size="26" />
            </span>
            <small class="badge-sheet__tier">
              {{ activeBadge.tierStyle ? activeBadge.tierStyle.label + '章' : '未解锁' }} ·
              {{ activeBadge.earned ? '已解锁' : '继续加油' }}
            </small>
            <strong>{{ activeBadge.name }}</strong>
            <p>{{ activeBadge.desc }}</p>
            <div class="badge-sheet__bar">
              <i
                :style="{
                  width: activeBadge.percent + '%',
                  background: activeBadge.earned && activeBadge.tierStyle
                    ? activeBadge.tierStyle.color
                    : 'var(--blue)'
                }"
              ></i>
            </div>
            <span class="badge-sheet__progress">
              {{ activeBadge.current }} / {{ activeBadge.target }} {{ activeBadge.unit }}
            </span>
            <button type="button" class="btn-primary btn-sm" @click="activeBadge = null">
              知道了
            </button>
          </div>
        </div>
      </transition>

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
          <span class="form-row__label">性别</span>
          <div class="gender-pills">
            <button
              v-for="item in genders"
              :key="item.value"
              type="button"
              :class="{ active: form.gender === item.value }"
              @click="form.gender = item.value"
            >
              {{ item.label }}
            </button>
          </div>
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

.route-entry {
  margin-top: 12px;
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

.gender-pills {
  flex: 1;
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}

.gender-pills button {
  height: 30px;
  padding: 0 14px;
  border: 1px solid var(--line-2);
  border-radius: 999px;
  background: var(--surface-2);
  color: var(--text-2);
  font-size: 12px;
  cursor: pointer;
}

.gender-pills button.active {
  border-color: var(--blue);
  background: var(--blue-soft);
  color: var(--blue);
  font-weight: 600;
}

.badge-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  grid-auto-rows: 112px;
  gap: 10px;
}

.badge-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 12px 6px 11px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--surface);
  text-align: center;
  font-family: inherit;
  cursor: pointer;
  opacity: 0.62;
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.badge-meter {
  display: block;
  width: 80%;
  height: 3px;
  margin-top: 4px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--surface-2);
}

.badge-meter i {
  display: block;
  height: 100%;
  border-radius: inherit;
  transition: width 0.4s ease;
}

.badge-card:active {
  transform: scale(0.97);
}

.badge-card.earned {
  border-color: var(--blue-soft-2);
  background: #fff;
  opacity: 1;
  box-shadow: 0 6px 16px rgba(17, 24, 39, 0.05);
}

.badge-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: var(--surface-2);
  color: var(--muted);
}

.badge-card.earned .badge-icon {
  box-shadow: inset 0 0 0 1px rgba(17, 24, 39, 0.05);
}

.badge-card strong {
  font-size: 12px;
  line-height: 17px;
  font-weight: 600;
  color: var(--text);
}

.badge-card small {
  display: -webkit-box;
  overflow: hidden;
  font-size: 10px;
  line-height: 14px;
  color: var(--muted);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.badge-overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: grid;
  place-items: center;
  background: rgba(15, 23, 42, 0.45);
  padding: 24px;
}

.badge-sheet {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: 100%;
  max-width: 320px;
  border-radius: 22px;
  background: #fff;
  padding: 24px 20px 18px;
  text-align: center;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.24);
}

.badge-sheet__icon {
  display: grid;
  place-items: center;
  width: 60px;
  height: 60px;
  border-radius: 20px;
  background: var(--surface-2);
  color: var(--muted);
}

.badge-sheet__icon.earned {
  background: var(--blue-soft);
  color: var(--blue);
}

.badge-sheet__tier {
  color: var(--muted);
  font-size: 10px;
  letter-spacing: 0.04em;
}

.badge-sheet strong {
  font-size: 17px;
}

.badge-sheet p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}

.badge-sheet__bar {
  width: 100%;
  height: 6px;
  margin-top: 6px;
  border-radius: 999px;
  background: var(--surface-2);
  overflow: hidden;
}

.badge-sheet__bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--blue);
}

.badge-sheet__progress {
  color: var(--text-2);
  font-size: 11px;
}

.badge-sheet .btn-sm {
  width: 100%;
  margin-top: 8px;
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

.list-icon--violet {
  background: var(--violet-soft);
  color: var(--violet);
}

.list-icon--green {
  background: var(--green-soft);
  color: var(--green);
}

.msg-count {
  display: inline-grid;
  place-items: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  margin-left: 4px;
  border-radius: 8px;
  background: var(--red);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  vertical-align: 1px;
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
</style>
