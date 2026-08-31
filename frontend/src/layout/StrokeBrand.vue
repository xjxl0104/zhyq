<script setup>
import { computed, onBeforeUnmount, onMounted, ref, useId } from 'vue'

defineOptions({ name: 'StrokeBrand' })
const props = defineProps({ compact: { type: Boolean, default: false } })
const fullTitle = '澳乐智慧云仓系统'
const title = computed(() => props.compact ? '澳乐' : fullTitle)
const characters = computed(() => Array.from(title.value))
const width = computed(() => props.compact ? 52 : 192)
const clipId = `brand-wipe-${useId()}`
const replay = ref(0)
const reducedMotion = ref(false)
let mediaQuery

function replayAnimation() {
  if (!reducedMotion.value) replay.value += 1
}

function onMotionChange(event) {
  reducedMotion.value = event.matches
}

onMounted(() => {
  mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = mediaQuery.matches
  mediaQuery.addEventListener?.('change', onMotionChange)
})
onBeforeUnmount(() => mediaQuery?.removeEventListener?.('change', onMotionChange))
</script>

<template>
  <span class="stroke-brand" :class="{ 'stroke-brand--reduced': reducedMotion }"
        role="img" :aria-label="fullTitle" @pointerenter="replayAnimation">
    <svg :key="`${replay}-${compact}`" :viewBox="`0 0 ${width} 36`" aria-hidden="true" focusable="false">
      <defs>
        <clipPath :id="clipId" clipPathUnits="userSpaceOnUse">
          <rect class="stroke-brand__wipe" x="0" y="0" :width="width" height="36" />
        </clipPath>
      </defs>
      <text class="stroke-brand__outline" x="4" y="26" :textLength="width - 8" lengthAdjust="spacing">
        <tspan v-for="(character, index) in characters" :key="index" data-stroke-char
               :style="{ '--char-delay': `${index * 45}ms` }">{{ character }}</tspan>
      </text>
      <text class="stroke-brand__fill" data-fill-text x="4" y="26" :textLength="width - 8"
            lengthAdjust="spacing" :clip-path="`url(#${clipId})`">{{ title }}</text>
    </svg>
  </span>
</template>

<style scoped>
/* Vue/SVG adaptation of React Bits Stroke Text: outline drawing, then a fill wipe.
   Reference: https://reactbits.dev/text-animations/stroke-text */
.stroke-brand { display: block; width: 100%; line-height: 0; }
.stroke-brand svg { display: block; width: 100%; height: 36px; overflow: visible; }
.stroke-brand text { font-family: inherit; font-size: 22px; font-weight: 750; }
.stroke-brand__outline { fill: none; stroke: #7c3aed; stroke-width: .8; stroke-linejoin: round; }
.stroke-brand__outline tspan {
  stroke-dasharray: 320;
  stroke-dashoffset: 0;
  animation: brand-draw 1000ms cubic-bezier(.16, 1, .3, 1) var(--char-delay) both;
}
.stroke-brand__fill { fill: #312e81; }
.stroke-brand__wipe {
  transform-origin: 0 0;
  animation: brand-fill 550ms cubic-bezier(.65, 0, .35, 1) 1050ms both;
}
@keyframes brand-draw { from { stroke-dashoffset: 320; } to { stroke-dashoffset: 0; } }
@keyframes brand-fill { from { transform: scaleX(0); } to { transform: scaleX(1); } }
.stroke-brand--reduced tspan, .stroke-brand--reduced .stroke-brand__wipe { animation: none; }
@media (prefers-reduced-motion: reduce) {
  .stroke-brand tspan, .stroke-brand__wipe { animation: none; }
}
</style>
