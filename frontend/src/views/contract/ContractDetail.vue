<template>
  <div class="page-container">
    <RecordDetail
      :loading="loading"
      :title="contract?.code || '合同详情'"
      :subtitle="contract ? `${tenantName(contract.tenantRefId)} · ${projectName(contract.projectId)}` : ''"
      :status-text="statusText(contract?.status)"
      :status-type="statusTagType(contract?.status)"
      :stats="stats"
      :stages="stages"
      :active-step="activeStep"
      :tabs="tabs"
    >
      <template #tab-rooms>
        <el-table :data="rooms" border size="small">
          <el-table-column prop="roomId" label="房源ID" />
          <el-table-column prop="rentArea" label="租赁面积" />
          <el-table-column prop="rentPrice" label="租赁单价" />
        </el-table>
      </template>
      <template #tab-bills>
        <el-table :data="bills" border size="small" v-loading="billsLoading">
          <el-table-column prop="code" label="账单编号" min-width="140" />
          <el-table-column prop="feeType" label="费用类型" width="110" />
          <el-table-column label="金额" width="110">
            <template #default="{ row }">{{ row.amount }} 元</template>
          </el-table-column>
          <el-table-column prop="dueDate" label="应收日期" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ billStatusText(row.status) }}</template>
          </el-table-column>
        </el-table>
      </template>
      <template #tab-receivables>
        <el-table :data="receivables" border size="small" v-loading="receivablesLoading">
          <el-table-column prop="internalCode" label="内部编号" min-width="150" />
          <el-table-column prop="agreementNoRaw" label="协议编号" min-width="150" />
          <el-table-column prop="tenantNameRaw" label="租户" min-width="150" />
          <el-table-column prop="spaceNameRaw" label="空间" min-width="130" />
          <el-table-column prop="monthlyRent" label="月租金" width="120" />
          <el-table-column prop="monthlyProperty" label="月物业费" width="120" />
          <el-table-column prop="monthlyTotal" label="月合计" width="120" />
          <el-table-column prop="status" label="状态" width="100" />
        </el-table>
      </template>
    </RecordDetail>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { contractApi } from '@/api/contract'
import { tenantApi } from '@/api/tenant'
import { projectApi } from '@/api/building'
import { billApi } from '@/api/finance'
import { receivableApi } from '@/api/receivable'
import RecordDetail from '@/components/RecordDetail.vue'

const route = useRoute()

const loading = ref(false)
const contract = ref(null)
const rooms = ref([])
const bills = ref([])
const billsLoading = ref(false)
const receivables = ref([])
const receivablesLoading = ref(false)

const tenants = ref([])
const projects = ref([])
const tenantName = (id) => tenants.value.find((t) => t.id === id)?.name ?? (id ?? '-')
const projectName = (id) => projects.value.find((p) => p.id === id)?.name ?? (id ?? '-')

const statusOptions = [
  { value: 1, label: '草稿' },
  { value: 2, label: '待审核' },
  { value: 3, label: '待签署' },
  { value: 4, label: '待执行' },
  { value: 5, label: '执行中' },
  { value: 6, label: '变更中' },
  { value: 7, label: '退租中' },
  { value: 8, label: '已到期' },
  { value: 9, label: '已终止' },
  { value: 10, label: '已归档' }
]
const statusMap = statusOptions.reduce((m, s) => ((m[s.value] = s.label), m), {})
function statusText(v) { return statusMap[v] || '-' }
function statusTagType(v) {
  if ([2, 3, 4, 7].includes(v)) return 'warning'
  if ([5, 6].includes(v)) return 'primary'
  if (v === 8) return 'success'
  if (v === 9) return 'danger'
  if (v === 10) return 'info'
  return 'info'
}

const billStatusMap = { 1: '待收', 2: '已收', 3: '部分收', 4: '已作废' }
function billStatusText(v) { return billStatusMap[v] || '-' }

// 阶段条：草稿 → 审批 → 生效 → 到期/退租
const stages = ['草稿', '审批', '生效', '到期/退租']
const activeStep = computed(() => {
  const s = contract.value?.status
  if (s === 1) return 0
  if ([2, 3, 4].includes(s)) return 1
  if ([5, 6].includes(s)) return 2
  if ([7, 8, 9, 10].includes(s)) return 3
  return 0
})

const stats = computed(() => {
  if (!contract.value) return []
  return [
    { label: '起止日期', value: `${contract.value.startDate} ~ ${contract.value.endDate}` },
    { label: '租赁单价', value: `${contract.value.rentPrice} 元/㎡` },
    { label: '租赁面积', value: `${contract.value.rentArea} ㎡` },
    { label: '保证金', value: `${contract.value.deposit} 元` }
  ]
})

const tabs = [
  { name: 'rooms', label: '关联房源' },
  { name: 'receivables', label: '应收与计费' },
  { name: 'bills', label: '关联账单' }
]

async function loadRefs() {
  try {
    const [t, p] = await Promise.all([tenantApi.list(), projectApi.list()])
    tenants.value = t || []
    projects.value = p || []
  } catch (e) { /* 下拉数据失败不阻塞详情 */ }
}

async function loadDetail() {
  loading.value = true
  try {
    const id = route.params.id
    const res = await contractApi.get(id)
    contract.value = res.contract
    rooms.value = res.rooms || []
    loadBills(id)
    loadReceivables(id)
  } finally {
    loading.value = false
  }
}

async function loadReceivables(contractId) {
  receivablesLoading.value = true
  try {
    const res = await receivableApi.page({ pageNo: 1, pageSize: 100, contractId })
    receivables.value = res.records || []
  } catch (e) {
    receivables.value = []
  } finally {
    receivablesLoading.value = false
  }
}

async function loadBills(contractId) {
  billsLoading.value = true
  try {
    const res = await billApi.page({ pageNo: 1, pageSize: 50, contractId })
    bills.value = res.records || []
  } catch (e) {
    bills.value = []
  } finally {
    billsLoading.value = false
  }
}

onMounted(() => {
  loadRefs()
  loadDetail()
})
</script>
