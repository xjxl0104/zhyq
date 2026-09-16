<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="账期">
          <el-date-picker v-model="query.period" type="month" placeholder="全部"
                          format="YYYY年M月" value-format="YYYY-MM" clearable style="width: 170px" />
        </el-form-item>
        <el-form-item label="能源类型">
          <el-select v-model="query.energyType" placeholder="全部" clearable style="width: 130px">
            <el-option label="电" value="电" /><el-option label="水" value="水" />
          </el-select>
        </el-form-item>
        <el-form-item label="结算状态">
          <el-select v-model="query.settlementStatus" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="item in settlementOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-alert class="ledger-note" type="info" :closable="false" show-icon
              title="结算数据直接取自已确认出账的租户能源账单及财务实收；请在收银台办理收款，本页会自动同步，不需要重复登记。" />

    <div class="summary-grid" v-loading="summaryLoading">
      <div class="summary-item"><span>电费最近全额结算账期</span><strong>{{ summary.latestElectricSettledPeriod || '暂无' }}</strong></div>
      <div class="summary-item"><span>水费最近全额结算账期</span><strong>{{ summary.latestWaterSettledPeriod || '暂无' }}</strong></div>
      <div class="summary-item"><span>当前范围租户应收</span><strong>¥{{ money(summary.receivableAmount) }}</strong></div>
      <div class="summary-item success"><span>当前范围已收</span><strong>¥{{ money(summary.receivedAmount) }}</strong></div>
      <div class="summary-item warning"><span>当前范围待收</span><strong>¥{{ money(summary.outstandingAmount) }}</strong><small>{{ summary.pendingSettlementCount || 0 }} 个账期待跟进</small></div>
    </div>

    <div class="table-card">
      <div class="toolbar-tip">每行对应一个园区、一个账期的一种能源；“最近全额结算账期”仅表示最近已结清月份，具体断档请以列表状态为准。</div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="period" label="账期" width="110" />
        <el-table-column label="能源类型" width="100">
          <template #default="{ row }"><el-tag :type="row.energyType === '电' ? 'warning' : 'primary'">{{ row.energyType }}</el-tag></template>
        </el-table-column>
        <el-table-column label="对外发票含税额" min-width="145" align="right">
          <template #default="{ row }">¥{{ money(row.settlementInvoiceAmount) }}</template>
        </el-table-column>
        <el-table-column label="租户应收" min-width="135" align="right">
          <template #default="{ row }">¥{{ money(row.settlementReceivableAmount) }}</template>
        </el-table-column>
        <el-table-column label="财务实收" min-width="135" align="right">
          <template #default="{ row }"><span class="received">¥{{ money(row.settlementReceivedAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="待收金额" min-width="135" align="right">
          <template #default="{ row }"><span :class="{ outstanding: Number(row.settlementOutstandingAmount || 0) > 0 }">¥{{ money(row.settlementOutstandingAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="已结清账单" width="135" align="center">
          <template #default="{ row }">{{ row.settlementSettledBillCount || 0 }} / {{ row.settlementBillCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="财务结算状态" min-width="135">
          <template #default="{ row }"><el-tag :type="statusMeta(row.settlementStatus).type">{{ statusMeta(row.settlementStatus).label }}</el-tag></template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo" v-model:page-size="query.pageSize"
                     :page-sizes="[10,20,50]" @change="load" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { utilityBillApi } from '@/api/energy'

const loading = ref(false)
const summaryLoading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, period: null, energyType: null, settlementStatus: null })
const summary = reactive({
  latestElectricSettledPeriod: null, latestWaterSettledPeriod: null,
  receivableAmount: 0, receivedAmount: 0, outstandingAmount: 0, pendingSettlementCount: 0
})
const settlementOptions = [
  { value: 'NOT_BILLED', label: '未出账' }, { value: 'PENDING_RECEIPT', label: '待收款' },
  { value: 'PARTIAL_RECEIPT', label: '部分结算' }, { value: 'SETTLED', label: '已结算' },
  { value: 'NO_RECEIVABLE', label: '无需结算' }, { value: 'BILL_EXCEPTION', label: '账单异常' }
]
const STATUS_META = Object.fromEntries(settlementOptions.map(item => [item.value, { label: item.label,
  type: ({ NOT_BILLED: 'info', PENDING_RECEIPT: 'warning', PARTIAL_RECEIPT: 'warning', SETTLED: 'success', NO_RECEIVABLE: 'info', BILL_EXCEPTION: 'danger' })[item.value] }]))
const statusMeta = (status) => STATUS_META[status] || { label: '-', type: 'info' }
const money = (value) => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

async function load() {
  loading.value = true
  try {
    const res = await utilityBillApi.settlementPage(query)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}
async function loadSummary() {
  summaryLoading.value = true
  try {
    Object.assign(summary, await utilityBillApi.settlementSummary({ period: query.period, energyType: query.energyType }))
  } finally {
    summaryLoading.value = false
  }
}
function search() { query.pageNo = 1; return Promise.all([load(), loadSummary()]) }
function reset() {
  Object.assign(query, { pageNo: 1, pageSize: 10, period: null, energyType: null, settlementStatus: null })
  return search()
}
onMounted(search)
</script>

<style scoped>
.ledger-note { margin-bottom: 14px; }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 12px; margin-bottom: 14px; }
.summary-item { padding: 14px 16px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-bg-color); display: flex; flex-direction: column; gap: 6px; }
.summary-item span, .summary-item small, .toolbar-tip { color: var(--el-text-color-secondary); font-size: 12px; }
.summary-item strong { font-size: 19px; font-variant-numeric: tabular-nums; }
.summary-item.success strong, .received { color: var(--el-color-success); }
.summary-item.warning strong, .outstanding { color: var(--el-color-danger); }
.toolbar-tip { margin-bottom: 14px; line-height: 1.6; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
