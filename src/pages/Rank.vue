<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Crown, Trophy } from 'lucide-vue-next'
import { getRank, errorMessage } from '../utils/api'
import { avatarFor } from '../utils/avatar'
import { toast } from '../utils/ui'
import emptyRank from '../assets/empty-rank.svg'

const router = useRouter()
const loading = ref(true)
const data = ref({ list: [], me: null })
const scope = ref('week')

const scopes = [
  { value: 'week', label: '本周' },
  { value: 'total', label: '累计' }
]

const list = computed(() => data.value.list || [])
const meInList = computed(() => list.value.some((item) => item.isMe))
const showMyCard = computed(() => Boolean(data.value.me) && !meInList.value)
const isWeek = computed(() => scope.value === 'week')
const myIndex = computed(() => list.value.findIndex((item) => item.isMe))
const topGap = computed(() => {
  if (myIndex.value <= 2 || !list.value[2] || !list.value[myIndex.value]) return null
  return Math.max(0, list.value[2].weeklyMinutes - list.value[myIndex.value].weeklyMinutes)
})

function medalClass(index) {
  if (index === 0) return 'gold'
  if (index === 1) return 'silver'
  if (index === 2) return 'bronze'
  return ''
}

async function load() {
  loading.value = true
  try {
    data.value = await getRank(scope.value)
  } catch (error) {
    toast(errorMessage(error, '加载排行榜失败'))
  } finally {
    loading.value = false
  }
}

function setScope(value) {
  if (scope.value === value) return
  scope.value = value
  load()
}

onMounted(load)
</script>

<template>
  <div class="page rank">
    <header class="nav-large">
      <div>
        <p class="eyebrow">{{ isWeek ? '每周一 00:00 更新' : '全部打卡累计' }}</p>
        <h1>{{ isWeek ? '本周排行' : '累计排行' }}</h1>
      </div>
      <div class="nav-large__action"><Trophy :size="20" /></div>
    </header>

    <div class="segmented rank-seg">
      <button
        v-for="item in scopes"
        :key="item.value"
        type="button"
        :class="{ active: scope === item.value }"
        @click="setScope(item.value)"
      >
        {{ item.label }}
      </button>
    </div>

    <template v-if="loading">
      <div class="card loading-card">
        <div class="loading-line"><span></span></div>
      </div>
    </template>

    <template v-else>
      <section v-if="showMyCard" class="my-rank">
        <div class="my-rank__left">
          <span>我的排名</span>
          <strong>{{ data.me.rank }}<em>名</em></strong>
        </div>
        <div class="my-rank__item">
          <span>{{ isWeek ? '本周' : '累计' }}</span>
          <strong>{{ data.me.minutes }}<em>分钟</em></strong>
        </div>
        <div class="my-rank__item">
          <span>距前一名</span>
          <strong>{{ data.me.gap }}<em>分钟</em></strong>
        </div>
      </section>

      <div class="section-title">
        <h2>{{ isWeek ? '本周榜单' : '累计榜单' }}</h2>
        <span>{{ list.length }} 人参与</span>
      </div>

      <div v-if="list.length" class="card rank-list">
        <div
          v-for="(item, index) in list"
          :key="item.id"
          class="rank-row"
          :class="{ me: item.isMe }"
        >
          <span class="rank-no" :class="medalClass(index)">
            <template v-if="index < 3">
              <span class="rank-medal-ribbon"></span>
              <span class="rank-medal">{{ index + 1 }}</span>
            </template>
            <template v-else>{{ index + 1 }}</template>
          </span>
          <span class="rank-avatar" :class="'rank-avatar--' + (index % 5)">
            <img
              v-if="avatarFor(item.gender)"
              :src="avatarFor(item.gender)"
              alt=""
              class="rank-avatar__img"
            />
            <template v-else>{{ item.nickname.slice(0, 1) }}</template>
          </span>
          <span class="rank-name">
            <Crown v-if="index === 0" :size="13" class="rank-crown" />
            {{ item.nickname }}
            <em v-if="item.isMe">我</em>
          </span>
          <span class="rank-min">{{ item.weeklyMinutes }}<em>分钟</em></span>
        </div>
      </div>

      <div v-if="list.length" class="rank-tip">
        <Trophy :size="15" />
        <span v-if="topGap !== null">
          再运动 <strong>{{ topGap }}</strong> 分钟就能进入前三
        </span>
        <span v-else-if="!meInList">完成一次打卡，就能出现在榜单上</span>
        <span v-else-if="list.length < 3">再完成一次打卡，名次会更稳</span>
        <span v-else>保持节奏，你已经在榜单前列</span>
        <button v-if="!meInList" type="button" @click="router.push('/checkin')">去打卡</button>
      </div>

      <div v-else class="card empty-state">
        <img class="empty-state__art" :src="emptyRank" alt="" aria-hidden="true" />
        <h3>{{ isWeek ? '本周还没有人打卡' : '还没有人留下记录' }}</h3>
        <p>{{ isWeek ? '你可以成为第一个上榜的人。' : '完成一次运动，就能出现在榜单上。' }}</p>
        <button type="button" class="btn-primary btn-sm empty-btn" @click="router.push('/checkin')">
          去打卡
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.rank {
  padding-top: 0;
}

.rank-seg {
  margin-bottom: 14px;
}

.empty-btn {
  margin: 16px auto 0;
}

