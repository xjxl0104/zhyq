<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WarehouseScene from './WarehouseScene.vue'
import SpatialWorkOrders from './SpatialWorkOrders.vue'
import FloorPlanViewer from '@/components/FloorPlanViewer.vue'
import { planFloor } from './floorPlanData'
import TwinIcon from './TwinIcon.vue'
import TwinOperationsPanel from './TwinOperationsPanel.vue'
import { FLOORS, MODULES, POINTS, REFERENCES, PARK_REFERENCE, floorHeight, moduleDestination } from './twinData'
import './twin.css'
import './twin-dashboard.css'
import './twin-immersive.css'

const route = useRoute(), router = useRouter()
const preview = computed(() => Boolean(route.meta.twinPreview))
const moduleView = computed(() => MODULES.find(item => item.id === route.params.module))
const scene = ref(null), workspace = ref(null), sceneReady = ref(false), sceneFailed = ref(false)
const workOrders = ref([]), planLabels = ref(true), planOpen = ref(false)
const hasSession = Boolean(localStorage.getItem('zhyq_token'))
const mode = ref('exterior'), floor = ref(null), layer = ref('all'), rotating = ref(!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches), markers = ref(true)
const selectedPoint = ref(null), gallery = ref(false), referenceIndex = ref(0), help = ref(false), exporting = ref(false), toast = ref(''), sidebarOpen = ref(false)
const modelFocus = ref(false)
const weather = ref('sunny'), viewpoint = ref('overview')
const weatherOptions = [{ id: 'sunny', name: '晴天', icon: 'sun' }, { id: 'night', name: '夜景', icon: 'moon' }]
async function viewEntrance() { viewpoint.value = 'entrance'; mode.value = 'exterior'; modelFocus.value = true; selectedPoint.value = null; await nextTick(); scene.value?.reset() }
function toggleModelFocus() { modelFocus.value = !modelFocus.value; if (!modelFocus.value) viewpoint.value = 'overview' }
const currentTime = ref(new Date())
const averageOccupancy = computed(() => (FLOORS.filter(item => item.id > 0).reduce((sum, item) => sum + item.occupancy, 0) / FLOORS.filter(item => item.id > 0).length).toFixed(1))
const currentFloor = computed(() => FLOORS.find(item => item.id === floor.value))
const activeReference = computed(() => REFERENCES[referenceIndex.value])
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
  selectedPoint.value = null
}
async function selectPoint(point) {
  selectedPoint.value = point; floor.value = point.floor
  if (point.orderId) {
    layer.value = 'property'; mode.value = 'interior'; modelFocus.value = true; rotating.value = false; markers.value = true; planOpen.value = false
    await nextTick()
    if (selectedPoint.value?.id === point.id) scene.value?.focusPoint?.(point)
  }
}
function planView() { const point = selectedPoint.value; setMode('interior'); selectedPoint.value = point; viewpoint.value = 'plan' }
function updateOrders(points) { workOrders.value = points; if (selectedPoint.value?.orderId) selectedPoint.value = points.find(point => point.id === selectedPoint.value.id) || null }
function locatePanelPoint(point) { layer.value = point.module; selectPoint(point) }
function openPointModule(point) { if (point.orderId) { router.push({ path: '/property/workorder', query: { highlightId: String(point.orderId) } }); return }; floor.value = point.floor; openModule(point.module) }
function setLayer(id) { layer.value = id; selectedPoint.value = null }
function resetScene() { viewpoint.value = 'overview'; floor.value = null; layer.value = 'all'; mode.value = 'exterior'; selectedPoint.value = null; rotating.value = false; scene.value?.reset() }
async function fullScreen() {
  try {
    if (document.fullscreenElement) await document.exitFullscreen()
    else await workspace.value.requestFullscreen()
  } catch { showToast('当前浏览器不支持全屏，可使用浏览器的全屏功能。') }
}
async function exportModel() {
  if (!sceneReady.value || !scene.value) return
  exporting.value = true
  try { await scene.value.exportModel(); showToast('已导出当前视图的 GLB 模型，可在三维软件中打开。') }
  catch (error) { console.error(error); showToast('模型导出失败，请重试。') }
  finally { exporting.value = false }
}
function locateModuleRow(row) {
  floor.value = row.floor; layer.value = moduleView.value.id; mode.value = 'exploded'
  selectedPoint.value = { ...row.point, floor: row.floor, name: row.name, code: row.id, location: row.location }
  router.push({ path: homePath.value, query: { floor: String(row.floor) } })
}
function closeDialogs() { gallery.value = false; help.value = false; selectedPoint.value = null }
function onKey(event) {
  if (event.key === 'Escape') { closeDialogs(); sidebarOpen.value = false }
  if (gallery.value && event.key === 'ArrowRight') referenceIndex.value = (referenceIndex.value + 1) % REFERENCES.length
  if (gallery.value && event.key === 'ArrowLeft') referenceIndex.value = (referenceIndex.value + REFERENCES.length - 1) % REFERENCES.length
  // Keep keyboard focus inside the currently open modal.
  if (event.key === 'Tab' && (gallery.value || help.value)) {
    const modal = workspace.value?.querySelector('[aria-modal="true"]')
    const focusable = modal?.querySelectorAll('button, input, [tabindex="0"]')
    if (!focusable?.length) return
    const first = focusable[0], last = focusable[focusable.length - 1]
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
  }
}
watch(() => [gallery.value, help.value].some(Boolean), async open => {
  if (open) { dialogFocusBefore = document.activeElement; await nextTick(); workspace.value?.querySelector('[aria-modal="true"] input, [aria-modal="true"] button')?.focus() }
  else { dialogFocusBefore?.focus?.() }
})
watch(() => route.query.floor, value => {
  const numeric = Number(value)
  if (Number.isInteger(numeric) && FLOORS.some(item => item.id === numeric)) { floor.value = numeric; if (numeric === -1 || route.query.workOrderId) { mode.value = 'interior'; modelFocus.value = true; rotating.value = false } }
}, { immediate: true })
onMounted(() => { clockTimer = setInterval(() => { currentTime.value = new Date() }, 60000); window.addEventListener('keydown', onKey) })
onBeforeUnmount(() => { clearInterval(clockTimer); clearTimeout(toastTimer); window.removeEventListener('keydown', onKey) })
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
        <button v-for="module in MODULES" :key="module.id" :data-testid="'module-' + module.id" class="twin-nav-item" :class="{ active: moduleView?.id === module.id }" @click="openModule(module.id)"><TwinIcon :name="module.icon" /><span>{{ module.name }}</span><span v-if="module.id === 'property'" class="nav-count">12</span></button>
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
          <div class="heading-actions"><span class="twin-date">{{ dateLabel }}</span><button class="twin-button" @click="gallery = true"><TwinIcon name="photo" :size="15" />实景对照</button><button class="twin-button primary" :disabled="!sceneReady || exporting || sceneFailed" @click="exportModel"><TwinIcon name="export" :size="15" />{{ exporting ? '导出中…' : '导出模型' }}</button></div>
        </section>
        <section class="twin-kpi-strip" aria-label="园区资料与运营演示指标">
          <button class="twin-kpi" style="--kpi-color:#387eaa" @click="openModule('park')"><span class="kpi-icon"><TwinIcon name="building" :size="21" /></span><span><span class="kpi-caption">{{ currentFloor ? currentFloor.label + ' 资料层高' : '园区资料面积' }}</span><strong>{{ currentFloor ? floorHeight(currentFloor.id) : PARK_REFERENCE.totalArea }}<em>{{ currentFloor ? 'm' : 'm²' }}</em></strong></span><small class="kpi-note">招商资料</small></button>
          <button class="twin-kpi" style="--kpi-color:#208b7c" @click="openModule('park')"><span class="kpi-icon"><TwinIcon name="layers" :size="21" /></span><span><span class="kpi-caption">{{ currentFloor ? currentFloor.label + ' 出租率' : '空间出租率' }}</span><strong>{{ currentFloor ? (currentFloor.occupancy ?? '—') : averageOccupancy }}<em>%</em></strong></span><small class="kpi-note">演示</small></button>
          <button class="twin-kpi" style="--kpi-color:#8c79b1" @click="openModule('contract')"><span class="kpi-icon"><TwinIcon name="document" :size="21" /></span><span><span class="kpi-caption">执行中合同</span><strong>36<em>份</em></strong></span><small class="kpi-note">演示</small></button>
          <button class="twin-kpi" style="--kpi-color:#b38e55" @click="openModule('property')"><span class="kpi-icon"><TwinIcon name="tool" :size="21" /></span><span><span class="kpi-caption">物业待办</span><strong>12<em>项</em></strong></span><small class="kpi-note">演示</small></button>
        </section>

        <div class="twin-content-grid" :class="{ 'is-focused': modelFocus }">
          <div class="twin-world-scene"><WarehouseScene ref="scene" :mode="mode" :floor="floor" :layer="layer" :focused="modelFocus" :weather="weather" :viewpoint="viewpoint" :rotating="rotating" :markers="markers" :work-orders="workOrders" :plan-labels="planLabels" @select-floor="selectFloor" @select-point="selectPoint" @open-module="openPointModule" @ready="sceneReady = true" @error="sceneFailed = true" /></div>
          <header class="environment-bar"><div class="environment-location"><TwinIcon name="pin" :size="14" /><strong>数智云仓产业园</strong><span>广州 · 花都炭步</span></div><div class="environment-actions"><span class="weather-simulation">天气模拟</span><div class="weather-switch" aria-label="天气场景"><button v-for="item in weatherOptions" :key="item.id" :data-testid="'weather-' + item.id" :aria-pressed="weather === item.id" :class="{ active: weather === item.id }" @click="weather = item.id"><TwinIcon :name="item.icon" :size="14" />{{ item.name }}</button></div><button class="model-focus-toggle" :aria-label="modelFocus ? '恢复运营看板' : '专注查看模型'" :aria-pressed="modelFocus" @click="toggleModelFocus"><TwinIcon :name="modelFocus ? 'grid' : 'expand'" :size="13" />{{ modelFocus ? '返回看板' : '专注模型' }}</button><button class="entrance-view" data-testid="view-entrance" :aria-pressed="viewpoint === 'entrance'" @click="viewEntrance"><TwinIcon name="gate" :size="15" />入口视角</button></div></header>
          <aside class="twin-board-rail twin-board-left"><TwinOperationsPanel side="left" :floor="floor" @open-module="openModule" @select-floor="selectFloor" @select-point="locatePanelPoint" /></aside>
          <section class="twin-viewport" :class="{ 'has-work-order-dock': !preview && hasSession }" aria-label="三维空间工作台">
            <div class="viewport-top"><div class="view-switch" aria-label="模型视图"><button v-for="view in [{ id: 'exterior', icon: 'cube', name: '建筑外观' }, { id: 'exploded', icon: 'layers', name: '楼层展开' }, { id: 'interior', icon: 'eye', name: '室内空间' }]" :key="view.id" :data-testid="'view-' + view.id" :class="{ active: mode === view.id }" :aria-pressed="mode === view.id" @click="setMode(view.id)"><TwinIcon :name="view.icon" :size="15" /><span>{{ view.name }}</span></button></div></div>
            <div v-if="mode === 'interior'" class="plan-tools-bar" aria-label="楼层结构核对">
              <button type="button" :aria-pressed="viewpoint === 'plan'" @click="planView">俯视核对</button>
              <button type="button" :aria-pressed="planLabels" @click="planLabels = !planLabels">空间名称</button>
              <button v-if="hasSession && !preview" type="button" :aria-expanded="planOpen" @click="planOpen = !planOpen">{{ planOpen ? '收起图纸' : '对照原图' }}</button>
            </div>
            <div v-if="planOpen && mode === 'interior' && hasSession && !preview" class="plan-source-panel"><strong>{{ currentFloor?.label }} · {{ planFloor(floor)?.drawing }}</strong><FloorPlanViewer :file-id="selectedPoint?.planFileId || planFloor(floor)?.fileId" :point="selectedPoint?.planPoint" /></div>
            <div class="viewport-caption"><span>{{ mode === 'exterior' ? '一座建筑，一个运营入口' : mode === 'exploded' ? '逐层看见空间的价值' : '结构、设备与业务，在此相遇' }}</span><small>{{ mode === 'interior' ? '柱网 / 分区墙 / 楼梯 / 货梯井 / 前室' : '点击建筑选择楼层，点击点位关联业务' }}</small></div>
            <div class="scene-compass" aria-hidden="true"><span>7轴</span><div class="compass-diamond" /><small>Q <span>A</span></small></div>
            <div class="floor-picker" aria-label="楼层选择"><span>楼层</span><button :class="{ active: floor == null }" :aria-pressed="floor == null" aria-label="查看全部楼层" @click="resetScene">全部</button><div class="floor-divider" /><button v-for="item in [...FLOORS].sort((a, b) => b.id - a.id)" :key="item.id" :data-testid="'floor-' + item.id" :aria-pressed="floor === item.id" :class="{ active: floor === item.id }" @click="selectFloor(item.id)">{{ item.label }}</button></div>
            <div v-if="currentFloor && !selectedPoint" class="floor-selection-card"><span class="selected-floor-number">{{ currentFloor.label }}</span><div><strong>{{ currentFloor.name }}</strong><small>{{ floorHeight(currentFloor.id) }} m 层高 · {{ planFloor(currentFloor.id)?.drawing }}</small></div><button class="icon-button" aria-label="查看所选楼层室内" @click="setMode('interior')"><TwinIcon name="arrow" :size="18" /></button></div>
            <div class="scene-controls"><div><button aria-label="放大模型" title="放大" @click="scene?.zoom(1.18)"><TwinIcon name="plus" :size="18" /></button><button aria-label="缩小模型" title="缩小" @click="scene?.zoom(1 / 1.18)"><TwinIcon name="minus" :size="18" /></button><span /><button aria-label="复位视角" title="复位视角" @click="scene?.reset()"><TwinIcon name="reset" :size="17" /></button><button aria-label="自动环绕" title="自动环绕" :class="{ active: rotating }" :aria-pressed="rotating" @click="rotating = !rotating"><TwinIcon name="rotate" :size="18" /></button><button aria-label="显示业务点位" title="显示业务点位" :class="{ active: markers }" :aria-pressed="markers" @click="markers = !markers"><TwinIcon name="pin" :size="17" /></button><span /><button aria-label="全屏查看" title="全屏查看" @click="fullScreen"><TwinIcon name="expand" :size="17" /></button></div></div>
            <div class="viewport-bottom"><span><i />{{ sceneFailed ? '渲染不可用' : sceneReady ? '空间渲染已连接' : '正在加载场景' }}</span><span>拖动旋转<span class="key-dot">·</span>滚轮缩放<span class="key-dot">·</span>右键平移</span><span class="scale-rule">场地布局示意</span></div>

            <div v-if="!preview && hasSession" class="spatial-orders-host"><SpatialWorkOrders :floor="floor" :requested-order-id="route.query.workOrderId" @points="updateOrders" @locate="selectPoint" /></div>
            <Transition name="twin-detail"><aside v-if="selectedPoint" class="point-detail" :style="{ '--detail-color': selectedModule.color }" aria-label="空间点位详情"><div class="detail-header"><span class="detail-icon"><TwinIcon :name="selectedModule.icon" :size="22" /></span><span>{{ selectedModule.name }}</span><button class="icon-button" aria-label="关闭点位详情" @click="selectedPoint = null"><TwinIcon name="close" :size="17" /></button></div><small class="detail-code">{{ selectedPoint.code }}</small><h3>{{ selectedPoint.name }}</h3><p class="detail-location"><TwinIcon name="pin" :size="14" />{{ selectedPoint.location }}</p><span class="detail-status"><i />{{ selectedPoint.status }}</span><div v-if="selectedPoint.module === 'camera'" class="camera-placeholder"><TwinIcon name="camera" :size="28" /><strong>视频通道待接入</strong><small>连接后可查看实时画面</small></div><dl><div v-for="pair in selectedPoint.values" :key="pair[0]"><dt>{{ pair[0] }}</dt><dd>{{ pair[1] }}</dd></div></dl><div class="detail-data-note">{{ selectedPoint.orderId ? '真实工单 · 按报修图纸定位' : '示例点位 · 未连接现场业务数据' }}</div><button data-testid="point-open-module" class="twin-button primary detail-action" @click="openPointModule(selectedPoint)">{{ selectedPoint.orderId ? '打开工单' : '进入' + selectedModule.name }}<TwinIcon name="arrow" :size="16" /></button></aside></Transition>
          </section>

          <aside class="twin-board-rail twin-board-right"><TwinOperationsPanel side="right" :floor="floor" @open-module="openModule" @select-floor="selectFloor" @select-point="locatePanelPoint" /></aside>
        </div>

        <section class="twin-layer-bar" aria-label="业务图层"><div class="layer-bar-label"><TwinIcon name="layers" :size="19" /><span>业务图层<small>LAYERS</small></span></div><button class="all-layers" :class="{ active: layer === 'all' }" :aria-pressed="layer === 'all'" @click="setLayer('all')">全部</button><button v-for="module in MODULES" :key="module.id" class="layer-button" :class="{ active: layer === module.id }" :aria-pressed="layer === module.id" :style="{ '--layer-color': module.color }" @click="setLayer(module.id)"><TwinIcon :name="module.icon" :size="18" /><span>{{ module.name }}</span><i /></button><button class="layer-help" aria-label="业务图层说明" @click="help = true"><TwinIcon name="help" :size="17" /></button></section>
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

    <div v-if="gallery" class="twin-modal-backdrop" @click.self="gallery = false"><section class="reference-modal" role="dialog" aria-modal="true" aria-label="云仓实景对照"><header><div><div class="twin-eyebrow">REALITY / DIGITAL TWIN</div><h2>从照片，重建一座云仓</h2><p>白色水平线条、青蓝幕墙转角与高标仓内部结构。</p></div><button class="icon-button" aria-label="关闭实景对照" @click="gallery = false"><TwinIcon name="close" /></button></header><div class="reference-image-wrap"><img :src="'/twin-reference/reference-' + activeReference.id + '.' + (activeReference.extension || 'png')" :alt="activeReference.title" /><span>{{ activeReference.title }}</span><small>{{ referenceIndex + 1 }} / {{ REFERENCES.length }}</small></div><div class="reference-thumbnails"><button v-for="(photo, index) in REFERENCES" :key="photo.id" :class="{ active: index === referenceIndex }" :aria-label="photo.title" :aria-pressed="index === referenceIndex" @click="referenceIndex = index"><img :src="'/twin-reference/reference-' + photo.id + '.' + (photo.extension || 'png')" :alt="photo.title" /></button></div><footer><TwinIcon name="help" :size="16" /><span>主楼平面结构参照 2025.10 建筑图纸，外立面沿用照片参照。图纸未载明的设备细节仍待现场核对。</span></footer></section></div>
    <div v-if="help" class="twin-modal-backdrop" @click.self="help = false"><section class="help-modal" role="dialog" aria-modal="true" aria-label="三维工作台操作指南"><header><span class="brand-symbol"><TwinIcon name="cube" :size="26" /></span><button class="icon-button" aria-label="关闭操作指南" @click="help = false"><TwinIcon name="close" /></button></header><div class="twin-eyebrow">EXPLORE YOUR SPACE</div><h2>以空间为起点。</h2><p>拖动建筑旋转视角，滚轮缩放，右键拖动平移。也可以用底部工具栏调整视图。</p><ol><li><strong>查看建筑与楼层</strong><span>右侧选择楼层，切换「楼层展开」或「室内空间」。</span></li><li><strong>按业务寻找设备</strong><span>选择底部业务图层，点击模型中的彩色点位查看详情。</span></li><li><strong>从位置进入业务</strong><span>详情内点击「进入」打开关联模块，演示模块支持返回模型定位。</span></li></ol><div class="help-note">主楼结构按建筑图纸还原。工单位置来源于报修标注；运营数字和其他业务点位仍为演示。室内可切换俯视、查看空间名称及对照原图。</div><button class="twin-button primary" @click="help = false">开始探索<TwinIcon name="arrow" :size="16" /></button></section></div>
    <Transition name="twin-detail"><div v-if="toast" class="twin-toast" role="status"><TwinIcon name="check" :size="18" />{{ toast }}</div></Transition>
  </div>
