<template>
  <div
    ref="root"
    class="gooey-nav"
    :class="{
      'gooey-nav--ready': ready,
      'gooey-nav--reduced': reducedMotion,
    }"
    :style="indicatorStyle"
    @pointerover="handlePointerOver"
    @pointerleave="handlePointerLeave"
    @focusin="handleFocusIn"
    @focusout="handleFocusOut"
    @click.capture="handleClick"
  >
    <div class="gooey-nav__effect" aria-hidden="true">
      <svg class="gooey-nav__filter" focusable="false">
        <defs>
          <filter :id="filterId" x="-30%" y="-30%" width="160%" height="160%">
            <feGaussianBlur in="SourceGraphic" stdDeviation="2" result="blur" />
            <feColorMatrix
              in="blur"
              mode="matrix"
              values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 20 -9"
              result="goo"
            />
            <feComposite in="SourceGraphic" in2="goo" operator="atop" />
          </filter>
        </defs>
      </svg>
      <span class="gooey-nav__pill" />
      <div class="gooey-nav__particles" :style="{ filter: `url(#${filterId})` }">
        <span
          v-for="particle in particles"
          :key="particle.id"
          class="gooey-nav__particle"
          :style="particle.style"
        />
      </div>
    </div>
    <div class="gooey-nav__content">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'

const props = defineProps({
  activePath: { type: String, required: true },
  collapsed: { type: Boolean, default: false },
})

const filterId = `gooey-${useId()}`
const root = ref(null)
const ready = ref(false)
const reducedMotion = ref(false)
const particles = ref([])
const geometry = ref({ x: 0, y: 0, width: 0, height: 0 })

let hasMeasured = false
let observedItem = null
let resizeObserver = null
let mediaQuery = null
let particleTimer = null
let particleSequence = 0
let pendingBurst = false
let hoveredItem = null
let focusedItem = null
let disposed = false
const animationFrames = new Set()

const indicatorStyle = computed(() => ({
  '--gooey-x': `${geometry.value.x}px`,
  '--gooey-y': `${geometry.value.y}px`,
  '--gooey-width': `${geometry.value.width}px`,
  '--gooey-height': `${geometry.value.height}px`,
}))

function requestFrame(callback) {
  const id = requestAnimationFrame(() => {
    animationFrames.delete(id)
    callback()
  })
  animationFrames.add(id)
  return id
}

function clearFrames() {
  animationFrames.forEach((id) => cancelAnimationFrame(id))
  animationFrames.clear()
}

function clearParticles() {
  if (particleTimer !== null) {
    clearTimeout(particleTimer)
    particleTimer = null
  }
  particles.value = []
}

function burstParticles() {
  if (reducedMotion.value) return

  clearParticles()
  const { x, y, width, height } = geometry.value
  const colors = ['#8b5cf6', '#f59e0b', '#6366f1', '#c084fc']
  const originX = x + width * (props.collapsed ? 0.5 : 0.76)
  particles.value = Array.from({ length: 12 }, (_, index) => {
    const angle = (Math.PI * 2 * index) / 12
    const distance = 25 + (index % 3) * 8
    return {
      id: `${++particleSequence}-${index}`,
      style: {
        '--particle-origin-x': `${originX}px`,
        '--particle-origin-y': `${y + height / 2}px`,
        '--particle-x': `${Math.cos(angle) * Math.min(distance, width * 0.18)}px`,
        '--particle-y': `${Math.sin(angle) * distance * 0.7}px`,
        '--particle-size': `${7 + (index % 3) * 2}px`,
        '--particle-color': colors[index % colors.length],
        '--particle-delay': `${(index % 4) * 18}ms`,
      },
    }
  })

  particleTimer = window.setTimeout(() => {
    particles.value = []
    particleTimer = null
  }, 1050)
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
  scheduleMeasure()
}

function handlePointerLeave() {
  hoveredItem = null
  scheduleMeasure()
}

function handleFocusIn(event) {
  focusedItem = menuItemFrom(event.target)
  scheduleMeasure()
}

function handleFocusOut(event) {
  focusedItem = menuItemFrom(event.relatedTarget)
  scheduleMeasure()
}

function handleClick(event) {
  const item = menuItemFrom(event.target)
  if (!item?.classList.contains('el-menu-item')) return
  hoveredItem = item
  scheduleMeasure({ burst: true })
}

