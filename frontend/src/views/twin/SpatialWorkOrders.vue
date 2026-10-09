<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useProjectStore } from '@/stores/project'
import { useAccessStore } from '@/stores/access'
import { workOrderApi } from '@/api/property'
import { ACTIVE_ORDER_STATUSES, PLAN_BUILDING, isUrgentWorkOrder, spatialWorkOrder } from './workOrderSpatial'
import TwinIcon from './TwinIcon.vue'
const props = defineProps({ floor: Number, requestedOrderId: [String, Number], selectedOrderId: [String, Number], planOpen: Boolean })
const emit = defineEmits(['points', 'locate', 'available', 'summary', 'toggle-plan', 'open-order'])
const project = useProjectStore(), access = useAccessStore()
const allowed = computed(() => Number(project.currentProjectId) === PLAN_BUILDING.projectId && (access.admin || access.paths.has('/property/workorder')))
const points = ref([]), activePoints = ref([]), activeCount = ref(0), urgentCount = ref(0), loading = ref(false), error = ref(''), unmapped = ref(0), unmappedUrgent = ref(0), truncated = ref(false), hasSnapshot = ref(false)
const expanded = ref(false), trigger = ref(null)
const regularCount = computed(() => activeCount.value - urgentCount.value)
const selected = computed(() => props.selectedOrderId == null ? null : points.value.find(point => Number(point.orderId) === Number(props.selectedOrderId)) || null)
const floorLabel = point => point.floor === -1 ? 'B1' : point.floor + 'F'
const assignee = point => point.values?.find(pair => pair[0] === '负责人')?.[1] || '待派单'
const summary = computed(() => activeCount.value ? `${activeCount.value}${truncated.value ? '+' : ''}项` : loading.value ? '载入中' : error.value ? '加载失败' : '暂无')
const triggerLabel = computed(() => activeCount.value ? `物业工单，待处理 ${activeCount.value}${truncated.value ? '+' : ''} 项，紧急 ${urgentCount.value} 项，常规 ${regularCount.value} 项，${expanded.value ? '收起' : '展开'}列表` : `物业工单，${loading.value ? '正在加载' : error.value ? '加载失败' : '暂无待处理工单'}`)
function toggle() { expanded.value = !expanded.value }
async function close() { expanded.value = false; await nextTick(); trigger.value?.focus() }
// Drawer motion: grow/shrink the real height so the rail cards below slide instead of jumping.
const DRAWER_MS = 220, DRAWER_EASE = 'cubic-bezier(.2, .8, .2, 1)'
const reducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
function animateDrawer(el, opening, done) {
  if (typeof el.animate !== 'function' || reducedMotion()) { done(); return }
  const height = el.getBoundingClientRect().height + 'px'
  const closed = { height: '0px', opacity: 0, paddingBottom: '0px' }, open = { height, opacity: 1 }
  el.style.overflow = 'hidden'
  const animation = el.animate(opening ? [closed, open] : [open, closed], { duration: DRAWER_MS, easing: DRAWER_EASE })
  let finished = false
  // A hidden page renders no frames, so animation events never arrive; finish on a timer instead.
  const finish = () => { if (finished) return; finished = true; clearTimeout(fallback); el.style.overflow = ''; done() }
  const fallback = setTimeout(() => { animation.cancel(); finish() }, DRAWER_MS + 150)
  animation.onfinish = animation.oncancel = finish
}
const onDrawerEnter = (el, done) => animateDrawer(el, true, done)
const onDrawerLeave = (el, done) => animateDrawer(el, false, done)
function expand() { expanded.value = true }
function collapse() { expanded.value = false }
defineExpose({ expand, collapse })
// The list stays open after locating, so the next order and the drawing remain one click away.
function locate(point) { emit('locate', point) }
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
watch([activeCount, urgentCount, truncated], ([active, urgent, partial]) => emit('summary', { active, urgent, truncated: partial }), { immediate: true })
onBeforeUnmount(() => { generation++ })
</script>
<template>
  <section v-if="allowed" class="spatial-orders" :class="{ 'is-expanded': expanded }" aria-label="物业工单" :aria-busy="loading" @keydown.esc.stop.prevent="close">
    <button ref="trigger" type="button" class="order-dock-toggle" :class="{ urgent: urgentCount > 0, warning: activeCount > 0 && !urgentCount }" :aria-label="triggerLabel" :aria-expanded="expanded" :aria-controls="expanded ? 'spatial-work-order-list' : undefined" @click="toggle">
      <span class="toggle-icon"><TwinIcon name="tool" :size="16" /></span><strong>物业工单</strong><span class="order-summary" :class="{ 'is-redundant': activeCount && !truncated }" aria-live="polite">{{ summary }}</span>
      <span v-if="activeCount" class="severity-pills"><span v-if="urgentCount" class="pill urgent">紧急 {{ urgentCount }}</span><span v-if="regularCount" class="pill warning">常规 {{ regularCount }}</span></span>
      <TwinIcon name="down" :size="13" class="toggle-chevron" :class="{ 'is-expanded': expanded }" />
    </button>
    <Transition :css="false" @enter="onDrawerEnter" @leave="onDrawerLeave">
    <div v-if="expanded" id="spatial-work-order-list" class="order-drawer" aria-label="全楼待处理工单">
      <div v-if="selected" class="order-selected" :class="selected.urgent ? 'urgent' : 'warning'" aria-label="当前工单">
        <small>{{ selected.code }} · {{ floorLabel(selected) }} · {{ selected.urgent ? '紧急' : '常规' }}</small>
        <strong>{{ selected.name }}</strong>
        <span>{{ selected.status }} · 负责人 {{ assignee(selected) }}</span>
        <div class="selected-actions"><button type="button" data-testid="order-toggle-plan" :aria-pressed="planOpen" @click="emit('toggle-plan')">{{ planOpen ? '收起图纸' : '对照图纸' }}</button><button type="button" class="primary" data-testid="order-open" @click="emit('open-order', selected)">打开工单</button></div>
      </div>
      <header><span>全楼待处理 · 点击定位</span><button type="button" :disabled="loading" @click="load">{{ loading ? '刷新中…' : '刷新' }}</button></header>
      <p v-if="error" role="alert">{{ error }}<button v-if="!loading" class="retry-button" type="button" @click="load">重试</button></p>
      <p v-else-if="loading" role="status">{{ hasSnapshot ? '正在刷新工单…' : '正在获取全楼工单…' }}</p>
      <p v-else-if="!activeCount">暂无待处理工单。</p>
      <ul v-if="activePoints.length"><li v-for="point in activePoints" :key="point.id"><button type="button" class="order-row" :class="[point.urgent ? 'urgent' : 'warning', { 'is-selected': selected?.id === point.id }]" :aria-current="selected?.id === point.id ? 'true' : undefined" @click="locate(point)"><TwinIcon name="alert" :size="14" /><span class="order-description"><strong :title="point.name">{{ floorLabel(point) }} · {{ point.name }}</strong><small>{{ point.urgent ? '紧急' : '常规' }} · {{ point.status }}</small></span></button></li></ul>
      <p v-if="unmapped" class="unmapped-note">{{ unmapped }} 条待处理工单需补充定位{{ unmappedUrgent ? '（其中紧急 ' + unmappedUrgent + ' 条）' : '' }}：缺少标注或图纸版本未匹配，请在工单中补充定位。</p>
      <p v-if="truncated">当前显示部分工单，请前往工单列表查看全部。</p>
    </div>
    </Transition>
  </section>
