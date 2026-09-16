<script setup>
import TwinHome from './TwinHome.vue'

// Layout initializes project context before rendering this page and remounts on switch.
// Excluded from Layout's KeepAlive so leaving the homepage releases the WebGL scene.
defineOptions({ name: 'TwinDashboard' })
</script>

<template>
  <TwinHome />
</template>
