<template>
  <div class="page-container">
    <!-- 统计卡 -->
    <div class="stat-row">
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">待结算金额</div>
        <div class="stat-value" style="color:#f59e0b">¥{{ fmtMoney(stats.pendingAmount) }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">已结算金额</div>
        <div class="stat-value" style="color:#16a34a">¥{{ fmtMoney(stats.settledAmount) }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">总笔数</div>
        <div class="stat-value">{{ stats.count }}</div>
      </el-card>
    </div>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="渠道">
          <el-select v-model="query.channelId" placeholder="全部" clearable filterable style="width: 180px">
            <el-option v-for="c in channels" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区 -->
    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openGen"><el-icon><Plus /></el-icon>生成佣金</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="channelId" label="渠道ID" width="90" align="center" />
        <el-table-column prop="contractId" label="合同ID" width="90" align="center" />
        <el-table-column label="计佣基数" width="150" align="right">
          <template #default="{ row }">¥{{ fmtMoney(row.baseAmount) }}</template>
        </el-table-column>
        <el-table-column label="比例" width="90" align="right">
          <template #default="{ row }">{{ row.rate }}%</template>
        </el-table-column>
        <el-table-column label="佣金额" width="150" align="right">
          <template #default="{ row }">¥{{ fmtMoney(row.commission) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="settleTime" label="结算时间" width="170" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 1">
              <el-popconfirm title="确认结算?" @confirm="settle(row.id)">
                <template #reference><el-button link type="success">结算</el-button></template>
              </el-popconfirm>
              <el-popconfirm title="确认作废?" @confirm="voidRow(row.id)">
                <template #reference><el-button link type="info">作废</el-button></template>
              </el-popconfirm>
            </template>
            <el-popconfirm title="确认删除?" @confirm="remove(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 生成佣金弹窗 -->
    <el-dialog v-model="genDialog.visible" title="生成佣金" width="480px">
      <el-form :model="genForm" label-width="90px" ref="genFormRef" :rules="genRules">
        <el-form-item label="合同" prop="contractId">
          <el-select v-model="genForm.contractId" placeholder="选择执行中合同" filterable style="width: 100%">
            <el-option v-for="c in contracts" :key="c.id" :label="c.code" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道" prop="channelId">
          <el-select v-model="genForm.channelId" placeholder="选择渠道" filterable style="width: 100%">
            <el-option v-for="c in channels" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitGen">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { commissionApi, channelApi } from '@/api/crm'
import { contractApi } from '@/api/contract'

const statusOptions = [
  { value: 1, label: '待结算', type: 'warning' },
  { value: 2, label: '已结算', type: 'success' },
  { value: 3, label: '已作废', type: 'info' }
]
function statusText(v) {
  const s = statusOptions.find(o => o.value === v)
  return s ? s.label : '-'
}
function statusType(v) {
  const s = statusOptions.find(o => o.value === v)
  return s ? s.type : 'info'
}
const fmtMoney = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, channelId: null, status: null })
const stats = reactive({ pendingAmount: 0, settledAmount: 0, count: 0 })
const channels = ref([])
const contracts = ref([])

async function loadStats() {
  Object.assign(stats, await commissionApi.stats())
}
async function load() {
  loading.value = true
  try {
    const res = await commissionApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, channelId: null, status: null })
  load()
}
async function remove(id) {
  await commissionApi.remove(id)
  ElMessage.success('删除成功')
  load()
  loadStats()
}
async function settle(id) {
  await commissionApi.settle(id)
  ElMessage.success('已结算')
  load()
  loadStats()
}
async function voidRow(id) {
  await commissionApi.void(id)
  ElMessage.success('已作废')
  load()
  loadStats()
}

// 生成佣金
const genFormRef = ref()
const genDialog = reactive({ visible: false })
const genForm = reactive({ contractId: null, channelId: null })
const genRules = {
  contractId: [{ required: true, message: '请选择合同', trigger: 'change' }],
  channelId: [{ required: true, message: '请选择渠道', trigger: 'change' }]
}
async function openGen() {
  Object.assign(genForm, { contractId: null, channelId: null })
  genDialog.visible = true
  // 拉取执行中合同(status=5)
  const res = await contractApi.page({ pageNo: 1, pageSize: 100, status: 5 })
  contracts.value = res.records || []
}
async function submitGen() {
  await genFormRef.value.validate()
  const res = await commissionApi.generate(genForm)
  ElMessage.success(`已生成佣金 ¥${fmtMoney(res.commission)}`)
  genDialog.visible = false
  load()
  loadStats()
}

onMounted(async () => {
  channels.value = await channelApi.list()
  load()
  loadStats()
})
</script>

<style scoped>
.stat-row { display: flex; gap: 16px; margin-bottom: 16px; }
.stat-card { flex: 1; }
.stat-label { color: var(--text-secondary); font-size: 13px; }
.stat-value { font-size: 26px; font-weight: 600; margin-top: 8px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
