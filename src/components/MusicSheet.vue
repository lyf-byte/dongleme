<script setup>
import { Music } from 'lucide-vue-next'
import { MUSIC_OPTIONS } from '../utils/musicApp'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  source: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'picked'])

function pick(value) {
  emit('picked', value)
  emit('update:modelValue', false)
}

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <transition name="ui">
    <div v-if="modelValue" class="music-overlay" @click.self="close">
      <div class="music-sheet" role="dialog" aria-modal="true" aria-label="选择音乐来源">
        <span class="music-sheet__handle"></span>
        <div class="music-sheet__head">
          <button type="button" class="music-sheet__cancel" @click="close">取消</button>
          <h3>选择音乐来源</h3>
          <button type="button" class="music-sheet__ok" @click="close">确认</button>
        </div>

        <div class="music-sheet__list">
          <button
            v-for="option in MUSIC_OPTIONS"
            :key="option.value || 'none'"
            type="button"
            class="music-row"
            :class="{ active: (source || '') === option.value }"
            @click="pick(option.value)"
          >
            <span class="music-row__icon">
              <Music v-if="option.value" :size="18" />
              <svg v-else viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <path d="M9 9l10 10M9 5l6 6M19 5l-6 6M5 5l14 14" opacity="0" />
                <path d="M4 4l16 16" />
                <path d="M9 9c-1.5 1.5-2 3.5-1 5M15 15c1.5-1.5 2-3.5 1-5" opacity="0" />
              </svg>
            </span>
            <span class="music-row__text">
              <strong>{{ option.name }}</strong>
              <small>{{ option.desc }}</small>
            </span>
            <span class="music-row__radio" :class="{ on: (source || '') === option.value }"></span>
          </button>
        </div>

        <p class="music-sheet__tip">跳转音乐 App 期间页面会退到后台，回来后记录继续。</p>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.music-overlay {
  position: fixed;
  inset: 0;
  z-index: 220;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(15, 23, 42, 0.45);
}

.music-sheet {
  width: 100%;
  max-width: 480px;
  border-radius: 24px 24px 0 0;
  background: var(--surface);
  padding: 12px 20px calc(18px + env(safe-area-inset-bottom));
  box-shadow: 0 -18px 50px rgba(15, 23, 42, 0.22);
}

.music-sheet__handle {
  display: block;
  width: 40px;
  height: 4px;
  margin: 0 auto 12px;
  border-radius: 999px;
  background: var(--line-2);
}

.music-sheet__head {
  display: grid;
  grid-template-columns: 40px 1fr 40px;
  align-items: center;
  margin-bottom: 8px;
}

.music-sheet__head h3 {
  margin: 0;
  text-align: center;
  font-size: 15px;
  font-weight: 500;
  color: var(--text);
}

.music-sheet__cancel {
  border: 0;
  background: transparent;
  padding: 0;
  text-align: left;
  color: var(--muted);
  font-size: 13px;
  cursor: pointer;
}

.music-sheet__ok {
  border: 0;
  background: transparent;
  padding: 0;
  text-align: right;
  color: var(--blue);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.music-sheet__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.music-row {
  display: grid;
  grid-template-columns: 44px 1fr 18px;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--surface-2);
  padding: 12px;
  text-align: left;
  cursor: pointer;
}

.music-row.active {
  border-color: rgba(47, 107, 255, 0.4);
  background: var(--blue-soft);
}

.music-row__icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: #fff;
  color: var(--blue);
  box-shadow: inset 0 0 0 1px rgba(17, 24, 39, 0.05);
}

.music-row__text {
  min-width: 0;
}

.music-row__text strong {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--text);
}

.music-row__text small {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--muted);
}

.music-row__radio {
  width: 18px;
  height: 18px;
  border: 1.5px solid var(--line-2);
  border-radius: 50%;
}

.music-row__radio.on {
  border-color: var(--blue);
  background: var(--blue);
  box-shadow: inset 0 0 0 3px #fff;
}

.music-sheet__tip {
  margin: 12px 0 0;
  text-align: center;
  font-size: 11px;
  line-height: 16px;
  color: var(--muted);
}
</style>
