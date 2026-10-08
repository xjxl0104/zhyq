<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useProjectStore } from '@/stores/project'
import { useAccessStore } from '@/stores/access'
import { workOrderApi } from '@/api/property'
import { ACTIVE_ORDER_STATUSES, PLAN_BUILDING, isUrgentWorkOrder, spatialWorkOrder } from './workOrderSpatial'
import TwinIcon from './TwinIcon.vue'
const props = defineProps({ floor: Number, requestedOrderId: [String, Number] })
const emit = defineEmits(['points', 'locate', 'available'])
const project = useProjectStore(), access = useAccessStore()
const allowed = computed(() => Number(project.currentProjectId) === PLAN_BUILDING.projectId && (access.admin || access.paths.has('/property/workorder')))
const points = ref([]), activePoints = ref([]), activeCount = ref(0), urgentCount = ref(0), loading = ref(false), error = ref(''), unmapped = ref(0), unmappedUrgent = ref(0), truncated = ref(false), hasSnapshot = ref(false)
const expanded = ref(false), trigger = ref(null)
const regularCount = computed(() => activeCount.value - urgentCount.value)
const summary = computed(() => activeCount.value ? `${activeCount.value}${truncated.value ? '+' : ''}项` : loading.value ? '载入中' : error.value ? '加载失败' : '暂无')
const triggerLabel = computed(() => activeCount.value ? `问题提醒，待处理 ${activeCount.value}${truncated.value ? '+' : ''} 项，紧急 ${urgentCount.value} 项，常规 ${regularCount.value} 项，${expanded.value ? '收起' : '展开'}列表` : `问题提醒，${loading.value ? '正在加载' : error.value ? '加载失败' : '暂无待处理工单'}`)
function toggle() { expanded.value = !expanded.value }
async function close() { expanded.value = false; await nextTick(); trigger.value?.focus() }
function locate(point) { emit('locate', point); close() }
let generation = 0, handledRequestedId = null
async function load() {
  const run = ++generation
  error.value = ''
  if (!allowed.value) {
    points.value = []; activePoints.value = []; activeCount.value = 0; urgentCount.value = 0; unmapped.value = 0; unmappedUrgent.value = 0; truncated.value = false; hasSnapshot.value = false; loading.value = false; expanded.value = false; handledRequestedId = null
    emit('points', [])
    return
  }
  loading.value = true
  try {
    const rows = []; let limited = false
    // Scope on the server before pagination. Completed orders do not crowd out live work.
    for (const status of ACTIVE_ORDER_STATUSES) {
      for (let pageNo = 1; pageNo <= 10; pageNo++) {
        const result = await workOrderApi.page({ projectId: PLAN_BUILDING.projectId, buildingId: PLAN_BUILDING.buildingId, status, pageNo, pageSize: 100 })
        if (run !== generation) return
        const page = result?.records || []
        rows.push(...page)
        if (pageNo * 100 >= (result?.total || 0) || !page.length) break
        if (pageNo === 10) limited = true
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
    urgentCount.value = active.filter(isUrgentWorkOrder).length
    const activeIds = new Set(active.map(row => Number(row.id)))
    activePoints.value = points.value.filter(point => activeIds.has(Number(point.orderId))).sort((a, b) => Number(b.urgent) - Number(a.urgent))
    unmapped.value = active.length - activePoints.value.length
    const mappedIds = new Set(activePoints.value.map(point => Number(point.orderId)))
    unmappedUrgent.value = active.filter(row => !mappedIds.has(Number(row.id)) && isUrgentWorkOrder(row)).length
    truncated.value = limited; hasSnapshot.value = true
    emit('points', points.value)
    if (requested > 0 && requested !== handledRequestedId) {
      const point = points.value.find(item => Number(item.orderId) === requested)
      if (point) { handledRequestedId = requested; emit('locate', point) }
      else error.value = '该工单不存在、无权查看，或图纸位置尚未匹配。'
    } else if (!(requested > 0)) handledRequestedId = null
  } catch { if (run === generation) error.value = hasSnapshot.value ? '工单刷新失败，保留上次结果，请重试。' : '工单位置加载失败，请重试。' }
  finally { if (run === generation) loading.value = false }
}
watch(() => [allowed.value, props.requestedOrderId], load, { immediate: true })
watch(allowed, value => emit('available', value), { immediate: true })
onBeforeUnmount(() => { generation++ })
</script>
<template>
  <section v-if="allowed" class="spatial-orders" aria-label="工单问题提醒" :aria-busy="loading" @keydown.esc.stop.prevent="close">
    <div v-if="expanded" id="spatial-work-order-list" class="order-drawer" aria-label="全楼待处理工单">
      <header><strong>全楼工单</strong><button type="button" :disabled="loading" @click="load">{{ loading ? '刷新中…' : '刷新' }}</button></header>
      <p v-if="error" role="alert">{{ error }}<button v-if="!loading" class="retry-button" type="button" @click="load">重试</button></p>
      <p v-else-if="loading" role="status">{{ hasSnapshot ? '正在刷新工单…' : '正在获取全楼工单…' }}</p>
      <p v-else-if="!activeCount">暂无待处理工单。</p>
      <div v-if="activeCount" class="severity-summary"><span v-if="urgentCount" class="urgent">紧急 {{ urgentCount }}</span><span v-if="regularCount" class="warning">常规 {{ regularCount }}</span><small>点击定位</small></div>
      <ul v-if="activePoints.length"><li v-for="point in activePoints" :key="point.id"><button type="button" class="order-row" :class="point.urgent ? 'urgent' : 'warning'" @click="locate(point)"><TwinIcon name="alert" :size="14" /><span class="order-description"><strong :title="point.name">{{ point.floor === -1 ? 'B1' : point.floor + 'F' }} · {{ point.name }}</strong><small>{{ point.urgent ? '紧急' : '常规' }} · {{ point.status }}</small></span></button></li></ul>
      <p v-if="unmapped" class="unmapped-note">{{ unmapped }} 条待处理工单需补充定位{{ unmappedUrgent ? '（其中紧急 ' + unmappedUrgent + ' 条）' : '' }}：缺少标注或图纸版本未匹配，请在工单中补充定位。</p>
      <p v-if="truncated">当前显示部分工单，请前往工单列表查看全部。</p>
    </div>
    <button ref="trigger" type="button" class="order-dock-toggle" :class="{ urgent: urgentCount > 0, warning: activeCount > 0 && !urgentCount }" :aria-label="triggerLabel" :aria-expanded="expanded" :aria-controls="expanded ? 'spatial-work-order-list' : undefined" @click="toggle">
      <TwinIcon name="alert" :size="16" /><strong>问题提醒</strong><span class="order-summary" aria-live="polite">{{ summary }}</span><TwinIcon name="down" :size="13" :class="{ 'is-expanded': expanded }" />
    </button>
  </section>
</template>
<style scoped>
.spatial-orders { --issue-red: #b34134; --issue-yellow: #946615; position: relative; color: var(--scene-ui, #345a6e); font-size: 12px; }
:global(.is-night) .spatial-orders { --issue-red: #f49384; --issue-yellow: #edc56c; }
.order-dock-toggle { display: flex; align-items: center; gap: 7px; min-height: 44px; padding: 8px 10px; border: 1px solid var(--scene-line, #bfd0dc); border-radius: 6px; background: var(--scene-ui-bg, #f8fdff); }
.order-dock-toggle strong { font-weight: 600; white-space: nowrap; }
.urgent { --issue-color: var(--issue-red); }
.warning { --issue-color: var(--issue-yellow); }
.order-dock-toggle:is(.urgent, .warning) { color: var(--issue-color); border-color: var(--issue-color); background: color-mix(in srgb, var(--issue-color) 6%, var(--scene-ui-bg, #f8fdff)); animation: dock-notice .8s ease-out; }
.order-dock-toggle:hover { filter: brightness(.97); }
.order-dock-toggle .is-expanded { transform: rotate(180deg); }
.order-summary { white-space: nowrap; font-weight: 600; font-variant-numeric: tabular-nums; }
.order-drawer { position: absolute; bottom: calc(100% + 7px); left: 0; width: min(260px, 100%); box-sizing: border-box; max-height: 260px; overflow: auto; padding: 11px; border: 1px solid var(--scene-line, #bfd0dc); border-radius: 7px; background: var(--scene-ui-bg, #f8fdff); box-shadow: 0 5px 18px #224c6a20; backdrop-filter: blur(12px); }
header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
header span { margin-left: 6px; font-variant-numeric: tabular-nums; }
button { color: inherit; font: inherit; cursor: pointer; background: transparent; border: 0; padding: 6px; }
button:focus-visible { outline: 2px solid #287c76; outline-offset: 2px; }
button:disabled { cursor: wait; opacity: .6; }
p { line-height: 1.6; margin: 7px 0 0; font-size: 11px; }
.severity-summary { display: flex; align-items: center; gap: 10px; margin-top: 6px; font-size: 11px; }
.severity-summary > span { color: var(--issue-color); }
.severity-summary small { margin-left: auto; color: var(--scene-ui-muted, #698392); }
ul { padding: 0; list-style: none; margin: 7px 0 0; }
li + li { margin-top: 5px; }
.order-row { display: flex; align-items: center; gap: 7px; width: 100%; text-align: left; border: 1px solid color-mix(in srgb, var(--issue-color) 65%, transparent); border-radius: 4px; padding: 7px; background: color-mix(in srgb, var(--issue-color) 5%, transparent); }
.order-row:hover { background: color-mix(in srgb, var(--issue-color) 12%, transparent); }
.order-row > svg { flex-shrink: 0; color: var(--issue-color); }
.order-description { flex: 1; min-width: 0; }
.order-description strong { display: block; font-size: 11px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; line-height: 1.5; }
.order-description small { display: block; color: var(--scene-ui-muted, #698392); margin-top: 3px; }
.unmapped-note { padding-top: 7px; border-top: 1px solid var(--scene-line, #dbe4df); }
.retry-button { text-decoration: underline; text-underline-offset: 3px; }
@keyframes dock-notice { from { box-shadow: 0 0 0 3px color-mix(in srgb, var(--issue-color) 20%, transparent); } to { box-shadow: 0 0 0 0 transparent; } }
@media (prefers-reduced-motion: reduce) { .order-dock-toggle:is(.urgent, .warning) { animation: none; } }
@media (max-width: 760px) { .order-drawer { max-height: 230px; } }
</style>
