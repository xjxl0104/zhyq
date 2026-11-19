<template>
  <div class="page-container mkt-page">
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="伙伴"><el-input v-model="query.promoterName" placeholder="姓名或手机号" clearable style="width: 160px" @keyup.enter="search" /></el-form-item>
        <el-form-item label="伙伴 ID"><el-input-number v-model="query.promoterId" :min="1" controls-position="right" style="width: 140px" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" plain @click="openMonthly()">按合同月结</el-button>
        <el-button @click="batches.visible = true; loadBatches()">结算批次</el-button>
        <span class="hint">一个合同一行，点击合同查看每月结算情况。佣金发货满冻结期（默认 7 天）解冻；每天 02:00 自动结算已结束月份的已解冻佣金（结算后计入伙伴可提现余额），也可以在合同里按月手动结算。</span>
      </div>
      <div class="summary">
        <div class="sum-card"><div class="sum-label">佣金合计(元)</div><div class="sum-value">{{ amt(totals.total) }}</div><div class="sub">{{ contracts.length }} 个合同</div></div>
        <div class="sum-card frozen"><div class="sum-label">冻结中</div><div class="sum-value">{{ amt(totals.frozen) }}</div><div class="sub">发货未满冻结期</div></div>
        <div class="sum-card settleable"><div class="sum-label">可结算</div><div class="sum-value">{{ amt(totals.settleable) }}</div><div class="sub">已结束月份次日 02:00 自动结算</div></div>
        <div class="sum-card settled"><div class="sum-label">已结算/已提现</div><div class="sum-value">{{ amt(totals.settled) }}</div><div class="sub">已计入伙伴余额</div></div>
      </div>
      <el-table :data="contracts" v-loading="loading" border stripe row-class-name="clickable" @row-click="openContract">
        <el-table-column label="合同" min-width="220"><template #default="{ row }"><div>{{ row.customerName || '—' }}</div><div class="sub">{{ row.contractNo || '#' + row.contractId }}</div></template></el-table-column>
        <el-table-column label="伙伴" min-width="120"><template #default="{ row }">{{ row.promoters || '—' }}</template></el-table-column>
        <el-table-column label="结算月份" width="170"><template #default="{ row }">{{ row.firstMonth === row.latestMonth ? row.latestMonth : row.firstMonth + ' ~ ' + row.latestMonth }}<div class="sub">共 {{ row.months.length }} 个月</div></template></el-table-column>
        <el-table-column prop="tickets" label="发货票数" width="100" align="right" />
        <el-table-column label="佣金合计(元)" width="120" align="right"><template #default="{ row }">{{ amt(row.total) }}</template></el-table-column>
        <el-table-column label="冻结中" width="100" align="right"><template #default="{ row }">{{ amt(row.frozen) }}</template></el-table-column>
        <el-table-column label="可结算" width="100" align="right"><template #default="{ row }"><span :class="{ ok: Number(row.settleable) > 0 }">{{ amt(row.settleable) }}</span></template></el-table-column>
        <el-table-column label="已结算/已提现" width="120" align="right"><template #default="{ row }">{{ amt(row.settled) }}</template></el-table-column>
        <el-table-column label="结算进度" width="170"><template #default="{ row }"><SettleBar :row="row" /></template></el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }"><el-button link type="primary" @click.stop="openContract(row)">每月结算</el-button></template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="contractView.visible" :title="drawerTitle" size="min(980px, 96vw)">
      <template v-if="currentContract">
        <div class="sub drawer-sub">{{ currentContract.contractNo }} · 伙伴：{{ currentContract.promoters || '—' }}</div>
        <div class="month-chart">
          <div v-for="m in chartMonths" :key="m.month" class="month-col" :title="m.month + '：合计 ' + amt(m.total) + ' 元'">
            <div class="month-amt">{{ Number(m.total || 0).toFixed(2) }}</div>
            <div class="month-bar" :style="{ height: barHeight(m) + 'px' }">
              <span class="seg settled" :style="{ flexGrow: Number(m.settled) || 0 }" />
              <span class="seg settleable" :style="{ flexGrow: Number(m.settleable) || 0 }" />
              <span class="seg frozen" :style="{ flexGrow: Number(m.frozen) || 0 }" />
            </div>
            <div class="month-label">{{ String(m.month).slice(2) }}</div>
          </div>
        </div>
        <div class="legend"><span><i class="dot settled" />已结算/已提现</span><span><i class="dot settleable" />可结算</span><span><i class="dot frozen" />冻结中</span></div>
        <el-table :data="currentContract.months" border stripe size="small">
          <el-table-column prop="month" label="月份" width="100" />
          <el-table-column prop="tickets" label="发货票数" width="100" align="right" />
          <el-table-column label="佣金合计(元)" width="120" align="right"><template #default="{ row }">{{ amt(row.total) }}</template></el-table-column>
          <el-table-column label="冻结中" width="100" align="right"><template #default="{ row }">{{ amt(row.frozen) }}</template></el-table-column>
          <el-table-column label="可结算" width="100" align="right"><template #default="{ row }"><span :class="{ ok: Number(row.settleable) > 0 }">{{ amt(row.settleable) }}</span></template></el-table-column>
          <el-table-column label="已结算/已提现" width="120" align="right"><template #default="{ row }">{{ amt(row.settled) }}</template></el-table-column>
          <el-table-column label="状态" min-width="120"><template #default="{ row }"><SettleBar :row="row" /></template></el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button v-if="Number(row.settleable) > 0" link type="primary" :disabled="isFiltered" :title="isFiltered ? '筛选伙伴时只显示该伙伴的金额，请取消筛选后再结算整个合同月份' : ''" @click="settleRow(row)">结算</el-button>
              <el-button link @click="openDetail(row)">明细</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <el-drawer v-model="detail.visible" :title="`${detail.title} 佣金明细（按发货日）`" size="920px">
      <el-table :data="detail.list" v-loading="detail.loading" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="发货日" width="110"><template #default="{ row }">{{ row.orderEventTime ? String(row.orderEventTime).slice(0, 10) : '—' }}</template></el-table-column>
        <el-table-column prop="promoterName" label="伙伴" width="100"><template #default="{ row }">{{ row.promoterName || '#' + row.promoterId }}</template></el-table-column>
        <el-table-column prop="positionCode" label="岗位" width="70" />
        <el-table-column label="计佣依据" min-width="190"><template #default="{ row }"><CommissionBasis :row="row" /></template></el-table-column>
        <el-table-column label="金额(元)" width="100" align="right"><template #default="{ row }"><span :class="{ neg: row.sign === -1 }">{{ row.pricingId != null ? Number(row.amount || 0).toFixed(3) : money(row.amount) }}</span></template></el-table-column>
        <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="stType(row.status)">{{ ST[row.status] }}</el-tag></template></el-table-column>
        <el-table-column prop="unfreezeAt" label="解冻时间" width="170" />
        <el-table-column label="操作" width="70">
          <template #default="{ row }"><el-button v-if="[1, 2].includes(row.status) && row.sign === 1 && !row.withdrawalId" link type="danger" @click="voidOne(row)">作废</el-button></template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" small background layout="total, prev, pager, next" :total="detail.total"
                     v-model:current-page="detail.pageNo" :page-size="detail.pageSize" @current-change="loadDetail" />
    </el-drawer>
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
      <p class="hint">可选择合同开始月份到上个月之间的任意月份,结算该月全部出库单中「可结算」状态的佣金,随时可结。冻结期未满的行留待解冻后结算(系统每天 02:00 也会自动结算),已结算/已提现的行不会重复结算。</p>
      <template #footer>
        <el-button @click="monthly.visible = false">关闭</el-button>
        <el-button v-if="monthly.result" @click="monthly.result = null">继续结算其他月份</el-button>
        <el-button type="primary" :loading="monthly.settling" :disabled="!monthly.contractId || !monthly.month || !!monthly.result" @click="runMonthly">结算所选月份佣金</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, h } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktCommissionApi, mktContractApi } from '@/api/marketing'
