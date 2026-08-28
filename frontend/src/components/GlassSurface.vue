<script setup>
defineOptions({ name: 'GlassSurface' })

defineProps({
  variant: {
    type: String,
    default: 'card',
    validator: (value) => ['upload', 'card'].includes(value),
  },
})
</script>

<template>
  <div class="glass-surface" :class="`glass-surface--${variant}`">
    <span class="glass-surface__glow" aria-hidden="true" />
    <span class="glass-surface__veil" aria-hidden="true" />
    <div class="glass-surface__content">
      <slot />
    </div>
  </div>
</template>

<style scoped>
/* Visual direction adapted from the Uiverse.io card by monkey_8812. */
.glass-surface {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  border: 1px solid rgb(255 255 255 / 82%);
  background: rgb(255 255 255 / 62%);
  backdrop-filter: blur(18px) saturate(128%);
  -webkit-backdrop-filter: blur(18px) saturate(128%);
  contain: paint;
}

.glass-surface--upload {
  border-radius: 14px;
  padding: 18px;
}

.glass-surface--card {
  border-radius: 14px;
}

.glass-surface__glow,
.glass-surface__veil {
  position: absolute;
  pointer-events: none;
}

.glass-surface__glow {
  inset: -58%;
  background: conic-gradient(
    from 24deg,
    rgb(124 58 237 / 17%),
    rgb(251 146 60 / 12%),
    rgb(99 102 241 / 16%),
    rgb(124 58 237 / 17%)
  );
  transform: translateZ(0);
}

.glass-surface--upload .glass-surface__glow {
  animation: glass-surface-orbit 18s linear infinite;
  will-change: transform;
}

.glass-surface--card .glass-surface__glow {
  opacity: .58;
  transform: rotate(-14deg);
}

.glass-surface__veil {
  inset: 0;
  background:
    linear-gradient(145deg, rgb(255 255 255 / 42%), transparent 48%),
    linear-gradient(0deg, rgb(255 255 255 / 28%), rgb(255 255 255 / 8%));
}

.glass-surface__content {
  position: relative;
  z-index: 1;
  min-width: 0;
  height: 100%;
}

@keyframes glass-surface-orbit {
  to { transform: rotate(1turn); }
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .glass-surface { background: #f8f9ff; }
  .glass-surface__glow { opacity: .34; }
}

@media (prefers-reduced-motion: reduce) {
  .glass-surface__glow {
    animation: none !important;
    will-change: auto;
  }
}
</style>