</template>

<style scoped>
.plan-tools-bar { position: absolute; top: 48px; left: 0; display: flex; gap: 5px; flex-wrap: wrap; max-width: calc(100% - 42px); }
.plan-tools-bar button { padding: 7px 10px; border: 1px solid #bfd0ca; border-radius: 5px; background: #f5f8f2; color: #31564d; font-size: 12px; cursor: pointer; }
.plan-tools-bar button[aria-pressed=true] { background: #326d64; color: #fff; border-color: #326d64; }
.plan-tools-bar button:focus-visible { outline: 2px solid #287c76; outline-offset: 2px; }
.plan-source-panel { position: absolute; top: 88px; left: 0; width: min(520px, calc(100% - 46px)); max-height: 65%; overflow: auto; padding: 12px; border-radius: 8px; background: #fff; color: #28483f; z-index: 6; }
.spatial-orders-host { position: absolute; z-index: 8; left: 0; bottom: 77px; width: min(320px, calc(100% - 48px)); }
.twin-workspace-v2 .has-work-order-dock .floor-selection-card { top: 91px; left: 0; bottom: auto; max-width: calc(100% - 48px); }
.twin-workspace-v2 .has-work-order-dock .scene-compass { left: auto; right: 48px; bottom: 80px; }
.is-focused .spatial-orders-host { left: 18px; }
.twin-workspace-v2 .is-focused .has-work-order-dock .floor-selection-card { left: 18px; }
@media (max-width: 760px) {
  .spatial-orders-host { bottom: 77px; width: min(300px, calc(100% - 52px)); }
  .plan-tools-bar { top: 45px; }
  .twin-workspace-v2 .has-work-order-dock .point-detail { max-height: calc(100% - 207px); box-sizing: border-box; }
}
</style>