.loading-card {
  padding: 18px;
  margin-top: 4px;
}

.my-rank {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr;
  gap: 10px;
  padding: 16px;
  border-radius: 20px;
  background: var(--blue-soft);
}

.my-rank__left {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.my-rank__left span,
.my-rank__item span {
  font-size: 11px;
  color: var(--text-2);
}

.my-rank__left strong {
  display: block;
  margin-top: 2px;
  font-size: 28px;
  line-height: 32px;
  font-weight: 700;
  color: var(--blue);
  font-variant-numeric: tabular-nums;
}

.my-rank__item {
  padding-left: 12px;
  border-left: 1px solid rgba(47, 107, 255, 0.16);
}

.my-rank__item strong {
  display: block;
  margin-top: 4px;
  font-size: 16px;
  line-height: 22px;
  font-weight: 700;
  color: var(--text);
  font-variant-numeric: tabular-nums;
}

.my-rank em {
  margin-left: 2px;
  font-size: 10px;
  font-weight: 400;
  font-style: normal;
  color: var(--text-2);
}

.rank-list {
  overflow: hidden;
  padding: 0 14px;
}

.rank-row {
  display: grid;
  grid-template-columns: 26px 36px 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
  border-top: 1px solid var(--line);
}

.rank-row:first-child {
  border-top: 0;
}

.rank-row:nth-child(-n + 3) {
  margin: 0 -14px;
  padding-left: 14px;
  padding-right: 14px;
}

.rank-no {
  position: relative;
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--surface-2);
  color: var(--text-2);
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.rank-no.gold,
.rank-no.silver,
.rank-no.bronze {
  background: transparent;
  color: inherit;
}

.rank-medal {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 2px solid #fff;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 2px 5px rgba(15, 23, 42, 0.14);
}

.rank-medal-ribbon {
  position: absolute;
  left: 50%;
  bottom: -5px;
  z-index: 0;
  width: 16px;
  height: 10px;
  transform: translateX(-50%);
  background: currentColor;
  opacity: 0.7;
  clip-path: polygon(0 0, 100% 0, 100% 100%, 50% 62%, 0 100%);
}

.rank-no.gold {
  color: #d9a406;
}

.rank-no.gold .rank-medal {
  background: linear-gradient(145deg, #ffeaa6, #f4c33d);
  color: #7a5200;
}

.rank-no.silver {
  color: #94a3b8;
}

.rank-no.silver .rank-medal {
  background: linear-gradient(145deg, #f4f7fa, #c8d3e1);
  color: #4b5a6d;
}

.rank-no.bronze {
  color: #b87333;
}

.rank-no.bronze .rank-medal {
  background: linear-gradient(145deg, #f9dfc8, #e0a56b);
  color: #7a4318;
}

.rank-avatar {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
}

.rank-avatar__img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.rank-avatar--0 {
  background: var(--blue-soft);
  color: var(--blue);
}

.rank-avatar--1 {
  background: var(--green-soft);
  color: var(--green);
}

.rank-avatar--2 {
  background: var(--yellow-soft);
  color: #b45309;
}

.rank-avatar--3 {
  background: var(--violet-soft);
  color: var(--violet);
}

.rank-avatar--4 {
  background: var(--orange-soft);
  color: var(--orange);
}

.rank-row.me {
  margin: 0 -14px;
  padding-left: 14px;
  padding-right: 14px;
  background: var(--blue-soft);
  box-shadow: inset 3px 0 0 var(--blue);
}

.rank-row:nth-child(1):not(.me) {
  background: linear-gradient(90deg, #fff6d9, #fffdf7);
}

.rank-row:nth-child(2):not(.me) {
  background: linear-gradient(90deg, #edf2f8, #fafcfe);
}

.rank-row:nth-child(3):not(.me) {
  background: linear-gradient(90deg, #fbe9d8, #fffaf6);
}

.rank-row.me:nth-child(1) {
  background: linear-gradient(90deg, #fff6d9, #fffdf7);
}

.rank-row.me:nth-child(2) {
  background: linear-gradient(90deg, #edf2f8, #fafcfe);
}

.rank-row.me:nth-child(3) {
  background: linear-gradient(90deg, #fbe9d8, #fffaf6);
}

.rank-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  border: 1px dashed rgba(47, 107, 255, 0.22);
  border-radius: 14px;
  background: rgba(47, 107, 255, 0.05);
  padding: 10px 12px;
  color: var(--text-2);
  font-size: 12px;
}

.rank-tip strong {
  color: var(--blue);
  font-weight: 700;
}

.rank-tip button {
  margin-left: auto;
  border: 0;
  border-radius: 999px;
  background: var(--blue);
  padding: 6px 12px;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

.rank-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 500;
}

.rank-row:nth-child(-n + 3) .rank-name {
  font-weight: 600;
}

.rank-crown {
  margin-right: 3px;
  color: #b45309;
  vertical-align: -2px;
}

.rank-name em {
  display: inline-block;
  margin-left: 5px;
  padding: 1px 5px;
  border-radius: 999px;
  background: var(--blue);
  color: #fff;
  font-size: 9px;
  font-style: normal;
  vertical-align: 1px;
}

.rank-min {
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.rank-min em {
  margin-left: 2px;
  font-size: 10px;
  font-weight: 400;
  font-style: normal;
  color: var(--muted);
}
</style>
