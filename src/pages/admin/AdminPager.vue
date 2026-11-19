<script setup>
import { computed } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

const props = defineProps({
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 },
  total: { type: Number, default: 0 }
})

const emit = defineEmits(['change'])

const totalPages = computed(() =>
  Math.max(1, Math.ceil(props.total / Math.max(props.pageSize, 1)))
)

function go(next) {
  if (next < 1 || next > totalPages.value || next === props.page) return
  emit('change', next)
}
</script>

<template>
  <div class="admin-pager">
    <span class="admin-pager__info">
      共 {{ total }} 条 · 第 {{ page }} / {{ totalPages }} 页
    </span>
    <div class="admin-pager__actions">
      <button type="button" class="admin-btn" :disabled="page <= 1" @click="go(page - 1)">
        <ChevronLeft :size="15" />
        上一页
      </button>
      <button
        type="button"
        class="admin-btn"
        :disabled="page >= totalPages"
        @click="go(page + 1)"
      >
        下一页
        <ChevronRight :size="15" />
      </button>
    </div>
  </div>
</template>
