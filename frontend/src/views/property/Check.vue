<template>
  <RoutineInspectionBoard :module-type="moduleType" />
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import RoutineInspectionBoard from './components/RoutineInspectionBoard.vue'

const route = useRoute()
const moduleType = computed(() => (route.meta.ctype === '绿化' ? 'GREENING' : 'CLEANING'))
</script>
