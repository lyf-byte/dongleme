<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ChartBar } from 'lucide-vue-next'
import * as echarts from 'echarts/core'
import { BarChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getStats, errorMessage } from '../utils/api'
import { toast } from '../utils/ui'
import emptyStats from '../assets/empty-stats.svg'

echarts.use([BarChart, PieChart, GridComponent, TooltipComponent, CanvasRenderer])

const router = useRouter()

const ranges = [
  { value: '7d', label: '近 7 天' },
  { value: '30d', label: '近 30 天' },
  { value: 'month', label: '本月' }
]

const chartColors = ['#2f6bff', '#16a34a', '#f6b21b', '#f97316', '#8b5cf6', '#06b6d4']

const range = ref('7d')
const loading = ref(true)
const data = ref({ daily: [], types: {}, total: { count: 0, minutes: 0, calories: 0 } })
const barEl = ref(null)
const pieEl = ref(null)

let barChart = null
let pieChart = null
let loadSeq = 0

const total = computed(() => data.value.total || { count: 0, minutes: 0, calories: 0 })
const hasData = computed(() => total.value.count > 0)
const typeList = computed(() =>
  Object.entries(data.value.types || {})
    .map(([name, minutes]) => ({ name, minutes }))
    .sort((a, b) => b.minutes - a.minutes)
)
const rangeLabel = computed(() => ranges.find((item) => item.value === range.value)?.label || '')
const periodLabel = computed(() => {
  if (!data.value.start || !data.value.end) return ''
  return `${data.value.start.slice(5).replace('-', '.')} - ${data.value.end
    .slice(5)
    .replace('-', '.')}`
})

function setRange(value) {
  if (range.value === value) return
  range.value = value
  load()
}

function disposeCharts() {
  if (barChart) {
    barChart.dispose()
    barChart = null
  }
  if (pieChart) {
    pieChart.dispose()
    pieChart = null
  }
}

function renderCharts() {
  if (!hasData.value) return

  if (barEl.value) {
    barChart = echarts.init(barEl.value)
    const dates = data.value.daily.map((day) => day.date.slice(5).replace('-', '/'))
    const values = data.value.daily.map((day) => day.minutes)
    barChart.setOption({
      grid: { left: 2, right: 6, top: 16, bottom: 2, containLabel: true },
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#111827',
        borderWidth: 0,
        padding: [8, 10],
        textStyle: { color: '#fff', fontSize: 11 },
        formatter: (params) => `${params[0].axisValue}<br/>${params[0].value} 分钟`
      },
      xAxis: {
        type: 'category',
        data: dates,
        axisLine: { lineStyle: { color: '#e1e5eb' } },
        axisTick: { show: false },
        axisLabel: {
          color: '#6b7280',
          fontSize: 10,
          interval: range.value === '7d' ? 0 : 4
        }
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#f1f3f6', type: 'dashed' } },
        axisLabel: { color: '#6b7280', fontSize: 10 }
      },
      series: [
        {
          type: 'bar',
          data: values,
          barMaxWidth: 18,
          itemStyle: { color: '#2f6bff', borderRadius: [6, 6, 0, 0] },
          emphasis: { itemStyle: { color: '#1f5bf0' } }
        }
      ]
    })
  }

  if (pieEl.value && typeList.value.length) {
    pieChart = echarts.init(pieEl.value)
    pieChart.setOption({
      color: chartColors,
      tooltip: {
        trigger: 'item',
        backgroundColor: '#111827',
        borderWidth: 0,
        padding: [8, 10],
        textStyle: { color: '#fff', fontSize: 11 },
        formatter: '{b}<br/>{c} 分钟 · {d}%'
      },
      series: [
        {
          type: 'pie',
          radius: ['52%', '74%'],
          center: ['50%', '50%'],
          itemStyle: { borderColor: '#fff', borderWidth: 3 },
          label: { show: false },
          emphasis: { scaleSize: 4 },
          data: typeList.value.map((item) => ({ name: item.name, value: item.minutes }))
        }
      ]
    })
  }
}

async function load() {
  const seq = ++loadSeq
  loading.value = true
  try {
    const result = await getStats(range.value)
    if (seq !== loadSeq) return
    data.value = result
    loading.value = false
    disposeCharts()
    await nextTick()
    if (seq !== loadSeq) return
    renderCharts()
  } catch (error) {
    if (seq !== loadSeq) return
    disposeCharts()
    toast(errorMessage(error, '加载统计失败'))
    data.value = { daily: [], types: {}, total: { count: 0, minutes: 0, calories: 0 } }
    loading.value = false
  }
}

function onResize() {
  barChart?.resize()
  pieChart?.resize()
}

