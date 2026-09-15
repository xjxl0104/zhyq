<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js'
import { loadWarehouse } from './warehouseAsset'
import { disposeSceneExtras } from './sceneResources'
import { createSceneWeather } from './sceneWeather'
import { createParkLandscape } from './parkLandscape'
import { createSceneRendering } from './sceneRendering'
import { MODULES, POINTS, visiblePoints } from './twinData'
import TwinIcon from './TwinIcon.vue'

const props = defineProps({ mode: { type: String, default: 'exterior' }, floor: { type: Number, default: null }, layer: { type: String, default: 'all' }, weather: { type: String, default: 'sunny' }, viewpoint: { type: String, default: 'overview' }, focused: Boolean, rotating: Boolean, markers: { type: Boolean, default: true } })
const emit = defineEmits(['select-floor', 'select-point', 'open-module', 'ready', 'error'])
const host = ref(null), canvasHost = ref(null), ready = ref(false), error = ref('')
const pins = computed(() => props.mode === 'interior'
  ? POINTS.filter(point => props.layer === 'all' || props.layer === point.module).filter(point => point.module !== 'park' && point.module !== 'energy').map(point => ({ ...point, floor: props.floor || 3, name: point.module === 'fire' ? '消防管网与消火栓' : point.module === 'contract' ? 'A 区 · 租赁空间' : point.module === 'property' ? '仓内设备 · 物业巡检' : '室内安防点位', location: '云仓 01 / ' + (props.floor || 3) + 'F / 示意点位' }))
  : visiblePoints(props.layer, props.floor, props.mode))
