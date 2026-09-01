<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

// React Bits ClickSpark 的 Vue 落地:全局点击处放射 8 根渐缩短线(canvas 绘制)。
// 与参考实现的差异:画布 fixed 覆盖全视口且监听 window 点击,弹层内点击同样生效;
// prefers-reduced-motion 时整体停用。
defineOptions({ name: 'ClickSpark' })

const props = defineProps({
  sparkColor: { type: String, default: '#6366f1' },
  sparkSize: { type: Number, default: 10 },
  sparkRadius: { type: Number, default: 15 },
  sparkCount: { type: Number, default: 8 },
  duration: { type: Number, default: 400 },
  extraScale: { type: Number, default: 1.0 },
})

const canvasEl = ref(null)
let sparks = []
let rafId = 0
let reduced = false
const easeOut = t => t * (2 - t)

function resizeCanvas() {
  const c = canvasEl.value
  if (!c) return
  if (c.width !== window.innerWidth || c.height !== window.innerHeight) {
    c.width = window.innerWidth
    c.height = window.innerHeight
  }
}

function draw(ts) {
  const c = canvasEl.value
  const ctx = c?.getContext ? c.getContext('2d') : null
  if (ctx) {
    ctx.clearRect(0, 0, c.width, c.height)
    sparks = sparks.filter(s => {
      const elapsed = ts - s.startTime
      if (elapsed >= props.duration) return false
      const eased = easeOut(elapsed / props.duration)
      const distance = eased * props.sparkRadius * props.extraScale
      const lineLength = props.sparkSize * (1 - eased)
      ctx.strokeStyle = props.sparkColor
      ctx.lineWidth = 2
      ctx.beginPath()
      ctx.moveTo(s.x + distance * Math.cos(s.angle), s.y + distance * Math.sin(s.angle))
      ctx.lineTo(s.x + (distance + lineLength) * Math.cos(s.angle), s.y + (distance + lineLength) * Math.sin(s.angle))
      ctx.stroke()
    return true
    })
  }
  rafId = requestAnimationFrame(draw)
}

function onClick(e) {
  if (reduced) return
  const now = performance.now()
  for (let i = 0; i < props.sparkCount; i++) {
    sparks.push({ x: e.clientX, y: e.clientY, angle: (2 * Math.PI * i) / props.sparkCount, startTime: now })
  }
}

onMounted(() => {
  reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
  resizeCanvas()
  window.addEventListener('resize', resizeCanvas)
  window.addEventListener('click', onClick, { passive: true })
  rafId = requestAnimationFrame(draw)
})
onBeforeUnmount(() => {
  cancelAnimationFrame(rafId)
  window.removeEventListener('resize', resizeCanvas)
  window.removeEventListener('click', onClick)
})

defineExpose({ activeSparks: () => sparks.length })
</script>

<template>
  <canvas ref="canvasEl" class="click-spark__canvas" aria-hidden="true"></canvas>
  <slot />
</template>

<style scoped>
.click-spark__canvas {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100vh;
  pointer-events: none;
  user-select: none;
  z-index: 4000;
}
</style>
