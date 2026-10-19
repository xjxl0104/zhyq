<template>
  <div class="page-container mkt-page">
    <el-tabs v-model="tab" class="order-tabs">
      <el-tab-pane label="订单明细" name="orders" />
      <el-tab-pane label="按合同汇总" name="summary" />
    </el-tabs>

    <!-- ===================== 订单明细 ===================== -->
    <template v-if="tab === 'orders'">
      <div class="search-bar">
        <el-form :inline="true" :model="query">
          <el-form-item label="单号"><el-input v-model="query.sourceNo" clearable style="width: 170px" /></el-form-item>
          <el-form-item label="合同号"><el-input v-model="query.contractNo" clearable placeholder="CS-xxxx" style="width: 150px" /></el-form-item>
          <el-form-item label="伙伴">
            <el-select v-model="query.promoterId" placeholder="全部" clearable filterable style="width: 140px">
              <el-option v-for="p in promoters" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="客户">
            <el-select v-model="query.customerId" placeholder="全部" clearable filterable style="width: 150px">
              <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="云仓">
            <el-select v-model="query.warehouseId" placeholder="全部" clearable filterable style="width: 150px">
              <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源">
            <el-select v-model="query.sourceType" placeholder="全部" clearable style="width: 130px">
              <el-option v-for="(t, v) in SRC" :key="v" :label="t" :value="Number(v)" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
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
          <el-upload :show-file-list="false" accept=".xlsx,.xls" :disabled="importing" :http-request="doImport">
            <el-button type="primary" :loading="importing"><el-icon><Upload /></el-icon>导入出库单(Excel)</el-button>
          </el-upload>
          <el-button link type="primary" @click="downloadTemplate">下载模板</el-button>
          <span class="hint hint--block">列:出库单号 · 客户手机号 · 件数 · 包裹数 · 发货时间 · 物流单号 · 云仓编码(可选) · 货值(可选)。服务费按园区签合同单价表计算；佣金按已配置冻结期到期解冻。发货日期须在合同有效期内。直签出库只记录运营数量，不生成园区服务费或佣金；平台费实际到账请到服务费账单页登记。</span>
        </div>
        <el-alert v-if="importResult" :type="importResult.errors.length ? 'warning' : 'success'" :closable="true" class="mb" @close="importResult = null">
          导入 {{ importResult.imported }} 条,跳过 {{ importResult.skipped }} 条(重复单号)
          <template v-if="importResult.errors.length">,失败 {{ importResult.errors.length }} 条:
            <ul class="errs"><li v-for="(e, i) in importResult.errors" :key="i">{{ e }}</li></ul>
          </template>
        </el-alert>
        <el-table :data="list" v-loading="loading" border stripe>
          <el-table-column prop="sourceNo" label="单号" width="170" />
          <el-table-column label="来源" width="100"><template #default="{ row }">{{ SRC[row.sourceType] }}</template></el-table-column>
          <el-table-column label="服务合同" min-width="170">
            <template #default="{ row }">
              <el-link v-if="row.sourceType === 1" type="primary" :href="`/contract/detail/${row.sourceId}`" target="_blank">租赁合同</el-link>
              <template v-else-if="row.contractNo">
                {{ row.contractNo }}
                <el-tag size="small" class="ml4" :type="row.signMode === 2 ? 'warning' : 'info'">{{ SIGN[row.signMode] || '—' }}</el-tag>
              </template>
              <span v-else class="dim">—</span>
            </template>
          </el-table-column>
          <el-table-column label="云仓" min-width="130">
            <template #default="{ row }">{{ row.warehouseName || '—' }}<div v-if="row.warehouseCode" class="basis-note">{{ row.warehouseCode }}</div></template>
          </el-table-column>
          <el-table-column prop="customerName" label="客户" min-width="120" />
          <el-table-column label="ERP货主编码" width="120"><template #default="{ row }">{{ row.customerCode || '—' }}</template></el-table-column>
          <el-table-column prop="promoterName" label="成交伙伴" width="100" />
          <el-table-column prop="customerGrade" label="评级" width="60" align="center" />
          <el-table-column prop="qty" label="件数" width="70" /><el-table-column prop="packages" label="包裹数" width="80" /><el-table-column label="服务费(元)" width="110" align="right"><template #default="{ row }">{{ money(row.serviceFee) }}</template></el-table-column>
          <el-table-column label="基数 / 计佣单量" width="130" align="right"><template #default="{ row }">{{ row.pricingId != null ? `${row.commissionOrderCount ?? '—'} 单` : `${money(row.baseAmount)} 元` }}</template></el-table-column>
          <el-table-column label="计佣规则" width="150" align="right">
            <template #default="{ row }">
              <template v-if="row.pricingId != null">{{ Number(row.commissionUnitPrice || 0).toFixed(3) }} 元 / 单<div class="basis-note">P4 自定义</div></template>
              <template v-else>{{ row.sourceType === 1 ? `${row.poolFactor} 月` : `${row.poolFactor}%` }}</template>
            </template>
          </el-table-column>
          <el-table-column label="伙伴佣金池(元)" width="120" align="right"><template #default="{ row }">{{ row.pricingId != null ? Number(row.poolAmount || 0).toFixed(3) : money(row.poolAmount) }}</template></el-table-column>
          <el-table-column label="状态" width="85"><template #default="{ row }"><el-tag :type="stType(row.status)">{{ ST[row.status] }}</el-tag></template></el-table-column>
          <el-table-column prop="remark" label="业务说明" min-width="160" show-overflow-tooltip />
          <el-table-column prop="eventTime" label="事件时间" width="160" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.pricingId != null || Number(row.poolAmount) > 0" link type="primary" @click="openSplits(row)">拆分</el-button>
              <el-button v-if="row.status === 2" link type="danger" @click="voidOrder(row)">作废</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                       :total="total" v-model:current-page="query.pageNo"
                       v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
      </div>
    </template>

    <!-- ===================== 按合同汇总 ===================== -->
    <template v-else>
      <div class="search-bar">
        <el-form :inline="true" :model="squery">
          <el-form-item label="合同号"><el-input v-model="squery.contractNo" clearable style="width: 150px" /></el-form-item>
          <el-form-item label="伙伴">
            <el-select v-model="squery.promoterId" placeholder="全部" clearable filterable style="width: 140px">
              <el-option v-for="p in promoters" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="客户">
            <el-select v-model="squery.customerId" placeholder="全部" clearable filterable style="width: 150px">
              <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="云仓">
            <el-select v-model="squery.warehouseId" placeholder="全部" clearable filterable style="width: 150px">
              <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="searchSummary"><el-icon><Search /></el-icon>查询</el-button>
            <el-button @click="resetSummary">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
      <div class="table-card">
        <el-table :data="summaryList" v-loading="summaryLoading" border stripe
                  @row-click="gotoContractOrders">
          <el-table-column prop="contractNo" label="合同号" min-width="150">
            <template #default="{ row }">
              {{ row.contractNo }}
              <el-tag size="small" class="ml4" :type="row.signMode === 2 ? 'warning' : 'info'">{{ SIGN[row.signMode] || '—' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="服务类型" width="90" align="center"><template #default="{ row }">{{ SVT[row.serviceType] || '—' }}</template></el-table-column>
          <el-table-column label="计费模式" width="90" align="center"><template #default="{ row }">{{ FM[row.feeModel] || '—' }}</template></el-table-column>
          <el-table-column prop="customerName" label="客户" min-width="130" />
          <el-table-column prop="partnerName" label="成交伙伴" width="110"><template #default="{ row }">{{ row.partnerName || '—' }}</template></el-table-column>
          <el-table-column prop="warehouseName" label="主仓" min-width="130"><template #default="{ row }">{{ row.warehouseName || '—' }}</template></el-table-column>
          <el-table-column label="计佣订单数" width="100" align="right"><template #default="{ row }">{{ row.orderCount ?? 0 }}</template></el-table-column>
          <el-table-column label="已确认" width="80" align="right"><template #default="{ row }">{{ row.confirmedCount ?? 0 }}</template></el-table-column>
          <el-table-column label="服务费合计(元)" width="130" align="right"><template #default="{ row }">{{ money(row.serviceFeeSum) }}</template></el-table-column>
          <el-table-column label="佣金池合计(元)" width="130" align="right"><template #default="{ row }">{{ money(row.poolSum) }}</template></el-table-column>
          <el-table-column prop="lastEventTime" label="最近出库时间" width="160"><template #default="{ row }">{{ row.lastEventTime || '—' }}</template></el-table-column>
          <el-table-column label="合同有效期" width="210"><template #default="{ row }">{{ (row.startDate || '—') + ' ~ ' + (row.endDate || '—') }}</template></el-table-column>
        </el-table>
        <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                       :total="summaryTotal" v-model:current-page="squery.pageNo"
                       v-model:page-size="squery.pageSize" :page-sizes="[10,20,50]" @change="loadSummary" />
        <p class="hint pager-hint">点击任意合同行可跳到「订单明细」并按该合同过滤。汇总只统计出库单类计佣事件(来源=出库单)。</p>
      </div>
    </template>

    <el-drawer v-model="splits.visible" :title="`佣金拆分 · ${splits.row?.sourceNo || ''}`" size="min(680px, 100vw)">
      <el-table :data="splits.list" size="small" border>
        <el-table-column prop="promoterId" label="伙伴" width="80" />
        <el-table-column prop="positionCode" label="岗位" width="70" />
        <el-table-column label="计佣依据" min-width="190"><template #default="{ row }"><CommissionBasis :row="row" /></template></el-table-column>
        <el-table-column label="金额(元)" width="110" align="right"><template #default="{ row }">{{ row.pricingId != null ? Number(row.amount || 0).toFixed(3) : money(row.amount) }}</template></el-table-column>
        <el-table-column label="状态"><template #default="{ row }">{{ CST[row.status] }}</template></el-table-column>
        <el-table-column prop="unfreezeAt" label="解冻时间" width="160" />
      </el-table>
      <p v-if="splits.row?.pricingId != null" class="hint split-summary">总佣金 {{ snapshotAmount(splits.row.commissionUnitPrice, splits.row.commissionOrderCount) }} 元；伙伴佣金池 {{ Number(splits.row.poolAmount || 0).toFixed(3) }} 元；公司剩余额 {{ snapshotAmount(splits.row.companyPerOrder, splits.row.commissionOrderCount) }} 元。按订单生成时的 P4 配置计算，当前流水合计 {{ sum }} 元。</p>
      <p v-else class="hint">合计 {{ sum }} / 池 {{ splits.row?.poolAmount }};差额为园区留存(链未到顶格、退出或内部人员)。</p>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktOrderApi, mktPromoterApi, mktCustomerApi, mktWarehouseApi } from '@/api/marketing'
import { money } from '@/utils/format'
import CommissionBasis from './CommissionBasis.vue'

const SRC = { 1: '租赁签约', 2: '出库单', 3: '平台费收款', 4: '签约奖', 5: '增值服务' }
const ST = { 1: '待确认', 2: '已确认', 3: '已退款', 4: '已取消', 5: '无归属' }
const CST = { 1: '冻结', 2: '可结算', 3: '已结算', 4: '已提现', 5: '作废' }
const SIGN = { 1: '园区签', 2: '直签' }
const SVT = { 1: '仓储', 2: '代发', 3: '仓配' }
const FM = { 1: '仓租', 2: '单票', 3: '按件', 4: '包月' }
const stType = (s) => ({ 1: 'warning', 2: 'success', 3: 'danger', 4: 'info', 5: 'danger' }[s] || 'info')

const tab = ref('orders')
const loading = ref(false)
const importing = ref(false)
const importResult = ref(null)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, sourceNo: '', contractNo: '', sourceType: null, status: null, promoterId: null, customerId: null, warehouseId: null })

