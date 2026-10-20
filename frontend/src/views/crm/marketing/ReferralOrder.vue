<template>
  <div class="page-container mkt-page">
    <!-- ===================== 按合同汇总 ===================== -->
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
                 >
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
          <el-table-column label="已确认服务费(元)" width="130" align="right"><template #default="{ row }">{{ money(row.serviceFeeSum) }}</template></el-table-column>
          <el-table-column label="已确认佣金池(元)" width="130" align="right"><template #default="{ row }">{{ money(row.poolSum) }}</template></el-table-column>
          <el-table-column prop="lastEventTime" label="最近出库时间" width="160"><template #default="{ row }">{{ row.lastEventTime || '—' }}</template></el-table-column>
          <el-table-column label="合同有效期" width="210"><template #default="{ row }">{{ (row.startDate || '—') + ' ~ ' + (row.endDate || '—') }}</template></el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openAlloc(row)">佣金分配</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                       :total="summaryTotal" v-model:current-page="squery.pageNo"
                       v-model:page-size="squery.pageSize" :page-sizes="[10,20,50]" @change="loadSummary" />
        <p class="hint pager-hint">计佣订单数 = 该合同客户在合同有效期内、主仓的云仓(ERP)发货票数，来源于「云仓发货量同步」，不同客户使用各自的 ERP 数据；金额只计已确认订单，与服务费账单口径一致。</p>
      </div>

    <!-- 合同佣金分配:链路成员每单金额(仅 P4 可改) + 该合同已产生佣金 -->
    <el-dialog v-model="alloc.visible" :title="`佣金分配 · ${alloc.view?.contractNo || ''}`" width="min(760px, 96vw)">
      <template v-if="alloc.view">
        <el-alert v-if="!alloc.view.canEdit" type="info" :closable="false" class="mb"
                  title="只读模式:仅 P4(且该合同在其邀请体系内)可以设置分配,其他账号仅可查看。" show-icon />
        <el-descriptions :column="3" border size="small" class="mb">
          <el-descriptions-item label="客户">{{ alloc.view.customerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="成交伙伴">{{ alloc.view.partnerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="每单总佣金(元)">
            <el-input-number v-if="alloc.view.canEdit" v-model="alloc.form.totalPerOrder" :min="0" :step="0.001" :precision="3" :controls="false" size="small" style="width: 110px" />
            <span v-else>{{ alloc.view.totalPerOrder != null ? Number(alloc.view.totalPerOrder).toFixed(3) : '未配置' }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-table :data="alloc.form.members" size="small" border>
          <el-table-column prop="positionCode" label="岗位" width="70" align="center" />
          <el-table-column prop="name" label="成员" min-width="120">
            <template #default="{ row }">{{ row.name }}<el-tag v-if="row.isOwner" size="small" class="ml4" type="success">P4 本人</el-tag></template>
          </el-table-column>
          <el-table-column label="关系" width="90" align="center">
            <template #default="{ row }">{{ row.promoterId === alloc.view.partnerId ? '成交' : '上级' }}</template>
          </el-table-column>
          <el-table-column label="每单金额(元)" width="150" align="right">
            <template #default="{ row }">
              <el-input-number v-if="alloc.view.canEdit" v-model="row.amount" :min="0" :step="0.001" :precision="3" :controls="false" size="small" style="width: 110px" />
              <span v-else>{{ row.amount != null ? Number(row.amount).toFixed(3) : '—' }}</span>
            </template>
          </el-table-column>
        </el-table>
        <p class="hint alloc-sum">
          成员合计 {{ allocSum }} 元 / 每单总佣金 {{ allocTotal }} 元,
          公司保留 <span :class="{ neg: allocCompany < 0 }">{{ allocCompany }}</span> 元
          <template v-if="allocCompany < 0">(超出总佣金,无法保存)</template>。
          配置保存后只对之后产生的出库单生效,历史订单不重算。
        </p>

        <el-divider content-position="left">该合同已产生佣金(按成员)</el-divider>
        <el-table :data="alloc.commissions" size="small" border v-loading="alloc.commissionLoading">
          <el-table-column prop="promoterName" label="成员" min-width="110"><template #default="{ row }">{{ row.promoterName || '#' + row.promoterId }}</template></el-table-column>
          <el-table-column prop="positionCode" label="岗位" width="70" align="center" />
          <el-table-column prop="count" label="流水数" width="80" align="right" />
          <el-table-column label="合计(元)" width="110" align="right"><template #default="{ row }">{{ money(row.total) }}</template></el-table-column>
          <el-table-column label="冻结(元)" width="110" align="right"><template #default="{ row }">{{ money(row.frozen) }}</template></el-table-column>
          <el-table-column label="可结算(元)" width="110" align="right"><template #default="{ row }">{{ money(row.settleable) }}</template></el-table-column>
          <el-table-column label="已结算/已提现(元)" width="140" align="right"><template #default="{ row }">{{ money(row.settled) }}</template></el-table-column>
        </el-table>
        <p v-if="!alloc.commissionLoading && !alloc.commissions.length" class="hint">该合同还没有产生佣金流水。</p>
      </template>
      <template #footer>
        <template v-if="alloc.view?.canEdit">
          <el-button @click="alloc.visible = false">取消</el-button>
          <el-button type="primary" :loading="alloc.saving" :disabled="allocCompany < 0" @click="saveAlloc">保存分配</el-button>
        </template>
        <el-button v-else @click="alloc.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { mktOrderApi, mktPromoterApi, mktCustomerApi, mktWarehouseApi } from '@/api/marketing'
import { money } from '@/utils/format'

const SIGN = { 1: '园区签', 2: '云仓直签' }
const SVT = { 1: '仓储', 2: '代发', 3: '仓配' }
const FM = { 1: '仓租', 2: '单票', 3: '按件', 4: '包月' }

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

// ==================== 合同佣金分配 ====================
const alloc = reactive({ visible: false, view: null, saving: false, commissions: [], commissionLoading: false,
  form: { totalPerOrder: 0, members: [] } })
const fixed3 = (v) => Number(v || 0).toFixed(3)
const allocSum = computed(() => fixed3(alloc.form.members.reduce((a, m) => a + Number(m.amount || 0), 0)))
const allocTotal = computed(() => fixed3(alloc.form.totalPerOrder))
const allocCompany = computed(() => fixed3(Number(alloc.form.totalPerOrder || 0) - alloc.form.members.reduce((a, m) => a + Number(m.amount || 0), 0)))

async function openAlloc(row) {
  Object.assign(alloc, { visible: true, view: null, saving: false, commissions: [], commissionLoading: false, form: { totalPerOrder: 0, members: [] } })
  try {
    const view = await mktOrderApi.contractPricing({ contractId: row.contractId })
    alloc.view = view
    alloc.form.totalPerOrder = Number(view.totalPerOrder || 0)
    alloc.form.members = (view.chain || []).map(m => ({
      promoterId: m.promoterId, name: m.name, positionCode: m.positionCode,
      status: m.status, isOwner: m.isOwner, amount: Number(m.amountPerOrder || 0)
    }))
    if (view.canEdit && !view.configured) {
      // 未配置过:默认把总佣金全给 P4 本人,其他成员 0,由 P4 自行调整
      const owner = alloc.form.members.find(m => m.isOwner)
      if (owner) owner.amount = alloc.form.totalPerOrder
    }
  } catch (e) {
    ElMessage.error(e?.message || '加载佣金分配失败')
    alloc.visible = false
    return
  }
  alloc.commissionLoading = true
  try { alloc.commissions = await mktOrderApi.contractCommissions({ contractId: row.contractId }) }
  finally { alloc.commissionLoading = false }
}

async function saveAlloc() {
  if (!alloc.view?.canEdit || alloc.saving) return
  if (Number(allocCompany.value) < 0) return ElMessage.warning('成员合计超出每单总佣金')
  const owner = alloc.form.members.find(m => m.isOwner)
  const beneficiaries = alloc.form.members
    .filter(m => !m.isOwner && Number(m.amount || 0) > 0)
    .map(m => ({ promoterId: m.promoterId, amountPerOrder: Number(m.amount) }))
  alloc.saving = true
  try {
    const view = await mktOrderApi.saveContractPricing({
      contractId: alloc.view.contractId,
      totalPerOrder: Number(alloc.form.totalPerOrder || 0),
      ownerPerOrder: Number(owner?.amount || 0),
      beneficiaries
    })
    alloc.view = view
    ElMessage.success('佣金分配已保存,之后的出库单按新分配计佣')
  } finally { alloc.saving = false }
}

onMounted(() => { loadSummary(); loadOptions() })
</script>

<style scoped>
.hint { color: var(--el-text-color-secondary); font-size: 12px; }
.basis-note { color: var(--el-text-color-regular); font-size: 12px; margin-top: 4px; }
.mb { margin-bottom: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.pager-hint { margin-top: 8px; }
.dim { color: var(--el-text-color-placeholder); }
.ml4 { margin-left: 4px; }
.alloc-sum { margin-top: 8px; }
.neg { color: var(--el-color-danger); }
</style>
