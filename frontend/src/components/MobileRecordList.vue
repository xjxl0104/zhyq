<template>
  <div class="mobile-record-list" aria-live="polite">
    <el-skeleton v-if="loading" :rows="4" animated />
    <el-empty v-else-if="!items.length" :description="emptyText" :image-size="72" />
    <article
      v-for="(row, index) in items"
      v-else
      :key="getRowKey(row, index)"
      :class="['mobile-record-card', getRowClass(row, index)]"
      :data-record-key="getRowKey(row, index)"
      :aria-labelledby="$slots.title ? getTitleId(row, index) : undefined"
    >
      <h2 v-if="$slots.title" :id="getTitleId(row, index)" class="mobile-record-card__header">
        <slot name="title" :row="row" :index="index" />
      </h2>
      <div class="mobile-record-card__body">
        <slot :row="row" :index="index" />
      </div>
      <footer
        v-if="$slots.actions"
        class="mobile-record-card__actions"
        role="group"
        :aria-labelledby="$slots.title ? getTitleId(row, index) : undefined"
      >
        <slot name="actions" :row="row" :index="index" />
      </footer>
    </article>
  </div>
</template>

<script setup>
import { getCurrentInstance } from 'vue'

const instanceId = getCurrentInstance().uid

const props = defineProps({
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  rowKey: { type: [String, Function], default: 'id' },
  rowClassName: { type: [String, Function, Array, Object], default: '' },
  emptyText: { type: String, default: '暂无数据' }
})

function getRowKey(row, index) {
  if (typeof props.rowKey === 'function') return props.rowKey(row)
  return row?.[props.rowKey] ?? index
}

function getTitleId(row, index) {
  const key = encodeURIComponent(String(getRowKey(row, index))).replaceAll('%', '-')
  return `mobile-record-title-${instanceId}-${key}`
}

function getRowClass(row, index) {
  if (typeof props.rowClassName === 'function') {
    return props.rowClassName({ row, rowIndex: index })
  }
  return props.rowClassName
}
</script>

<style scoped>
.mobile-record-list {
  display: grid;
  gap: 12px;
}

.mobile-record-card {
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--border, var(--el-border-color-lighter));
  border-radius: var(--radius, 12px);
  background: var(--bg-card, var(--el-bg-color));
}

.mobile-record-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin: 0 0 12px;
  color: var(--text-title, var(--el-text-color-primary));
  font-size: 16px;
  font-weight: 600;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.mobile-record-card__body {
  min-width: 0;
  color: var(--text-regular, var(--el-text-color-regular));
  font-size: 14px;
  line-height: 1.6;
}

.mobile-record-card__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 12px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--border, var(--el-border-color-lighter));
}

.mobile-record-card__actions :deep(.el-button) {
  min-width: 44px;
  min-height: 44px;
  margin-left: 0;
}

.mobile-record-card__body :deep(.mobile-record-summary) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 10px;
}

.mobile-record-card__body :deep(.mobile-record-meta) {
  margin-top: 8px;
  color: var(--text-secondary, var(--el-text-color-secondary));
  overflow-wrap: anywhere;
}

.mobile-record-card__body :deep(.mobile-record-details) {
  margin-top: 10px;
}

.mobile-record-card__body :deep(.mobile-record-details summary) {
  display: flex;
  align-items: center;
  min-width: 44px;
  min-height: 44px;
  color: var(--el-color-primary);
  cursor: pointer;
  user-select: none;
}

.mobile-record-card__body :deep(.mobile-record-fields) {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 10px 16px;
  margin: 2px 0 0;
}

.mobile-record-card__body :deep(.mobile-record-field) {
  min-width: 0;
}

.mobile-record-card__body :deep(.mobile-record-field--wide) {
  grid-column: 1 / -1;
}

.mobile-record-card__body :deep(dt) {
  color: var(--text-secondary, var(--el-text-color-secondary));
  font-size: 12px;
}

.mobile-record-card__body :deep(dd) {
  margin: 2px 0 0;
  color: var(--text-regular, var(--el-text-color-regular));
  overflow-wrap: anywhere;
}
</style>
