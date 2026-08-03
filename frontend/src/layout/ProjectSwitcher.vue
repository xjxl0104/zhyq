<template>
  <el-select
    v-if="store.projects.length"
    :model-value="store.currentProjectId"
    class="project-switcher"
    size="default"
    placeholder="选择项目"
    @change="onChange"
  >
    <template #prefix><el-icon><OfficeBuilding /></el-icon></template>
    <el-option
      v-for="p in store.projects"
      :key="p.id"
      :label="p.name"
      :value="p.id"
    />
  </el-select>
  <span v-else class="project-empty" @click="goCreate">暂无项目</span>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'

const store = useProjectStore()
const router = useRouter()
const emit = defineEmits(['switched'])

function onChange(id) {
  if (store.switchTo(id)) emit('switched', id)
}
function goCreate() {
  router.push('/building/project')
}
</script>

<style scoped>
.project-switcher { width: 180px; margin-right: 12px; }
.project-empty { color: var(--el-text-color-secondary); cursor: pointer; margin-right: 12px; }
</style>
