<template>
  <div ref="root" class="gooey-nav"
       :class="{ 'gooey-nav--ready': ready, 'gooey-nav--reduced': reducedMotion }"
       :style="indicatorStyle"
       @pointerover="handlePointerOver" @pointerleave="handlePointerLeave"
       @focusin="handleFocusIn" @focusout="handleFocusOut" @click.capture="handleClick">
    <div class="gooey-nav__effect" aria-hidden="true">
      <svg class="gooey-nav__filter" focusable="false">
        <defs>
          <!-- Alpha contrast gives the reference's blur(7px) / contrast(100)
               merging on a light background without its black blend rectangle. -->
          <filter :id="filterId" x="-150%" y="-300%" width="400%" height="700%" color-interpolation-filters="sRGB">
            <feGaussianBlur in="SourceGraphic" stdDeviation="7" result="blur" />
            <feColorMatrix in="blur" type="matrix"
                           values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 100 -49.5" result="goo" />
            <feComposite in="SourceGraphic" in2="goo" operator="atop" />
          </filter>
        </defs>
      </svg>
      <span class="gooey-nav__pill" />
      <div v-if="particles.length" :key="burstSequence" class="gooey-nav__burst" :style="burstStyle">
        <span class="gooey-nav__pulse" />
        <span v-for="particle in particles" :key="particle.id" class="gooey-nav__particle" :style="particle.style">
          <span class="gooey-nav__point" />
        </span>
      </div>
    </div>
    <div class="gooey-nav__content"><slot /></div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'

// The supplied React Bits particle trajectory, timing and two-element animation
// are retained. The slot adapter leaves Element Plus routing and keyboard use intact.
const props = defineProps({
  activePath: { type: String, required: true },
  collapsed: { type: Boolean, default: false },
  animationTime: { type: Number, default: 600 },
  particleCount: { type: Number, default: 15 },
  particleDistances: { type: Array, default: () => [90, 10] },
  particleR: { type: Number, default: 100 },
  timeVariance: { type: Number, default: 300 },
  colors: { type: Array, default: () => [1, 2, 3, 1, 2, 3, 1, 4] },
})
const filterId = `gooey-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`
const root = ref(null)
const ready = ref(false)
const reducedMotion = ref(false)
const particles = ref([])
const geometry = ref({ x: 0, y: 0, width: 0, height: 0 })
const burstGeometry = ref({ x: 0, y: 0, width: 0, height: 0 })
const burstSequence = ref(0)
let resizeObserver
let mediaQuery
let particleTimer
let observedItem
let highlightedItem
let burstAnchor
let hoveredItem
let focusedItem
let clickedItem
let pendingBurst = false
let disposed = false
let measureVersion = 0
const animationFrames = new Set()

const indicatorStyle = computed(() => ({
  '--gooey-x': `${geometry.value.x}px`, '--gooey-y': `${geometry.value.y}px`,
  '--gooey-width': `${geometry.value.width}px`, '--gooey-height': `${geometry.value.height}px`,
}))
const burstStyle = computed(() => ({
  left: `${burstGeometry.value.x}px`, top: `${burstGeometry.value.y}px`,
  width: `${burstGeometry.value.width}px`, height: `${burstGeometry.value.height}px`,
  filter: `url(#${filterId})`,
}))

function requestFrame(callback) {
  const id = requestAnimationFrame(() => { animationFrames.delete(id); callback() })
  animationFrames.add(id)
}
function clearFrames() {
  animationFrames.forEach(id => cancelAnimationFrame(id))
  animationFrames.clear()
}
function clearParticles() {
  clearTimeout(particleTimer)
  particleTimer = undefined
  particles.value = []
  burstAnchor?.classList.remove('is-gooey-burst')
  burstAnchor = null
}
const noise = (n = 1) => n / 2 - Math.random() * n
function getXY(distance, pointIndex, totalPoints) {
  const angle = ((360 + noise(8)) / totalPoints) * pointIndex * (Math.PI / 180)
  return [distance * Math.cos(angle), distance * Math.sin(angle)]
}
function burstParticles(rect, anchor) {
  if (reducedMotion.value) return
  clearParticles()
  burstAnchor = anchor
  burstAnchor?.classList.add('is-gooey-burst')
  burstGeometry.value = { ...rect }
  burstSequence.value += 1
  let longestTime = 0
  particles.value = Array.from({ length: props.particleCount }, (_, i) => {
    const time = props.animationTime * 2 + noise(props.timeVariance * 2)
    const start = getXY(props.particleDistances[0], props.particleCount - i, props.particleCount)
    const end = getXY(props.particleDistances[1] + noise(7), props.particleCount - i, props.particleCount)
    const rotate = noise(props.particleR / 10)
    const rotation = rotate > 0 ? (rotate + props.particleR / 20) * 10 : (rotate - props.particleR / 20) * 10
    longestTime = Math.max(longestTime, time)
    return {
      id: i,
      style: {
        '--start-x': `${start[0]}px`, '--start-y': `${start[1]}px`,
        '--end-x': `${end[0]}px`, '--end-y': `${end[1]}px`,
        '--time': `${time}ms`, '--scale': 1 + noise(0.2), '--rotate': `${rotation}deg`,
        '--color': `var(--color-${props.colors[Math.floor(Math.random() * props.colors.length)]}, var(--gooey-fill))`,
      },
    }
  })
  particleTimer = window.setTimeout(clearParticles, longestTime + 30)
}

