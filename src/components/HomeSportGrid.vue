<script setup>
import { LayoutGrid } from 'lucide-vue-next'

defineProps({
  sports: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  hasMore: { type: Boolean, default: false }
})
const emit = defineEmits(['pick'])
</script>

<template>
  <div class="sport-grid">
    <button
      v-for="(sport, index) in sports"
      :key="sport.custom ? 'custom-' + sport.id : sport.name"
      type="button"
      class="sport-card"
      @click="emit('pick', sport.name)"
    >
      <span
        class="sport-icon"
        :class="sport.custom ? 'sport-icon--custom' : 'sport-icon--' + index"
      >
        <component :is="sport.icon" :size="20" />
      </span>
      <span class="sport-name">{{ sport.name }}</span>
      <span class="sport-met">MET {{ sport.met.toFixed(1) }}</span>
    </button>

    <button v-if="hasMore" type="button" class="sport-card" @click="emit('pick')">
      <span class="sport-icon sport-icon--more"><LayoutGrid :size="20" /></span>
      <span class="sport-name">全部运动</span>
      <span class="sport-met">共 {{ total }} 项</span>
    </button>
  </div>
</template>

<style scoped>
.sport-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.sport-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  padding: 14px 8px 12px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--surface);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: transform 0.14s ease;
}

.sport-card:active {
  transform: scale(0.97);
}

.sport-icon {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  font-size: 17px;
  font-weight: 700;
}

.sport-icon--0 {
  background: var(--blue-soft);
  color: var(--blue);
}

.sport-icon--1 {
  background: var(--green-soft);
  color: var(--green);
}

.sport-icon--2 {
  background: var(--cyan-soft);
  color: var(--cyan);
}

.sport-icon--3 {
  background: var(--yellow-soft);
  color: #b45309;
}

.sport-icon--4 {
  background: var(--violet-soft);
  color: var(--violet);
}

.sport-icon--5 {
  background: var(--orange-soft);
  color: var(--orange);
}

.sport-icon--6 {
  background: var(--red-soft);
  color: var(--red);
}

.sport-icon--custom {
  background: var(--violet-soft);
  color: var(--violet);
}

.sport-icon--more {
  background: var(--surface-2);
  color: var(--muted);
}

.sport-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.sport-met {
  font-size: 10px;
  color: var(--muted);
}
</style>
