<template>
  <div class="page-container">
    <RecordDetail
      :loading="loading"
      :title="room ? `${room.roomNo} (${room.code})` : '房源详情'"
      :subtitle="room ? `${projectName(room.projectId)} · ${buildingName(room.buildingId)}` : ''"
      :status-text="statusText(room?.status)"
      :status-type="statusType(room?.status)"
      :stats="stats"
      :stages="stages"
      :active-step="activeStep"
      :tabs="tabs"
    >
      <template #tab-info>
        <el-descriptions :column="2" border v-if="room">
          <el-descriptions-item label="计租面积">{{ room.rentArea }} ㎡</el-descriptions-item>
          <el-descriptions-item label="建筑面积">{{ room.buildArea }} ㎡</el-descriptions-item>
          <el-descriptions-item label="朝向">{{ room.orientation || '-' }}</el-descriptions-item>
          <el-descriptions-item label="装修">{{ room.decoration || '-' }}</el-descriptions-item>
          <el-descriptions-item label="用途">{{ room.usageType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="租金底价">¥{{ room.basePrice }}/㎡</el-descriptions-item>
          <el-descriptions-item label="物业费">¥{{ room.propertyFee }}/㎡</el-descriptions-item>
          <el-descriptions-item label="备注">{{ room.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </RecordDetail>
  </div>
</template>

<script setup>
// 房源详情页。#18 规格要求"房源详情 → 关联合同"一个 Tab，但走查后端合同接口
// (contractApi.page) 仅支持按 tenantRefId/projectId/status/contractType 过滤，
// 没有按 roomId 过滤的入口；合同-房源关系表(biz_contract_room)也没有暴露对应
// Controller 方法。为避免臆造不存在的 API，这里先不加"关联合同"Tab，只保留
// 房源基础信息 Tab，并在报告中记录这个缺口。
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { roomApi, projectApi, buildingApi } from '@/api/building'
import RecordDetail from '@/components/RecordDetail.vue'

const route = useRoute()

const loading = ref(false)
const room = ref(null)
const projects = ref([])
const buildings = ref([])
const projectName = (id) => projects.value.find((p) => p.id === id)?.name ?? (id ?? '-')
const buildingName = (id) => buildings.value.find((b) => b.id === id)?.name ?? (id ?? '-')

// 状态映射(与 RoomControl.vue 保持一致)
const allStatus = [
  { v: 0, label: '未配置', type: 'info' }, { v: 1, label: '可租', type: 'success' },
  { v: 2, label: '锁定', type: 'warning' }, { v: 3, label: '意向占用', type: 'warning' },
  { v: 4, label: '签约中', type: 'warning' }, { v: 5, label: '在租', type: 'primary' },
  { v: 6, label: '退租处理中', type: 'danger' }, { v: 7, label: '维修', type: 'danger' },
  { v: 8, label: '停用', type: 'danger' }
]
const statusMap = Object.fromEntries(allStatus.map((s) => [s.v, s]))
const statusText = (v) => statusMap[v]?.label || '-'
const statusType = (v) => statusMap[v]?.type || 'info'

// 房态阶段条：可租 → 意向/签约 → 在租 → 退租/维修/停用
const stages = ['可租', '意向/签约', '在租', '退租/维修/停用']
const activeStep = computed(() => {
  const s = room.value?.status
  if (s === 1 || s === 0) return 0
  if (s === 3 || s === 4) return 1
  if (s === 5) return 2
  if ([2, 6, 7, 8].includes(s)) return 3
  return 0
})

const stats = computed(() => {
  if (!room.value) return []
  return [
    { label: '计租面积', value: `${room.value.rentArea} ㎡` },
    { label: '建筑面积', value: `${room.value.buildArea} ㎡` },
    { label: '租金底价', value: `¥${room.value.basePrice}/㎡` },
    { label: '物业费', value: `¥${room.value.propertyFee}/㎡` }
  ]
})

const tabs = [{ name: 'info', label: '基础信息' }]

async function loadRefs() {
  try {
    const p = await projectApi.list()
    projects.value = p || []
    if (room.value?.projectId) {
      buildings.value = (await buildingApi.list(room.value.projectId)) || []
    }
  } catch (e) { /* 下拉数据失败不阻塞详情 */ }
}

async function loadDetail() {
  loading.value = true
  try {
    const id = route.params.id
    room.value = await roomApi.get(id)
    await loadRefs()
  } finally {
    loading.value = false
  }
}

onMounted(() => { loadDetail() })
</script>
