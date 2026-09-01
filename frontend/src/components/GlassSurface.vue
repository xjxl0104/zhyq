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

/* 简约化(2026-09-01):彩色锥形光晕(紫/橙/靛,上传变体旋转)视觉过闹,整体停用 */
.glass-surface__glow { display: none; }

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
