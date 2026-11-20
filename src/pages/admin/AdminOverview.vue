<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import {
  ClipboardList,
  Clock3,
  Flame,
  RefreshCw,
  Route as RouteIcon,
  UserPlus,
  Users
} from 'lucide-vue-next'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { adminOverview, errorMessage } from '../../utils/api'
import { sportTagClass } from '../../utils/sport'

echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const chartColors = ['#2f6bff', '#16a34a', '#f97316', '#06b6d4', '#8b5cf6', '#f6b21b', '#ef4444']

const data = ref(null)
const loading = ref(true)
const errorText = ref('')
const trendEl = ref(null)
const pieEl = ref(null)
let trendChart = null
let pieChart = null

function disposeCharts() {
  trendChart?.dispose()
  pieChart?.dispose()
  trendChart = null
  pieChart = null
}

function renderCharts() {
  if (!data.value) return

  if (trendEl.value) {
    trendChart = echarts.init(trendEl.value)
    const trend = data.value.trend || []
    trendChart.setOption({
      grid: { left: 6, right: 10, top: 34, bottom: 2, containLabel: true },
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#111827',
        borderWidth: 0,
        padding: [8, 10],
        textStyle: { color: '#fff', fontSize: 11 }
      },
      legend: {
        top: 0,
        right: 0,
        itemWidth: 10,
        itemHeight: 8,
        textStyle: { fontSize: 11, color: '#64748b' }
      },
      xAxis: {
        type: 'category',
        data: trend.map((item) => item.date.slice(5).replace('-', '/')),
        axisTick: { show: false },
        axisLine: { lineStyle: { color: '#e5eaf2' } },
        axisLabel: { fontSize: 10, color: '#7c889c', interval: 1 }
      },
      yAxis: [
        {
          type: 'value',
          splitLine: { lineStyle: { color: '#f1f4f9', type: 'dashed' } },
          axisLabel: { fontSize: 10, color: '#7c889c' }
        },
        {
          type: 'value',
          splitLine: { show: false },
          axisLabel: { fontSize: 10, color: '#7c889c' }
        }
      ],
      series: [
        {
          name: '打卡数',
          type: 'bar',
          data: trend.map((item) => item.records),
          barMaxWidth: 18,
          itemStyle: { color: '#2f6bff', borderRadius: [6, 6, 0, 0] }
        },
        {
          name: '活跃用户',
          type: 'line',
          yAxisIndex: 1,
          data: trend.map((item) => item.users),
          smooth: true,
          symbol: 'circle',
          symbolSize: 5,
          lineStyle: { color: '#16a34a', width: 2 },
          itemStyle: { color: '#16a34a' }
        }
      ]
    })
  }

  if (pieEl.value && data.value.types?.length) {
    pieChart = echarts.init(pieEl.value)
    pieChart.setOption({
      color: chartColors,
      tooltip: {
        trigger: 'item',
        backgroundColor: '#111827',
        borderWidth: 0,
        padding: [8, 10],
        textStyle: { color: '#fff', fontSize: 11 },
        formatter: '{b}<br/>{c} 次 · {d}%'
      },
      series: [
        {
          type: 'pie',
          radius: ['54%', '76%'],
          center: ['50%', '50%'],
          itemStyle: { borderColor: '#fff', borderWidth: 3 },
          label: { show: false },
          data: data.value.types.map((item) => ({ name: item.name, value: item.count }))
        }
      ]
    })
  }
}

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    data.value = await adminOverview()
    loading.value = false
    disposeCharts()
    await nextTick()
    renderCharts()
  } catch (error) {
    errorText.value = errorMessage(error, '加载概览失败')
    loading.value = false
  }
}

function onResize() {
  trendChart?.resize()
  pieChart?.resize()
}

onMounted(() => {
  window.addEventListener('resize', onResize)
  load()
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  disposeCharts()
})
</script>

