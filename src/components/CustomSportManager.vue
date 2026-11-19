<script setup>
import { computed, ref, watch } from 'vue'
import { Pencil, Plus, Sparkles, Trash2, X } from 'lucide-vue-next'
import {
  createCustomSport,
  deleteCustomSport,
  errorMessage,
  getCustomSports,
  updateCustomSport
} from '../utils/api'
import { BUILTIN_NAMES, INTENSITY_PRESETS } from '../utils/sport'
import { toast, confirmDialog } from '../utils/ui'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'changed'])

const NAME_RE = /^[\u4e00-\u9fa5A-Za-z0-9_-]{1,12}$/

const loading = ref(false)
const saving = ref(false)
const sports = ref([])
const name = ref('')
const met = ref(INTENSITY_PRESETS[1].met)
const editingId = ref(null)

const nameError = computed(() => {
  const value = name.value.trim()
  if (!value) return ''
  if (BUILTIN_NAMES.has(value)) return '该名称是内置运动'
  if (sports.value.some((sport) => sport.name === value && sport.id !== editingId.value)) {
    return '已经添加过这个运动'
  }
  if (!NAME_RE.test(value)) return '1–12 位中英文、数字、下划线或连字符'
  return ''
})

const canSubmit = computed(
  () => Boolean(name.value.trim()) && !nameError.value && !saving.value
)

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      resetForm()
      load()
    }
  }
)

function resetForm() {
  editingId.value = null
  name.value = ''
  met.value = INTENSITY_PRESETS[1].met
}

async function load() {
  loading.value = true
  try {
    sports.value = await getCustomSports(true)
  } catch (error) {
    toast(errorMessage(error, '自定义运动加载失败'))
  } finally {
    loading.value = false
  }
}

function startEdit(sport) {
  editingId.value = sport.id
  name.value = sport.name
  met.value = Number(sport.met)
}

