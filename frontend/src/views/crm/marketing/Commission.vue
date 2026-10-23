<template>
  <div class="page-container mkt-page">
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="伙伴"><el-input v-model="query.promoterName" placeholder="姓名或手机号" clearable style="width: 160px" @keyup.enter="search" /></el-form-item>
        <el-form-item label="伙伴 ID"><el-input-number v-model="query.promoterId" :min="1" controls-position="right" style="width: 140px" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(t, v) in ST" :key="v" :label="t" :value="Number(v)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" :disabled="!settleable.length || settling" :loading="settling" @click="settle">批量结算(已选 {{ settleable.length }} 条,合计 {{ settleSum }})</el-button>
        <el-button @click="batches.visible = true; loadBatches()">结算批次</el-button>
        <el-button type="primary" plain @click="openMonthly">按合同月结</el-button>
        <span class="hint">只有「可结算」状态的行能结算;冻结行等到账/解冻期满后自动转可结算。按合同月结:可选择合同开始月到上个月之间的任意月份(上个月仅限每月 3-10 号,更早月份可随时补结)。</span>
        <p class="hint hint-block">本表是按发货日记录的佣金流水：每个发货日一条，“解冻时间”是发货日加冻结期（默认 7 天），所以会出现晚于今天的日期，不是发货日。结算单（结算批次）按月生成：每月 8 日 10:00 自动把当月之前已解冻的佣金按“合同 × 月份”各生成一个批次，也可以手动按合同月结。</p>
      </div>
      <el-table :data="list" v-loading="loading" border stripe @selection-change="onSelect">
        <el-table-column type="selection" width="45" :selectable="isSettleable" />
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="referralOrderId" label="订单" width="80" />
        <el-table-column label="发货日" width="110"><template #default="{ row }">{{ row.orderEventTime ? String(row.orderEventTime).slice(0, 10) : '—' }}</template></el-table-column>
        <el-table-column prop="promoterName" label="伙伴" width="110"><template #default="{ row }">{{ row.promoterName || '#' + row.promoterId }}</template></el-table-column>
        <el-table-column prop="positionCode" label="岗位" width="70" />
        <el-table-column label="计佣依据" min-width="190"><template #default="{ row }"><CommissionBasis :row="row" /></template></el-table-column>
        <el-table-column label="历史基数(元)" width="115" align="right"><template #default="{ row }">{{ row.pricingId != null ? '—' : money(row.baseAmount) }}</template></el-table-column>
        <el-table-column label="金额(元)" width="110" align="right">
          <template #default="{ row }"><span :class="{ neg: row.sign === -1 }">{{ row.pricingId != null ? Number(row.amount || 0).toFixed(3) : money(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="stType(row.status)">{{ ST[row.status] }}</el-tag></template></el-table-column>
        <el-table-column prop="unfreezeAt" label="解冻时间（发货日＋冻结期）" width="190" />
        <el-table-column prop="settleBatchId" label="批次" width="80" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button v-if="[1, 2].includes(row.status) && row.sign === 1 && !row.withdrawalId" link type="danger" @click="voidOne(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <el-drawer v-model="batches.visible" title="结算批次" size="520px">
      <el-table :data="batches.list" size="small" border>
        <el-table-column prop="batchNo" label="批次号" width="180" />
        <el-table-column prop="cnt" label="条数" width="70" />
        <el-table-column label="合计(元)" width="110" align="right"><template #default="{ row }">{{ Number(row.totalAmount || 0).toFixed(3) }}</template></el-table-column>
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column prop="createTime" label="时间" />
      </el-table>
      <el-pagination class="pager" small background layout="total, prev, pager, next" :total="batches.total"
                     v-model:current-page="batches.pageNo" :page-size="batches.pageSize" @current-change="loadBatches" />
    </el-drawer>

    <el-dialog v-model="monthly.visible" title="按合同月结（选择月份结算出库佣金）" width="min(560px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="服务合同" required>
          <el-select v-model="monthly.contractId" filterable class="full" :loading="monthly.loadingContracts" @change="onContractChange">
            <el-option v-for="c in monthly.contracts" :key="c.id" :label="`${c.contractNo} · ${SIGN[c.signMode] || ''} · #${c.id}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="结算月份" required>
          <el-date-picker v-model="monthly.month" type="month" value-format="YYYY-MM" format="YYYY 年 MM 月" :clearable="false"
                          :disabled="!monthly.contractId" :disabled-date="monthDisabled" placeholder="选择已结束的月份" class="full" />
        </el-form-item>
      </el-form>
      <el-descriptions v-if="monthly.result" :column="1" border class="mt">
        <el-descriptions-item label="结算账期">{{ monthly.result.periodStart }} 至 {{ monthly.result.periodEnd }}</el-descriptions-item>
        <el-descriptions-item label="本次结算">{{ monthly.result.settleCount }} 条,合计 {{ monthly.result.settleAmount }} 元<template v-if="monthly.result.batchNo">(批次 {{ monthly.result.batchNo }})</template></el-descriptions-item>
        <el-descriptions-item label="仍在冻结期">{{ monthly.result.frozenCount }} 条,本次不结算</el-descriptions-item>
        <el-descriptions-item label="此前已结算/已提现">{{ monthly.result.alreadySettledCount }} 条</el-descriptions-item>
      </el-descriptions>
      <p class="hint">可选择合同开始月份到上个月之间的任意月份,结算该月全部出库单中「可结算」状态的佣金;结算上个月仅限每月 3-10 号,更早月份可随时补结。冻结期未满的行留待解冻后结算,已结算/已提现的行不会重复结算。</p>
      <template #footer>
        <el-button @click="monthly.visible = false">关闭</el-button>
        <el-button v-if="monthly.result" @click="monthly.result = null">继续结算其他月份</el-button>
        <el-button type="primary" :loading="monthly.settling" :disabled="!monthly.contractId || !monthly.month || !!monthly.result" @click="runMonthly">结算所选月份佣金</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktCommissionApi, mktContractApi } from '@/api/marketing'