function menuItemFrom(target) {
  const item = target?.closest?.('.el-menu-item, .el-sub-menu__title')
  if (!item || !root.value?.contains(item) || item.matches('.is-disabled, [aria-disabled="true"]') || item.closest('.el-sub-menu.is-disabled')) return null
  return item
}
function handlePointerOver(event) {
  if (event.pointerType === 'touch') return
  const item = menuItemFrom(event.target)
  if (item === hoveredItem) return
  hoveredItem = item
  focusedItem = null
  scheduleMeasure()
}
function handlePointerLeave() { hoveredItem = null; scheduleMeasure() }
function handleFocusIn(event) { focusedItem = menuItemFrom(event.target); scheduleMeasure() }
function handleFocusOut(event) { focusedItem = menuItemFrom(event.relatedTarget); scheduleMeasure() }
function handleClick(event) {
  const item = menuItemFrom(event.target)
  if (!item) return
  hoveredItem = item
  clickedItem = item
  scheduleMeasure({ burst: true })
}
function itemGeometry(item) {
  if (!item || !root.value?.contains(item)) return null
  const frame = root.value.getBoundingClientRect()
  const rect = item.getBoundingClientRect()
  if (!rect.width || !rect.height) return null
  return { x: rect.left - frame.left, y: rect.top - frame.top, width: rect.width, height: rect.height }
}
function activeVisibleItem() {
  let item = root.value?.querySelector('.el-menu-item.is-active') || root.value?.querySelector('.el-sub-menu.is-active > .el-sub-menu__title')
  // A selected leaf can be hidden by a closed/collapsed group. Use its visible
  // ancestor title so the current destination never loses selection feedback.
  while (item && !itemGeometry(item)) {
    const parent = item.classList.contains('el-sub-menu__title') ? item.parentElement?.parentElement : item.parentElement
    const group = parent?.closest('.el-sub-menu')
    item = group?.querySelector(':scope > .el-sub-menu__title')
  }
  return item
}
function measureActive(burst) {
  const preview = focusedItem || hoveredItem
  const item = itemGeometry(preview) ? preview : activeVisibleItem()
  const rect = itemGeometry(item)
  highlightedItem?.classList.remove('is-gooey-target')
  highlightedItem = null
  ready.value = Boolean(rect)
  if (!rect) { clearParticles(); return }
  geometry.value = rect
  item.classList.add('is-gooey-target')
  highlightedItem = item
  // Route rendering may refresh the menu's class attribute during a burst.
  burstAnchor?.classList.add('is-gooey-burst')
  if (resizeObserver && observedItem !== item) {
    if (observedItem) resizeObserver.unobserve(observedItem)
    resizeObserver.observe(item)
    observedItem = item
  }
  if (burst) {
    const clickedRect = itemGeometry(clickedItem)
    burstParticles(clickedRect || rect, clickedRect ? clickedItem : item)
  }
  clickedItem = null
}
async function scheduleMeasure({ burst = false } = {}) {
  pendingBurst ||= burst
  const version = ++measureVersion
  clearFrames()
  await nextTick()
  if (disposed || version !== measureVersion) return
  requestFrame(() => requestFrame(() => {
    const playBurst = pendingBurst
    pendingBurst = false
    measureActive(playBurst)
  }))
}
function handleReflow(event) {
  if (event?.target?.closest?.('.gooey-nav__effect')) return
  scheduleMeasure()
}
function handleMotionPreference(event) {
  reducedMotion.value = event.matches
  if (event.matches) clearParticles()
}
watch(() => props.activePath, (path, previous) => {
  hoveredItem = null
  focusedItem = null
  scheduleMeasure({ burst: Boolean(previous && path !== previous) })
}, { flush: 'post' })
watch(() => props.collapsed, () => {
  clearParticles()
  hoveredItem = null
  focusedItem = null
  scheduleMeasure()
})
onMounted(() => {
  mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = mediaQuery.matches
  mediaQuery.addEventListener?.('change', handleMotionPreference)
  root.value.addEventListener('scroll', handleReflow, true)
  root.value.addEventListener('transitionend', handleReflow)
  window.addEventListener('resize', handleReflow)
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(handleReflow)
    resizeObserver.observe(root.value)
  }
  scheduleMeasure()
})
onBeforeUnmount(() => {
  disposed = true
  pendingBurst = false
  clearFrames()
  clearParticles()
  highlightedItem?.classList.remove('is-gooey-target')
  resizeObserver?.disconnect()
  root.value?.removeEventListener('scroll', handleReflow, true)
  root.value?.removeEventListener('transitionend', handleReflow)
  window.removeEventListener('resize', handleReflow)
  mediaQuery?.removeEventListener?.('change', handleMotionPreference)
})
</script>

