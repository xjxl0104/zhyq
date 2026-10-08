<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useProjectStore } from '@/stores/project'
import { useAccessStore } from '@/stores/access'
import { workOrderApi } from '@/api/property'
import { ACTIVE_ORDER_STATUSES, PLAN_BUILDING, spatialWorkOrder } from './workOrderSpatial'
const props = defineProps({ floor: Number, requestedOrderId: [String, Number] })
const emit = defineEmits(['points', 'locate'])
const project = useProjectStore(), access = useAccessStore()
const allowed = computed(() => Number(project.currentProjectId) === PLAN_BUILDING.projectId && (access.admin || access.paths.has('/property/workorder')))
const points = ref([]), loading = ref(false), error = ref(''), unmapped = ref(0), truncated = ref(false)
const visiblePoints = computed(() => points.value.filter(point => props.floor == null || props.floor === point.floor))
let generation = 0
async function load() {
  const run = ++generation
  points.value = []; emit('points', []); unmapped.value = 0; error.value = ''; truncated.value = false; loading.value = false
  if (!allowed.value) return
  loading.value = true
  try {
    const rows = []
    // Scope on the server before pagination. Completed orders do not crowd out live work.
    for (const status of ACTIVE_ORDER_STATUSES) {
      for (let pageNo = 1; pageNo <= 10; pageNo++) {
        const result = await workOrderApi.page({ projectId: PLAN_BUILDING.projectId, buildingId: PLAN_BUILDING.buildingId, status, pageNo, pageSize: 100 })
        if (run !== generation) return
        const page = result?.records || []
        rows.push(...page)
        if (pageNo * 100 >= (result?.total || 0) || !page.length) break
        if (pageNo === 10) truncated.value = true
      }
    }
    const requested = Number(props.requestedOrderId)
    if (requested > 0 && !rows.some(row => Number(row.id) === requested)) {
      const result = await workOrderApi.page({ projectId: PLAN_BUILDING.projectId, buildingId: PLAN_BUILDING.buildingId, id: requested, pageNo: 1, pageSize: 1 })
      if (run !== generation) return
      rows.push(...(result?.records || []))
    }
    // Defense in depth for project switches, unexpected responses and future API changes.
    const scoped = rows.filter(row => Number(row.projectId) === PLAN_BUILDING.projectId && Number(row.buildingId) === PLAN_BUILDING.buildingId)
    points.value = scoped.map(spatialWorkOrder).filter(Boolean)
    unmapped.value = scoped.length - points.value.length
    emit('points', points.value)
    if (requested > 0) {
      const point = points.value.find(item => Number(item.orderId) === requested)
      if (point) emit('locate', point)
      else error.value = '该工单不存在、无权查看，或图纸位置尚未匹配。'
    }
  } catch { if (run === generation) { error.value = '工单位置加载失败，请重试。'; emit('points', []) } }
  finally { if (run === generation) loading.value = false }
}
watch(() => [allowed.value, props.requestedOrderId], load, { immediate: true })
onBeforeUnmount(() => { generation++ })
</script>
<template>
  <section v-if="allowed" class="spatial-orders" aria-label="真实工单位置">
    <header><strong>工单位置 <span>{{ visiblePoints.length }}</span></strong><button type="button" :disabled="loading" @click="load">{{ loading ? '加载中…' : '刷新' }}</button></header>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-else-if="!loading && !visiblePoints.length">{{ floor ? '该层暂无已定位工单。' : '暂无已定位的在办工单。' }}</p>
    <ul v-if="visiblePoints.length"><li v-for="point in visiblePoints" :key="point.id"><button type="button" @click="emit('locate', point)"><span class="order-state" :class="{ urgent: point.urgent }">{{ point.status }}</span><span>{{ point.floor === -1 ? 'B1' : point.floor + 'F' }} · {{ point.name }}</span></button></li></ul>
    <p v-if="unmapped">另有 {{ unmapped }} 条缺少标注或图纸版本未匹配，可在工单中补充定位。</p>
    <p v-if="truncated">当前显示部分工单，请前往工单列表查看全部。</p>
  </section>
</template>
<style scoped>
.spatial-orders { background: var(--panel-surface, #f5f8f5); color: var(--panel-ink, #263f49); border-radius: 12px; padding: 14px; margin-top: 12px; font-size: 12px; }
header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
header span { margin-left: 6px; font-variant-numeric: tabular-nums; }
button { color: inherit; font: inherit; cursor: pointer; background: transparent; border: 0; padding: 6px; }
button:focus-visible { outline: 2px solid #287c76; outline-offset: 2px; }
button:disabled { cursor: wait; opacity: .6; }
p { line-height: 1.6; margin: 10px 0 0; }
ul { padding: 0; list-style: none; margin: 8px 0 0; max-height: 200px; overflow: auto; }
li button { display: flex; align-items: start; gap: 8px; width: 100%; text-align: left; border-bottom: 1px solid #dbe4df; padding: 10px 0; }
li button:hover { background: #e1eee7; }
.order-state { white-space: nowrap; color: #226861; }
.order-state.urgent { color: #a23b30; }
</style>
