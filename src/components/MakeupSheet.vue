<script setup>
import { computed, ref, watch } from 'vue'
import { Check, Ticket } from 'lucide-vue-next'
import { errorMessage, useMakeupCard } from '../utils/api'
import { todayShanghai } from '../utils/sport'
import { toast } from '../utils/ui'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  status: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'done'])

const local = ref({ remaining: 0, dates: [], checked: [] })
const selected = ref('')
const saving = ref(false)
const errorText = ref('')

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      local.value = {
        remaining: props.status?.remaining || 0,
        dates: props.status?.dates || [],
        checked: props.status?.checked || []
      }
      selected.value = ''
      errorText.value = ''
    }
  }
)

const days = computed(() => {
  const checked = new Set(local.value.checked)
  const madeup = new Set(local.value.dates)
  const today = new Date(`${todayShanghai()}T00:00:00Z`)
  const weekday = '日一二三四五六'
  const result = []
  for (let offset = 1; offset <= 7; offset++) {
    const date = new Date(today)
    date.setUTCDate(today.getUTCDate() - offset)
    const iso = date.toISOString().slice(0, 10)
    result.push({
      date: iso,
      month: date.getUTCMonth() + 1,
      day: date.getUTCDate(),
      weekday: weekday[date.getUTCDay()],
      state: madeup.has(iso) ? 'madeup' : checked.has(iso) ? 'done' : 'free'
    })
  }
  return result
})

const canSubmit = computed(() => Boolean(selected.value) && !saving.value)

function close() {
  emit('update:modelValue', false)
}

function pick(day) {
  if (day.state !== 'free') return
  selected.value = selected.value === day.date ? '' : day.date
  errorText.value = ''
}

async function submit() {
  if (!canSubmit.value) {
    if (!selected.value) errorText.value = '请先选择要补签的日期'
    return
  }
  saving.value = true
  errorText.value = ''
  try {
    const result = await useMakeupCard(selected.value)
    local.value = {
      remaining: result.remaining || 0,
      dates: result.dates || [],
      checked: result.checked || []
    }
    const [, month, day] = selected.value.split('-')
    toast(`已补签 ${Number(month)} 月 ${Number(day)} 日`)
    emit('done', result)
    close()
  } catch (error) {
    errorText.value = errorMessage(error, '补签失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <transition name="ui">
    <div v-if="modelValue" class="makeup-overlay" @click.self="close">
      <div class="makeup-sheet" role="dialog" aria-modal="true" aria-label="使用补签卡">
        <span class="makeup-sheet__handle"></span>
        <h3>
          <Ticket :size="16" />
          补签卡
        </h3>
        <p class="makeup-sheet__sub">
          每月 2 张，可补最近 7 天。补签只计入连续天数，不计时长和排行。
        </p>

        <div class="makeup-sheet__quota">
          <strong>{{ local.remaining }}</strong>
          <span>张可用</span>
        </div>

        <div class="makeup-days">
          <button
            v-for="day in days"
            :key="day.date"
            type="button"
            class="makeup-day"
            :class="[`is-${day.state}`, { selected: selected === day.date }]"
            :disabled="day.state !== 'free'"
            @click="pick(day)"
          >
            <span class="makeup-day__week">{{ day.weekday }}</span>
            <span class="makeup-day__date">{{ day.day }}</span>
            <Check v-if="day.state === 'done'" :size="11" class="makeup-day__mark" />
            <span v-else-if="day.state === 'madeup'" class="makeup-day__mark">补</span>
          </button>
        </div>

        <p class="makeup-sheet__legend">已打卡与已补签的日期不可重复选择</p>
        <p v-if="errorText" class="makeup-sheet__error">{{ errorText }}</p>

        <div class="makeup-sheet__actions">
          <button type="button" class="makeup-sheet__cancel" @click="close">取消</button>
          <button
            type="button"
            class="btn-primary"
            :disabled="!canSubmit"
            @click="submit"
          >
            {{ saving ? '补签中…' : '确认补签' }}
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.makeup-overlay {
  position: fixed;
  inset: 0;
  z-index: 210;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(15, 23, 42, 0.45);
}

.makeup-sheet {
  width: 100%;
  max-width: 480px;
  border-radius: 24px 24px 0 0;
  background: var(--surface);
  padding: 12px 20px calc(20px + env(safe-area-inset-bottom));
  box-shadow: 0 -18px 50px rgba(15, 23, 42, 0.22);
}

.makeup-sheet__handle {
  display: block;
  width: 40px;
  height: 4px;
  margin: 0 auto 14px;
  border-radius: 999px;
  background: var(--line-2);
}

.makeup-sheet h3 {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--text);
}

.makeup-sheet__sub {
  margin: 6px 0 0;
  font-size: 11px;
  line-height: 16px;
  color: var(--muted);
}

.makeup-sheet__quota {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 14px;
}

.makeup-sheet__quota strong {
  font-size: 30px;
  line-height: 34px;
  font-weight: 700;
  color: var(--blue);
  font-variant-numeric: tabular-nums;
}

.makeup-sheet__quota span {
  font-size: 12px;
  color: var(--muted);
}

.makeup-days {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 6px;
  margin-top: 14px;
}

.makeup-day {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 0 9px;
  border: 1px solid var(--line-2);
  border-radius: 12px;
  background: var(--surface-2);
  color: var(--text-2);
  cursor: pointer;
}

.makeup-day__week {
  font-size: 10px;
  color: var(--muted);
}

.makeup-day__date {
  font-size: 14px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.makeup-day__mark {
  position: absolute;
  top: 3px;
  right: 4px;
  font-size: 9px;
  line-height: 1;
  color: var(--green);
}

.makeup-day.is-done,
.makeup-day.is-madeup {
  opacity: 0.55;
  cursor: not-allowed;
}

.makeup-day.is-free.selected {
  border-color: var(--blue);
  background: var(--blue-soft);
  color: var(--blue);
}

.makeup-sheet__legend {
  margin: 12px 0 0;
  font-size: 11px;
  color: var(--muted);
}

.makeup-sheet__error {
  margin: 6px 0 0;
  font-size: 11px;
  color: var(--red);
}

.makeup-sheet__actions {
  display: grid;
  grid-template-columns: 1fr 1.6fr;
  gap: 10px;
  margin-top: 16px;
}

.makeup-sheet__cancel {
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
