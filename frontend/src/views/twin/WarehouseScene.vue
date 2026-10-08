<script setup>
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js'
import { loadWarehouse } from './warehouseAsset'
import { disposeSceneExtras } from './sceneResources'
import { createSceneWeather } from './sceneWeather'
import { createParkLandscape } from './parkLandscape'
import { createSceneRendering, createAnimeSceneRendering } from './sceneRendering'
import { floorLandmarks } from './floorPlanGeometry'
import { PLAN_BUILDING } from './floorPlanData'
import { MODULES, POINTS, visiblePoints } from './twinData'
import TwinIcon from './TwinIcon.vue'

const props = defineProps({ mode: { type: String, default: 'exterior' }, floor: { type: Number, default: null }, layer: { type: String, default: 'all' }, weather: { type: String, default: 'sunny' }, viewpoint: { type: String, default: 'overview' }, focused: Boolean, rotating: Boolean, markers: { type: Boolean, default: true }, workOrders: { type: Array, default: () => [] }, planLabels: { type: Boolean, default: true } })
const emit = defineEmits(['select-floor', 'select-point', 'open-module', 'ready', 'error'])
// The approved anime finish is the homepage default. Only the local comparison
// page can opt into the previous renderer or collect diagnostic measurements.
const demoProfile = import.meta.env.DEV ? inject('warehouse-scene-demo', null) : null
const anime = import.meta.env.DEV ? demoProfile?.style !== 'original' : true
const host = ref(null), canvasHost = ref(null), ready = ref(false), error = ref('')
const pins = computed(() => {
  const orders = props.workOrders.filter(point => (props.layer === 'all' || props.layer === 'property') && (props.floor == null || point.floor === props.floor) && (point.floor > 0 || props.mode === 'interior'))
  const illustrative = props.mode === 'interior' ? [] : visiblePoints(props.layer, props.floor, props.mode).filter(point => point.module !== 'property')
  return [...illustrative, ...orders]
})
const landmarks = computed(() => props.mode === 'interior' && props.planLabels ? floorLandmarks(props.floor || 3) : [])
const landmarkElements = new Map()
const moduleFor = id => MODULES.find(item => item.id === id)
const markerElements = new Map()
// Keep the first frame and reset at the same close overview, with room for the entrance.
const overviewCamera = { position: [86, 58, 174], target: [0, 24, 0] }
let renderer, scene, camera, controls, model, observer, environmentTarget, weatherEffects, landscape, rendering, stylization, metrics, frame = 0, lastTime = 0, disposed = false, tween = null, pendingPoint = null, width = 1, height = 1, needsRender = true
const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
const raycaster = new THREE.Raycaster(), pointer = new THREE.Vector2()
let pointerDown = null
function resize() {
  if (!host.value || !renderer) return
  width = host.value.clientWidth; height = host.value.clientHeight
  if (!width || !height) return
  needsRender = true
  // Match the original composer's scene resolution, rather than silently doubling
  // the pixel workload when drawing directly to a high-DPI canvas.
  if (anime) renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.35, 1800 / Math.max(width, height)))
  renderer.setSize(width, height)
  rendering?.resize(width, height)
  // Preserve horizontal context in narrow windows without changing the user's orbit.
  camera.aspect = width / height
  const baseFov = props.focused ? 37 : 42
  camera.fov = THREE.MathUtils.clamp(THREE.MathUtils.radToDeg(2 * Math.atan(Math.tan(THREE.MathUtils.degToRad(baseFov / 2)) * Math.max(1, 1.25 / camera.aspect))), baseFov, 88)
  camera.updateProjectionMatrix()
}
function reset() {
  if (!camera) return
  const inside = props.mode === 'interior', exploded = props.mode === 'exploded'
  const entrance = props.viewpoint === 'entrance' && !inside && !exploded
  const basement = inside && props.floor === -1
  const center = basement ? [37, 1, -14.4] : [0, 1, 0]
  const top = inside && props.viewpoint === 'plan'
  const destination = entrance ? [44, 7, 96] : inside ? top ? [center[0], basement ? 46 : 112, center[2] + .1] : basement ? [66, 35, 22] : [48, 60, 70] : exploded ? [181, 137, 193] : overviewCamera.position
  const target = entrance ? [22, 5, 56] : inside ? center : exploded ? [0, 38, 0] : overviewCamera.target
  tween = {
    from: camera.position.clone(), to: new THREE.Vector3(...destination),
    targetFrom: controls.target.clone(), targetTo: new THREE.Vector3(...target), start: performance.now(),
    fromZoom: camera.zoom,
  }
  resize()
}
function zoom(amount) {
  if (!camera) return
  tween = null
  needsRender = true
  camera.zoom = THREE.MathUtils.clamp(camera.zoom * amount, .55, 3)
  camera.updateProjectionMatrix()
}
function focusPoint(point) {
  if (!point?.localPosition) return
  if (!model || !camera || !controls) { pendingPoint = point; return }
  pendingPoint = null
  // Use the settled interior transform: an incoming floor may still be animating
  // down from its exterior elevation when the user picks another order.
  const target = new THREE.Vector3(...point.localPosition).multiplyScalar(PLAN_BUILDING.sceneScale)
  target.y += .4
  const offset = props.viewpoint === 'plan' ? new THREE.Vector3(0, 45, .1) : new THREE.Vector3(25, 36, 33)
  tween = {
    from: camera.position.clone(), to: target.clone().add(offset),
    targetFrom: controls.target.clone(), targetTo: target, start: performance.now(), fromZoom: camera.zoom,
  }
  needsRender = true
}
function onPointerDown(event) { pointerDown = { x: event.clientX, y: event.clientY } }
function onPointerUp(event) {
  if (!pointerDown || Math.hypot(event.clientX - pointerDown.x, event.clientY - pointerDown.y) > 6 || event.button !== 0) return
  pointerDown = null
  const rect = renderer.domElement.getBoundingClientRect()
  pointer.set(((event.clientX - rect.left) / rect.width) * 2 - 1, -((event.clientY - rect.top) / rect.height) * 2 + 1)
  raycaster.setFromCamera(pointer, camera)
  const meshes = []
  model.floors.filter(item => item.group.visible).forEach(item => {
    for (const group of [item.shell, item.structure, item.interior]) if (group.visible) group.traverse(object => { if (object.isMesh) meshes.push(object) })
  })
  const hit = raycaster.intersectObjects(meshes, false)[0]
  if (hit?.object.userData.floor) emit('select-floor', hit.object.userData.floor)
}
function onContextLost(event) {
  event.preventDefault()
  error.value = '三维画面暂时中断，请重新加载场景。'
  emit('error', error.value)
  cancelAnimationFrame(frame)
}
function animate(time) {
  if (disposed) return
  frame = requestAnimationFrame(animate)
  if (document.hidden) { lastTime = time; return }
  const delta = Math.min((time - (lastTime || time)) / 1000, .05); lastTime = time
  const modelChanged = model.update(delta)
  const cameraTweening = Boolean(tween)
  controls.autoRotate = props.rotating && !reducedMotion && props.viewpoint !== 'plan'
  if (tween) {
    const progress = reducedMotion ? 1 : Math.min((time - tween.start) / 850, 1)
    const ease = 1 - Math.pow(1 - progress, 3)
    camera.position.lerpVectors(tween.from, tween.to, ease)
    controls.target.lerpVectors(tween.targetFrom, tween.targetTo, ease)
    camera.zoom = THREE.MathUtils.lerp(tween.fromZoom, 1, ease); camera.updateProjectionMatrix()
    if (progress === 1) tween = null
  }
  const cameraChanged = controls.update(delta)
  const landscapeChanged = landscape?.update?.(camera) || false
  metrics?.tick(time, {
    mode: props.mode, floor: props.floor, weather: props.weather, rotating: props.rotating,
    camera: camera.position.toArray().map(value => Number(value.toFixed(2))),
    target: controls.target.toArray().map(value => Number(value.toFixed(2))),
    buffer: rendering?.getInfo?.(), style: anime ? 'anime' : 'original',
  })
  if (!needsRender && !modelChanged && !landscapeChanged && !cameraTweening && !cameraChanged && !rendering?.needsRender(time)) return
  // Orbiting alone keeps cached shadows; a tree LOD switch changes geometry.
  if (modelChanged || landscapeChanged) renderer.shadowMap.needsUpdate = true
  metrics?.beforeRender()
  rendering.render(time, modelChanged || cameraTweening || cameraChanged)
  metrics?.afterRender()
  needsRender = false
  for (const point of pins.value) {
    const element = markerElements.get(point.id)
    if (!element) continue
    const position = model.pointPosition(point)
    if (!position) { element.style.visibility = 'hidden'; continue }
    const projected = position.project(camera)
    const x = (projected.x + 1) / 2 * width, y = (1 - projected.y) / 2 * height
    element.style.transform = 'translate3d(' + Math.round(x) + 'px,' + Math.round(y) + 'px,0) translate(-50%,-100%)'
    element.style.visibility = projected.z < 1 && projected.z > -1 && x > 20 && x < width - 20 && y > 50 && y < height - 35 ? 'visible' : 'hidden'
  }
  for (const point of landmarks.value) {
    const element = landmarkElements.get(point.id)
    if (!element) continue
    const position = model.localPointPosition(props.floor || 3, point.position)
    if (!position) continue
    const projected = position.project(camera)
    const x = (projected.x + 1) / 2 * width, y = (1 - projected.y) / 2 * height
    element.style.transform = `translate(${Math.round(x)}px, ${Math.round(y)}px) translate(-50%, -50%)`
    element.style.visibility = projected.z > -1 && projected.z < 1 && x > 25 && x < width - 25 && y > 65 && y < height - 70 ? 'visible' : 'hidden'
  }

}
onMounted(async () => {
  try {
    scene = new THREE.Scene()
    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.75))
    renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFSoftShadowMap
    renderer.shadowMap.autoUpdate = false; renderer.shadowMap.needsUpdate = true
    renderer.toneMapping = anime ? THREE.NeutralToneMapping : THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.02
    if (!anime) {
      const environment = new RoomEnvironment()
      const pmrem = new THREE.PMREMGenerator(renderer)
      environmentTarget = pmrem.fromScene(environment, .04)
      scene.environment = environmentTarget.texture
      scene.environmentIntensity = .35
      environment.dispose(); pmrem.dispose()
    }
    renderer.domElement.setAttribute('aria-label', '云仓三维模型，可拖动旋转、滚轮缩放，点击建筑选择楼层')
    renderer.domElement.setAttribute('role', 'img')
    canvasHost.value.appendChild(renderer.domElement)
    camera = new THREE.PerspectiveCamera(42, 1, .2, 1800)
    camera.position.set(...overviewCamera.position)
    controls = new OrbitControls(camera, renderer.domElement)
    controls.target.set(...overviewCamera.target); controls.enableDamping = true; controls.dampingFactor = .065
    controls.minZoom = .55; controls.maxZoom = 3; controls.minPolarAngle = .15; controls.maxPolarAngle = Math.PI / 2 - .025
    controls.minDistance = 12; controls.maxDistance = 500
    controls.autoRotateSpeed = .45
    controls.addEventListener('start', () => { tween = null })
    const hemisphere = new THREE.HemisphereLight('#e8edff', '#858fb0', 1.6)
    scene.add(hemisphere)
    const sunlight = new THREE.DirectionalLight('#f5f5ff', 2.8)
    sunlight.position.set(-55, 110, 70); sunlight.castShadow = true
    const shadowSize = anime ? 2048 : window.innerWidth >= 1100 ? 4096 : 2048
    sunlight.shadow.mapSize.set(shadowSize, shadowSize)
    Object.assign(sunlight.shadow.camera, { left: -225, right: 225, top: 205, bottom: -205, near: 1, far: 480 })
    sunlight.shadow.bias = anime ? -.00025 : -.00015; sunlight.shadow.normalBias = anime ? .25 : .035
    sunlight.shadow.radius = 1
    scene.add(sunlight)
    const fill = new THREE.DirectionalLight('#dce2ff', .65); fill.position.set(60, 70, -90); scene.add(fill)
    model = await loadWarehouse()
    if (disposed) { model.dispose(); return }
    model.setState({ mode: props.mode, floor: props.floor, layer: props.layer }); scene.add(model.root)
    landscape = createParkLandscape(scene, model)
    landscape.setMode(props.mode); landscape.setWeather(props.weather)
    if (anime) {
      const { createAnimeSceneStyle } = await import('./animeSceneStyle.js')
      if (disposed) return
      stylization = createAnimeSceneStyle(scene)
    }
    weatherEffects = createSceneWeather({ scene, renderer, sunlight, fill, hemisphere, style: anime ? 'anime' : 'realistic' })
    weatherEffects.setWeather(props.weather)
    rendering = anime ? createAnimeSceneRendering(renderer, scene, camera) : createSceneRendering(renderer, scene, camera)
    if (import.meta.env.DEV && demoProfile?.onMetrics) {
      const { createSceneDemoMetrics } = await import('./sceneDemoMetrics.js')
      if (disposed) return
      metrics = createSceneDemoMetrics(renderer, demoProfile.onMetrics)
    }
    renderer.domElement.addEventListener('pointerdown', onPointerDown)
    renderer.domElement.addEventListener('pointerup', onPointerUp)
    renderer.domElement.addEventListener('webglcontextlost', onContextLost)
    observer = new ResizeObserver(resize); observer.observe(host.value)
    resize(); reset(); if (pendingPoint) focusPoint(pendingPoint); animate(performance.now())
    ready.value = true; emit('ready')
  } catch (exception) {
    if (disposed) return
    error.value = renderer ? '云仓模型加载失败，请检查连接后重新加载。' : '当前环境未能启动 WebGL 三维渲染，请启用浏览器硬件加速后重试。'
    console.error('Warehouse scene initialization failed', exception)
    emit('error', error.value)
  }
})
watch(() => [props.mode, props.floor, props.layer], () => {
  model?.setState({ mode: props.mode, floor: props.floor, layer: props.layer })
  landscape?.setMode(props.mode)
})
watch(() => props.mode, reset)
watch(() => props.floor, (value, previous) => { if (value === -1 || previous === -1) reset() })
watch(() => [props.workOrders, props.planLabels, props.floor, props.layer], () => { needsRender = true }, { deep: true, flush: 'post' })
watch(() => props.focused, reset)
watch(() => props.viewpoint, reset)
watch(() => props.weather, value => {
  weatherEffects?.setWeather(value)
  landscape?.setWeather(value)
  needsRender = true
  if (renderer) renderer.shadowMap.needsUpdate = true
})
watch(() => props.markers, () => { needsRender = true })
onBeforeUnmount(() => {
  disposed = true; cancelAnimationFrame(frame); observer?.disconnect(); controls?.dispose()
  if (renderer) {
    renderer.domElement.removeEventListener('pointerdown', onPointerDown)
    renderer.domElement.removeEventListener('pointerup', onPointerUp)
    renderer.domElement.removeEventListener('webglcontextlost', onContextLost)
  }
  weatherEffects?.dispose()
  stylization?.dispose()
  landscape?.dispose()
  rendering?.dispose()
  metrics?.dispose()
  model?.dispose()
  disposeSceneExtras(scene, model?.root)
  environmentTarget?.dispose()
  renderer?.dispose(); renderer?.domElement.remove(); markerElements.clear(); landmarkElements.clear()
})
async function exportModel() {
  if (!model) return
  const { GLTFExporter } = await import('three/addons/exporters/GLTFExporter.js')
  // Clone freezes transforms during the asynchronous export, even while orbiting/animating.
  const snapshot = model.root.clone(true)
  stylization?.prepareExport(snapshot)
  const glb = await new GLTFExporter().parseAsync(snapshot, { binary: true, onlyVisible: true })
  const url = URL.createObjectURL(new Blob([glb], { type: 'model/gltf-binary' }))
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'dipark-warehouse-' + props.mode + '.glb'; anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
defineExpose({ reset, zoom, focusPoint, exportModel })
</script>

<template>
  <div ref="host" class="warehouse-scene">
    <div ref="canvasHost" class="warehouse-canvas" />
    <div v-if="error" class="scene-error" role="alert"><TwinIcon name="cube" :size="40" /><p>{{ error }}</p><button @click="() => $router.go(0)">重新加载</button></div>
    <div v-else-if="!ready" class="scene-loading"><span class="scene-spinner" />正在加载云仓模型…</div>
    <div v-if="ready && !error" class="plan-landmarks" aria-label="图纸空间名称">
      <span v-for="point in landmarks" :key="point.id" :ref="element => element ? landmarkElements.set(point.id, element) : landmarkElements.delete(point.id)" class="plan-landmark">{{ point.name }}</span>
    </div>
    <div v-show="ready && markers && !error" class="scene-pins">
      <div v-for="point in pins" :key="point.id" :ref="element => element ? markerElements.set(point.id, element) : markerElements.delete(point.id)" class="scene-pin" :class="{ 'pin-building': point.module === 'park', 'pin-workorder': point.orderId, 'pin-urgent': point.urgent }" :style="{ '--pin-color': point.urgent ? '#b44234' : moduleFor(point.module).color }">
        <span class="pin-card"><button class="pin-info" :aria-label="(point.orderId ? '查看工单：' : '查看') + point.name" @click.stop="emit('select-point', point)"><span class="pin-icon"><TwinIcon :name="moduleFor(point.module).icon" :size="15" /></span><span class="pin-label"><span class="pin-short-name">{{ point.orderId ? point.status : moduleFor(point.module).short }}</span><span class="pin-full-name">{{ point.name }}</span></span></button><button class="pin-enter" :aria-label="'直接进入' + moduleFor(point.module).name" :title="'直接进入' + moduleFor(point.module).name" @click.stop="emit('open-module', point)"><TwinIcon name="chevron" :size="12" /></button></span>
        <span class="pin-stem" /><span class="pin-dot" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.plan-landmarks { position: absolute; inset: 0; pointer-events: none; overflow: hidden; }
.plan-landmark { position: absolute; left: 0; top: 0; visibility: hidden; padding: 3px 6px; border-radius: 3px; background: rgba(250, 250, 242, .92); color: #31514d; font-size: 11px; white-space: nowrap; }
.pin-workorder .pin-card { border-color: var(--pin-color); }
.pin-workorder .pin-short-name { display: none; }
.pin-workorder .pin-full-name { display: inline; max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
