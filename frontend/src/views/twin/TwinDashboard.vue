<script setup>
import { useProjectStore } from '@/stores/project'
import TwinHome from './TwinHome.vue'

// Layout initializes project context before rendering this page and remounts on switch.
// Excluded from Layout's KeepAlive so leaving the homepage releases the WebGL scene.
defineOptions({ name: 'TwinDashboard' })
const projectStore = useProjectStore()
</script>

<template>
  <TwinHome :project-name="projectStore.currentProject?.name || '未选择项目'" />
</template>