const summaryLoading = ref(false)
const summaryList = ref([])
const summaryTotal = ref(0)
const squery = reactive({ pageNo: 1, pageSize: 10, contractNo: '', promoterId: null, customerId: null, warehouseId: null })

// 筛选下拉数据
const promoters = ref([])
const customers = ref([])
const warehouses = ref([])

async function loadOptions() {
  try {
    const [pr, cu, wh] = await Promise.all([
      mktPromoterApi.page({ pageNo: 1, pageSize: 200 }),
      mktCustomerApi.page({ pageNo: 1, pageSize: 200 }),
      mktWarehouseApi.online()
    ])
    promoters.value = pr?.records || []
    customers.value = cu?.records || []
    warehouses.value = Array.isArray(wh) ? wh : (wh?.records || [])
  } catch (e) { console.error('加载筛选项失败', e) }
}

async function load() {
  loading.value = true
  try {
    const res = await mktOrderApi.page(query)
    list.value = res.records; total.value = res.total
  } finally { loading.value = false }
}
function search() { query.pageNo = 1; load() }
function reset() {
  Object.assign(query, { pageNo: 1, sourceNo: '', contractNo: '', sourceType: null, status: null, promoterId: null, customerId: null, warehouseId: null })
  load()
}

async function loadSummary() {
  summaryLoading.value = true
  try {
    const res = await mktOrderApi.contractSummary(squery)
    summaryList.value = res.records; summaryTotal.value = res.total
  } finally { summaryLoading.value = false }
}
function searchSummary() { squery.pageNo = 1; loadSummary() }
function resetSummary() {
  Object.assign(squery, { pageNo: 1, contractNo: '', promoterId: null, customerId: null, warehouseId: null })
  loadSummary()
}
// 汇总行点击 → 跳回明细并按合同过滤
function gotoContractOrders(row) {
  if (!row?.contractNo) return
  tab.value = 'orders'
  Object.assign(query, { pageNo: 1, contractNo: row.contractNo, sourceType: null, status: null, promoterId: null, customerId: null, warehouseId: null })
  load()
}

