<script setup>
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { floorApi } from '@/api/building'
import { hasPermission } from '@/utils/permission'
import FloorPlanViewer from './FloorPlanViewer.vue'

const props = defineProps({ building: { type: Object, required: true } })
const floors = ref([])
const selectedId = ref(null)
const currentPlan = ref(null)
const loading = ref(false)
const uploading = ref(false)
const error = ref('')
const input = ref()
let generation = 0
const addVisible = ref(false)
const adding = ref(false)
const newFloor = reactive({ name: '', floorNo: 1 })
async function addFloor() {
  if (!newFloor.name.trim()) { ElMessage.warning('请输入楼层名称'); return }
  if (floors.value.some(f => f.floorNo === newFloor.floorNo)) { ElMessage.warning('楼层编号已存在'); return }
  adding.value = true
  try {
    const id = await floorApi.add({ buildingId: props.building.id, projectId: props.building.projectId,
      name: newFloor.name.trim(), floorNo: newFloor.floorNo, sort: newFloor.floorNo })
    floors.value = await floorApi.list(props.building.id)
    addVisible.value = false
    await select(id)
  } finally { adding.value = false }
}
const canUpload = hasPermission('building:floorPlan:edit') || hasPermission('ROLE_admin')
const selected = computed(() => floors.value.find(f => f.id === selectedId.value))
async function select(id) {
  const run = ++generation
  selectedId.value = id
  currentPlan.value = null
  error.value = ''
  loading.value = true
  try {
    const plan = await floorApi.plan(id)
    if (run === generation) currentPlan.value = plan
  } catch { if (run === generation) error.value = '平面图加载失败，请重试' }
  finally { if (run === generation) loading.value = false }
}
async function load() {
  error.value = ''
  loading.value = true
  try {
    floors.value = await floorApi.list(props.building.id)
    if (floors.value.length) await select(floors.value[0].id)
  } catch { error.value = '楼层加载失败，请重试' }
  finally { loading.value = false }
}
async function ensureFloors() {
  loading.value = true
  try {
    floors.value = await floorApi.ensure(props.building.id)
    if (floors.value.length) await select(floors.value[0].id)
  } finally { loading.value = false }
}
async function upload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || !selectedId.value) return
  if (!/\.(png|jpe?g)$/i.test(file.name) || file.size > 20 * 1024 * 1024) {
    ElMessage.error('请选择 20MB 以内的 JPG 或 PNG 图片')
    return
  }
  const floorId = selectedId.value
  const data = new FormData()
  data.append('file', file)
  uploading.value = true
  try {
    const result = await floorApi.uploadPlan(floorId, data)
    if (selectedId.value === floorId) { currentPlan.value = result; error.value = '' }
    ElMessage.success('平面图已保存')
  } finally { uploading.value = false }
}
onMounted(load)
onBeforeUnmount(() => { generation++ })
</script>

<template>
  <div v-loading="loading">
    <el-dialog v-model="addVisible" title="新增楼层" width="min(420px, 92vw)" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="楼层名称"><el-input v-model="newFloor.name" maxlength="100" placeholder="如 8 层、地下 1 层" /></el-form-item>
        <el-form-item label="楼层编号"><el-input-number v-model="newFloor.floorNo" :min="-20" :max="300" :precision="0" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="addVisible = false">取消</el-button><el-button type="primary" :loading="adding" @click="addFloor">保存</el-button></template>
    </el-dialog>
    <div class="floor-plan-controls">
      <el-select :model-value="selectedId" placeholder="选择楼层" :disabled="uploading" aria-label="平面图楼层" @change="select">
        <el-option v-for="floor in floors" :key="floor.id" :label="floor.name" :value="floor.id" />
      </el-select>
      <el-button v-if="canUpload && selected" type="primary" :loading="uploading" :disabled="loading" @click="input?.click()">{{ currentPlan ? '更换此层平面图' : '上传此层平面图' }}</el-button>
      <el-button v-if="canUpload && floors.length" :disabled="uploading" @click="addVisible = true">新增楼层</el-button>
      <el-button v-if="error" @click="load">重试</el-button>
      <input ref="input" type="file" accept="image/png,image/jpeg,.png,.jpg,.jpeg" hidden @change="upload" />
    </div>
    <p class="plan-note">每层独立保存一张当前平面图。支持 JPG / PNG，单张不超过 20MB；PDF 或 CAD 请先导出为图片。更换图纸后，历史工单继续显示报修时的图纸。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-else-if="!floors.length && !loading">
      <el-empty description="该楼宇尚未建立楼层" :image-size="80" />
      <el-button v-if="canUpload" type="primary" @click="ensureFloors">按楼宇层数建立楼层</el-button>
    </template>
    <template v-else-if="selected">
      <p>{{ selected.name }}<span v-if="currentPlan"> · {{ currentPlan.originalName }}</span></p>
      <FloorPlanViewer :file-id="currentPlan?.id" />
    </template>
  </div>
</template>

<style scoped>
.floor-plan-controls { display: flex; gap: 12px; flex-wrap: wrap; }
.plan-note { color: var(--text-secondary); font-size: 13px; line-height: 1.8; }
</style>