function measureActive({ burst = false } = {}) {
  const frame = root.value
  const preview = hoveredItem || focusedItem
  const activeItem = (frame?.contains(preview) ? preview : null) || frame?.querySelector('.el-menu-item.is-active')
  if (!frame || !activeItem) {
    ready.value = false
    return
  }

  const frameRect = frame.getBoundingClientRect()
  const itemRect = activeItem.getBoundingClientRect()
  if (!itemRect.width || !itemRect.height) {
    ready.value = false
    return
  }

  geometry.value = {
    x: itemRect.left - frameRect.left,
    y: itemRect.top - frameRect.top,
    width: itemRect.width,
    height: itemRect.height,
  }
  ready.value = true

  if (resizeObserver && observedItem !== activeItem) {
    if (observedItem) resizeObserver.unobserve(observedItem)
    resizeObserver.observe(activeItem)
    observedItem = activeItem
  }

  if (burst && hasMeasured) burstParticles()
  hasMeasured = true
}

async function scheduleMeasure(options = {}) {
  pendingBurst = pendingBurst || Boolean(options.burst)
  clearFrames()
  await nextTick()
  if (disposed) return
  requestFrame(() => requestFrame(() => {
    const burst = pendingBurst
    pendingBurst = false
    measureActive({ burst })
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

watch(
  () => props.activePath,
  (nextPath, previousPath) => {
    hoveredItem = null
    focusedItem = null
    scheduleMeasure({ burst: Boolean(previousPath && nextPath !== previousPath) })
  },
  { flush: 'post' },
)
watch(() => props.collapsed, () => scheduleMeasure())

onMounted(() => {
  mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = mediaQuery.matches
  if (mediaQuery.addEventListener) mediaQuery.addEventListener('change', handleMotionPreference)
  else mediaQuery.addListener?.(handleMotionPreference)

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
  resizeObserver?.disconnect()
  root.value?.removeEventListener('scroll', handleReflow, true)
  root.value?.removeEventListener('transitionend', handleReflow)
  window.removeEventListener('resize', handleReflow)
  if (mediaQuery?.removeEventListener) mediaQuery.removeEventListener('change', handleMotionPreference)
  else mediaQuery?.removeListener?.(handleMotionPreference)
})
</script>

<style scoped>
.gooey-nav {
  position: relative;
  min-height: 100%;
  isolation: isolate;
}

.gooey-nav__effect {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
  transition: opacity 120ms ease;
}

.gooey-nav--ready .gooey-nav__effect {
  opacity: 1;
}

.gooey-nav__filter {
  position: absolute;
  width: 0;
  height: 0;
}

.gooey-nav__pill {
  position: absolute;
  top: 0;
  left: 0;
  width: var(--gooey-width);
  height: var(--gooey-height);
  border-radius: 13px;
  box-sizing: border-box;
  border: 1px solid rgb(129 140 248 / 48%);
  background:
    radial-gradient(ellipse at 90% 10%, rgb(192 132 252 / 48%), transparent 70%),
    linear-gradient(125deg, rgb(238 242 255 / 90%), rgb(199 210 254 / 84%) 60%, rgb(221 214 254 / 92%));
  backdrop-filter: blur(18px) saturate(160%);
  -webkit-backdrop-filter: blur(18px) saturate(160%);
  box-shadow: 0 6px 16px rgb(79 70 229 / 16%), inset 0 1px 0 rgb(255 255 255 / 95%);
  transform: translate3d(var(--gooey-x), var(--gooey-y), 0);
  transform-origin: center;
  transition: transform 300ms cubic-bezier(.16, 1, .3, 1);
}

.gooey-nav__pill::after {
  content: '';
  position: absolute;
  inset: 2px;
  border-radius: 10px;
  background: linear-gradient(170deg, rgb(255 255 255 / 56%), transparent 48%);
}

.gooey-nav__particles {
  position: absolute;
  inset: 0;
}

.gooey-nav__particle {
  position: absolute;
  top: var(--particle-origin-y);
  left: var(--particle-origin-x);
  width: var(--particle-size);
  height: var(--particle-size);
  border-radius: 50%;
  background: var(--particle-color);
  margin: calc(var(--particle-size) / -2);
  animation: gooey-nav-particle 940ms cubic-bezier(.16, 1, .3, 1) var(--particle-delay) both;
  will-change: transform, opacity;
}

.gooey-nav__content {
  position: relative;
  z-index: 1;
}

@keyframes gooey-nav-particle {
  0% { opacity: 0; transform: translate3d(0, 0, 0) scale(.35); }
  20% { opacity: 1; }
  65% { opacity: .95; transform: translate3d(var(--particle-x), var(--particle-y), 0) scale(1); }
  100% { opacity: 0; transform: translate3d(calc(var(--particle-x) * .55), calc(var(--particle-y) * .55), 0) scale(.3); }
}

.gooey-nav--reduced .gooey-nav__pill {
  transition: none;
}

@supports not (filter: url("#gooey-nav-filter")) {
  .gooey-nav__effect { filter: none; }
  .gooey-nav__particle { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  .gooey-nav__pill { transition: none; }
  .gooey-nav__particle { display: none; animation: none; }
}
</style>