async function submit() {
  const value = name.value.trim()
  if (!value || nameError.value) return

  saving.value = true
  try {
    if (editingId.value) {
      await updateCustomSport({ id: editingId.value, name: value, met: met.value })
    } else {
      await createCustomSport({ name: value, met: met.value })
    }
    const list = await getCustomSports(true)
    sports.value = list
    emit('changed', list)
    toast(editingId.value ? '修改已保存' : '自定义运动已添加')
    resetForm()
  } catch (error) {
    toast(errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function remove(sport) {
  try {
    await confirmDialog({
      title: '删除自定义运动',
      message: `确定删除「${sport.name}」吗？已产生的打卡记录不会受影响。`,
      confirmText: '删除',
      cancelText: '取消',
      danger: true
    })
  } catch {
    return
  }

  try {
    await deleteCustomSport(sport.id)
    const list = await getCustomSports(true)
    sports.value = list
    emit('changed', list)
    if (editingId.value === sport.id) resetForm()
    toast('已删除')
  } catch (error) {
    toast(errorMessage(error))
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <transition name="sheet">
    <div v-if="modelValue" class="sport-manager" @click.self="close">
      <div class="sport-manager__panel" role="dialog" aria-modal="true">
        <header class="sport-manager__head">
          <div>
            <h2>自定义运动</h2>
            <p>添加自己的运动项目，打卡时可选择</p>
          </div>
          <button type="button" class="sport-manager__close" aria-label="关闭" @click="close">
            <X :size="18" />
          </button>
        </header>

        <div class="sport-manager__body">
          <label class="field">
            <span>{{ editingId ? '编辑名称' : '运动名称' }}</span>
            <input
              v-model="name"
              type="text"
              maxlength="12"
              placeholder="例如：太极、瑜伽、爬楼梯"
              @keyup.enter="submit"
            />
            <em v-if="nameError">{{ nameError }}</em>
          </label>

          <p v-if="editingId" class="editing-tip">
            正在编辑「{{ name || '未命名' }}」
            <button type="button" @click="resetForm">取消编辑</button>
          </p>

          <div class="presets">
            <span class="presets__label">运动强度</span>
            <div class="presets__grid">
              <button
                v-for="preset in INTENSITY_PRESETS"
                :key="preset.label"
                type="button"
                :class="{ active: met === preset.met }"
                @click="met = preset.met"
              >
                <strong>{{ preset.label }}</strong>
                <span>{{ preset.hint }}</span>
                <small>MET {{ preset.met.toFixed(1) }}</small>
              </button>
            </div>
          </div>

          <button type="button" class="btn-primary add-btn" :disabled="!canSubmit" @click="submit">
            <Plus :size="16" />
            {{ saving ? '保存中…' : editingId ? '保存修改' : '添加运动' }}
          </button>

          <div class="sport-list">
            <div class="sport-list__title">
              <span>我添加的运动</span>
              <small>最多 20 个</small>
            </div>
            <p v-if="loading" class="sport-list__empty">加载中…</p>
            <p v-else-if="!sports.length" class="sport-list__empty">
              还没有自定义运动，先添加一个吧
            </p>
            <ul v-else>
              <li v-for="sport in sports" :key="sport.id">
                <span class="sport-list__icon"><Sparkles :size="15" /></span>
                <span class="sport-list__name">{{ sport.name }}</span>
                <span class="sport-list__met">MET {{ Number(sport.met).toFixed(1) }}</span>
                <button
                  type="button"
                  class="sport-list__edit"
                  :aria-label="`编辑 ${sport.name}`"
                  @click="startEdit(sport)"
                >
                  <Pencil :size="15" />
                </button>
                <button
                  type="button"
                  class="sport-list__del"
                  :aria-label="`删除 ${sport.name}`"
                  @click="remove(sport)"
                >
                  <Trash2 :size="15" />
                </button>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.sport-manager {
  position: fixed;
  inset: 0;
  z-index: 260;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(17, 24, 39, 0.4);
  backdrop-filter: blur(3px);
}

.sport-manager__panel {
  width: 100%;
  max-width: 380px;
  max-height: min(86vh, 640px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 24px;
  background: var(--surface);
  box-shadow: 0 24px 70px rgba(17, 24, 39, 0.25);
}

.sport-manager__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 18px 12px;
}

.sport-manager__head h2 {
  margin: 0;
  font-size: 18px;
  line-height: 24px;
  font-weight: 700;
}

.sport-manager__head p {
  margin: 3px 0 0;
  font-size: 12px;
  line-height: 17px;
  color: var(--muted);
}

.sport-manager__close {
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: var(--surface-2);
  color: var(--text-2);
  cursor: pointer;
}

.sport-manager__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 18px 20px;
  -webkit-overflow-scrolling: touch;
}

.field {
  display: block;
}

.field > span {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-2);
}

.field input {
  width: 100%;
  height: 44px;
  padding: 0 14px;
  border: 1px solid var(--line-2);
  border-radius: 12px;
  background: var(--surface-2);
  font-size: 14px;
  color: var(--text);
  outline: none;
  box-sizing: border-box;
}

.field input:focus {
  border-color: var(--blue);
  background: #fff;
}

.field em {
  display: block;
  margin-top: 5px;
  font-size: 11px;
  font-style: normal;
  color: var(--red);
}

.presets {
  margin-top: 14px;
}

.presets__label {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-2);
}

.presets__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.presets__grid button {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.14s ease, background 0.14s ease;
}

.presets__grid button.active {
  border-color: var(--blue);
  background: var(--blue-soft);
  box-shadow: inset 0 0 0 1px var(--blue);
}

.presets__grid strong {
  font-size: 13px;
  line-height: 18px;
  color: var(--text);
}

.presets__grid span {
  font-size: 11px;
  line-height: 15px;
  color: var(--muted);
}

.presets__grid small {
  font-size: 10px;
  line-height: 14px;
  color: var(--blue);
}

.add-btn {
  width: 100%;
  margin-top: 16px;
}

.sport-list {
  margin-top: 20px;
}

.sport-list__title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.sport-list__title small {
  font-size: 11px;
  font-weight: 400;
  color: var(--muted);
}

.sport-list__empty {
  margin: 0;
  padding: 18px 0;
  text-align: center;
  font-size: 12px;
  color: var(--muted);
}

.sport-list ul {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.sport-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface-2);
}

.sport-list__icon {
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: var(--violet-soft);
  color: var(--violet);
}

.sport-list__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.sport-list__met {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--muted);
}

.sport-list__del {
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}

.sport-list__edit {
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}

.sport-list__edit:active {
  background: var(--blue-soft);
  color: var(--blue);
}

.editing-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 8px 0 0;
  padding: 8px 10px;
  border-radius: 10px;
  background: var(--blue-soft);
  font-size: 12px;
  color: var(--blue);
}

.editing-tip button {
  flex: 0 0 auto;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--blue);
  font-size: 12px;
  font-weight: 600;
  text-decoration: underline;
  cursor: pointer;
}

.sport-list__del:active {
  background: var(--red-soft);
  color: var(--red);
}

.sheet-enter-active,
.sheet-leave-active {
  transition: opacity 0.18s ease;
}

.sheet-enter-active .sport-manager__panel,
.sheet-leave-active .sport-manager__panel {
  transition: transform 0.2s ease;
}

.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
}

.sheet-enter-from .sport-manager__panel,
.sheet-leave-to .sport-manager__panel {
  transform: translateY(14px) scale(0.97);
}
</style>
