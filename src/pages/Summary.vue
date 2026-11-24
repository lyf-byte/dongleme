<script setup>
import { computed, onMounted, ref } from 'vue'
import { Copy, Download, RefreshCw, Sparkles } from 'lucide-vue-next'
import { generateSummary, getProfile, updateProfile, errorMessage } from '../utils/api'
import { toast } from '../utils/ui'
import { saveBlob } from '../utils/native'
import emptySummary from '../assets/empty-summary.svg'
import posterSummary from '../assets/poster-summary.jpg'
import {
  POSTER_FONT,
  canvasToBlob,
  drawBandChrome,
  loadPosterImage,
  wrapLines
} from '../utils/poster'

const loading = ref(true)
const generating = ref(false)
const content = ref('')
const weekStart = ref('')
const cached = ref(false)
const degraded = ref(false)
const stale = ref(false)
const planGoal = ref(0)
const profile = ref(null)
const applying = ref(false)
const errorText = ref('')

const isCurrentGoal = computed(
  () => Boolean(profile.value && planGoal.value && profile.value.weeklyGoal === planGoal.value)
)

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
    planGoal.value = result.planGoal || 0
    if (result.content) {
      content.value = result.content
      cached.value = result.cached
      stale.value = Boolean(result.stale)
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
    planGoal.value = result.planGoal || 0
    cached.value = Boolean(result.cached)
    degraded.value = Boolean(result.degraded)
    stale.value = false
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

// 底图 1024×1024，下方接一块随文字长度自适应的信息面板
const POSTER_WIDTH = 1024
const POSTER_IMAGE_HEIGHT = 1024
const SUMMARY_LINE_HEIGHT = 62
const SUMMARY_MAX_LINES = 14

async function savePoster() {
  if (!content.value) return

  let image
  try {
    image = await loadPosterImage(posterSummary)
  } catch {
    toast('海报底图加载失败，请重试')
    return
  }

  const canvas = document.createElement('canvas')
  const bandTop = POSTER_IMAGE_HEIGHT
  canvas.width = POSTER_WIDTH
  // 高度依赖文字行数，先占位再改尺寸；改尺寸会重置绘图状态，因此必须在绘制前算好
  canvas.height = bandTop + 10
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    toast('当前浏览器不支持生成海报')
    return
  }

  ctx.font = `40px ${POSTER_FONT}`
  const lines = wrapLines(
    ctx,
    content.value.replace(/\s+/g, ' ').trim(),
    POSTER_WIDTH - 128
  ).slice(0, SUMMARY_MAX_LINES)

  const textTop = bandTop + 206
  const footerY = textTop + Math.max(0, lines.length - 1) * SUMMARY_LINE_HEIGHT + 76
  const bandHeight = footerY - bandTop + 56
  canvas.height = bandTop + bandHeight

  ctx.drawImage(image, 0, 0, POSTER_WIDTH, POSTER_IMAGE_HEIGHT)
  drawBandChrome(ctx, POSTER_WIDTH, bandTop, bandHeight)

  ctx.fillStyle = 'rgba(255,255,255,0.5)'
  ctx.font = `24px ${POSTER_FONT}`
  ctx.fillText('动了么 · 运动周小结', 64, bandTop + 62)

  ctx.fillStyle = '#ffffff'
  ctx.font = `bold 44px ${POSTER_FONT}`
  ctx.fillText(weekLabel.value || '本周小结', 64, bandTop + 124)

  ctx.fillStyle = 'rgba(255,255,255,0.14)'
  ctx.fillRect(64, bandTop + 152, POSTER_WIDTH - 128, 1)

  ctx.fillStyle = 'rgba(255,255,255,0.92)'
  ctx.font = `40px ${POSTER_FONT}`
  lines.forEach((line, index) => {
    ctx.fillText(line, 64, textTop + index * SUMMARY_LINE_HEIGHT)
  })

  ctx.fillStyle = 'rgba(255,255,255,0.42)'
  ctx.font = `24px ${POSTER_FONT}`
  ctx.fillText('每天动一点，连续看得见', 64, footerY)

  try {
    const blob = await canvasToBlob(canvas)
    const result = await saveBlob(blob, `dongleme-${weekStart.value || 'summary'}.png`)
    toast(result === 'ok' ? '海报已保存' : '保存失败，请重试')
  } catch {
    toast('海报生成失败')
  }
}

async function applyPlan() {
  if (!planGoal.value || applying.value) return
  applying.value = true
  try {
    if (!profile.value) {
      profile.value = await getProfile()
    }
    await updateProfile({
      nickname: profile.value.nickname,
      gender: profile.value.gender || 'secret',
      weight: profile.value.weight,
      weeklyGoal: planGoal.value
    })
    profile.value.weeklyGoal = planGoal.value
    toast(`本周目标已更新为 ${planGoal.value} 分钟`)
  } catch (error) {
    toast(errorMessage(error, '设置失败'))
  } finally {
    applying.value = false
  }
}

onMounted(() => {
  peek()
  getProfile()
    .then((data) => {
      profile.value = data
    })
    .catch(() => {
      /* 资料加载失败时按钮仍可点，点击时会重试 */
    })
})
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

        <div v-if="planGoal" class="plan-card">
          <div class="plan-info">
            <span>AI 下周计划</span>
            <strong>{{ planGoal }} 分钟 / 周</strong>
          </div>
          <button
            type="button"
            class="btn-primary btn-sm"
            :disabled="applying || isCurrentGoal"
            @click="applyPlan"
          >
            {{ isCurrentGoal ? '已是本周目标' : applying ? '设置中…' : '设为本周目标' }}
          </button>
        </div>

        <p v-if="stale" class="report-note">
          本周有新打卡记录，建议重新生成小结。
        </p>

        <p v-if="degraded" class="report-note">
          AI 服务暂时不可用，以上为根据数据生成的兜底建议。
        </p>

        <div class="report-actions">
          <button type="button" class="btn-primary btn-sm btn-secondary" @click="copy">
            <Copy :size="16" />
            复制
          </button>
          <button type="button" class="btn-primary btn-sm btn-secondary" @click="savePoster">
            <Download :size="16" />
            保存海报
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
        <img class="empty-state__art" :src="emptySummary" alt="" aria-hidden="true" />
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

.plan-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px dashed var(--blue);
  border-radius: 14px;
  background: var(--blue-soft);
}

.plan-info span {
  display: block;
  font-size: 11px;
  color: var(--muted);
}

.plan-info strong {
  display: block;
  margin-top: 2px;
  font-size: 15px;
  font-weight: 700;
  color: var(--blue);
}

.plan-card .btn-primary {
  flex: 0 0 auto;
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
  margin: 16px auto 0;
}

.foot {
  margin: 20px 0 0;
  text-align: center;
  font-size: 11px;
  line-height: 17px;
  color: var(--muted);
}
</style>
