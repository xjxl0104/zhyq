<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WarehouseScene from './WarehouseScene.vue'
import SpatialWorkOrders from './SpatialWorkOrders.vue'
import FloorPlanViewer from '@/components/FloorPlanViewer.vue'
import { planFloor } from './floorPlanData'
import TwinIcon from './TwinIcon.vue'
import TwinOperationsPanel from './TwinOperationsPanel.vue'
import { FLOORS, MODULES, POINTS, PARK_REFERENCE, floorHeight, moduleDestination } from './twinData'
import './twin.css'
import './twin-dashboard.css'
import './twin-immersive.css'

const route = useRoute(), router = useRouter()
const preview = computed(() => Boolean(route.meta.twinPreview))
const moduleView = computed(() => MODULES.find(item => item.id === route.params.module))
const scene = ref(null), workspace = ref(null), sceneReady = ref(false), sceneFailed = ref(false)
const dockAvailable = ref(false), orderDock = ref(null), railOrdersSlot = ref(null), orderSummary = ref({ active: 0, urgent: 0, truncated: false })
const workOrders = ref([]), planLabels = ref(true), planOpen = ref(false)
const hasSession = Boolean(localStorage.getItem('zhyq_token'))
const mode = ref('exterior'), floor = ref(null), layers = ref(['property']), layersOpen = ref(false), rotating = ref(!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches), markers = ref(true)
const selectedPoint = ref(null), help = ref(false), toast = ref(''), sidebarOpen = ref(false)
// modelFocus hides the rails; ordersHidden is the user's own "专注模型": only the model, no work-order panel or drawing.
const modelFocus = ref(false), ordersHidden = ref(false)
const weather = ref('sunny'), viewpoint = ref('overview')
const weatherOptions = [{ id: 'sunny', name: '晴天', icon: 'sun' }, { id: 'night', name: '夜景', icon: 'moon' }]
function enterPureFocus() { modelFocus.value = true; ordersHidden.value = true; planOpen.value = false }
async function viewEntrance() { viewpoint.value = 'entrance'; mode.value = 'exterior'; enterPureFocus(); selectedPoint.value = null; await nextTick(); scene.value?.reset() }
function toggleModelFocus() {
  if (modelFocus.value) { modelFocus.value = false; ordersHidden.value = false; viewpoint.value = 'overview' }
  else enterPureFocus()
}
const currentTime = ref(new Date())
const averageOccupancy = computed(() => (FLOORS.filter(item => item.id > 0).reduce((sum, item) => sum + item.occupancy, 0) / FLOORS.filter(item => item.id > 0).length).toFixed(1))
const currentFloor = computed(() => FLOORS.find(item => item.id === floor.value))
const selectedModule = computed(() => MODULES.find(item => item.id === selectedPoint.value?.module))
const dateLabel = computed(() => currentTime.value.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }))
const moduleRows = computed(() => {
  if (!moduleView.value) return []
  const base = POINTS.find(point => point.module === moduleView.value.id)
  return Array.from({ length: 6 }, (_, index) => ({
    id: index ? base.code + '-' + String(index + 1).padStart(2, '0') : base.code,
    name: index ? moduleView.value.short + '示例' + String(index + 1).padStart(2, '0') : base.name,
    floor: index ? index + 1 : base.floor,
    location: '云仓 01 / ' + (index ? index + 1 : base.floor) + 'F / ' + ['A', 'B', 'C'][index % 3] + ' 区',
    status: index === 2 ? '待跟进' : base.status,
    point: base,
  }))
})
const homePath = computed(() => preview.value ? '/twin-preview' : '/dashboard')
let clockTimer, toastTimer, dialogFocusBefore = null
function showToast(message) { toast.value = message; clearTimeout(toastTimer); toastTimer = setTimeout(() => { toast.value = '' }, 4000) }
function goHome() { router.push(homePath.value); sidebarOpen.value = false }
function openModule(id) {
  const path = moduleDestination(id, preview.value)
  if (!path) return
  selectedPoint.value = null; sidebarOpen.value = false
  router.push(preview.value ? { path, query: floor.value ? { floor: String(floor.value) } : {} } : path)
}
function selectFloor(value) { floor.value = value; selectedPoint.value = null; if (value === -1) { mode.value = 'interior'; modelFocus.value = true; rotating.value = false } }
function setMode(value) {
  mode.value = value; viewpoint.value = 'overview'
  if (value !== 'exterior' && floor.value == null) floor.value = 3
  if (value === 'interior') { modelFocus.value = true; rotating.value = false }
  if (value !== 'interior' && floor.value === -1) floor.value = 1
  // A chosen work order survives view switches on its own floor, so its pin stays highlighted.
  if (!(selectedPoint.value?.orderId && selectedPoint.value.floor === floor.value)) selectedPoint.value = null
}
async function selectPoint(point) {
  selectedPoint.value = point; floor.value = point.floor
  if (point.orderId) {
    showLayer('property'); mode.value = 'interior'; modelFocus.value = true; ordersHidden.value = false; rotating.value = false; markers.value = true
    orderDock.value?.expand?.()
    await nextTick()
    if (selectedPoint.value?.id === point.id) scene.value?.focusPoint?.(point)
  }
}
function planView() { const point = selectedPoint.value; setMode('interior'); selectedPoint.value = point; viewpoint.value = 'plan' }
async function updateOrders(points) {
  workOrders.value = points
  const previous = selectedPoint.value
  if (!previous?.orderId) return
  const current = points.find(point => point.id === previous.id) || null
  selectedPoint.value = current
  if (current && mode.value === 'interior' && (current.floor !== previous.floor || current.localPosition?.join(',') !== previous.localPosition?.join(','))) await selectPoint(current)
}
function locatePanelPoint(point) { showLayer(point.module); selectPoint(point) }
function openPointModule(point) { if (point.orderId) { router.push({ path: '/property/workorder', query: { highlightId: String(point.orderId) } }); return }; floor.value = point.floor; openModule(point.module) }
// Layer panel works like Photoshop's: each business layer has its own eye; work orders are on by default.
function showLayer(id) { if (!layers.value.includes(id)) layers.value = [...layers.value, id] }
function toggleLayer(id) {
  layers.value = layers.value.includes(id) ? layers.value.filter(item => item !== id) : [...layers.value, id]
  if (selectedPoint.value && !layers.value.includes(selectedPoint.value.module)) selectedPoint.value = null
}
function toggleAllLayers() {
  const all = layers.value.length === MODULES.length
  layers.value = all ? [] : MODULES.map(item => item.id)
  if (all) selectedPoint.value = null
}
function closeLayersOnOutside(event) { if (!event.target.closest?.('.layer-panel, [data-testid="layers-toggle"]')) layersOpen.value = false }
watch(layersOpen, open => { if (open) window.addEventListener('pointerdown', closeLayersOnOutside); else window.removeEventListener('pointerdown', closeLayersOnOutside) })
// Floors are chosen on the model itself (hover highlights, click enters); inside, step between floors.
const FLOOR_ORDER = FLOORS.map(item => item.id).sort((a, b) => a - b)
const VIEWS = [{ id: 'exterior', icon: 'cube', name: '建筑外观', short: '外观' }, { id: 'exploded', icon: 'layers', name: '楼层展开', short: '展开' }, { id: 'interior', icon: 'eye', name: '室内空间', short: '室内' }]
function openPlanFromDock() { planOpen.value = !planOpen.value; if (planOpen.value) ordersHidden.value = false }
function enterFloor(value) { floor.value = value; setMode('interior') }
function stepFloor(direction) {
  const next = FLOOR_ORDER[FLOOR_ORDER.indexOf(floor.value) + direction]
  if (next != null) selectFloor(next)
}
async function fullScreen() {
  try {
    if (document.fullscreenElement) await document.exitFullscreen()
    else await workspace.value.requestFullscreen()
  } catch { showToast('当前浏览器不支持全屏，可使用浏览器的全屏功能。') }
}
function locateModuleRow(row) {
  floor.value = row.floor; showLayer(moduleView.value.id); mode.value = 'exploded'
  selectedPoint.value = { ...row.point, floor: row.floor, name: row.name, code: row.id, location: row.location }
  router.push({ path: homePath.value, query: { floor: String(row.floor) } })
}
function closeDialogs() { help.value = false; selectedPoint.value = null }
function onKey(event) {
  if (event.key === 'Escape') { closeDialogs(); sidebarOpen.value = false; layersOpen.value = false }
  // Keep keyboard focus inside the currently open modal.
  if (event.key === 'Tab' && help.value) {
    const modal = workspace.value?.querySelector('[aria-modal="true"]')
    const focusable = modal?.querySelectorAll('button, input, [tabindex="0"]')
    if (!focusable?.length) return
    const first = focusable[0], last = focusable[focusable.length - 1]
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
  }
}
watch(help, async open => {
  if (open) { dialogFocusBefore = document.activeElement; await nextTick(); workspace.value?.querySelector('[aria-modal="true"] input, [aria-modal="true"] button')?.focus() }
  else { dialogFocusBefore?.focus?.() }
})
// Live work orders replace the demo property card in the KPI strip; both open the same panel.
const ordersCountLabel = computed(() => orderSummary.value.active + (orderSummary.value.truncated ? '+' : ''))
function showOrders() { ordersHidden.value = false; orderDock.value?.expand?.() }
// The rail hosts the panel only while the rails sit beside the model (twin-main wider than 880px, as in the CSS);
// once they stack below it, the panel stays in the scene where it is still seen first.
const RAILS_BESIDE_MODEL = 881
const railsBeside = ref(true)
let mainObserver
function observeMainWidth() {
  const main = workspace.value?.querySelector('.twin-main')
  if (!main) return
  const style = getComputedStyle(main)
  railsBeside.value = main.clientWidth - (parseFloat(style.paddingLeft) || 0) - (parseFloat(style.paddingRight) || 0) >= RAILS_BESIDE_MODEL
  if (typeof ResizeObserver === 'undefined') return
  mainObserver = new ResizeObserver(([entry]) => { railsBeside.value = entry.contentRect.width >= RAILS_BESIDE_MODEL })
  mainObserver.observe(main)
}
watch(() => route.query.floor, value => {
  const numeric = Number(value)
  if (Number.isInteger(numeric) && FLOORS.some(item => item.id === numeric)) { if (selectedPoint.value && selectedPoint.value.floor !== numeric) selectedPoint.value = null; floor.value = numeric; if (numeric === -1 || route.query.workOrderId) { mode.value = 'interior'; modelFocus.value = true; rotating.value = false } }
}, { immediate: true })
onMounted(() => { observeMainWidth(); clockTimer = setInterval(() => { currentTime.value = new Date() }, 60000); window.addEventListener('keydown', onKey) })
onBeforeUnmount(() => { window.removeEventListener('pointerdown', closeLayersOnOutside); mainObserver?.disconnect(); clearInterval(clockTimer); clearTimeout(toastTimer); window.removeEventListener('keydown', onKey) })
</script>

