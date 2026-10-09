<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { fileApi } from '@/api/file'

const props = defineProps({ fileId: [Number, String], point: Object, editable: Boolean })
const emit = defineEmits(['update:point'])
const imageUrl = ref('')
const loading = ref(false)
const error = ref('')
const MIN_ZOOM = 1, MAX_ZOOM = 6, STEP = 1.5, DRAG_THRESHOLD = 4
const viewport = ref(null), canvas = ref(null)
const zoom = ref(1), pan = ref({ x: 0, y: 0 }), animated = ref(false), dragging = ref(false)
let generation = 0
const markerStyle = computed(() => props.point ? { left: `${props.point.x * 100}%`, top: `${props.point.y * 100}%`, '--marker-scale': 1 / zoom.value } : {})
const canvasStyle = computed(() => ({ transform: `translate(${pan.value.x}px, ${pan.value.y}px) scale(${zoom.value})` }))
function release() {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  imageUrl.value = ''
}
function resetView() { zoom.value = 1; pan.value = { x: 0, y: 0 } }
watch(() => props.fileId, async id => {
  const run = ++generation
  release()
  error.value = ''
  resetView()
  loading.value = !!id
  if (!id) return
  try {
    const res = await fileApi.download(id)
    if (run !== generation) return
    if (!['image/png', 'image/jpeg'].includes(res.data.type)) throw new Error('不是可显示的平面图')
    imageUrl.value = URL.createObjectURL(res.data)
  } catch {
    if (run === generation) error.value = '平面图加载失败，请检查访问权限或重新打开'
  } finally { if (run === generation) loading.value = false }
}, { immediate: true })
onBeforeUnmount(() => { generation++; release(); endGesture() })

// Pan/zoom: the canvas keeps its untransformed layout size, so offsetWidth/Height are the scale-1 size.
// The pan is clamped so the drawing always covers the viewport and cannot be dragged out of sight.
function clampPan(x, y, scale) {
  const view = viewport.value, base = canvas.value
  if (!view || !base) return { x, y }
  const minX = Math.min(0, view.clientWidth - base.offsetWidth * scale)
  const minY = Math.min(0, view.clientHeight - base.offsetHeight * scale)
  return { x: Math.min(0, Math.max(minX, x)), y: Math.min(0, Math.max(minY, y)) }
}
function zoomAt(factor, clientX, clientY) {
  const view = viewport.value
  if (!view) return
  const rect = view.getBoundingClientRect()
  const px = clientX == null ? rect.width / 2 : clientX - rect.left
  const py = clientY == null ? rect.height / 2 : clientY - rect.top
  const next = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom.value * factor))
  const k = next / zoom.value
  pan.value = clampPan(px - (px - pan.value.x) * k, py - (py - pan.value.y) * k, next)
  zoom.value = next
}
function stepZoom(factor) { animated.value = true; zoomAt(factor) }
function resetZoom() { animated.value = true; resetView() }
function centerPoint() {
  const view = viewport.value, base = canvas.value
  if (!props.point || !view || !base) return
  animated.value = true
  const scale = Math.max(zoom.value, 2.5)
  zoom.value = scale
  pan.value = clampPan(view.clientWidth / 2 - props.point.x * base.offsetWidth * scale, view.clientHeight / 2 - props.point.y * base.offsetHeight * scale, scale)
}
function onWheel(event) {
  if (!imageUrl.value) return
  animated.value = false
  zoomAt(Math.exp(-event.deltaY * 0.0015), event.clientX, event.clientY)
}
function onDoubleClick(event) { animated.value = true; zoomAt(2, event.clientX, event.clientY) }

// Pointer gestures without pointer capture: capture would retarget the click that marks a repair point.
const pointers = new Map()
let gesture = null, suppressClick = false
function onPointerDown(event) {
  if (!imageUrl.value || (event.pointerType === 'mouse' && event.button !== 0)) return
  pointers.set(event.pointerId, { x: event.clientX, y: event.clientY })
  if (pointers.size === 1) {
    window.addEventListener('pointermove', onPointerMove)
    window.addEventListener('pointerup', onPointerUp)
    window.addEventListener('pointercancel', onPointerUp)
  }
  beginGesture()
}
function beginGesture() {
  const [a, b] = [...pointers.values()]
  animated.value = false
  if (b) gesture = { type: 'pinch', distance: Math.hypot(a.x - b.x, a.y - b.y), moved: true }
  else if (a) gesture = { type: 'pan', startX: a.x, startY: a.y, origin: { ...pan.value }, moved: gesture?.moved || false }
}
function onPointerMove(event) {
  if (!pointers.has(event.pointerId) || !gesture) return
  pointers.set(event.pointerId, { x: event.clientX, y: event.clientY })
  const [a, b] = [...pointers.values()]
  if (gesture.type === 'pinch' && b) {
    const distance = Math.hypot(a.x - b.x, a.y - b.y)
    if (gesture.distance > 0) zoomAt(distance / gesture.distance, (a.x + b.x) / 2, (a.y + b.y) / 2)
    gesture.distance = distance
    return
  }
  const dx = a.x - gesture.startX, dy = a.y - gesture.startY
  if (!gesture.moved && Math.hypot(dx, dy) < DRAG_THRESHOLD) return
  gesture.moved = true; dragging.value = true
  pan.value = clampPan(gesture.origin.x + dx, gesture.origin.y + dy, zoom.value)
}
function onPointerUp(event) {
  pointers.delete(event.pointerId)
  if (gesture?.moved) suppressClick = true
  if (pointers.size) { beginGesture(); return }
  endGesture()
  // The click (if any) follows pointerup synchronously; forget the drag once it has been ignored.
  setTimeout(() => { suppressClick = false })
}
function endGesture() {
  pointers.clear(); gesture = null; dragging.value = false
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  window.removeEventListener('pointercancel', onPointerUp)
}