async function downloadTemplate() {
  const res = await mktOrderApi.template()
  const a = document.createElement('a')
  a.href = URL.createObjectURL(res.data)
  a.download = '出库单导入模板.xlsx'
  a.click()
  URL.revokeObjectURL(a.href)
}
async function doImport({ file }) {
  const fd = new FormData(); fd.append('file', file)
  importing.value = true
  try {
    importResult.value = await mktOrderApi.importExcel(fd)
    load(); if (tab.value === 'summary') loadSummary()
  } finally { importing.value = false }
}
async function voidOrder(row) {
  const { value } = await ElMessageBox.prompt('作废原因(未结算佣金作废,已结算生成扣回)', '作废计佣订单', { inputPattern: /\S+/, inputErrorMessage: '原因必填' })
  await mktOrderApi.void(row.id, { reason: value }); ElMessage.success('已作废'); load()
}

const splits = reactive({ visible: false, row: null, list: [] })
async function openSplits(row) { splits.row = row; splits.list = await mktOrderApi.splits(row.id); splits.visible = true }
const snapshotAmount = (unitPrice, count) => unitPrice == null || count == null ? '—' : (Number(unitPrice) * Number(count)).toFixed(3)
const sum = computed(() => splits.list.reduce((a, s) => a + Number(s.amount || 0), 0).toFixed(splits.row?.pricingId != null ? 3 : 2))

watch(tab, (t) => { if (t === 'summary' && !summaryList.value.length) loadSummary() })
onMounted(() => { load(); loadOptions() })
</script>

<style scoped>
.order-tabs { margin-bottom: 4px; }
.toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; }
.basis-note { color: var(--el-text-color-regular); font-size: 12px; margin-top: 4px; }
.mb { margin-bottom: 12px; }
.errs { margin: 4px 0 0 16px; max-height: 120px; overflow: auto; }
.pager { margin-top: 16px; justify-content: flex-end; }
.pager-hint { margin-top: 8px; }
.dim { color: var(--el-text-color-placeholder); }
.ml4 { margin-left: 4px; }
</style>
