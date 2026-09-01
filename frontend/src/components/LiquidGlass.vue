<script setup>
import { onBeforeUnmount, onMounted, ref, useId } from 'vue'

// React Bits GlassSurface(液态玻璃)的 Vue 落地:
// SVG 位移贴图折射 + RGB 三通道色散,backdrop-filter: url(#filter) 驱动。
// Chromium 完整支持;Safari/Firefox 自动降级为普通磨砂(--fallback)。
defineOptions({ name: 'LiquidGlass' })

const props = defineProps({
  borderRadius: { type: Number, default: 18 },
  borderWidth: { type: Number, default: 0.07 },
  brightness: { type: Number, default: 50 },
  opacity: { type: Number, default: 0.93 },
  blur: { type: Number, default: 11 },
  displace: { type: Number, default: 0.7 },
  backgroundOpacity: { type: Number, default: 0 },
  saturation: { type: Number, default: 1 },
  distortionScale: { type: Number, default: -180 },
  redOffset: { type: Number, default: 0 },
  greenOffset: { type: Number, default: 10 },
  blueOffset: { type: Number, default: 20 },
})

const uid = useId().replace(/[^a-zA-Z0-9_-]/g, '')
const filterId = `liquid-glass-${uid}`
const redGradId = `lg-red-${uid}`
const blueGradId = `lg-blue-${uid}`
const rootEl = ref(null)
const feImageEl = ref(null)
const svgSupported = ref(false)
let resizeObserver

// 位移贴图:红/蓝渐变按 difference 混合形成边缘矢量场,
// 内部一块模糊亮度矩形决定"镜片"主体的明度。尺寸/圆角变化时重新生成。
function displacementMap() {
  const rect = rootEl.value?.getBoundingClientRect()
  const w = rect?.width || 400
  const h = rect?.height || 200
  const edge = Math.min(w, h) * (props.borderWidth * 0.5)
  const r = props.borderRadius
  const svg = `<svg viewBox="0 0 ${w} ${h}" xmlns="http://www.w3.org/2000/svg">`
    + `<defs>`
    + `<linearGradient id="${redGradId}" x1="100%" y1="0%" x2="0%" y2="0%">`
    + `<stop offset="0%" stop-color="#0000"/><stop offset="100%" stop-color="red"/></linearGradient>`
    + `<linearGradient id="${blueGradId}" x1="0%" y1="0%" x2="0%" y2="100%">`
    + `<stop offset="0%" stop-color="#0000"/><stop offset="100%" stop-color="blue"/></linearGradient>`
    + `</defs>`
    + `<rect x="0" y="0" width="${w}" height="${h}" fill="black"></rect>`
    + `<rect x="0" y="0" width="${w}" height="${h}" rx="${r}" fill="url(#${redGradId})" />`
    + `<rect x="0" y="0" width="${w}" height="${h}" rx="${r}" fill="url(#${blueGradId})" style="mix-blend-mode: difference" />`
    + `<rect x="${edge}" y="${edge}" width="${w - edge * 2}" height="${h - edge * 2}" rx="${r}" `
    + `fill="hsl(0 0% ${props.brightness}% / ${props.opacity})" style="filter:blur(${props.blur}px)" />`
    + `</svg>`
  return `data:image/svg+xml,${encodeURIComponent(svg)}`
}

function updateMap() {
  feImageEl.value?.setAttribute('href', displacementMap())
}

function detectSupport() {
  const ua = navigator.userAgent
  const isWebkitOnly = /Safari/.test(ua) && !/Chrome/.test(ua)
  if (isWebkitOnly || /Firefox/.test(ua)) return false
  const probe = document.createElement('div')
  probe.style.backdropFilter = `url(#${filterId})`
  return probe.style.backdropFilter !== ''
}

onMounted(() => {
  svgSupported.value = detectSupport()
  updateMap()
  resizeObserver = new ResizeObserver(() => setTimeout(updateMap, 0))
  resizeObserver.observe(rootEl.value)
})
onBeforeUnmount(() => resizeObserver?.disconnect())
</script>

