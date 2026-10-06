<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useProjectStore } from '@/stores/project'
import { buildingApi, floorApi } from '@/api/building'
import FloorPlanViewer from './FloorPlanViewer.vue'

const projectStore = useProjectStore()
const props = defineProps({ modelValue: { type: Object, required: true }, projectId: [Number, String] })
const emit = defineEmits(['update:modelValue', 'busy'])
const buildings = ref([])
const floors = ref([])
const planId = ref(null)
const error = ref('')
const loading = ref(false)
let request = 0
const point = computed(() => props.modelValue.x != null && props.modelValue.y != null ? { x: props.modelValue.x, y: props.modelValue.y } : null)
function update(patch) { emit('update:modelValue', { ...props.modelValue, ...patch }) }
async function loadPlan(floorId, savedPlan = null) {
  const run = ++request
  loading.value = true
  emit('busy', true)
  error.value = ''
  planId.value = null
  try {
    if (floorId) {
      const plan = savedPlan ? { id: savedPlan } : await floorApi.plan(floorId)
      if (run === request) planId.value = plan?.id || null
    }
  } catch { if (run === request) error.value = '平面图加载失败，可重新选择楼层重试' }
  finally { if (run === request) { loading.value = false; emit('busy', false) } }
}
async function changeBuilding(id) {
  const run = ++request
  floors.value = []
  planId.value = null
  update({ buildingId: id || null, floorId: null, zone: null, planFileId: null, x: null, y: null })
  error.value = ''
  loading.value = true
  emit('busy', true)
  try {
    if (id) {
      const result = await floorApi.list(id)
      if (run === request) floors.value = result
    }
  } catch { if (run === request) error.value = '楼层加载失败，请重新选择楼宇' }
  finally { if (run === request) { loading.value = false; emit('busy', false) } }
}
function changeFloor(id) {
  update({ floorId: id || null, zone: null, planFileId: null, x: null, y: null })
  loadPlan(id)
}
function mark(value) {
  update({ planFileId: value ? planId.value : null, x: value?.x ?? null, y: value?.y ?? null })
}
onMounted(async () => {
  const run = ++request
  loading.value = true
  emit('busy', true)
  try {
    const result = await buildingApi.list(props.projectId ?? projectStore.currentProjectId)
    if (run !== request) return
    buildings.value = result
    if (props.modelValue.buildingId) {
      const result = await floorApi.list(props.modelValue.buildingId)
      if (run !== request) return
      floors.value = result
    }
    await loadPlan(props.modelValue.floorId, props.modelValue.planFileId)
  } catch { if (run === request) { error.value = '楼宇信息加载失败，请关闭后重试'; loading.value = false; emit('busy', false) } }
})
onBeforeUnmount(() => { request++; emit('busy', false) })
</script>

<template>
  <div class="work-order-location">
    <div class="location-selects">
      <el-select :model-value="modelValue.buildingId" placeholder="选择楼宇（可选）" clearable filterable aria-label="报修楼宇" @change="changeBuilding">
        <el-option v-for="building in buildings" :key="building.id" :value="building.id" :label="building.name" />
      </el-select>
      <el-select :model-value="modelValue.floorId" placeholder="选择楼层" clearable :disabled="!modelValue.buildingId || loading" aria-label="报修楼层" @change="changeFloor">
        <el-option v-for="floor in floors" :key="floor.id" :value="floor.id" :label="floor.name" />
      </el-select>
      <el-select :model-value="modelValue.zone" placeholder="整层 / 选择分区" clearable :disabled="!modelValue.floorId" aria-label="报修楼层分区" @change="update({ zone: $event || null })">
        <el-option v-for="zone in ['A', 'B', 'C']" :key="zone" :value="zone" :label="`${zone}区`" />
      </el-select>
    </div>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <p v-else-if="modelValue.buildingId && !loading && !floors.length" class="hint">该楼宇尚未维护楼层，请先在建筑管理中维护。</p>
    <FloorPlanViewer v-if="modelValue.floorId && !loading && !error" :file-id="planId" :point="point" editable @update:point="mark" />
  </div>
</template>

<style scoped>
.work-order-location { width: 100%; }
.location-selects { display: grid; grid-template-columns: minmax(0, 2fr) repeat(2, minmax(0, 1fr)); gap: 10px; margin-bottom: 12px; }
.hint { color: var(--text-secondary); font-size: 13px; }
@media (max-width: 600px) { .location-selects { grid-template-columns: minmax(0, 1fr); } }
</style>