<template>
  <div ref="workspace" class="twin-workspace twin-workspace-v2" :class="{ 'twin-embedded': !preview, 'is-night': weather === 'night' }">
    <aside v-if="preview" class="twin-sidebar" :class="{ 'sidebar-open': sidebarOpen }">
      <button class="twin-brand" aria-label="返回首页" @click="goHome"><span class="brand-symbol"><TwinIcon name="cube" :size="28" /></span><span>DIPARK<span class="brand-caption">智慧园区 · 空间连接未来</span></span></button>
      <div class="park-switch"><span class="park-switch-icon"><TwinIcon name="building" :size="18" /></span><span><strong>云仓产业园</strong><small>空间运营工作台</small></span><span class="park-switch-dot" /></div>
      <div class="nav-caption">工作空间 <span>WORKSPACE</span></div>
      <nav aria-label="主要导航">
        <button class="twin-nav-item" :class="{ active: !moduleView }" @click="goHome"><TwinIcon name="cube" /><span>首页</span><span class="nav-tag">3D</span></button>
        <button v-if="!preview" class="twin-nav-item" @click="router.push('/overview')"><TwinIcon name="grid" /><span>经营看板</span></button>
        <div class="nav-caption nav-caption-second">园区运营 <span>OPERATIONS</span></div>
        <button v-for="module in MODULES" :key="module.id" :data-testid="'module-' + module.id" class="twin-nav-item" :class="{ active: moduleView?.id === module.id }" @click="openModule(module.id)"><TwinIcon :name="module.icon" /><span>{{ module.name }}</span></button>
      </nav>
      <div class="sidebar-bottom"><div class="twin-side-note"><span class="side-note-orbit"><TwinIcon name="layers" :size="23" /></span><strong>让空间成为入口</strong><p>从一座建筑，连接园区的每一天。</p><button @click="help = true">探索三维工作台 <TwinIcon name="arrow" :size="14" /></button></div>
        <button class="sidebar-help" @click="help = true"><TwinIcon name="help" :size="18" /><span>操作指南</span><small>V 1.0</small></button>
        <div class="sidebar-user"><div class="twin-avatar">管</div><span><strong>{{ preview ? '本地体验空间' : '园区工作空间' }}</strong><small><i />{{ preview ? 'LOCAL PREVIEW' : 'DIGITAL TWIN' }}</small></span></div>
      </div>
    </aside>
    <div v-if="sidebarOpen" class="sidebar-scrim" @click="sidebarOpen = false" />

    <main class="twin-main">
      <template v-if="!moduleView">
        <section class="twin-heading">
          <div><div class="heading-kicker"><button v-if="preview" class="mobile-menu icon-button" aria-label="打开菜单" :aria-expanded="sidebarOpen" @click="sidebarOpen = !sidebarOpen"><TwinIcon name="menu" /></button><h1>云仓运营总览</h1><span class="demo-pill">演示数据</span></div><p>{{ preview ? '主楼平面图模型 · 地下室与 1–7 层' : '主楼按建筑平面图还原 · 选择楼层查看结构与工单位置' }}</p></div>
          <div class="heading-actions"><span class="twin-date">{{ dateLabel }}</span></div>
        </section>
        <section class="twin-kpi-strip" aria-label="园区资料与运营演示指标">
          <button class="twin-kpi" style="--kpi-color:#387eaa" @click="openModule('park')"><span class="kpi-icon"><TwinIcon name="building" :size="21" /></span><span><span class="kpi-caption">{{ currentFloor ? currentFloor.label + ' 资料层高' : '园区资料面积' }}</span><strong>{{ currentFloor ? floorHeight(currentFloor.id) : PARK_REFERENCE.totalArea }}<em>{{ currentFloor ? 'm' : 'm²' }}</em></strong></span><small class="kpi-note">招商资料</small></button>
          <button class="twin-kpi" style="--kpi-color:#208b7c" @click="openModule('park')"><span class="kpi-icon"><TwinIcon name="layers" :size="21" /></span><span><span class="kpi-caption">{{ currentFloor ? currentFloor.label + ' 出租率' : '空间出租率' }}</span><strong>{{ currentFloor ? (currentFloor.occupancy ?? '—') : averageOccupancy }}<em>%</em></strong></span><small class="kpi-note">演示</small></button>
          <button class="twin-kpi" style="--kpi-color:#8c79b1" @click="openModule('contract')"><span class="kpi-icon"><TwinIcon name="document" :size="21" /></span><span><span class="kpi-caption">执行中合同</span><strong>36<em>份</em></strong></span><small class="kpi-note">演示</small></button>
          <button v-if="dockAvailable" class="twin-kpi" data-testid="kpi-work-orders" :style="{ '--kpi-color': orderSummary.urgent ? '#b34134' : '#b38e55' }" @click="showOrders"><span class="kpi-icon"><TwinIcon name="tool" :size="21" /></span><span><span class="kpi-caption">待处理工单</span><strong>{{ ordersCountLabel }}<em>项</em></strong></span><small class="kpi-note" :class="{ 'kpi-urgent': orderSummary.urgent }">{{ orderSummary.urgent ? '紧急 ' + orderSummary.urgent : '实时' }}</small></button>
          <button v-else class="twin-kpi" style="--kpi-color:#b38e55" @click="openModule('property')"><span class="kpi-icon"><TwinIcon name="tool" :size="21" /></span><span><span class="kpi-caption">物业服务</span><strong>工单管理</strong></span><small class="kpi-note">进入</small></button>
        </section>

        <div class="twin-content-grid" :class="{ 'is-focused': modelFocus }">
          <div class="twin-world-scene"><WarehouseScene ref="scene" :mode="mode" :floor="floor" :layers="layers" :focused="modelFocus" :weather="weather" :viewpoint="viewpoint" :rotating="rotating" :markers="markers" :work-orders="workOrders" :selected-point="selectedPoint" :plan-labels="planLabels" @select-floor="enterFloor" @select-point="selectPoint" @open-module="openPointModule" @ready="sceneReady = true" @error="sceneFailed = true" /></div>
          <header class="environment-bar"><div class="environment-location"><TwinIcon name="pin" :size="14" /><strong>数智云仓产业园</strong><span>广州 · 花都炭步</span></div><div class="environment-actions"><div class="weather-switch" aria-label="天气场景"><button v-for="item in weatherOptions" :key="item.id" :data-testid="'weather-' + item.id" :aria-pressed="weather === item.id" :class="{ active: weather === item.id }" @click="weather = item.id"><TwinIcon :name="item.icon" :size="14" />{{ item.name }}</button></div></div></header>
          <aside class="twin-board-rail twin-board-left"><TwinOperationsPanel side="left" :show-issue-examples="false" :floor="floor" @open-module="openModule" @select-floor="selectFloor" @select-point="locatePanelPoint" /></aside>
          <section class="twin-viewport" :class="{ 'plan-open': planOpen && mode === 'interior', 'is-interior': mode === 'interior' }" aria-label="三维空间工作台">
            <div v-if="planOpen && mode === 'interior' && hasSession && !preview && !ordersHidden" class="plan-source-panel" aria-label="报修图纸"><header><strong>{{ currentFloor?.label }} · {{ planFloor(floor)?.drawing }}</strong><button class="icon-button" aria-label="收起图纸" @click="planOpen = false"><TwinIcon name="close" :size="16" /></button></header><FloorPlanViewer :file-id="selectedPoint?.planFileId || planFloor(floor)?.fileId" :point="selectedPoint?.planPoint" /></div>
            <div class="viewport-caption"><span>{{ mode === 'exterior' ? '一座建筑，一个运营入口' : mode === 'exploded' ? '逐层看见空间的价值' : '结构、设备与业务，在此相遇' }}</span><small>{{ mode === 'interior' ? '柱网 / 分区墙 / 楼梯 / 货梯井 / 前室' : dockAvailable ? '从左上角物业工单定位' : '点击建筑选择楼层，查看室内空间' }}</small></div>
            <nav class="scene-dock" aria-label="模型操作">
              <div class="dock-group dock-views" role="group" aria-label="模型视图"><button v-for="view in VIEWS" :key="view.id" :data-testid="'view-' + view.id" :class="{ active: mode === view.id }" :aria-pressed="mode === view.id" :title="view.name" @click="setMode(view.id)"><TwinIcon :name="view.icon" :size="15" /><span class="label-long">{{ view.name }}</span><span class="label-short">{{ view.short }}</span></button></div>
              <div v-if="mode === 'interior'" class="dock-group dock-interior" role="group" aria-label="室内楼层与图纸"><button data-testid="floor-down" aria-label="下一层" title="下一层" :disabled="floor === FLOOR_ORDER[0]" @click="stepFloor(-1)"><TwinIcon name="down" :size="15" /></button><span class="dock-floor" aria-live="polite">{{ currentFloor?.label }}</span><button data-testid="floor-up" aria-label="上一层" title="上一层" :disabled="floor === FLOOR_ORDER.at(-1)" @click="stepFloor(1)"><TwinIcon name="down" :size="15" class="icon-up" /></button><i class="dock-sep" /><button class="dock-text" data-testid="plan-view" title="俯视核对楼层结构" :class="{ active: viewpoint === 'plan' }" :aria-pressed="viewpoint === 'plan'" @click="planView">俯视</button><button class="dock-text" data-testid="plan-labels" title="显示空间名称" :class="{ active: planLabels }" :aria-pressed="planLabels" @click="planLabels = !planLabels">名称</button><button v-if="hasSession && !preview" class="dock-text" data-testid="plan-source" title="对照报修图纸" :class="{ active: planOpen }" :aria-pressed="planOpen" @click="openPlanFromDock">图纸</button></div>
              <div class="dock-group dock-tools">
                <button class="dock-zoom" aria-label="放大模型" title="放大" @click="scene?.zoom(1.18)"><TwinIcon name="plus" :size="17" /></button><button class="dock-zoom" aria-label="缩小模型" title="缩小" @click="scene?.zoom(1 / 1.18)"><TwinIcon name="minus" :size="17" /></button><button aria-label="复位视角" title="复位视角" @click="scene?.reset()"><TwinIcon name="reset" :size="16" /></button><button aria-label="自动环绕" title="自动环绕" :class="{ active: rotating }" :aria-pressed="rotating" @click="rotating = !rotating"><TwinIcon name="rotate" :size="17" /></button><i class="dock-sep" />
                <button data-testid="view-entrance" aria-label="入口视角" title="入口视角" :class="{ active: viewpoint === 'entrance' }" :aria-pressed="viewpoint === 'entrance'" @click="viewEntrance"><TwinIcon name="gate" :size="16" /></button><button class="dock-focus" :aria-label="modelFocus ? '恢复运营看板' : '专注查看模型'" :title="modelFocus ? '返回看板' : '专注模型'" :class="{ active: modelFocus }" :aria-pressed="modelFocus" @click="toggleModelFocus"><TwinIcon :name="modelFocus ? 'grid' : 'eye'" :size="16" /><span class="label-long">{{ modelFocus ? '返回看板' : '专注模型' }}</span></button><button aria-label="全屏查看" title="全屏查看" @click="fullScreen"><TwinIcon name="expand" :size="16" /></button>
              </div>
              <div class="dock-group dock-layers"><button data-testid="layers-toggle" aria-label="业务图层" :class="{ active: layersOpen }" :aria-expanded="layersOpen" @click="layersOpen = !layersOpen"><TwinIcon name="layers" :size="16" /><strong>业务图层</strong><span class="dock-count">{{ layers.length }}/{{ MODULES.length }}</span></button></div>
            </nav>
            <Transition name="layer-panel"><section v-if="layersOpen" class="layer-panel" aria-label="业务图层"><header><strong>图层</strong><button type="button" class="layer-all" @click="toggleAllLayers">{{ layers.length === MODULES.length ? '全部隐藏' : '全部显示' }}</button><button type="button" class="icon-button" aria-label="业务图层说明" @click="help = true"><TwinIcon name="help" :size="15" /></button></header><ul><li v-for="module in MODULES" :key="module.id"><button type="button" class="layer-row" :class="{ hidden: !layers.includes(module.id) }" :data-testid="'layer-' + module.id" :aria-pressed="layers.includes(module.id)" :style="{ '--layer-color': module.color }" @click="toggleLayer(module.id)"><span class="layer-eye"><TwinIcon name="eye" :size="15" /></span><span class="layer-swatch"><TwinIcon :name="module.icon" :size="14" /></span><span class="layer-name">{{ module.name }}</span><small>{{ module.id === 'property' ? (dockAvailable ? orderSummary.active + ' 单' : '') : '演示' }}</small></button></li></ul></section></Transition>
            <div v-if="!sceneReady || sceneFailed" class="viewport-bottom"><span><i />{{ sceneFailed ? '渲染不可用' : '正在加载场景' }}</span></div>
            <div v-if="!preview && hasSession" v-show="!ordersHidden" class="spatial-orders-host"><Teleport defer :to="railOrdersSlot || 'body'" :disabled="modelFocus || !railsBeside || !railOrdersSlot"><SpatialWorkOrders ref="orderDock" :floor="floor" :requested-order-id="route.query.workOrderId" :selected-order-id="selectedPoint?.orderId" :plan-open="planOpen && mode === 'interior'" @available="dockAvailable = $event" @summary="orderSummary = $event" @points="updateOrders" @locate="selectPoint" @toggle-plan="planOpen = !planOpen" @open-order="openPointModule" /></Teleport></div>

            <Transition name="twin-detail"><aside v-if="selectedPoint && !(selectedPoint.orderId && dockAvailable)" class="point-detail" :style="{ '--detail-color': selectedModule.color }" aria-label="空间点位详情"><div class="detail-header"><span class="detail-icon"><TwinIcon :name="selectedModule.icon" :size="22" /></span><span>{{ selectedModule.name }}</span><button class="icon-button" aria-label="关闭点位详情" @click="selectedPoint = null"><TwinIcon name="close" :size="17" /></button></div><small class="detail-code">{{ selectedPoint.code }}</small><h3>{{ selectedPoint.name }}</h3><p class="detail-location"><TwinIcon name="pin" :size="14" />{{ selectedPoint.location }}</p><span class="detail-status"><i />{{ selectedPoint.status }}</span><div v-if="selectedPoint.module === 'camera'" class="camera-placeholder"><TwinIcon name="camera" :size="28" /><strong>视频通道待接入</strong><small>连接后可查看实时画面</small></div><dl><div v-for="pair in selectedPoint.values" :key="pair[0]"><dt>{{ pair[0] }}</dt><dd>{{ pair[1] }}</dd></div></dl><div class="detail-data-note">{{ selectedPoint.orderId ? '真实工单 · 按报修图纸定位' : '示例点位 · 未连接现场业务数据' }}</div><button data-testid="point-open-module" class="twin-button primary detail-action" @click="openPointModule(selectedPoint)">{{ selectedPoint.orderId ? '打开工单' : '进入' + selectedModule.name }}<TwinIcon name="arrow" :size="16" /></button></aside></Transition>
          </section>

          <aside class="twin-board-rail twin-board-right"><div v-if="!preview && hasSession" ref="railOrdersSlot" class="rail-orders-slot" /><TwinOperationsPanel side="right" :show-issue-examples="false" :floor="floor" @open-module="openModule" @select-floor="selectFloor" @select-point="locatePanelPoint" /></aside>
        </div>
        <footer class="twin-footer"><span><span class="footer-logo">DIPARK</span>让园区更有生命力</span><span>建筑图纸模型 · 设备细节待现场核对<span class="footer-separator">/</span>业务数据为演示数据</span></footer>
      </template>

      <section v-else class="twin-module-view">
        <button class="module-back" @click="goHome"><TwinIcon name="arrow" :size="16" />返回三维园区</button>
        <div class="module-page-heading"><button v-if="preview" class="mobile-menu icon-button" aria-label="打开菜单" :aria-expanded="sidebarOpen" @click="sidebarOpen = !sidebarOpen"><TwinIcon name="menu" /></button><span class="module-page-icon" :style="{ color: moduleView.color }"><TwinIcon :name="moduleView.icon" :size="30" /></span><div><div class="twin-eyebrow">CONNECTED TO YOUR SPACE</div><h1>{{ moduleView.name }}</h1><p>{{ moduleView.description }}</p></div><span class="demo-pill">本地模块演示</span></div>
        <div class="module-context"><TwinIcon name="pin" :size="18" /><strong>云仓 01{{ floor ? ' / ' + floor + 'F' : ' / 全部楼层' }}</strong><span>从三维模型进入的业务空间</span><button class="twin-button" @click="router.push(moduleView.route)">打开系统正式模块<TwinIcon name="arrow" :size="15" /></button></div>
        <div class="module-demo-note"><TwinIcon name="help" :size="17" /><span>以下为本地演示记录，用于体验空间与业务的关联。正式模块使用现有登录和后端数据。</span></div>
        <div class="module-table-card"><div class="insight-heading"><h2>{{ moduleView.name }} · 空间关联记录</h2><span>6 条示例记录</span></div><table><thead><tr><th>编号</th><th>名称</th><th>关联空间</th><th>状态</th><th>空间操作</th></tr></thead><tbody><tr v-for="row in moduleRows" :key="row.id"><td class="record-code">{{ row.id }}</td><td>{{ row.name }}</td><td>{{ row.location }}</td><td><span class="table-status" :class="{ amber: row.status === '待跟进' }">{{ row.status }}</span></td><td><button class="table-locate" @click="locateModuleRow(row)"><TwinIcon name="pin" :size="14" />定位模型</button></td></tr></tbody></table></div>
        <div class="module-related"><TwinIcon name="cube" :size="38" /><div><strong>业务有了空间坐标</strong><p>返回模型查看所在楼层，也可以打开正式模块，继续完整的业务流程。</p></div><button class="twin-button primary" @click="goHome">回到云仓模型<TwinIcon name="arrow" :size="16" /></button></div>
      </section>
    </main>
    <div v-if="help" class="twin-modal-backdrop" @click.self="help = false"><section class="help-modal" role="dialog" aria-modal="true" aria-label="三维工作台操作指南"><header><span class="brand-symbol"><TwinIcon name="cube" :size="26" /></span><button class="icon-button" aria-label="关闭操作指南" @click="help = false"><TwinIcon name="close" /></button></header><div class="twin-eyebrow">EXPLORE YOUR SPACE</div><h2>以空间为起点。</h2><p>拖动建筑旋转视角，滚轮缩放，右键拖动平移。也可以用底部工具栏调整视图。</p><ol><li><strong>查看建筑与楼层</strong><span>鼠标移到建筑上会高亮所在楼层并显示层号，点击即进入该层室内。视图切换、缩放旋转、入口视角和专注模型都在模型底部的操作条里；室内用其中的上下箭头切换楼层。</span></li><li><strong>定位待处理工单</strong><span>展开左上角「物业工单」，点击工单进入对应楼层，可同时对照报修图纸。红色表示紧急或超时，黄色表示常规待处理。</span></li><li><strong>业务图层</strong><span>操作条右侧的「业务图层」可逐项显示或隐藏物业工单、监控、消防等点位，像 PS 图层一样点眼睛切换。</span></li><li><strong>从位置进入业务</strong><span>详情内点击「进入」打开关联模块，演示模块支持返回模型定位。</span></li></ol><div class="help-note">主楼结构按建筑图纸还原。工单位置来源于报修标注；运营数字仍为演示。室内可在底部操作条切换俯视、空间名称及图纸。</div><button class="twin-button primary" @click="help = false">开始探索<TwinIcon name="arrow" :size="16" /></button></section></div>
    <Transition name="twin-detail"><div v-if="toast" class="twin-toast" role="status"><TwinIcon name="check" :size="18" />{{ toast }}</div></Transition>
  </div>
