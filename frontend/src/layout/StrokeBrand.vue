<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { gsap } from 'gsap'

// Vue adaptation of the supplied React Bits StrokeText source: measured SVG
// glyphs, a staggered outline draw, then the original GSAP fill wipe.
defineOptions({ name: 'StrokeBrand' })
const fullTitle = '智慧云仓系统'
const title = computed(() => fullTitle)
const characters = computed(() => Array.from(title.value))
const root = ref(null)
const outline = ref(null)
const wipe = ref(null)
const box = ref(null)
const clipId = `brand-wipe-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`
const reducedMotion = ref(false)

const fontSize = 128
const strokeWidth = 2.4 // Compensate for scaling the reference to a sidebar title.
const dash = Math.max(fontSize * 7, 200)
const bounds = computed(() => box.value || {
  x: -13, y: -128, width: characters.value.length * 124 + 26, height: 154,
})
const viewBox = computed(() => {
  const { x, y, width, height } = bounds.value
  return `${x} ${y} ${width} ${height}`
})
let timeline
let mediaQuery
let disposed = false

function measure() {
  let bbox
  try { bbox = outline.value?.getBBox() } catch { return false }
  if (!bbox?.width || !bbox.height) return false
  const pad = Math.max(strokeWidth, fontSize * 0.1)
  const next = { x: bbox.x - pad, y: bbox.y - pad, width: bbox.width + pad * 2, height: bbox.height + pad * 2 }
  if (box.value && Object.keys(next).every(key => Math.abs(box.value[key] - next[key]) < 0.5)) return false
  box.value = next
  return true
}

function play() {
  if (disposed || !root.value) return
  timeline?.kill()
  const strokes = root.value.querySelectorAll('[data-stroke-char]')
  if (reducedMotion.value) {
    gsap.set(strokes, { strokeDasharray: dash, strokeDashoffset: 0 })
    gsap.set(wipe.value, { attr: { width: bounds.value.width } })
    return
  }
  gsap.set(strokes, { strokeDasharray: dash, strokeDashoffset: dash })
  gsap.set(wipe.value, { attr: { width: 0 } })
  timeline = gsap.timeline({ defaults: { overwrite: 'auto' } })
  timeline.to(strokes, { strokeDashoffset: 0, duration: 1.6, ease: 'power2.out', stagger: 0.05 }, 0)
  timeline.to(wipe.value, {
    attr: { width: bounds.value.width }, duration: 0.8, ease: 'power2.inOut',
  }, 1.6 + 0.2)
}

function onMotionChange(event) {
  reducedMotion.value = event.matches
  if (event.matches) play()
}

watch(title, async () => {
  box.value = null
  await nextTick()
  if (disposed) return
  measure()
  await nextTick()
  play()
})

onMounted(async () => {
  mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = mediaQuery.matches
  mediaQuery.addEventListener?.('change', onMotionChange)
  measure()
  await nextTick()
  play()
  document.fonts?.ready.then(async () => {
    if (disposed || !measure()) return
    await nextTick()
    play()
  }).catch(() => {})
})

onBeforeUnmount(() => {
  disposed = true
  timeline?.kill()
  mediaQuery?.removeEventListener?.('change', onMotionChange)
})
</script>

<template>
  <span ref="root" class="stroke-brand" :class="{ 'stroke-brand--reduced': reducedMotion }"
        role="img" :aria-label="fullTitle" @pointerenter="play">
    <svg :viewBox="viewBox" preserveAspectRatio="xMidYMid meet" aria-hidden="true" focusable="false">
      <defs>
        <clipPath :id="clipId" clipPathUnits="userSpaceOnUse">
          <rect ref="wipe" :x="bounds.x" :y="bounds.y" :width="bounds.width" :height="bounds.height" />
        </clipPath>
      </defs>
      <text ref="outline" class="stroke-brand__outline" x="0" y="0" fill="none"
            :stroke-width="strokeWidth" :stroke-dasharray="dash">
        <tspan v-for="(character, index) in characters" :key="index" data-stroke-char>{{ character }}</tspan>
      </text>
      <text class="stroke-brand__fill" data-fill-text x="0" y="0" :clip-path="`url(#${clipId})`">
        <tspan v-for="(character, index) in characters" :key="index" data-fill-char>{{ character }}</tspan>
      </text>
    </svg>
  </span>
</template>

<style scoped>
.stroke-brand { display: block; width: 100%; line-height: 0; }
.stroke-brand svg { display: block; width: 100%; height: 36px; }
.stroke-brand text { font-family: inherit; font-size: 128px; font-weight: 800; letter-spacing: -4px; user-select: none; }
.stroke-brand__outline { stroke: #d7d7e0; stroke-linejoin: round; stroke-linecap: round; }
.stroke-brand__fill { fill: #f5f5f7; stroke: none; }
</style>
