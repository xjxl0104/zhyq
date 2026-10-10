<template>
  <nav class="section-tabs" :aria-label="label + '分页'">
    <router-link v-for="tab in tabs" :key="tab.path" :to="tab.path" class="section-tab"
      :class="{ active: tab.path === route.path }" :aria-current="tab.path === route.path ? 'page' : undefined">
      {{ tab.title }}
    </router-link>
  </nav>
</template>

<script setup>
import { useRoute } from 'vue-router'

defineProps({ tabs: { type: Array, required: true }, label: { type: String, default: '' } })
const route = useRoute()
</script>

<style scoped>
.section-tabs {
  /* 与 .page-container 的 20px 内边距对齐;负的下外边距让标签与页面首块间距收到 14px。 */
  display: flex; gap: 4px; margin: 20px 20px -6px; padding: 4px; width: fit-content; max-width: calc(100% - 40px);
  overflow-x: auto; border-radius: 10px; background: rgba(15, 23, 42, .06);
}
.section-tab {
  flex: none; padding: 7px 16px; border-radius: 7px; color: #475569; font-size: 13.5px; line-height: 20px;
  text-decoration: none; white-space: nowrap; transition: background-color .15s ease, color .15s ease;
}
.section-tab:hover { color: #0f172a; }
.section-tab.active { background: #fff; color: #0f172a; font-weight: 600; box-shadow: 0 1px 2px rgba(15, 23, 42, .12); }
.section-tab:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 1px; }
</style>