</template>

<style scoped>
/* The 3D viewport keeps one zone per layer so nothing stacks:
   left = work-order panel, right = drawing / point detail, bottom = the model dock (views, tools, layers).
   --dock-reserve is the strip the dock occupies; panels above it end before it. */
.twin-workspace-v2 .twin-viewport { container: twin-viewport / inline-size; }
.twin-viewport > * { --dock-reserve: 62px; }
.spatial-orders-host { position: absolute; z-index: 8; top: 8px; left: 0; bottom: var(--dock-reserve); width: min(300px, 100%); display: flex; flex-direction: column; pointer-events: none; }
.spatial-orders-host > * { pointer-events: auto; }
.plan-source-panel { position: absolute; top: 8px; right: 0; width: min(520px, calc(100% - 300px - 16px)); max-height: calc(100% - 8px - var(--dock-reserve)); box-sizing: border-box; overflow: auto; padding: 10px 12px 12px; border: 1px solid var(--scene-line); border-radius: 8px; background: #fff; color: #28483f; box-shadow: 0 6px 20px #224c6a24; z-index: 7; }
.plan-source-panel header { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 4px; }
.twin-workspace-v2 .point-detail { top: 8px; right: 0; left: auto; width: min(300px, 100%); max-height: calc(100% - 8px - var(--dock-reserve)); box-sizing: border-box; }
.twin-workspace-v2 .is-focused .spatial-orders-host { left: 18px; width: min(300px, calc(100% - 36px)); }
.twin-workspace-v2 .is-focused :is(.plan-source-panel, .point-detail) { right: 18px; }
.twin-workspace-v2 .is-focused .plan-source-panel { width: min(560px, calc(100% - 36px - 300px - 16px)); }
.twin-workspace-v2 .viewport-bottom { bottom: calc(var(--dock-reserve) + 4px); }
.kpi-urgent { color: #b34134; font-weight: 700; }

/* Model dock: one bar along the bottom of the scene. */
.scene-dock { position: absolute; z-index: 9; left: 0; right: 0; bottom: 10px; display: flex; align-items: center; justify-content: space-between; gap: 8px; pointer-events: none; }
.twin-workspace-v2 .is-focused .scene-dock { left: 18px; right: 18px; }
.dock-group { display: flex; align-items: center; gap: 2px; height: 40px; padding: 0 4px; box-sizing: border-box; border: 1px solid var(--scene-line); border-radius: 10px; background: var(--scene-ui-bg); box-shadow: 0 6px 18px #18405a1c; backdrop-filter: blur(14px); pointer-events: auto; }
.dock-group button { display: inline-flex; align-items: center; justify-content: center; gap: 5px; flex-shrink: 0; min-width: 32px; height: 32px; padding: 0 7px; border: 0; border-radius: 7px; background: transparent; color: var(--scene-ui); font: inherit; font-size: 12px; white-space: nowrap; cursor: pointer; transition: background .15s, color .15s; }
.dock-group button:hover:not(:disabled) { background: #4dacc01f; }
.dock-group button.active { color: #fff; background: #337f92; }
.dock-group button:focus-visible { outline: 2px solid #287c76; outline-offset: 1px; }
.dock-group button:disabled { opacity: .35; cursor: default; }
.dock-views button { padding: 0 10px; color: var(--scene-ui-muted); }
.dock-views button.active { color: #fff; }
.dock-tools { justify-content: center; }
.dock-text { font-weight: 600; }
.dock-floor { min-width: 30px; text-align: center; font-size: 13px; font-weight: 700; color: var(--scene-ui); }
.dock-sep { width: 1px; height: 18px; margin: 0 4px; background: var(--scene-line); }
.scene-dock .icon-up { transform: rotate(180deg); }
.label-short { display: none; }
/* Business layers: its own labelled block at the right end of the dock. */
.dock-layers { border-color: color-mix(in srgb, #337f92 40%, var(--scene-line)); }
.dock-layers button { padding: 0 10px; font-size: 13px; }
.dock-layers strong { font-weight: 700; }
.dock-count { padding: 1px 6px; border-radius: 8px; background: #337f9222; font-size: 11px; font-variant-numeric: tabular-nums; }
.dock-layers button.active .dock-count { background: #ffffff33; }

/* Photoshop-style layer list, opening above the 业务图层 block. */
.layer-panel { position: absolute; z-index: 10; right: 0; bottom: calc(var(--dock-reserve) + 4px); width: 240px; padding: 8px; border: 1px solid var(--scene-line); border-radius: 10px; background: rgba(250, 253, 255, .97); color: var(--scene-ui); box-shadow: 0 10px 30px #18405a2e; backdrop-filter: blur(14px); }
.twin-workspace-v2 .is-focused .layer-panel { right: 18px; }
.layer-panel header { display: flex; align-items: center; gap: 6px; padding: 0 2px 6px; border-bottom: 1px solid var(--scene-line); }
.layer-panel header strong { margin-right: auto; font-size: 13px; }
.layer-panel .layer-all { padding: 3px 6px; border: 0; border-radius: 4px; background: transparent; color: #2a7f93; font-size: 11px; cursor: pointer; }
.layer-panel ul { margin: 6px 0 0; padding: 0; list-style: none; }
.layer-row { display: flex; align-items: center; gap: 8px; width: 100%; padding: 6px; border: 0; border-radius: 5px; background: transparent; color: inherit; font-size: 12px; text-align: left; cursor: pointer; transition: opacity .15s, background .15s; }
.layer-row:hover { background: color-mix(in srgb, var(--layer-color) 9%, transparent); }
.layer-row:focus-visible { outline: 2px solid #287c76; outline-offset: 1px; }
.layer-eye { display: grid; place-items: center; width: 22px; height: 22px; border: 1px solid var(--scene-line); border-radius: 4px; color: var(--scene-ui); }
.layer-swatch { display: grid; place-items: center; width: 22px; height: 22px; border-radius: 5px; color: #fff; background: var(--layer-color); }
.layer-name { flex: 1; font-weight: 600; }
.layer-row small { color: var(--scene-ui-muted); font-size: 11px; }
.layer-row.hidden .layer-eye svg { opacity: 0; }
.layer-row.hidden :is(.layer-swatch, .layer-name, small) { opacity: .4; }
.layer-panel-enter-active, .layer-panel-leave-active { transition: opacity .16s ease, transform .16s ease; }
.layer-panel-enter-from, .layer-panel-leave-to { opacity: 0; transform: translateY(6px); }
@media (prefers-reduced-motion: reduce) { .layer-panel-enter-active, .layer-panel-leave-active { transition: none; } }

/* Top bar: location and weather only. */
.twin-workspace-v2 .environment-actions { flex-wrap: nowrap; }
/* Dashboard: the work-order panel heads the right rail, above the device cards. */
.twin-workspace-v2 .twin-content-grid:not(.is-focused) .twin-board-right { display: flex; flex-direction: column; gap: 8px; }
.twin-workspace-v2 .twin-board-right > :last-child { flex: 1 1 auto; min-height: 0; height: auto; overflow: auto; }
.rail-orders-slot { flex: 0 1 auto; min-height: 0; max-height: 58%; display: flex; flex-direction: column; }
.rail-orders-slot:empty { display: none; }
@container twin-main (max-width: 880px) {
  .rail-orders-slot { max-height: none; }
}

/* Dock sizes: one row while everything fits; then short view names; then rows —
   views + 业务图层 on top, the interior group and tools below (three rows on phones inside). */
.dock-views { grid-area: views; justify-self: start; }
.dock-layers { grid-area: layers; justify-self: end; }
.dock-interior { grid-area: interior; justify-self: center; }
.dock-tools { grid-area: tools; justify-self: center; }
@container twin-viewport (max-width: 979px) {
  .dock-views .label-long, .dock-focus .label-long { display: none; }
  .dock-views .label-short { display: inline; }
  .is-interior .scene-dock { display: grid; grid-template-columns: 1fr 1fr; grid-template-areas: "views layers" "interior tools"; gap: 6px; }
  .is-interior .dock-interior { justify-self: start; }
  .is-interior .dock-tools { justify-self: end; }
  .is-interior > * { --dock-reserve: 108px; }
}
@container twin-viewport (max-width: 599px) {
  .scene-dock { display: grid; grid-template-columns: 1fr 1fr; grid-template-areas: "views layers" "tools tools"; gap: 6px; }
  .twin-viewport > * { --dock-reserve: 108px; }
  .is-interior .scene-dock { grid-template-areas: "views layers" "interior interior" "tools tools"; }
  .is-interior .dock-interior, .is-interior .dock-tools { justify-self: center; }
  .is-interior > * { --dock-reserve: 154px; }
}
@container twin-viewport (max-width: 459px) {
  .dock-zoom { display: none; }
  .dock-group button { min-width: 30px; padding: 0 6px; }
}
/* The 业务图层 name always stays; only its count gives way on the narrowest screens. */
@container twin-viewport (max-width: 359px) { .dock-count { display: none; } }
/* Too narrow for panel and drawing side by side: the drawing spans the viewport below the panel header,
   and the panel keeps only its header (with counts) while the drawing is open. */
@container twin-viewport (max-width: 759px) {
  .twin-workspace-v2 .twin-viewport .plan-source-panel { top: 64px; left: 0; right: 0; width: auto; max-height: calc(100% - 64px - var(--dock-reserve)); }
  .twin-workspace-v2 .is-focused .twin-viewport .plan-source-panel { left: 18px; }
  .plan-open .spatial-orders-host :deep(.order-drawer) { display: none; }
}
</style>
