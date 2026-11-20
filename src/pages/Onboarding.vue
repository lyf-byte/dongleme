<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight } from 'lucide-vue-next'
import { completeOnboarding } from '../utils/auth'
import { updateProfile, errorMessage } from '../utils/api'
import { toast } from '../utils/ui'
import onboardingArt from '../assets/onboarding.jpg'

const route = useRoute()
const router = useRouter()

const form = ref({ nickname: '', gender: 'secret', weight: 60, weeklyGoal: 150 })
const loading = ref(false)
const errorText = ref('')

const genders = [
  { value: 'male', label: '男' },
  { value: 'female', label: '女' },
  { value: 'secret', label: '保密' }
]
const presets = [100, 150, 200, 300]
const avatarText = computed(() => form.value.nickname.trim().slice(0, 1) || '动')

async function submit() {
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

  loading.value = true
  try {
    await updateProfile({
      nickname,
      gender: form.value.gender,
      weight,
      weeklyGoal
    })
    await completeOnboarding()
    toast('设置完成')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    router.replace(redirect || '/')
  } catch (error) {
    errorText.value = errorMessage(error, '保存失败，请稍后再试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page page--plain onboarding">
    <header class="nav-large">
      <div>
        <p class="eyebrow">欢迎加入</p>
        <h1>先认识一下你</h1>
      </div>
    </header>

    <p class="intro">这些信息用于计算卡路里和每周目标，之后可以在「我的」里修改。</p>

    <img class="onboarding-art" :src="onboardingArt" alt="" aria-hidden="true" />

    <div class="card profile-preview">
      <div class="avatar profile-avatar">{{ avatarText }}</div>
      <div class="profile-text">
        <strong>{{ form.nickname.trim() || '你的昵称' }}</strong>
        <span>这个昵称会出现在排行榜上</span>
      </div>
    </div>

    <div class="form-card">
      <div class="form-row">
        <span class="form-row__label">昵称</span>
        <input
          v-model="form.nickname"
          type="text"
          maxlength="12"
          placeholder="1–12 位中英文或数字"
        />
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

    <div class="goal-pills">
      <button
        v-for="preset in presets"
        :key="preset"
        type="button"
        :class="{ active: Number(form.weeklyGoal) === preset }"
        @click="form.weeklyGoal = preset"
      >
        {{ preset }} 分钟
      </button>
    </div>

    <p v-if="errorText" class="form-error">{{ errorText }}</p>

    <button class="btn-primary save-btn" type="button" :disabled="loading" @click="submit">
      <span>{{ loading ? '保存中…' : '开始打卡' }}</span>
      <ArrowRight :size="18" />
    </button>
  </div>
</template>

<style scoped>
.onboarding {
  padding-top: 0;
}

.intro {
  margin: 0 4px 16px;
  font-size: 14px;
  line-height: 22px;
  color: var(--text-2);
}

.onboarding-art {
  display: block;
  width: 100%;
  height: auto;
  margin: 0 0 16px;
  border-radius: 22px;
  box-shadow: 0 16px 36px rgba(47, 107, 255, 0.16);
}

.profile-preview {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  margin-bottom: 14px;
}

.profile-avatar {
  width: 56px;
  height: 56px;
  font-size: 22px;
  background: var(--blue);
  color: #fff;
}

.profile-text {
  flex: 1;
  min-width: 0;
}

.profile-text strong {
  display: block;
  font-size: 17px;
  line-height: 24px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-text span {
  display: block;
  margin-top: 3px;
  font-size: 12px;
  color: var(--muted);
}

.goal-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding: 0 2px;
}

.goal-pills button {
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-2);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}

.goal-pills button.active {
  border-color: var(--blue);
  background: var(--blue-soft);
  color: var(--blue);
  font-weight: 600;
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

.save-btn {
  margin-top: 24px;
}
</style>