onMounted(() => {
  window.addEventListener('resize', onResize)
  load()
})

onUnmounted(() => {
  loadSeq++
  window.removeEventListener('resize', onResize)
  disposeCharts()
})
</script>

<template>
  <div class="page stats">
    <header class="nav-large">
      <div>
        <p class="eyebrow">{{ rangeLabel }} · {{ periodLabel }}</p>
        <h1>运动统计</h1>
      </div>
    </header>

    <div class="segmented stats-seg">
      <button
        v-for="item in ranges"
        :key="item.value"
        type="button"
        :class="{ active: range === item.value }"
        @click="setRange(item.value)"
      >
        {{ item.label }}
      </button>
    </div>

    <div class="card stats-summary">
      <div class="summary-item">
        <span>打卡次数</span>
        <strong>{{ total.count }}<em>次</em></strong>
      </div>
      <div class="summary-item">
        <span>运动时长</span>
        <strong>{{ total.minutes }}<em>分钟</em></strong>
      </div>
      <div class="summary-item">
        <span>消耗热量</span>
        <strong>{{ total.calories }}<em>千卡</em></strong>
      </div>
    </div>

    <template v-if="loading">
      <div class="card loading-card">
        <div class="loading-line"><span></span></div>
      </div>
    </template>

    <template v-else-if="hasData">
      <section class="card chart-card">
        <div class="chart-head">
          <h2>每日运动</h2>
          <span>分钟</span>
        </div>
        <div ref="barEl" class="chart chart-bar"></div>
      </section>

      <section class="card chart-card">
        <div class="chart-head">
          <h2>运动类型</h2>
          <span>分钟</span>
        </div>
        <div class="pie-wrap">
          <div ref="pieEl" class="chart chart-pie"></div>
          <div class="pie-center">
            <strong>{{ total.minutes }}</strong>
            <span>分钟</span>
          </div>
        </div>
        <div class="legend">
          <div v-for="(item, index) in typeList" :key="item.name" class="legend__row">
            <span
              class="legend__dot"
              :style="{ background: chartColors[index % chartColors.length] }"
            ></span>
            <span class="legend__name">{{ item.name }}</span>
            <span class="legend__value">{{ item.minutes }} 分钟</span>
          </div>
        </div>
      </section>
    </template>

    <div v-else class="card empty-state">
      <img class="empty-state__art" :src="emptyStats" alt="" aria-hidden="true" />
      <h3>这段时间还没有记录</h3>
      <p>去完成一次运动，数据就会出现在这里。</p>
      <button type="button" class="btn-primary btn-sm empty-btn" @click="router.push('/checkin')">
        去打卡
      </button>
    </div>
  </div>
</template>

<style scoped>
.stats {
  padding-top: 0;
}

.stats-seg {
  margin-bottom: 14px;
}

.stats-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  padding: 16px 0;
}

.summary-item {
  padding: 0 14px;
}

.summary-item + .summary-item {
  border-left: 1px solid var(--line);
}

.summary-item span {
  display: block;
  font-size: 12px;
  color: var(--muted);
}

.summary-item strong {
  display: block;
  margin-top: 4px;
  font-size: 24px;
  line-height: 30px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.summary-item em {
  margin-left: 2px;
  font-size: 10px;
  font-weight: 400;
  font-style: normal;
  color: var(--muted);
}

.loading-card {
  margin-top: 14px;
  padding: 18px;
}

.chart-card {
  margin-top: 14px;
  padding: 16px;
  border-radius: 18px;
}

.chart-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.chart-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.chart-head span {
  font-size: 12px;
  color: var(--muted);
}

.chart {
  width: 100%;
}

.chart-bar {
  height: 200px;
  margin-top: 6px;
}

.pie-wrap {
  position: relative;
  width: 100%;
  max-width: 240px;
  height: 200px;
  margin: 4px auto 0;
}

.chart-pie {
  height: 100%;
}

.pie-center {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  pointer-events: none;
}

.pie-center strong {
  display: block;
  font-size: 26px;
  line-height: 28px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.pie-center span {
  display: block;
  margin-top: 2px;
  font-size: 10px;
  color: var(--muted);
}

.legend {
  margin-top: 6px;
  border-top: 1px solid var(--line);
}

.legend__row {
  display: grid;
  grid-template-columns: 8px 1fr auto;
  align-items: center;
  gap: 8px;
  padding: 9px 0;
  border-bottom: 1px solid var(--line);
}

.legend__row:last-child {
  border-bottom: 0;
}

.legend__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.legend__name {
  font-size: 13px;
  font-weight: 500;
}

.legend__value {
  font-size: 12px;
  color: var(--muted);
}

.empty-btn {
  margin: 16px auto 0;
}
</style>