<style scoped>
.gooey-nav {
  --gooey-fill: #312e81;
  --color-1: #6d5ce7;
  --color-2: #a78bfa;
  --color-3: #818cf8;
  --color-4: #f1b66a;
  position: relative;
  min-height: 100%;
  isolation: isolate;
}
.gooey-nav__effect { position: absolute; inset: 0; z-index: 0; overflow: hidden; opacity: 0; pointer-events: none; }
.gooey-nav--ready .gooey-nav__effect { opacity: 1; }
.gooey-nav__filter { position: absolute; width: 0; height: 0; }
.gooey-nav__pill {
  position: absolute;
  top: 0;
  left: 0;
  width: var(--gooey-width);
  height: var(--gooey-height);
  border-radius: 10px;
  background: var(--gooey-fill);
  transform: translate3d(var(--gooey-x), var(--gooey-y), 0);
}
.gooey-nav__burst { position: absolute; pointer-events: none; }
.gooey-nav__pulse {
  position: absolute;
  inset: 0;
  border-radius: 100vw;
  background: var(--gooey-fill);
  transform: scale(0);
  opacity: 0;
  animation: gooey-nav-pill 300ms ease both;
}
.gooey-nav__particle, .gooey-nav__point {
  display: block;
  opacity: 0;
  width: 20px;
  height: 20px;
  border-radius: 100%;
  transform-origin: center;
}
.gooey-nav__particle {
  position: absolute;
  top: calc(50% - 8px);
  left: calc(50% - 8px);
  animation: gooey-nav-particle var(--time) ease 1 -350ms;
}
.gooey-nav__point { background: var(--color); opacity: 1; animation: gooey-nav-point var(--time) ease 1 -350ms; }
.gooey-nav__content { position: relative; z-index: 3; }
@keyframes gooey-nav-pill { to { transform: scale(1); opacity: 1; } }
@keyframes gooey-nav-particle {
  0% { transform: rotate(0deg) translate(var(--start-x), var(--start-y)); opacity: 1; animation-timing-function: cubic-bezier(.55, 0, 1, .45); }
  70% { transform: rotate(calc(var(--rotate) * .5)) translate(calc(var(--end-x) * 1.2), calc(var(--end-y) * 1.2)); opacity: 1; animation-timing-function: ease; }
  85% { transform: rotate(calc(var(--rotate) * .66)) translate(var(--end-x), var(--end-y)); opacity: 1; }
  100% { transform: rotate(calc(var(--rotate) * 1.2)) translate(calc(var(--end-x) * .5), calc(var(--end-y) * .5)); opacity: 1; }
}
@keyframes gooey-nav-point {
  0% { transform: scale(0); opacity: 0; animation-timing-function: cubic-bezier(.55, 0, 1, .45); }
  25% { transform: scale(calc(var(--scale) * .25)); }
  38% { opacity: 1; }
  65% { transform: scale(var(--scale)); opacity: 1; animation-timing-function: ease; }
  85% { transform: scale(var(--scale)); opacity: 1; }
  100% { transform: scale(0); opacity: 0; }
}
.gooey-nav--reduced .gooey-nav__pill { transition: none; }
@media (prefers-reduced-motion: reduce) {
  .gooey-nav__pill { transition: none; }
  .gooey-nav__burst { display: none; }
}
</style>
