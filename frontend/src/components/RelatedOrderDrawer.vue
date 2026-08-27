<template>
  <el-drawer
    :model-value="visible"
    :title="`关联工单${sourceLabel ? ' · ' + sourceLabel : ''}`"
    size="620px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-table v-if="list.length" :data="list" v-loading="loading" border stripe size="small">
      <el-table-column prop="code" label="工单号" width="150" show-overflow-tooltip />
      <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="STATUS_MAP[row.status]?.type || 'info'">
            {{ STATUS_MAP[row.status]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="assignee" label="处理人" width="90">
        <template #default="{ row }">{{ row.assignee || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$emit('goto', row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else-if="!loading" description="该记录未派生工单" :image-size="80" />
    <div v-else v-loading="loading" style="height: 120px" />
  </el-drawer>
</template>

<script setup>
import { SOURCE_LABELS } from '@/composables/useSourceLink'
import { computed } from 'vue'

/**
 * 源记录派生工单的展示抽屉(纯展示,取数在调用方的 useRelatedOrders 里)。
 * 巡检/巡更/三检/投诉/告警五个页面共用。
 */
const props = defineProps({
  visible: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  list: { type: Array, default: () => [] },
  sourceType: { type: String, default: '' }
})

defineEmits(['update:visible', 'goto'])

// 与 WorkOrder.vue 的 statusMap 同源:1待派单 2待接单 3处理中 4待验收 5已完成 6已关闭 7已超时
const STATUS_MAP = {
  1: { label: '待派单', type: 'warning' },
  2: { label: '待接单', type: 'warning' },
  3: { label: '处理中', type: 'primary' },
  4: { label: '待验收', type: 'primary' },
  5: { label: '已完成', type: 'success' },
  6: { label: '已关闭', type: 'info' },
  7: { label: '已超时', type: 'danger' }
}

const sourceLabel = computed(() => SOURCE_LABELS[props.sourceType] || '')
</script>
