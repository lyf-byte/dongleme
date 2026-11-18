<script setup>
import { computed, onMounted, ref } from 'vue'
import { Copy, RefreshCw, Sparkles } from 'lucide-vue-next'
import { generateSummary, errorMessage } from '../utils/api'
import { toast } from '../utils/ui'

const loading = ref(true)
const generating = ref(false)
const content = ref('')
const weekStart = ref('')
const cached = ref(false)
const degraded = ref(false)
const errorText = ref('')

const weekLabel = computed(() => {
  if (!weekStart.value) return ''
  const start = new Date(`${weekStart.value}T00:00:00Z`)
  const end = new Date(start)
  end.setUTCDate(end.getUTCDate() + 6)
  const format = (date) => `${date.getUTCMonth() + 1}月${date.getUTCDate()}日`
  return `${format(start)} - ${format(end)}`
})

async function peek() {
  loading.value = true
  try {
    const result = await generateSummary({ peek: true })
    weekStart.value = result.weekStart || ''
    if (result.content) {
      content.value = result.content
      cached.value = result.cached
    }
  } catch {
    /* 首次进入不打断用户 */
  } finally {
    loading.value = false
  }
}

async function generate(force = false) {
  if (generating.value) return
  generating.value = true
  errorText.value = ''
  try {
    const result = await generateSummary(force ? { force: true } : {})
    weekStart.value = result.weekStart || weekStart.value
    content.value = result.content || ''
    cached.value = Boolean(result.cached)
    degraded.value = Boolean(result.degraded)
    if (!result.content) errorText.value = '本周还没有可总结的数据，先去打卡吧'
  } catch (error) {
    errorText.value = errorMessage(error, '生成失败，请稍后再试')
  } finally {
    generating.value = false
  }
}

async function copy() {
  try {
    await navigator.clipboard.writeText(content.value)
    toast('已复制')
  } catch {
    toast('复制失败，请长按选择文本')
  }
}

onMounted(peek)
</script>

<template>
  <div class="page summary">
    <header class="nav-large">
      <div>
        <p class="eyebrow">{{ weekLabel || '每周更新' }}</p>
        <h1>本周小结</h1>
      </div>
      <div class="nav-large__action"><Sparkles :size="20" /></div>
    </header>

    <template v-if="loading">
      <div class="card loading-card">
        <div class="loading-line"><span></span></div>
      </div>
    </template>

    <template v-else>
      <section v-if="content" class="card report">
        <div class="report-head">
          <span class="report-icon"><Sparkles :size="18" /></span>
          <div class="report-title">
            <strong>AI 教练</strong>
            <span>{{ weekLabel }} · 每周小结</span>
          </div>
          <span v-if="cached" class="chip chip--blue">已缓存</span>
        </div>

        <p class="report-text">{{ content }}</p>

        <p v-if="degraded" class="report-note">
          AI 服务暂时不可用，以上为根据数据生成的兜底建议。
        </p>

        <div class="report-actions">
          <button type="button" class="btn-primary btn-sm btn-secondary" @click="copy">
            <Copy :size="16" />
            复制
          </button>
          <button
            type="button"
            class="btn-primary btn-sm btn-ghost"
            :disabled="generating"
            @click="generate(true)"
          >
            <RefreshCw :size="16" />
            {{ generating ? '生成中' : '重新生成' }}
          </button>
        </div>
      </section>

      <div v-else class="card empty-state report-empty">
        <span class="empty-state__icon"><Sparkles :size="24" /></span>
        <h3>还没有本周小结</h3>
        <p>汇总你本周的打卡数据，生成一段专属的教练建议。</p>
        <button
          type="button"
          class="btn-primary btn-sm"
          :disabled="generating"
          @click="generate(false)"
        >
          {{ generating ? '生成中…' : '生成本周小结' }}
        </button>
      </div>

      <p v-if="errorText" class="form-error">{{ errorText }}</p>
      <p class="foot">AI 生成内容仅作运动参考，不构成医疗建议。</p>
    </template>
  </div>
</template>

<style scoped>
.summary {
  padding-top: 0;
}

.loading-card {
  padding: 18px;
  margin-top: 4px;
}

.report {
  padding: 16px;
  border-radius: 20px;
}

.report-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.report-icon {
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: var(--blue-soft);
  color: var(--blue);
}

.report-title {
  flex: 1;
  min-width: 0;
}

.report-title strong {
  display: block;
  font-size: 15px;
  line-height: 21px;
  font-weight: 600;
}

.report-title span {
  display: block;
  margin-top: 1px;
  font-size: 11px;
  color: var(--muted);
}

.report-text {
  margin: 14px 0 0;
  font-size: 15px;
  line-height: 26px;
  color: var(--text);
  white-space: pre-wrap;
}

.report-note {
  margin: 12px 0 0;
  padding-top: 10px;
  border-top: 1px dashed var(--line-2);
  font-size: 12px;
  line-height: 18px;
  color: var(--muted);
}

.report-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.report-empty .btn-primary {
  margin-top: 16px;
}

.foot {
  margin: 20px 0 0;
  text-align: center;
  font-size: 11px;
  line-height: 17px;
  color: var(--muted);
}
</style>
