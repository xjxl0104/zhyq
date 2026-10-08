<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useProjectStore } from '@/stores/project'
import { useAccessStore } from '@/stores/access'
import { workOrderApi } from '@/api/property'
import { ACTIVE_ORDER_STATUSES, PLAN_BUILDING, spatialWorkOrder } from './workOrderSpatial'
import TwinIcon from './TwinIcon.vue'
const props = defineProps({ floor: Number, requestedOrderId: [String, Number] })
const emit = defineEmits(['points', 'locate'])
const project = useProjectStore(), access = useAccessStore()
const allowed = computed(() => Number(project.currentProjectId) === PLAN_BUILDING.projectId && (access.admin || access.paths.has('/property/workorder')))
const points = ref([]), activePoints = ref([]), activeCount = ref(0), loading = ref(false), error = ref(''), unmapped = ref(0), truncated = ref(false)
const expanded = ref(false), trigger = ref(null)
const summary = computed(() => loading.value ? '加载中…' : error.value && !activeCount.value ? '加载失败' : activeCount.value ? `待处理 ${activeCount.value}${truncated.value ? '+' : ''}` : '暂无待处理')
function toggle() { expanded.value = !expanded.value }
async function close() { expanded.value = false; await nextTick(); trigger.value?.focus() }
function locate(point) { emit('locate', point); close() }
let generation = 0
async function load() {
  const run = ++generation
  points.value = []; activePoints.value = []; activeCount.value = 0; emit('points', []); unmapped.value = 0; error.value = ''; truncated.value = false; loading.value = false
  if (!allowed.value) { expanded.value = false; return }
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
    const scoped = [...new Map(rows.filter(row => Number(row.projectId) === PLAN_BUILDING.projectId && Number(row.buildingId) === PLAN_BUILDING.buildingId).map(row => [Number(row.id), row])).values()]
    points.value = scoped.map(spatialWorkOrder).filter(Boolean)
    const active = scoped.filter(row => ACTIVE_ORDER_STATUSES.includes(Number(row.status)))
    activeCount.value = active.length
    const activeIds = new Set(active.map(row => Number(row.id)))
    activePoints.value = points.value.filter(point => activeIds.has(Number(point.orderId)))
    unmapped.value = active.length - activePoints.value.length
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
  <section v-if="allowed" class="spatial-orders" aria-label="真实工单位置" @keydown.esc.stop.prevent="close">
    <div v-if="expanded" id="spatial-work-order-list" class="order-drawer" aria-label="全楼待处理工单">
      <header><strong>全楼待处理工单 <span>{{ activeCount }}</span></strong><button type="button" :disabled="loading" @click="load">{{ loading ? '加载中…' : '刷新' }}</button></header>
      <p v-if="error" role="alert">{{ error }}<button v-if="!loading" class="retry-button" type="button" @click="load">重试</button></p>
      <p v-else-if="loading" role="status">正在获取全楼工单…</p>
      <p v-else-if="!activeCount">暂无待处理工单。</p>
      <p v-else class="drawer-hint">选择工单，定位到对应楼层与标注点。</p>
      <ul v-if="activePoints.length"><li v-for="point in activePoints" :key="point.id"><button type="button" @click="locate(point)"><span class="order-state" :class="{ urgent: point.urgent }">{{ point.status }}</span><span class="order-description"><strong>{{ point.floor === -1 ? 'B1' : point.floor + 'F' }} · {{ point.name }}</strong><small>{{ point.code || '工单 #' + point.orderId }}</small></span><TwinIcon name="pin" :size="14" /></button></li></ul>
      <p v-if="unmapped" class="unmapped-note">{{ unmapped }} 条待处理工单缺少标注或图纸版本未匹配，请在工单中补充定位。</p>
      <p v-if="truncated">当前显示部分工单，请前往工单列表查看全部。</p>
    </div>
    <button ref="trigger" type="button" class="order-dock-toggle" :class="{ pending: activeCount > 0 }" :aria-expanded="expanded" aria-controls="spatial-work-order-list" @click="toggle">
      <TwinIcon name="tool" :size="17" /><strong>物业工单</strong><span class="order-summary" aria-live="polite">{{ summary }}</span><TwinIcon :name="expanded ? 'minus' : 'plus'" :size="15" />
    </button>
  </section>
</template>
<style scoped>
.spatial-orders { position: relative; color: var(--scene-ui, #345a6e); font-size: 12px; }
.order-dock-toggle { display: flex; align-items: center; gap: 9px; min-height: 44px; padding: 10px 12px; border: 1px solid var(--scene-line, #bfd0dc); border-radius: 8px; background: var(--scene-ui-bg, #f8fdff); box-shadow: 0 3px 12px #224c6a16; }
.order-dock-toggle strong { font-weight: 600; white-space: nowrap; }
.order-dock-toggle.pending { color: #a83e36; border-color: #cc8c8266; background: #fff4f1; }
.order-summary { white-space: nowrap; font-variant-numeric: tabular-nums; }
.order-dock-toggle.pending .order-summary { padding: 2px 6px; border-radius: 4px; background: #a83e36; color: #fff; }
.order-drawer { position: absolute; bottom: calc(100% + 8px); left: 0; width: 100%; box-sizing: border-box; max-height: 310px; overflow: auto; padding: 14px; border: 1px solid var(--scene-line, #bfd0dc); border-radius: 9px; background: var(--scene-ui-bg, #f8fdff); box-shadow: 0 8px 24px #224c6a20; backdrop-filter: blur(12px); }
header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
header span { margin-left: 6px; font-variant-numeric: tabular-nums; }
button { color: inherit; font: inherit; cursor: pointer; background: transparent; border: 0; padding: 6px; }
button:focus-visible { outline: 2px solid #287c76; outline-offset: 2px; }
button:disabled { cursor: wait; opacity: .6; }
p { line-height: 1.6; margin: 10px 0 0; }
.drawer-hint { color: var(--scene-ui-muted, #698392); }
ul { padding: 0; list-style: none; margin: 8px 0 0; max-height: 178px; overflow: auto; }
li button { display: flex; align-items: start; gap: 8px; width: 100%; text-align: left; border-bottom: 1px solid var(--scene-line, #dbe4df); padding: 10px 0; }
li button:hover { background: #458ca414; }
li button > svg { margin-left: auto; flex-shrink: 0; }
.order-description { min-width: 0; }
.order-description strong { display: block; font-weight: 500; overflow-wrap: anywhere; line-height: 1.5; }
.order-description small { display: block; color: var(--scene-ui-muted, #698392); margin-top: 3px; }
.order-state { flex-shrink: 0; white-space: nowrap; color: #a83e36; }
.order-state.urgent { font-weight: 700; }
.unmapped-note { padding-top: 9px; border-top: 1px solid var(--scene-line, #dbe4df); }
.retry-button { text-decoration: underline; text-underline-offset: 3px; }
@media (max-width: 760px) { .order-drawer { max-height: 250px; padding: 12px; } ul { max-height: 140px; } }
</style>
