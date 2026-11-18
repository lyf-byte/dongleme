<script setup>
import { computed, onMounted, ref } from 'vue'
import { Trophy } from 'lucide-vue-next'
import { getRank, errorMessage } from '../utils/api'
import { toast } from '../utils/ui'

const loading = ref(true)
const data = ref({ list: [], me: null })

const list = computed(() => data.value.list || [])
const meInList = computed(() => list.value.some((item) => item.isMe))
const showMyCard = computed(() => Boolean(data.value.me) && !meInList.value)

function medalClass(index) {
  if (index === 0) return 'gold'
  if (index === 1) return 'silver'
  if (index === 2) return 'bronze'
  return ''
}

async function load() {
  loading.value = true
  try {
    data.value = await getRank()
  } catch (error) {
    toast(errorMessage(error, '加载排行榜失败'))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page rank">
    <header class="nav-large">
      <div>
        <p class="eyebrow">每周一 00:00 更新</p>
        <h1>本周排行</h1>
      </div>
      <div class="nav-large__action"><Trophy :size="20" /></div>
    </header>

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
          <span>本周</span>
          <strong>{{ data.me.minutes }}<em>分钟</em></strong>
        </div>
        <div class="my-rank__item">
          <span>距前一名</span>
          <strong>{{ data.me.gap }}<em>分钟</em></strong>
        </div>
      </section>

      <div class="section-title">
        <h2>本周榜单</h2>
        <span>TOP 20</span>
      </div>

      <div v-if="list.length" class="card rank-list">
        <div
          v-for="(item, index) in list"
          :key="item.id"
          class="rank-row"
          :class="{ me: item.isMe }"
        >
          <span class="rank-no" :class="medalClass(index)">{{ index + 1 }}</span>
          <span class="rank-avatar" :class="'rank-avatar--' + (index % 5)">
            {{ item.nickname.slice(0, 1) }}
          </span>
          <span class="rank-name">
            {{ item.nickname }}
            <em v-if="item.isMe">我</em>
          </span>
          <span class="rank-min">{{ item.weeklyMinutes }}<em>分钟</em></span>
        </div>
      </div>

      <div v-else class="card empty-state">
        <span class="empty-state__icon"><Trophy :size="24" /></span>
        <h3>本周还没有人打卡</h3>
        <p>你可以成为第一个上榜的人。</p>
      </div>
    </template>
  </div>
</template>

<style scoped>
.rank {
  padding-top: 0;
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

.rank-no {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--surface-2);
  color: var(--text-2);
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.rank-no.gold {
  background: var(--yellow-soft);
  color: #b45309;
}

.rank-no.silver {
  background: #f1f5f9;
  color: #64748b;
}

.rank-no.bronze {
  background: #fbeee3;
  color: #b25e2a;
}

.rank-avatar {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 600;
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
}

.rank-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 500;
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
