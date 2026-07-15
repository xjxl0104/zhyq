<template>
  <div class="page-container">
    <RecordDetail
      :loading="loading"
      :title="tenant?.name || '租客详情'"
      :subtitle="tenant ? `${tenant.code || ''} · ${projectName(tenant.projectId)}` : ''"
      :status-text="tenant ? (tenant.status === 1 ? '正常' : '已归档') : ''"
      :status-type="tenant?.status === 1 ? 'success' : 'info'"
      :stats="stats"
      :stages="stages"
      :active-step="activeStep"
      :tabs="tabs"
    >
      <template #tab-contracts>
        <el-table :data="contracts" border size="small" v-loading="contractsLoading">
          <el-table-column prop="code" label="合同编号" min-width="150" />
          <el-table-column label="起止日期" min-width="200">
            <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
          </el-table-column>
          <el-table-column label="租赁面积" width="110">
            <template #default="{ row }">{{ row.rentArea }} ㎡</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ statusText(row.status) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </RecordDetail>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { tenantApi } from '@/api/tenant'
import { contractApi } from '@/api/contract'
import { projectApi } from '@/api/building'
import RecordDetail from '@/components/RecordDetail.vue'

const route = useRoute()

const loading = ref(false)
const tenant = ref(null)
const contracts = ref([])
const contractsLoading = ref(false)
const projects = ref([])
const projectName = (id) => projects.value.find((p) => p.id === id)?.name ?? (id ?? '-')

const contractStatusMap = {
  1: '草稿', 2: '待审核', 3: '待签署', 4: '待执行', 5: '执行中',
  6: '变更中', 7: '退租中', 8: '已到期', 9: '已终止', 10: '已归档'
}
function statusText(v) { return contractStatusMap[v] || '-' }

// 租客简单阶段条：正常 → 已归档
const stages = ['正常', '已归档']
const activeStep = computed(() => (tenant.value?.status === 2 ? 1 : 0))

const stats = computed(() => {
  if (!tenant.value) return []
  return [
    { label: '联系人', value: tenant.value.contact || '-' },
    { label: '电话', value: tenant.value.phone || '-' },
    { label: '行业', value: tenant.value.industry || '-' },
    { label: '类型', value: tenant.value.tenantType === 1 ? '企业' : '个人' }
  ]
})

const tabs = [{ name: 'contracts', label: '关联合同' }]

async function loadRefs() {
  try {
    projects.value = (await projectApi.list()) || []
  } catch (e) { /* 下拉数据失败不阻塞详情 */ }
}

async function loadDetail() {
  loading.value = true
  try {
    const id = route.params.id
    tenant.value = await tenantApi.get(id)
    loadContracts(id)
  } finally {
    loading.value = false
  }
}

async function loadContracts(tenantRefId) {
  contractsLoading.value = true
  try {
    const res = await contractApi.page({ pageNo: 1, pageSize: 50, tenantRefId })
    contracts.value = res.records || []
  } catch (e) {
    contracts.value = []
  } finally {
    contractsLoading.value = false
  }
}

onMounted(() => {
  loadRefs()
  loadDetail()
})
</script>
