<template>
  <span v-if="row.pricingId != null" class="commission-basis">
    <span>{{ perOrder(row.amountPerOrder) }} 元 / 单 × {{ row.orderCount ?? '—' }} 单</span>
    <span class="basis-note">P4 自定义 · 配置 #{{ row.pricingId }}</span>
  </span>
  <span v-else class="commission-basis">
    <span>历史比例：{{ row.sharePct ?? '—' }}% / {{ row.diffPct ?? '—' }}%</span>
    <span class="basis-note">岗位份额 / 级差快照</span>
  </span>
</template>

<script setup>
defineProps({ row: { type: Object, required: true } })
const perOrder = value => value == null || !Number.isFinite(Number(value)) ? '—' : Number(value).toFixed(3)
</script>

<style scoped>
.commission-basis { display: flex; flex-direction: column; font-variant-numeric: tabular-nums; }
.basis-note { margin-top: 4px; color: var(--el-text-color-regular); font-size: 12px; }
</style>