import { money } from '@/utils/format'
import CommissionBasis from './CommissionBasis.vue'

const ST = { 1: '冻结', 2: '可结算', 3: '已结算', 4: '已提现', 5: '作废' }
const SIGN = { 1: '园区签', 2: '直签' }
const stType = (s) => ({ 2: 'success', 3: '', 4: 'info', 5: 'danger' }[s] || 'warning')

const settling = ref(false)
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 20, promoterId: null, promoterName: '', status: null })
const selected = ref([])
// 扣回行(sign=-1)只能在打款时抵扣,不能拿去结算,否则余额公式就不再认它
const isSettleable = (row) => row.status === 2 && row.sign === 1 && !row.withdrawalId
const settleable = computed(() => selected.value.filter(isSettleable))
const settleSum = computed(() => settleable.value.reduce((a, r) => a + Number(r.amount || 0), 0).toFixed(3))

async function load() {
  loading.value = true
  try {
    const res = await mktCommissionApi.page(query)
    list.value = res.records; total.value = res.total
  } finally { loading.value = false }
}
function search() { query.pageNo = 1; load() }
function reset() { Object.assign(query, { pageNo: 1, promoterId: null, promoterName: '', status: null }); load() }
function onSelect(rows) { selected.value = rows }

async function settle() {
  await ElMessageBox.confirm(`将 ${settleable.value.length} 条可结算流水结算为「已结算」,合计 ${settleSum.value} 元?`, '批量结算', { type: 'warning' })
  if(settling.value)return
  settling.value=true
  try { const batchNo = await mktCommissionApi.settle(settleable.value.map(r => r.id)); selected.value=[]
    ElMessage.success(`已结算,批次 ${batchNo}`); await load()
  } finally { settling.value=false }
}
async function voidOne(row) {
  const { value } = await ElMessageBox.prompt('作废原因', '作废佣金', { inputPattern: /\S+/, inputErrorMessage: '原因必填' })
  await mktCommissionApi.void(row.id, { reason: value }); ElMessage.success('已作废'); load()
}

// 按合同月结:可选合同开始月到上个月之间的任意月份;上个月仅限每月 3-10 号,更早月份可随时补结
const monthly = reactive({ visible: false, contracts: [], loadingContracts: false, contractId: null, month: null, settling: false, result: null })
const ym = (y, m) => `${y}-${String(m).padStart(2, '0')}`
const lastMonth = () => { const d = new Date(); return d.getMonth() === 0 ? ym(d.getFullYear() - 1, 12) : ym(d.getFullYear(), d.getMonth()) }
const contractStartMonth = () => (monthly.contracts.find(c => c.id === monthly.contractId)?.startDate || '').slice(0, 7)
function monthDisabled(date) {
  const value = ym(date.getFullYear(), date.getMonth() + 1), start = contractStartMonth()
  return value > lastMonth() || (!!start && value < start)
}
function onContractChange() {
  monthly.result = null
  const start = contractStartMonth(), last = lastMonth()
  monthly.month = start && start > last ? null : last
}
async function openMonthly() {
  monthly.result = null
  monthly.visible = true
  if (monthly.contracts.length) return
  monthly.loadingContracts = true
  try {
    const records = []
    for (let pageNo = 1; ; pageNo++) {
      const r = await mktContractApi.page({ pageNo, pageSize: 100 })
      const page = r?.records || []
      records.push(...page)
      if (pageNo * 100 >= Number(r?.total || 0) || !page.length) break
    }
    monthly.contracts = [...new Map(records.map(c => [c.id, c])).values()]
  } finally { monthly.loadingContracts = false }
}
async function runMonthly() {
  if (!monthly.contractId || !monthly.month) return
  try {
    await ElMessageBox.confirm(`将结算所选合同 ${monthly.month} 月的出库单佣金(仅「可结算」状态),确认执行?`, '按合同月结', { type: 'warning' })
  } catch { return }
  monthly.settling = true
  try {
    monthly.result = await mktCommissionApi.settleContract({ contractId: monthly.contractId, month: monthly.month })
    if (monthly.result.settleCount > 0) ElMessage.success(`已结算 ${monthly.result.settleCount} 条,批次 ${monthly.result.batchNo}`)
    else ElMessage.info(`该合同 ${monthly.month} 月没有可结算的佣金`)
    await load()
  } finally { monthly.settling = false }
}

const batches = reactive({ visible: false, list: [], total: 0, pageNo: 1, pageSize: 20 })
async function loadBatches() {
  const res = await mktCommissionApi.batches({ pageNo: batches.pageNo, pageSize: batches.pageSize })
  batches.list = res.records; batches.total = res.total
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; }
.hint-block { flex-basis: 100%; margin: 4px 0 0; line-height: 1.6; }
.neg { color: var(--el-color-danger); }
.pager { margin-top: 16px; justify-content: flex-end; }
.mt { margin-top: 12px; }
.full { width: 100%; }
</style>