import { money } from '@/utils/format'
import CommissionBasis from './CommissionBasis.vue'
import { groupByContract, settleShares } from './commissionGroups'

const ST = { 1: '冻结', 2: '可结算', 3: '已结算', 4: '已提现', 5: '作废' }
const SIGN = { 1: '园区签', 2: '直签' }
const stType = (s) => ({ 2: 'success', 3: '', 4: 'info', 5: 'danger' }[s] || 'warning')

const loading = ref(false)
const months = ref([])
const query = reactive({ promoterId: null, promoterName: '' })
const isFiltered = computed(() => !!query.promoterId || !!String(query.promoterName || '').trim())
const amt = (v) => Number(v || 0).toFixed(3)
// 主列表一个合同一行;点进合同看每月(结算后数据刷新,抽屉跟着变)
const contracts = computed(() => groupByContract(months.value))
const totals = computed(() => contracts.value.reduce((s, c) => {
  for (const k of ['total', 'frozen', 'settleable', 'settled']) s[k] += Number(c[k] || 0)
  return s
}, { total: 0, frozen: 0, settleable: 0, settled: 0 }))
const contractView = reactive({ visible: false, contractId: null })
const currentContract = computed(() => contracts.value.find(c => c.contractId === contractView.contractId) || null)
const drawerTitle = computed(() => currentContract.value ? `${currentContract.value.customerName || currentContract.value.contractNo} · 每月结算` : '每月结算')
function openContract(row) { contractView.contractId = row.contractId; contractView.visible = true }
const chartMonths = computed(() => [...(currentContract.value?.months || [])].reverse())
const maxMonth = computed(() => Math.max(1, ...chartMonths.value.map(m => Number(m.total || 0))))
const barHeight = m => Math.max(2, Math.round(Number(m.total || 0) / maxMonth.value * 120))
// 结算进度条:绿=已结算,蓝=可结算,橙=冻结
const SettleBar = (props) => {
  const s = settleShares(props.row)
  const label = s.settled >= 100 ? '已全部结算' : s.settleable > 0 ? `可结算 ${s.settleable}%` : s.frozen > 0 ? `冻结中 ${s.frozen}%` : '—'
  return h('div', { class: 'settle-bar-wrap' }, [
    h('div', { class: 'settle-bar' }, [
      h('span', { class: 'seg settled', style: { width: s.settled + '%' } }),
      h('span', { class: 'seg settleable', style: { width: s.settleable + '%' } }),
      h('span', { class: 'seg frozen', style: { width: s.frozen + '%' } })]),
    h('div', { class: 'sub' }, label)])
}
SettleBar.props = ['row']