</template>
<style scoped>
.spatial-orders { --issue-red: #b34134; --issue-yellow: #946615; container-type: inline-size; position: relative; display: flex; flex-direction: column; max-height: 100%; min-height: 0; color: var(--scene-ui, #345a6e); font-size: 12px; border: 1px solid var(--scene-line, #bfd0dc); border-radius: 8px; background: rgba(250, 253, 255, .96); box-shadow: 0 6px 20px #224c6a24; overflow: hidden; }
:global(.is-night) .spatial-orders { --issue-red: #f49384; --issue-yellow: #edc56c; background: rgba(18, 40, 57, .95); }
.urgent { --issue-color: var(--issue-red); }
.warning { --issue-color: var(--issue-yellow); }
button { color: inherit; font: inherit; cursor: pointer; background: transparent; border: 0; padding: 6px; }
button:focus-visible { outline: 2px solid #287c76; outline-offset: 2px; }
button:disabled { cursor: wait; opacity: .6; }
.order-dock-toggle { position: relative; flex-shrink: 0; display: flex; align-items: center; gap: 7px; width: 100%; min-height: 48px; padding: 8px 12px; text-align: left; border-left: 4px solid var(--issue-color, var(--scene-line, #bfd0dc)); }
.order-dock-toggle:is(.urgent, .warning) { animation: dock-notice .8s ease-out; }
.order-dock-toggle:hover { background: color-mix(in srgb, var(--issue-color, #8fb3c4) 7%, transparent); }
.toggle-icon { display: grid; place-items: center; width: 28px; height: 28px; border-radius: 6px; color: #fff; background: var(--issue-color, #5b8296); flex-shrink: 0; }
.order-dock-toggle strong { font-size: 14px; font-weight: 700; white-space: nowrap; }
.order-summary { white-space: nowrap; font-weight: 600; font-variant-numeric: tabular-nums; color: var(--scene-ui-muted, #698392); }
.order-summary.is-redundant { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
.severity-pills { display: flex; gap: 4px; margin-left: auto; min-width: 0; }
.pill { padding: 2px 7px; border-radius: 10px; font-size: 11px; font-weight: 600; white-space: nowrap; color: var(--issue-color); border: 1px solid var(--issue-color); }
.pill.urgent { color: #fff; background: var(--issue-red); }
.toggle-chevron { flex-shrink: 0; margin-left: auto; transition: transform .22s cubic-bezier(.2, .8, .2, 1); }
.severity-pills + .toggle-chevron { margin-left: 0; }
.toggle-chevron.is-expanded { transform: rotate(180deg); }
.order-drawer { flex: 1 1 auto; min-height: 0; overflow: auto; padding: 0 11px 11px; border-top: 1px solid var(--scene-line, #dbe4df); }
.order-selected { display: grid; gap: 3px; margin: 10px 0 2px; padding: 9px 10px; border: 1px solid var(--issue-color); border-left-width: 4px; border-radius: 6px; background: color-mix(in srgb, var(--issue-color) 6%, transparent); }
.order-selected small { color: var(--scene-ui-muted, #698392); font-size: 11px; }
.order-selected strong { font-size: 14px; line-height: 1.4; }
.order-selected > span { font-size: 11px; }
.selected-actions { display: flex; gap: 6px; margin-top: 6px; }
.selected-actions button { flex: 1; padding: 6px 8px; border: 1px solid #9fbcc7; border-radius: 5px; font-size: 12px; background: #fff; }
.selected-actions button[aria-pressed=true] { background: #e3f0f2; border-color: #326d64; color: #326d64; }
.selected-actions .primary { color: #fff; background: #326d64; border-color: #326d64; }
header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 6px; color: var(--scene-ui-muted, #698392); font-size: 11px; }
p { line-height: 1.6; margin: 7px 0 0; font-size: 11px; }
ul { padding: 0; list-style: none; margin: 4px 0 0; }
li + li { margin-top: 5px; }
.order-row { display: flex; align-items: center; gap: 7px; width: 100%; text-align: left; border: 1px solid color-mix(in srgb, var(--issue-color) 55%, transparent); border-radius: 5px; padding: 7px; background: color-mix(in srgb, var(--issue-color) 4%, transparent); }
.order-row:hover { background: color-mix(in srgb, var(--issue-color) 12%, transparent); }
.order-row.is-selected { border-color: var(--issue-color); box-shadow: inset 3px 0 0 var(--issue-color); background: color-mix(in srgb, var(--issue-color) 14%, transparent); }
.order-row > svg { flex-shrink: 0; color: var(--issue-color); }
.order-description { flex: 1; min-width: 0; }
.order-description strong { display: block; font-size: 12px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; line-height: 1.5; }
.order-description small { display: block; color: var(--scene-ui-muted, #698392); margin-top: 2px; }
.unmapped-note { padding-top: 7px; border-top: 1px solid var(--scene-line, #dbe4df); }
.retry-button { text-decoration: underline; text-underline-offset: 3px; }
@keyframes dock-notice { from { box-shadow: inset 0 0 0 3px color-mix(in srgb, var(--issue-color) 20%, transparent); } to { box-shadow: none; } }
/* Narrow rail: drop the icon tile and tighten the pills so both counts stay readable. */
@container (max-width: 250px) {
  .toggle-icon { display: none; }
  .order-dock-toggle { gap: 5px; padding: 8px 9px; }
  .pill { padding: 1px 5px; }
}
@media (prefers-reduced-motion: reduce) { .order-dock-toggle:is(.urgent, .warning) { animation: none; } .toggle-chevron { transition: none; } }
</style>