<template>
  <div
    ref="rootEl"
    class="liquid-glass"
    :class="svgSupported ? 'liquid-glass--svg' : 'liquid-glass--fallback'"
    :style="{
      borderRadius: `${borderRadius}px`,
      '--glass-frost': backgroundOpacity,
      '--glass-saturation': saturation,
      '--filter-id': `url(#${filterId})`,
    }"
  >
    <svg class="liquid-glass__filter" aria-hidden="true" focusable="false">
      <defs>
        <filter :id="filterId" color-interpolation-filters="sRGB" x="0%" y="0%" width="100%" height="100%">
          <feImage ref="feImageEl" x="0" y="0" width="100%" height="100%" preserveAspectRatio="none" result="map" />
          <feDisplacementMap in="SourceGraphic" in2="map" :scale="distortionScale + redOffset"
                             xChannelSelector="R" yChannelSelector="G" result="dispRed" />
          <feColorMatrix in="dispRed" type="matrix"
                         values="1 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 1 0" result="red" />
          <feDisplacementMap in="SourceGraphic" in2="map" :scale="distortionScale + greenOffset"
                             xChannelSelector="R" yChannelSelector="G" result="dispGreen" />
          <feColorMatrix in="dispGreen" type="matrix"
                         values="0 0 0 0 0  0 1 0 0 0  0 0 0 0 0  0 0 0 1 0" result="green" />
          <feDisplacementMap in="SourceGraphic" in2="map" :scale="distortionScale + blueOffset"
                             xChannelSelector="R" yChannelSelector="G" result="dispBlue" />
          <feColorMatrix in="dispBlue" type="matrix"
                         values="0 0 0 0 0  0 0 0 0 0  0 0 1 0 0  0 0 0 1 0" result="blue" />
          <feBlend in="red" in2="green" mode="screen" result="rg" />
          <feBlend in="rg" in2="blue" mode="screen" result="output" />
          <feGaussianBlur in="output" :stdDeviation="displace" />
        </filter>
      </defs>
    </svg>
    <div class="liquid-glass__content"><slot /></div>
  </div>
</template>

<style scoped>
.liquid-glass {
  position: relative;
  overflow: hidden;
}
.liquid-glass__filter {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  pointer-events: none;
  z-index: -1;
}
.liquid-glass__content {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
  border-radius: inherit;
}
.liquid-glass--svg {
  background: hsl(0 0% 100% / var(--glass-frost, 0));
  backdrop-filter: var(--filter-id) saturate(var(--glass-saturation, 1));
  box-shadow:
    0 0 2px 1px color-mix(in oklch, black, transparent 85%) inset,
    0 0 10px 4px color-mix(in oklch, black, transparent 90%) inset,
    0 4px 16px rgba(17, 17, 26, .05),
    0 8px 24px rgba(17, 17, 26, .05),
    0 16px 56px rgba(17, 17, 26, .05),
    0 4px 16px rgba(17, 17, 26, .05) inset,
    0 8px 24px rgba(17, 17, 26, .05) inset,
    0 16px 56px rgba(17, 17, 26, .05) inset;
}
.liquid-glass--fallback {
  background: rgba(255, 255, 255, .25);
  backdrop-filter: blur(12px) saturate(1.8) brightness(1.1);
  -webkit-backdrop-filter: blur(12px) saturate(1.8) brightness(1.1);
  border: 1px solid rgba(255, 255, 255, .3);
  box-shadow:
    0 8px 32px 0 rgba(31, 38, 135, .2),
    0 2px 16px 0 rgba(31, 38, 135, .1),
    inset 0 1px 0 0 rgba(255, 255, 255, .4),
    inset 0 -1px 0 0 rgba(255, 255, 255, .2);
}
@supports not ((backdrop-filter: blur(10px)) or (-webkit-backdrop-filter: blur(10px))) {
  .liquid-glass--fallback { background: rgba(255, 255, 255, .55); }
}
</style>