async function load() {
  loading.value = true
  try {
    months.value = await mktCommissionApi.monthly({ promoterId: query.promoterId || undefined, promoterName: String(query.promoterName || '').trim() || undefined })
  } finally { loading.value = false }
}
function search() { load() }
function reset() { Object.assign(query, { promoterId: null, promoterName: '' }); load() }

// 一行 = 一个合同的一个月份:直接按合同月结
async function settleRow(row) {
  try {
    await ElMessageBox.confirm(`将结算 ${row.customerName || row.contractNo} ${row.month} 月可结算的佣金 ${amt(row.settleable)} 元(含该合同当月全部伙伴),确认?`, '按合同月结', { type: 'warning' })
  } catch { return }
  const result = await mktCommissionApi.settleContract({ contractId: row.contractId, month: row.month })
  if (result.settleCount > 0) ElMessage.success(`已结算 ${result.settleCount} 条,批次 ${result.batchNo}`)
  else ElMessage.info('该月份没有可结算的佣金')
  await load()
}

const detail = reactive({ visible: false, loading: false, title: '', contractId: null, month: null, list: [], total: 0, pageNo: 1, pageSize: 20 })
async function loadDetail() {
  detail.loading = true
  try {
    const res = await mktCommissionApi.page({ pageNo: detail.pageNo, pageSize: detail.pageSize, contractId: detail.contractId, month: detail.month })
    detail.list = res.records; detail.total = res.total
  } finally { detail.loading = false }
}
function openDetail(row) {
  Object.assign(detail, { visible: true, title: `${row.customerName || row.contractNo} ${row.month}`, contractId: row.contractId, month: row.month, pageNo: 1 })
  loadDetail()
}
async function voidOne(row) {
  const { value } = await ElMessageBox.prompt('作废原因', '作废佣金', { inputPattern: /\S+/, inputErrorMessage: '原因必填' })
  await mktCommissionApi.void(row.id, { reason: value }); ElMessage.success('已作废'); await loadDetail(); await load()
}
// 按合同月结:可选合同开始月到上个月之间的任意月份,随时可结
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
.ok { color: var(--el-color-success); font-weight: 600; }
.sub { color: var(--el-text-color-secondary); font-size: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.mt { margin-top: 12px; }
.full { width: 100%; }
.summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; margin: 12px 0; }
.sum-card { padding: 12px 14px; border: 1px solid var(--el-border-color-lighter); border-left: 4px solid var(--el-color-info); border-radius: 8px; background: var(--el-bg-color); }
.sum-card.frozen { border-left-color: var(--el-color-warning); }
.sum-card.settleable { border-left-color: var(--el-color-primary); }
.sum-card.settled { border-left-color: var(--el-color-success); }
.sum-label { font-size: 12px; color: var(--el-text-color-secondary); }
.sum-value { margin: 4px 0; font-size: 22px; font-weight: 600; font-variant-numeric: tabular-nums; }
:deep(.clickable) { cursor: pointer; }
.drawer-sub { margin: -8px 0 12px; }
.month-chart { display: flex; align-items: flex-end; gap: 10px; min-height: 170px; padding: 8px 4px 0; overflow-x: auto; border-bottom: 1px solid var(--el-border-color-lighter); }
.month-col { display: flex; flex-direction: column; align-items: center; min-width: 44px; }
.month-amt { margin-bottom: 4px; font-size: 11px; color: var(--el-text-color-secondary); }
.month-bar { display: flex; flex-direction: column-reverse; width: 26px; border-radius: 4px 4px 0 0; overflow: hidden; background: var(--el-fill-color); }
.month-bar .seg { display: block; flex-basis: 0; }
.month-label { margin: 6px 0; font-size: 12px; color: var(--el-text-color-regular); }
.legend { display: flex; gap: 16px; margin: 10px 0 14px; font-size: 12px; color: var(--el-text-color-secondary); }
.dot { display: inline-block; width: 10px; height: 10px; margin-right: 4px; border-radius: 2px; vertical-align: -1px; }
:deep(.settle-bar) { display: flex; height: 8px; border-radius: 4px; overflow: hidden; background: var(--el-fill-color); }
:deep(.seg.settled), .dot.settled { background: var(--el-color-success); }
:deep(.seg.settleable), .dot.settleable { background: var(--el-color-primary); }
:deep(.seg.frozen), .dot.frozen { background: var(--el-color-warning); }
</style>