function selectPoint(event) {
  if (suppressClick) { suppressClick = false; return }
  if (!props.editable || !imageUrl.value || error.value) return
  const rect = event.currentTarget.getBoundingClientRect()
  if (!rect.width || !rect.height) return
  emit('update:point', {
    x: Number(Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)).toFixed(8)),
    y: Number(Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height)).toFixed(8))
  })
}
function movePoint(event) {
  if (!props.editable || !imageUrl.value || error.value) return
  const delta = { ArrowLeft: [-0.01, 0], ArrowRight: [0.01, 0], ArrowUp: [0, -0.01], ArrowDown: [0, 0.01], Enter: [0, 0], ' ': [0, 0] }[event.key]
  if (!delta) return
  event.preventDefault()
  const current = props.point || { x: 0.5, y: 0.5 }
  emit('update:point', { x: Number(Math.max(0, Math.min(1, current.x + delta[0])).toFixed(8)), y: Number(Math.max(0, Math.min(1, current.y + delta[1])).toFixed(8)) })
}
</script>

<template>
  <div class="floor-plan-viewer" v-loading="loading">
    <div v-if="imageUrl && !error" class="plan-tools">
      <span>{{ editable ? '点击图纸标记维修位置；拖动平移、滚轮缩放' : (point ? '红点为报修位置 · 拖动平移、滚轮或双指缩放' : '拖动平移、滚轮或双指缩放') }}</span>
      <div>
        <el-button size="small" :disabled="zoom <= 1" aria-label="缩小平面图" @click="stepZoom(1 / STEP)">−</el-button>
        <el-button size="small" :disabled="zoom >= MAX_ZOOM" aria-label="放大平面图" @click="stepZoom(STEP)">＋</el-button>
        <el-button size="small" :disabled="zoom === 1 && !pan.x && !pan.y" aria-label="复位平面图" @click="resetZoom">复位</el-button>
        <el-button v-if="point && !editable" size="small" aria-label="定位报修点" @click="centerPoint">定位报修点</el-button>
        <el-button v-if="editable && point" size="small" @click="emit('update:point', null)">清除点位</el-button>
      </div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-else-if="imageUrl" ref="viewport" class="plan-scroll" :class="{ dragging }"
         @wheel.prevent="onWheel" @pointerdown="onPointerDown" @dblclick.prevent="onDoubleClick">
      <div ref="canvas" class="plan-canvas" :class="{ editable, animated }" :style="canvasStyle"
           :role="editable ? 'button' : 'img'" :tabindex="editable ? 0 : undefined"
           :aria-label="editable ? '平面图维修定位，点击或使用方向键调整位置' : '维修位置平面图'"
           @click="selectPoint" @keydown="movePoint" @transitionend="animated = false">
        <img :src="imageUrl" alt="楼层建筑平面图" draggable="false" @error="error = '图片无法显示，请重新上传 JPG 或 PNG 平面图'" />
        <span v-if="point" class="plan-marker" :style="markerStyle" aria-label="报修点位" />
      </div>
    </div>
    <el-empty v-else-if="!loading" description="该楼层尚未上传平面图，可先填写位置说明" :image-size="64" />
  </div>
</template>

<style scoped>
.floor-plan-viewer { width: 100%; min-height: 80px; }
.plan-tools { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 10px; font-size: 13px; color: var(--text-secondary); }
.plan-scroll { position: relative; max-height: 60vh; overflow: hidden; border: 1px solid var(--el-border-color); border-radius: 8px; background: #f5f7fa; cursor: grab; touch-action: none; user-select: none; }
.plan-scroll.dragging { cursor: grabbing; }
.plan-canvas { position: relative; line-height: 0; transform-origin: 0 0; will-change: transform; }
.plan-canvas.animated { transition: transform .2s ease-out; }
.plan-canvas img { display: block; width: 100%; height: auto; pointer-events: none; user-select: none; }
.plan-canvas.editable { cursor: crosshair; }
.plan-scroll.dragging .plan-canvas { cursor: grabbing; }
.plan-canvas:focus-visible { outline: 3px solid var(--el-color-primary); outline-offset: -3px; }
/* The marker sits inside the scaled canvas; counter-scale it so it stays the same size on screen. */
.plan-marker { position: absolute; width: 18px; height: 18px; border: 3px solid #fff; border-radius: 50%; box-sizing: border-box; background: #e43131; box-shadow: 0 0 0 2px #e43131, 0 2px 8px #0006; transform: translate(-50%, -50%) scale(var(--marker-scale, 1)); pointer-events: none; }
@media (prefers-reduced-motion: reduce) { .plan-canvas.animated { transition: none; } }
</style>
