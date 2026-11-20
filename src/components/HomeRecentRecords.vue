<script setup>
defineProps({
  rows: { type: Array, default: () => [] }
})
</script>

<template>
  <div class="recent-records">
    <div v-for="row in rows" :key="row.key" class="recent-record">
      <span class="recent-record__icon">
        <component :is="row.icon" :size="15" />
      </span>
      <span class="recent-record__main">
        <strong>{{ row.type }}</strong>
        <small>{{ row.dateText }} · {{ row.duration }} 分钟</small>
      </span>
      <span class="recent-record__cal">{{ row.calories }}<small>千卡</small></span>
    </div>
  </div>
</template>

<style scoped>
.recent-records {
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--surface);
  padding: 2px 14px;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.04);
}

.recent-record {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-top: 1px solid var(--line);
}

.recent-record:first-child {
  border-top: 0;
}

.recent-record__icon {
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: var(--blue-soft);
  color: var(--blue);
}

.recent-record__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.recent-record__main strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.recent-record__main small {
  font-size: 10px;
  color: var(--muted);
}

.recent-record__cal {
  flex: 0 0 auto;
  color: var(--orange);
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.recent-record__cal small {
  margin-left: 1px;
  color: var(--muted);
  font-size: 9px;
  font-weight: 500;
}
</style>
