<script setup>
import { computed, ref, watch } from 'vue'
import { updateProfile, errorMessage } from '../utils/api'
import { toast } from '../utils/ui'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  goal: { type: Number, default: 150 },
  profile: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const PRESETS = [60, 90, 150, 210, 300]
const MIN_GOAL = 15
const MAX_GOAL = 2000

const draft = ref(props.goal)
const saving = ref(false)
const errorText = ref('')

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      draft.value = props.goal || 150
      errorText.value = ''
    }
  }
)

const goalText = computed(() => String(draft.value))

const hint = computed(() => {
  if (draft.value <= 70) return '轻量档：每天动 10 分钟就能达成'
  if (draft.value <= 120) return '稳健档：隔天一次中等强度刚刚好'
  if (draft.value <= 240) return '标准档：接近世卫建议的每周活动量'
  return '挑战档：坚持住，这可是进阶选手的水平'
})

function clamp(value) {
  return Math.max(MIN_GOAL, Math.min(MAX_GOAL, Math.round(value / 5) * 5))
}

function step(delta) {
  draft.value = clamp((Number(draft.value) || MIN_GOAL) + delta)
}

function pickPreset(value) {
  draft.value = value
}

function close() {
  emit('update:modelValue', false)
}

async function save() {
  if (saving.value) return
  const weeklyGoal = Number(draft.value)
  if (!Number.isInteger(weeklyGoal) || weeklyGoal < MIN_GOAL || weeklyGoal > MAX_GOAL) {
    errorText.value = `目标需为 ${MIN_GOAL}–${MAX_GOAL} 的整数`
    return
  }
  saving.value = true
  errorText.value = ''
  try {
    /* 后端 ProfileUpdateRequest 要求四个字段一起提交 */
    await updateProfile({
      nickname: props.profile?.nickname,
      gender: props.profile?.gender || 'secret',
      weight: props.profile?.weight,
      weeklyGoal
    })
    toast('周目标已更新')
    emit('saved', weeklyGoal)
    close()
  } catch (error) {
    errorText.value = errorMessage(error, '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <transition name="ui">
    <div v-if="modelValue" class="goal-overlay" @click.self="close">
      <div class="goal-sheet" role="dialog" aria-modal="true" aria-label="设置每周运动目标">
        <span class="goal-sheet__handle"></span>
        <h3>每周运动目标</h3>
        <p class="goal-sheet__sub">达成后进度环会转满，还能解锁「本周达标」勋章</p>

        <div class="goal-sheet__value">
          <button
            type="button"
            class="goal-sheet__step"
            aria-label="减少 5 分钟"
            @click="step(-5)"
          >
            −
          </button>
          <strong>{{ goalText }}</strong>
          <span>分钟 / 周</span>
          <button
            type="button"
            class="goal-sheet__step"
            aria-label="增加 5 分钟"
            @click="step(5)"
          >
            +
          </button>
        </div>

        <div class="goal-sheet__presets">
          <button
            v-for="preset in PRESETS"
            :key="preset"
            type="button"
            :class="{ active: draft === preset }"
            @click="pickPreset(preset)"
          >
            {{ preset }}
          </button>
        </div>

        <p class="goal-sheet__hint">{{ hint }}</p>
        <p v-if="errorText" class="goal-sheet__error">{{ errorText }}</p>

        <div class="goal-sheet__actions">
          <button type="button" class="goal-sheet__cancel" @click="close">取消</button>
          <button type="button" class="btn-primary" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存目标' }}
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.goal-overlay {
  position: fixed;
  inset: 0;
  z-index: 210;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(15, 23, 42, 0.45);
}

.goal-sheet {
  width: 100%;
  max-width: 480px;
  border-radius: 24px 24px 0 0;
  background: var(--surface);
  padding: 12px 20px calc(20px + env(safe-area-inset-bottom));
  box-shadow: 0 -18px 50px rgba(15, 23, 42, 0.22);
}

.goal-sheet__handle {
  display: block;
  width: 40px;
  height: 4px;
  margin: 0 auto 14px;
  border-radius: 999px;
  background: var(--line-2);
}

.goal-sheet h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--text);
}

.goal-sheet__sub {
  margin: 4px 0 0;
  font-size: 11px;
  line-height: 16px;
  color: var(--muted);
}

.goal-sheet__value {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 8px;
  margin-top: 18px;
}

.goal-sheet__value strong {
  font-size: 40px;
  line-height: 44px;
  font-weight: 700;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
  color: var(--text);
}

.goal-sheet__value span {
  font-size: 12px;
  color: var(--muted);
}

.goal-sheet__step {
  align-self: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--line-2);
  border-radius: 50%;
  background: var(--surface-2);
  color: var(--text);
  font-size: 17px;
  line-height: 1;
  cursor: pointer;
}

.goal-sheet__step:active {
  transform: scale(0.94);
}

.goal-sheet__presets {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;
}

.goal-sheet__presets button {
  min-width: 52px;
  height: 32px;
  border: 1px solid var(--line-2);
  border-radius: 999px;
  background: var(--surface-2);
  color: var(--text-2);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.goal-sheet__presets button.active {
  border-color: var(--blue);
  background: var(--blue-soft);
  color: var(--blue);
}

.goal-sheet__hint {
  margin: 14px 0 0;
  text-align: center;
  font-size: 11px;
  color: var(--muted);
}

.goal-sheet__error {
  margin: 8px 0 0;
  text-align: center;
  font-size: 11px;
  color: var(--red);
}

.goal-sheet__actions {
  display: grid;
  grid-template-columns: 1fr 1.6fr;
  gap: 10px;
  margin-top: 18px;
}

.goal-sheet__cancel {
  height: 46px;
  border: 1px solid var(--line-2);
  border-radius: 14px;
  background: var(--surface-2);
  color: var(--text-2);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
</style>