<template>
  <div>
    <div class="admin-page-head">
      <div>
        <h1>数据概览</h1>
        <p>用户、打卡、轨迹与本周活跃情况</p>
      </div>
      <div class="admin-actions">
        <button type="button" class="admin-btn" :disabled="loading" @click="load">
          <RefreshCw :size="15" />
          刷新
        </button>
      </div>
    </div>

    <p v-if="errorText" class="admin-error">{{ errorText }}</p>

    <div v-if="loading" class="admin-loading">加载中…</div>

    <template v-else-if="data">
      <div class="admin-metrics">
        <div class="admin-metric">
          <span class="admin-metric__icon admin-metric__icon--blue"><Users :size="17" /></span>
          <span>总用户</span>
          <strong>{{ data.totalUsers }}</strong>
          <p class="admin-metric__sub">本周新增 <em>+{{ data.weekNewUsers }}</em></p>
        </div>
        <div class="admin-metric">
          <span class="admin-metric__icon admin-metric__icon--green"><ClipboardList :size="17" /></span>
          <span>总打卡</span>
          <strong>{{ data.totalRecords }}</strong>
          <p class="admin-metric__sub">今日 {{ data.todayRecords }} 条</p>
        </div>
        <div class="admin-metric">
          <span class="admin-metric__icon admin-metric__icon--orange"><Flame :size="17" /></span>
          <span>今日活跃</span>
          <strong>{{ data.activeToday }}</strong>
          <p class="admin-metric__sub">按打卡人数统计</p>
        </div>
        <div class="admin-metric">
          <span class="admin-metric__icon admin-metric__icon--violet"><Clock3 :size="17" /></span>
          <span>本周时长</span>
          <strong>{{ data.weekMinutes }}</strong>
          <p class="admin-metric__sub">单位：分钟</p>
        </div>
        <div class="admin-metric">
          <span class="admin-metric__icon admin-metric__icon--cyan"><UserPlus :size="17" /></span>
          <span>本周新增</span>
          <strong>{{ data.weekNewUsers }}</strong>
          <p class="admin-metric__sub">新注册账号</p>
        </div>
        <div class="admin-metric">
          <span class="admin-metric__icon admin-metric__icon--red"><RouteIcon :size="17" /></span>
          <span>运动轨迹</span>
          <strong>{{ data.routeCount }}</strong>
          <p class="admin-metric__sub">GPS 记录总数</p>
        </div>
      </div>

      <div class="admin-grid">
        <section class="admin-card">
          <div class="admin-card__head">
            <div>
              <h2>近 14 天趋势</h2>
              <p>打卡数量与活跃用户</p>
            </div>
            <span class="admin-tag admin-tag--blue">实时</span>
          </div>
          <div ref="trendEl" class="admin-chart"></div>
        </section>

        <section class="admin-card">
          <div class="admin-card__head">
            <div>
              <h2>运动类型分布</h2>
              <p>按打卡次数统计</p>
            </div>
            <span class="admin-tag">{{ data.types?.length || 0 }} 类</span>
          </div>
          <div class="admin-pie-wrap">
            <div ref="pieEl" class="admin-chart admin-chart--pie"></div>
            <div class="admin-pie-center">
              <strong>{{ data.totalRecords }}</strong>
              <span>总打卡</span>
            </div>
          </div>
          <div class="admin-chart-legend">
            <span v-for="(item, index) in data.types" :key="item.name">
              <i :style="{ background: chartColors[index % chartColors.length] }"></i>
              {{ item.name }} · {{ item.count }} 次
            </span>
          </div>
        </section>
      </div>

      <section class="admin-card">
        <div class="admin-card__head">
          <div>
            <h2>最近打卡</h2>
            <p>最新 10 条用户记录</p>
          </div>
          <span class="admin-tag">最近 10 条</span>
        </div>

        <div v-if="!data.recent?.length" class="admin-empty">
          <strong>还没有打卡记录</strong>
          <span>用户完成打卡后会显示在这里</span>
        </div>
        <div v-else class="admin-table-wrap">
          <table class="admin-table">
            <thead>
              <tr>
                <th>日期</th>
                <th>用户</th>
                <th>类型</th>
                <th>时长</th>
                <th>卡路里</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in data.recent" :key="row.id">
                <td class="num">{{ row.date }}</td>
                <td>{{ row.nickname }}</td>
                <td><span :class="sportTagClass(row.type)">{{ row.type }}</span></td>
                <td class="num">{{ row.duration }} 分钟</td>
                <td class="num">{{ row.calories }} 千卡</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>
