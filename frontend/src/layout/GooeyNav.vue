<template>
  <div
    ref="root"
    class="gooey-nav"
    :class="{
      'gooey-nav--ready': ready,
      'gooey-nav--reduced': reducedMotion,
    }"
    :style="indicatorStyle"
  >
    <div class="gooey-nav__effect" aria-hidden="true">
      <svg class="gooey-nav__filter" focusable="false">
        <defs>
          <filter id="gooey-nav-filter" x="-30%" y="-30%" width="160%" height="160%">
            <feGaussianBlur in="SourceGraphic" stdDeviation="5" result="blur" />
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
      <span
        v-for="particle in particles"
        :key="particle.id"
        class="gooey-nav__particle"
        :style="particle.style"
      />
    </div>
    <div class="gooey-nav__content">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  activePath: { type: String, required: true },
})

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
  const colors = ['#a78bfa', '#fb923c', '#818cf8', '#c4b5fd']
  particles.value = Array.from({ length: 8 }, (_, index) => {
    const angle = (Math.PI * 2 * index) / 8
    const distance = 18 + (index % 3) * 7
    return {
      id: `${++particleSequence}-${index}`,
      style: {
        '--particle-origin-x': `${x + width / 2}px`,
        '--particle-origin-y': `${y + height / 2}px`,
        '--particle-x': `${Math.cos(angle) * distance}px`,
        '--particle-y': `${Math.sin(angle) * distance}px`,
        '--particle-size': `${5 + (index % 3) * 1.5}px`,
        '--particle-color': colors[index % colors.length],
        '--particle-delay': `${(index % 4) * 18}ms`,
      },
    }
  })

  particleTimer = window.setTimeout(() => {
    particles.value = []
    particleTimer = null
  }, 860)
}

function measureActive({ burst = false } = {}) {
  const frame = root.value
  const activeItem = frame?.querySelector('.el-menu-item.is-active')
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
  clearFrames()
  await nextTick()
  requestFrame(() => requestFrame(() => measureActive(options)))
}

function handleReflow() {
  scheduleMeasure()
}

function handleMotionPreference(event) {
  reducedMotion.value = event.matches
  if (event.matches) clearParticles()
}

watch(
  () => props.activePath,
  (nextPath, previousPath) => {
    scheduleMeasure({ burst: Boolean(previousPath && nextPath !== previousPath) })
  },
  { flush: 'post' },
)

onMounted(() => {
  mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = mediaQuery.matches
  mediaQuery.addEventListener?.('change', handleMotionPreference)
  mediaQuery.addListener?.(handleMotionPreference)

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
  clearFrames()
  clearParticles()
  resizeObserver?.disconnect()
  root.value?.removeEventListener('scroll', handleReflow, true)
  root.value?.removeEventListener('transitionend', handleReflow)
  window.removeEventListener('resize', handleReflow)
  mediaQuery?.removeEventListener?.('change', handleMotionPreference)
  mediaQuery?.removeListener?.(handleMotionPreference)
})
</script>

<style scoped>
.gooey-nav {
  position: relative;
  min-height: 100%;
}

.gooey-nav__effect {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
  filter: url("#gooey-nav-filter");
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
  background: linear-gradient(135deg, #4f46e5 0%, #6d5ce8 58%, #8b5cf6 100%);
  box-shadow: 0 8px 18px rgb(79 70 229 / 20%);
  transform: translate3d(var(--gooey-x), var(--gooey-y), 0);
  transform-origin: center;
  transition:
    transform 360ms cubic-bezier(.16, 1, .3, 1),
    width 220ms cubic-bezier(.16, 1, .3, 1),
    height 220ms cubic-bezier(.16, 1, .3, 1);
}

.gooey-nav__particle {
  position: absolute;
  top: var(--particle-origin-y);
  left: var(--particle-origin-x);
  width: var(--particle-size);
  height: var(--particle-size);
  border-radius: 50%;
  background: var(--particle-color);
  animation: gooey-nav-particle 760ms cubic-bezier(.16, 1, .3, 1) var(--particle-delay) both;
  will-change: transform, opacity;
}

.gooey-nav__content {
  position: relative;
  z-index: 1;
}

@keyframes gooey-nav-particle {
  0% { opacity: 0; transform: translate3d(0, 0, 0) scale(.35); }
  18% { opacity: .9; }
  100% { opacity: 0; transform: translate3d(var(--particle-x), var(--particle-y), 0) scale(1); }
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
