<template>
  <span ref="root" class="depth-brand">
    <span ref="stage" class="depth-brand__stage">
      <span
        v-for="layer in depthLayers"
        :key="layer.index"
        aria-hidden="true"
        class="depth-brand__layer"
        :style="layer.style"
      >{{ TEXT }}</span>
      <span class="depth-brand__face">{{ TEXT }}</span>
    </span>
  </span>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

// React Bits DepthText 的 Vue 落地:层叠 Z 轴挤出立体字 + 指针反向视差 + 空闲自动环绕。
// 参数按 60px 顶栏品牌尺寸收敛(原 demo 是 7rem 大字)。
defineOptions({ name: 'DepthBrand' })

const TEXT = '智慧云仓系统'
const LAYERS = 14
const DEPTH = 0.9      // 每层 Z 位移(px),总挤出深度 ≈ LAYERS*DEPTH
const TILT = 8
const SMOOTHING = 0.14
const ORBIT_SPEED = 0.35
const FACE_COLOR = '#f8fafc'
const DEPTH_COLOR = '#7c3aed'

const clamp = (v, min, max) => Math.min(Math.max(v, min), max)

// 层色从面色向深度色二次缓动过渡(原版 getLayerColor)
function layerColor(index) {
  const eased = (index / LAYERS) ** 2
  const faceMix = Math.round((1 - eased) * 72 + 4)
  return `color-mix(in srgb, ${FACE_COLOR} ${faceMix}%, ${DEPTH_COLOR})`
}
const depthLayers = computed(() =>
  Array.from({ length: LAYERS }, (_, i) => {
    const index = LAYERS - i
    return {
      index,
      style: { color: layerColor(index), transform: `translateZ(${-index * DEPTH}px)` },
    }
  })
)

const root = ref(null)
const stage = ref(null)
const BASE = { x: -TILT * 0.32, y: TILT * 0.42 }
const current = { ...BASE }
const target = { ...BASE }
let frameId = 0
let activePointer = false
let startTime = 0
let tracking = false

function setTransform() {
  stage.value.style.transform = `rotateX(${current.x.toFixed(3)}deg) rotateY(${current.y.toFixed(3)}deg)`
}
function onPointerMove(event) {
  const rect = root.value.getBoundingClientRect()
  if (!rect.width || !rect.height) return
  activePointer = true
  const x = clamp((event.clientX - (rect.left + rect.width / 2)) / (rect.width * 0.8), -1, 1)
  const y = clamp((event.clientY - (rect.top + rect.height / 2)) / (rect.height * 0.8), -1, 1)
  target.x = BASE.x - y * TILT
  target.y = BASE.y + x * TILT
}
function onPointerLeave() {
  activePointer = false
  target.x = BASE.x
  target.y = BASE.y
}
function tick(now) {
  if (!activePointer) {
    const elapsed = (now - startTime) / 1000
    const orbit = elapsed * ORBIT_SPEED * Math.PI * 2
    const amount = tracking ? 0.18 : 0.55
    target.x = BASE.x + Math.sin(orbit) * TILT * amount
    target.y = BASE.y + Math.cos(orbit * 0.85) * TILT * amount
  }
  current.x += (target.x - current.x) * SMOOTHING
  current.y += (target.y - current.y) * SMOOTHING
  setTransform()
  frameId = requestAnimationFrame(tick)
}

onMounted(() => {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    setTransform() // 静止在基础倾角
    return
  }
  tracking = window.matchMedia('(hover: hover) and (pointer: fine)').matches
  if (tracking) {
    window.addEventListener('pointermove', onPointerMove)
    window.addEventListener('pointerleave', onPointerLeave)
    window.addEventListener('blur', onPointerLeave)
  }
  startTime = performance.now()
  setTransform()
  frameId = requestAnimationFrame(tick)
})
onBeforeUnmount(() => {
  if (tracking) {
    window.removeEventListener('pointermove', onPointerMove)
    window.removeEventListener('pointerleave', onPointerLeave)
    window.removeEventListener('blur', onPointerLeave)
  }
  cancelAnimationFrame(frameId)
})
</script>

<style scoped>
.depth-brand {
  display: inline-block;
  perspective: 800px;
  perspective-origin: 50% 48%;
  isolation: isolate;
}
.depth-brand__stage {
  position: relative;
  display: inline-grid;
  place-items: center;
  transform-style: preserve-3d;
  transform: rotateX(-2.4deg) rotateY(3.15deg);
  transform-origin: 50% 50%;
  will-change: transform;
}
.depth-brand__layer,
.depth-brand__face {
  grid-area: 1 / 1;
  display: inline-block;
  font-size: 22px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: 1px;
  white-space: nowrap;
  user-select: none;
  transform-style: preserve-3d;
  backface-visibility: hidden;
  text-rendering: geometricPrecision;
}
.depth-brand__layer {
  position: absolute;
  inset: 0;
  z-index: 0;
  filter: saturate(.95) brightness(.92);
  pointer-events: none;
}
.depth-brand__face {
  position: relative;
  z-index: 1;
  color: #f8fafc;
  text-shadow: 0 10px 18px color-mix(in srgb, #7c3aed 36%, transparent), 0 2px 4px rgba(0, 0, 0, .28);
  transform: translateZ(.6px);
}
@media (prefers-reduced-motion: reduce) {
  .depth-brand__stage { will-change: auto; }
}
</style>
