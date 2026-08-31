<template>
  <span class="gradient-brand">智慧云仓系统</span>
</template>

<script setup>
// React Bits GradientText 的 Vue 落地:流动渐变文字品牌标。
// 纯 CSS 动画,reduced-motion 下静止为首帧渐变。
defineOptions({ name: 'GradientBrand' })
</script>

<style scoped>
.gradient-brand {
  display: inline-block;
  font-size: 21px;
  font-weight: 800;
  letter-spacing: 2px;
  line-height: 1;
  white-space: nowrap;
  user-select: none;
  background: linear-gradient(90deg, #7cf7d4, #7ca9ff, #c9a8ff, #7ca9ff, #7cf7d4);
  background-size: 300% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
  animation: gradient-brand-flow 8s linear infinite;
}
@keyframes gradient-brand-flow {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}
@media (prefers-reduced-motion: reduce) {
  .gradient-brand { animation: none; }
}
</style>