const moduleFor = id => MODULES.find(item => item.id === id)
const markerElements = new Map()
// Keep the first frame and reset at the same close overview, with room for the entrance.
const overviewCamera = { position: [86, 58, 174], target: [0, 24, 0] }
let renderer, scene, camera, controls, model, observer, environmentTarget, weatherEffects, landscape, rendering, frame = 0, lastTime = 0, disposed = false, tween = null, width = 1, height = 1, needsRender = true
const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
const raycaster = new THREE.Raycaster(), pointer = new THREE.Vector2()
let pointerDown = null
function resize() {
  if (!host.value || !renderer) return
  width = host.value.clientWidth; height = host.value.clientHeight
  if (!width || !height) return
  needsRender = true
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
  const destination = entrance ? [44, 7, 96] : inside ? [113, 126, 133] : exploded ? [181, 137, 193] : overviewCamera.position
  const target = entrance ? [22, 5, 56] : inside ? [0, 1, 0] : exploded ? [0, 38, 0] : overviewCamera.target
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
  controls.autoRotate = props.rotating && !reducedMotion
  if (tween) {
    const progress = reducedMotion ? 1 : Math.min((time - tween.start) / 850, 1)
    const ease = 1 - Math.pow(1 - progress, 3)
    camera.position.lerpVectors(tween.from, tween.to, ease)
    controls.target.lerpVectors(tween.targetFrom, tween.targetTo, ease)
    camera.zoom = THREE.MathUtils.lerp(tween.fromZoom, 1, ease); camera.updateProjectionMatrix()
    if (progress === 1) tween = null
  }
  const cameraChanged = controls.update(delta)
  if (!needsRender && !modelChanged && !cameraTweening && !cameraChanged && !rendering?.needsRender(time)) return
  // Camera movement does not change shadows. Rebuild only when geometry or lighting changes.
  if (modelChanged) renderer.shadowMap.needsUpdate = true
  rendering.render(time, modelChanged || cameraTweening || cameraChanged)
  needsRender = false
  for (const point of pins.value) {
    const element = markerElements.get(point.id)
    if (!element) continue
    const projected = model.pointPosition(point).project(camera)
    const x = (projected.x + 1) / 2 * width, y = (1 - projected.y) / 2 * height
    element.style.transform = 'translate3d(' + Math.round(x) + 'px,' + Math.round(y) + 'px,0) translate(-50%,-100%)'
    element.style.visibility = projected.z < 1 && projected.z > -1 && x > 20 && x < width - 20 && y > 50 && y < height - 35 ? 'visible' : 'hidden'
  }
}
onMounted(async () => {
  try {
    scene = new THREE.Scene()
    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.75))
    renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFSoftShadowMap
    renderer.shadowMap.autoUpdate = false; renderer.shadowMap.needsUpdate = true
    renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.02
    const environment = new RoomEnvironment()
    const pmrem = new THREE.PMREMGenerator(renderer)
    environmentTarget = pmrem.fromScene(environment, .04)
    scene.environment = environmentTarget.texture
    scene.environmentIntensity = .35
    environment.dispose(); pmrem.dispose()
    renderer.domElement.setAttribute('aria-label', '云仓三维模型，可拖动旋转、滚轮缩放，点击建筑选择楼层')
    renderer.domElement.setAttribute('role', 'img')
    canvasHost.value.appendChild(renderer.domElement)
    camera = new THREE.PerspectiveCamera(42, 1, .2, 1800)
    camera.position.set(...overviewCamera.position)
    controls = new OrbitControls(camera, renderer.domElement)
    controls.target.set(...overviewCamera.target); controls.enableDamping = true; controls.dampingFactor = .065
    controls.minZoom = .55; controls.maxZoom = 3; controls.minPolarAngle = .15; controls.maxPolarAngle = Math.PI / 2 - .025
    controls.minDistance = 45; controls.maxDistance = 500
    controls.autoRotateSpeed = .45
    controls.addEventListener('start', () => { tween = null })
    const hemisphere = new THREE.HemisphereLight('#e8edff', '#858fb0', 1.6)
    scene.add(hemisphere)
    const sunlight = new THREE.DirectionalLight('#f5f5ff', 2.8)
    sunlight.position.set(-55, 110, 70); sunlight.castShadow = true
    const shadowSize = window.innerWidth >= 1100 ? 4096 : 2048
    sunlight.shadow.mapSize.set(shadowSize, shadowSize)
    Object.assign(sunlight.shadow.camera, { left: -225, right: 225, top: 205, bottom: -205, near: 1, far: 480 })
    sunlight.shadow.bias = -.00015; sunlight.shadow.normalBias = .035
    sunlight.shadow.radius = 1
    scene.add(sunlight)
    const fill = new THREE.DirectionalLight('#dce2ff', .65); fill.position.set(60, 70, -90); scene.add(fill)
    model = await loadWarehouse()
    if (disposed) { model.dispose(); return }
    model.setState({ mode: props.mode, floor: props.floor, layer: props.layer }); scene.add(model.root)
    landscape = createParkLandscape(scene, model)
    landscape.setMode(props.mode); landscape.setWeather(props.weather)
    weatherEffects = createSceneWeather({ scene, renderer, sunlight, fill, hemisphere })
    weatherEffects.setWeather(props.weather)
    rendering = createSceneRendering(renderer, scene, camera)
    renderer.domElement.addEventListener('pointerdown', onPointerDown)
    renderer.domElement.addEventListener('pointerup', onPointerUp)
    renderer.domElement.addEventListener('webglcontextlost', onContextLost)
    observer = new ResizeObserver(resize); observer.observe(host.value)
    resize(); reset(); animate(performance.now())
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
  landscape?.dispose()
  rendering?.dispose()
  model?.dispose()
  disposeSceneExtras(scene, model?.root)
  environmentTarget?.dispose()
  renderer?.dispose(); renderer?.domElement.remove(); markerElements.clear()
})
async function exportModel() {
  if (!model) return
  const { GLTFExporter } = await import('three/addons/exporters/GLTFExporter.js')
  // Clone freezes transforms during the asynchronous export, even while orbiting/animating.
  const snapshot = model.root.clone(true)
  const glb = await new GLTFExporter().parseAsync(snapshot, { binary: true, onlyVisible: true })
  const url = URL.createObjectURL(new Blob([glb], { type: 'model/gltf-binary' }))
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'dipark-warehouse-' + props.mode + '.glb'; anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
defineExpose({ reset, zoom, exportModel })
</script>

<template>
  <div ref="host" class="warehouse-scene">
    <div ref="canvasHost" class="warehouse-canvas" />
    <div v-if="error" class="scene-error" role="alert"><TwinIcon name="cube" :size="40" /><p>{{ error }}</p><button @click="() => $router.go(0)">重新加载</button></div>
    <div v-else-if="!ready" class="scene-loading"><span class="scene-spinner" />正在加载云仓模型…</div>
    <div v-show="ready && markers && !error" class="scene-pins">
      <div v-for="point in pins" :key="point.id" :ref="element => element ? markerElements.set(point.id, element) : markerElements.delete(point.id)" class="scene-pin" :class="{ 'pin-building': point.module === 'park' }" :style="{ '--pin-color': moduleFor(point.module).color }">
        <span class="pin-card"><button class="pin-info" :aria-label="'查看' + point.name" @click.stop="emit('select-point', point)"><span class="pin-icon"><TwinIcon :name="moduleFor(point.module).icon" :size="15" /></span><span class="pin-label"><span class="pin-short-name">{{ moduleFor(point.module).short }}</span><span class="pin-full-name">{{ point.name }}</span></span></button><button class="pin-enter" :aria-label="'直接进入' + moduleFor(point.module).name" :title="'直接进入' + moduleFor(point.module).name" @click.stop="emit('open-module', point)"><TwinIcon name="chevron" :size="12" /></button></span>
        <span class="pin-stem" /><span class="pin-dot" />
      </div>
    </div>
  </div>
</template>
