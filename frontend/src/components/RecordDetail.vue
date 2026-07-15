<!--
  通用记录详情页布局：Highlights(标题+状态+关键指标) + 阶段条(el-steps) + 关联区(el-tabs)。
  用于合同/房源/租客等核心对象的详情页(#18)，替代原来的 el-dialog 详情弹窗。

  用法：
    <RecordDetail :title="..." :subtitle="..." :status-text="..." :status-type="..."
                   :stats="[{label:'租客', value:'xxx'}]"
                   :stages="['草稿','审批','生效','到期/退租']" :active-step="2"
                   :tabs="[{name:'bills', label:'账单'}, {name:'rooms', label:'房源'}]"
                   :loading="loading">
      <template #extra>额外的 highlights 内容(可选)</template>
      <template #tab-bills><el-table :data="bills" ... /></template>
      <template #tab-rooms><el-table :data="rooms" ... /></template>
    </RecordDetail>
-->
<template>
  <div class="record-detail" v-loading="loading">
    <!-- Highlights -->
    <div class="rd-card rd-header">
      <div class="rd-title-row">
        <div class="rd-title-block">
          <div class="rd-title">{{ title }}</div>
          <div v-if="subtitle" class="rd-subtitle">{{ subtitle }}</div>
        </div>
        <el-tag v-if="statusText" :type="statusType" size="large">{{ statusText }}</el-tag>
      </div>
      <div v-if="stats && stats.length" class="rd-stats-row">
        <div v-for="s in stats" :key="s.label" class="rd-stat">
          <div class="rd-stat-label">{{ s.label }}</div>
          <div class="rd-stat-value">{{ s.value }}</div>
        </div>
      </div>
      <slot name="extra" />
    </div>

    <!-- 阶段条 -->
    <div v-if="stages && stages.length" class="rd-card rd-stage">
      <el-steps :active="activeStep" finish-status="success" simple>
        <el-step v-for="s in stages" :key="s" :title="s" />
      </el-steps>
    </div>

    <!-- 关联区 -->
    <div v-if="tabs && tabs.length" class="rd-card rd-related">
      <el-tabs v-model="activeTabModel">
        <el-tab-pane v-for="t in tabs" :key="t.name" :label="t.label" :name="t.name">
          <slot :name="`tab-${t.name}`" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  statusText: { type: String, default: '' },
  statusType: { type: String, default: 'info' },
  stats: { type: Array, default: () => [] }, // [{label, value}]
  stages: { type: Array, default: () => [] }, // ['草稿','审批',...]
  activeStep: { type: Number, default: 0 },
  tabs: { type: Array, default: () => [] }, // [{name, label}]
  loading: { type: Boolean, default: false }
})

const activeTabModel = ref(props.tabs[0]?.name || '')
watch(
  () => props.tabs,
  (t) => {
    if (t?.length && !t.some((x) => x.name === activeTabModel.value)) {
      activeTabModel.value = t[0].name
    }
  }
)
</script>

<style scoped>
.record-detail { display: flex; flex-direction: column; gap: 16px; }
.rd-card {
  background: var(--bg-card, #fff);
  border: 1px solid var(--border, #e5e7eb);
  border-radius: var(--radius, 8px);
  padding: 20px 24px;
}
.rd-title-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.rd-title { font-size: 20px; font-weight: 600; }
.rd-subtitle { color: var(--text-secondary, #6b7280); margin-top: 4px; font-size: 13px; }
.rd-stats-row { display: flex; gap: 32px; margin-top: 20px; flex-wrap: wrap; }
.rd-stat-label { color: var(--text-secondary, #6b7280); font-size: 13px; }
.rd-stat-value { font-size: 18px; font-weight: 600; margin-top: 4px; }
.rd-stage :deep(.el-steps) { padding: 4px 0; }
</style>
