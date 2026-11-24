<template>
  <div class="page-container mkt-page">
    <el-alert
      type="info"
      show-icon
      :closable="false"
      class="mb"
      title="计佣双轨口径：园区签合同按出库单 / 云山每日发货票数计佣并纳入园区服务费账单；云仓直签合同仅按实收平台费计佣，出库票数仅作运营统计，不重复生成出库佣金。"
    />

    <!-- ===================== 按合同汇总 ===================== -->
    <div class="search-bar">
      <el-form :inline="true" :model="squery">
        <el-form-item label="合同号"><el-input v-model="squery.contractNo" clearable style="width: 150px" /></el-form-item>
        <el-form-item label="伙伴">
          <el-select v-model="squery.promoterId" placeholder="全部" clearable filterable style="width: 160px">
            <el-option v-for="p in promoters" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户">
          <el-select v-model="squery.customerId" placeholder="全部" clearable filterable style="width: 160px">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="云仓">
          <el-select v-model="squery.warehouseId" placeholder="全部" clearable filterable style="width: 160px">
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
      <el-table :data="summaryList" v-loading="summaryLoading" border stripe>
        <el-table-column prop="contractNo" label="合同号" min-width="155">
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
        <el-table-column label="已确认票数" width="100" align="right"><template #default="{ row }">{{ row.confirmedCount ?? 0 }}</template></el-table-column>
        <el-table-column label="已确认服务费(元)" width="130" align="right"><template #default="{ row }">{{ money(row.serviceFeeSum) }}</template></el-table-column>
        <el-table-column label="已确认佣金池(元)" width="130" align="right"><template #default="{ row }">{{ money(row.poolSum) }}</template></el-table-column>
        <el-table-column prop="lastEventTime" label="最近出库时间" width="160"><template #default="{ row }">{{ row.lastEventTime || '—' }}</template></el-table-column>
        <el-table-column label="合同有效期" width="210"><template #default="{ row }">{{ (row.startDate || '—') + ' ~ ' + (row.endDate || '—') }}</template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openAlloc(row)">佣金分配</el-button>
            <el-button link type="primary" @click.stop="openOrderDrawer(row)">订单明细 ›</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="summaryTotal" v-model:current-page="squery.pageNo"
                     v-model:page-size="squery.pageSize" :page-sizes="[10,20,50]" @change="loadSummary" />
      <p class="hint pager-hint">计佣订单数 = 该合同客户在合同有效期内、主仓的云仓(ERP)发货票数；已确认票数 = 已生成佣金的票数（云山每个发货日生成一条 YS- 汇总订单代表当天多票，通用 ERP 按单笔事件入账）；点击「订单明细 ›」可逐笔核查分佣拆分并对异常未提现订单执行作废冲正。</p>
    </div>

    <!-- ===================== 逐笔计佣订单明细与冲正侧滑抽屉 ===================== -->
    <el-drawer
      v-model="orderDrawer.visible"
      :title="`计佣订单明细 · ${orderDrawer.contract?.contractNo || ''}`"
      size="min(940px, 96vw)"
    >
      <template v-if="orderDrawer.contract">
        <div class="drawer-head-bar mb">
          <div class="sub">
            客户：<b>{{ orderDrawer.contract.customerName || '—' }}</b>
            · 签约方式：<el-tag size="small" :type="orderDrawer.contract.signMode === 2 ? 'warning' : 'info'">{{ SIGN[orderDrawer.contract.signMode] || '—' }}</el-tag>
            · 主仓：{{ orderDrawer.contract.warehouseName || '—' }}
          </div>
          <div class="drawer-filters">
            <el-input v-model="orderDrawer.sourceNo" clearable size="small" placeholder="筛选单号 (如 YS-)" style="width: 160px" @keyup.enter="searchContractOrders" />
            <el-select v-model="orderDrawer.status" clearable size="small" placeholder="全部状态" style="width: 120px" @change="searchContractOrders">
              <el-option v-for="(label, val) in ORDER_ST" :key="val" :label="label" :value="Number(val)" />
            </el-select>
            <el-button size="small" type="primary" @click="searchContractOrders">筛选</el-button>
          </div>
        </div>

        <el-table :data="orderDrawer.list" v-loading="orderDrawer.loading" border stripe size="small" row-key="id">
          <el-table-column prop="sourceNo" label="计佣单号" min-width="175">
            <template #default="{ row }">
              <div>
                <code>{{ row.sourceNo || `#${row.id}` }}</code>
                <el-tag v-if="String(row.sourceNo || '').startsWith('YS-')" size="small" type="info" class="ml4">云山日汇</el-tag>
              </div>
              <div v-if="row.remark" class="sub">备注：{{ row.remark }}</div>
            </template>
          </el-table-column>
          <el-table-column label="来源类型" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="row.sourceType === 2 ? 'primary' : 'warning'">
                {{ SOURCE_TYPE[row.sourceType] || `类型${row.sourceType}` }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="计佣票数" width="90" align="right">
            <template #default="{ row }">{{ row.commissionOrderCount ?? 1 }}</template>
          </el-table-column>
          <el-table-column label="服务费 / 基数(元)" width="130" align="right">
            <template #default="{ row }">{{ money(row.serviceFee ?? row.baseAmount ?? 0) }}</template>
          </el-table-column>
          <el-table-column label="佣金池(元)" width="110" align="right">
            <template #default="{ row }">{{ row.poolAmount != null ? Number(row.poolAmount).toFixed(3) : '0.000' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="95">
            <template #default="{ row }">
              <el-tag size="small" :type="orderStatusType(row.status)">{{ ORDER_ST[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="eventTime" label="业务发生时间" width="160">
            <template #default="{ row }">{{ row.eventTime || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="165" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="toggleSplits(row)">
                {{ expandedOrderId === row.id ? '收起分佣' : '分佣明细' }}
              </el-button>
              <el-button
                v-if="row.status === 2 && !hasWithdrawnSplit(row.id)"
                link
                type="danger"
                @click="openVoidDialog(row)"
              >
                作废冲正
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 当前展开订单的分佣拆分明细面板 -->
        <div v-if="expandedOrderId != null" class="splits-box mt12" v-loading="splitsLoading">
          <div class="splits-head">
            <span>订单 <code>{{ currentExpandedOrder?.sourceNo || `#${expandedOrderId}` }}</code> 的分佣拆分明细</span>
            <el-button link size="small" @click="expandedOrderId = null">收起</el-button>
          </div>
          <el-table :data="splitsCache[expandedOrderId] || []" size="small" border>
            <el-table-column prop="promoterId" label="伙伴编号" width="100">
              <template #default="{ row }">#{{ row.promoterId }}</template>
            </el-table-column>
            <el-table-column prop="positionCode" label="岗位" width="80" align="center" />
            <el-table-column label="方向" width="95" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.sign === -1 ? 'danger' : 'success'">
                  {{ row.sign === -1 ? '冲正扣回' : '正向计佣' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="拆分金额(元)" width="120" align="right">
              <template #default="{ row }">
                <span :class="{ neg: row.sign === -1 }">{{ Number(row.amount || 0).toFixed(3) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="佣金状态" width="110">
              <template #default="{ row }">{{ COMM_ST[row.status] || row.status }}</template>
            </el-table-column>
            <el-table-column label="提现 / 作废说明" min-width="160">
              <template #default="{ row }">
                <span v-if="row.withdrawalId">关联提现单 #{{ row.withdrawalId }}</span>
                <span v-else-if="row.voidReason" class="sub">{{ row.voidReason }}</span>
                <span v-else class="sub">未提现</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <el-pagination
          class="pager"
          small
          background
          layout="total, prev, pager, next"
          :total="orderDrawer.total"
          v-model:current-page="orderDrawer.pageNo"
          :page-size="orderDrawer.pageSize"
          @current-change="loadContractOrders"
        />
      </template>
    </el-drawer>

    <!-- 作废冲正必填原因弹窗 -->
    <el-dialog v-model="voidDialog.visible" title="计佣订单作废冲正" width="min(460px, 92vw)">
      <el-alert
        type="warning"
        show-icon
        :closable="false"
        class="mb"
        title="作废确认后：未结算佣金直接作废，已结算未提现佣金自动生成负向扣回流水；已进入服务费账单或已提现订单将由后端拦截。"
      />
      <p class="sub mb">目标单号：<code>{{ voidDialog.row?.sourceNo || `#${voidDialog.row?.id}` }}</code></p>
      <el-form label-position="top">
        <el-form-item label="作废冲正原因" required>
          <el-input v-model="voidDialog.reason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="请填写作废冲正原因（必填，写入审计日志）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="voidDialog.busy" @click="voidDialog.visible = false">取消</el-button>
        <el-button type="danger" :loading="voidDialog.busy" @click="submitVoidOrder">确认作废冲正</el-button>
      </template>
    </el-dialog>

    <!-- 合同佣金分配:按每单具体金额配置(后台仅超级管理员丁超、杨洪可修改) + 该合同已产生佣金 -->
    <el-dialog v-model="alloc.visible" :title="`佣金分配 · ${alloc.view?.contractNo || ''}`" width="min(760px, 96vw)">
      <template v-if="alloc.view">
        <el-alert v-if="!alloc.view.canEdit" type="warning" :closable="false" class="mb"
                  :title="`只读模式：${alloc.view.readOnlyReason || '后台仅超级管理员（丁超、杨洪）可修改佣金分配，当前账号仅可查看。'}`" show-icon />
        <el-descriptions :column="2" border size="small" class="mb">
          <el-descriptions-item label="客户">{{ alloc.view.customerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="成交伙伴">{{ alloc.view.partnerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="每单总佣金(元)">
            <template v-if="alloc.view.totalPerOrderLocked">
              <span>{{ fixed3(alloc.form.totalPerOrder) }}</span>
              <el-tag size="small" type="success" class="ml4">云仓确认返点·锁定</el-tag>
            </template>
            <el-input-number v-else-if="alloc.view.canEdit" v-model="alloc.form.totalPerOrder" :min="0" :step="0.001" :precision="3" :controls="false" size="small" style="width: 120px" />
            <span v-else>{{ alloc.view.totalPerOrder != null ? Number(alloc.view.totalPerOrder).toFixed(3) : '未配置' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="平台服务费">{{ PLATFORM_PCT }}%（{{ allocServiceFee }} 元/单）</el-descriptions-item>
          <el-descriptions-item label="业务员佣金（P0）">
            <span>{{ fixed3(allocSalespersonFee) }} 元/单（固定）</span>
            <span class="sub ml4">{{ alloc.view.salespersonName ? `· ${alloc.view.salespersonName}` : '· 待云仓分配业务员' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="成员可分配上限">{{ allocMaxMember }} 元/单</el-descriptions-item>
        </el-descriptions>

        <el-table :data="alloc.form.members" size="small" border>
          <el-table-column prop="positionCode" label="岗位" width="70" align="center" />
          <el-table-column prop="name" label="成员" min-width="140">
            <template #default="{ row }">{{ row.name }}<el-tag v-if="row.isOwner" size="small" class="ml4" type="success">P4 本人</el-tag><el-tag v-if="row.former || row.added" size="small" class="ml4" type="warning">额外分成成员</el-tag></template>
          </el-table-column>
          <el-table-column label="关系" width="90" align="center">
            <template #default="{ row }">{{ row.former || row.added ? '团队成员' : row.promoterId === alloc.view.partnerId ? '成交' : '上级' }}</template>
          </el-table-column>
          <el-table-column label="每单金额(元)" width="170" align="right">
            <template #default="{ row }">
              <el-input-number v-if="alloc.view.canEdit" v-model="row.amountPerOrder" :min="0" :max="allocMaxMemberNumber" :step="0.001" :precision="3" :controls="false" size="small" style="width: 130px" />
              <span v-else>{{ fixed3(row.amountPerOrder) }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="alloc.view.canEdit" label="操作" width="70" align="center">
            <template #default="{ row }"><el-button v-if="!row.isOwner && (row.former || row.added)" link type="danger" @click="removeMember(row)">移除</el-button></template>
          </el-table-column>
        </el-table>
        <div v-if="alloc.view.canEdit" class="add-member">
          <el-select v-model="alloc.addId" filterable clearable placeholder="添加本人团队里的其他成员参与分佣（最多 8 人）" style="width: 320px" :disabled="!addableMembers.length || alloc.form.members.length >= MAX_SPLIT_MEMBERS">
            <el-option v-for="m in addableMembers" :key="m.id" :label="`${m.name || '未命名伙伴'} · ${m.positionCode}${m.phoneTail ? ' · 尾号' + m.phoneTail : ''}（#${m.id}）`" :value="m.id" />
          </el-select>
          <el-button type="primary" plain :disabled="!alloc.addId || alloc.form.members.length >= MAX_SPLIT_MEMBERS" @click="addMember">添加成员</el-button>
          <span v-if="alloc.form.members.length >= MAX_SPLIT_MEMBERS" class="hint">已达单合同最多 {{ MAX_SPLIT_MEMBERS }} 人分佣上限。</span>
          <span v-else-if="!addableMembers.length" class="hint">本人团队里暂无可添加的其他正常成员。</span>
        </div>
        <p class="hint alloc-sum">
          成员分配合计 <span :class="{ neg: allocOver }">{{ allocSum }} 元/单</span>（上限 {{ allocMaxMember }} 元/单，最多 {{ MAX_SPLIT_MEMBERS }} 人参与分佣），
          每单总佣金 {{ allocTotal }} 元，平台服务费 {{ allocServiceFee }} 元（{{ PLATFORM_PCT }}%），业务员佣金（P0）{{ fixed3(allocSalespersonFee) }} 元，剩余未分配 <span :class="{ neg: allocOver }">{{ allocUnallocated }}</span> 元
          <template v-if="allocOver">（成员分配金额超过可分配上限，无法保存）</template>。
          与小程序口径一致，均按每单具体金额（精确到 0.001 元）配置。配置保存后只对之后产生的出库单生效，历史订单不重算；成员被调整层级前签的合同会保留其原有分成。
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
          <el-button type="primary" :loading="alloc.saving" :disabled="allocOver || !(Number(alloc.form.totalPerOrder) > 0)" @click="saveAlloc">保存分配</el-button>
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
const SOURCE_TYPE = { 1: '园区租赁', 2: '出库单', 3: '直签平台费', 4: '入仓签约奖' }
const ORDER_ST = { 1: '待确认', 2: '已确认', 3: '已退款', 4: '已作废' }
const COMM_ST = { 1: '冻结中', 2: '可结算', 3: '已结算', 4: '已提现', 5: '已作废' }
const MAX_SPLIT_MEMBERS = 8

const orderStatusType = (s) => ({ 1: 'warning', 2: 'success', 3: 'info', 4: 'danger' }[s] || 'info')

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

// ==================== 逐笔订单明细抽屉与作废冲正 ====================
const orderDrawer = reactive({
  visible: false,
  loading: false,
  contract: null,
  sourceNo: '',
  status: null,
  pageNo: 1,
  pageSize: 15,
  list: [],
  total: 0
})
const expandedOrderId = ref(null)
const splitsLoading = ref(false)
const splitsCache = reactive({})
const currentExpandedOrder = computed(() => orderDrawer.list.find(o => o.id === expandedOrderId.value) || null)

function hasWithdrawnSplit(orderId) {
  const splits = splitsCache[orderId]
  if (!Array.isArray(splits)) return false
  return splits.some(s => s.withdrawalId != null || s.status === 4)
}

async function openOrderDrawer(contractRow) {
  Object.assign(orderDrawer, {
    visible: true,
    contract: contractRow,
    sourceNo: '',
    status: null,
    pageNo: 1,
    list: [],
    total: 0
  })
  expandedOrderId.value = null
  await loadContractOrders()
}

async function loadContractOrders() {
  if (!orderDrawer.contract) return
  const sourceId = orderDrawer.contract.contractId ?? orderDrawer.contract.id
  orderDrawer.loading = true
  try {
    const res = await mktOrderApi.page({
      pageNo: orderDrawer.pageNo,
      pageSize: orderDrawer.pageSize,
      sourceId,
      contractNo: orderDrawer.contract.contractNo || undefined,
      sourceNo: orderDrawer.sourceNo.trim() || undefined,
      status: orderDrawer.status || undefined
    })
    const records = res?.records || []
    orderDrawer.list = sourceId != null
      ? records.filter(r => r.sourceId == null || String(r.sourceId) === String(sourceId))
      : records
    orderDrawer.total = Number(res?.total ?? orderDrawer.list.length)
  } finally {
    orderDrawer.loading = false
  }
}

function searchContractOrders() {
  orderDrawer.pageNo = 1
  expandedOrderId.value = null
  return loadContractOrders()
}

async function toggleSplits(orderRow) {
  if (expandedOrderId.value === orderRow.id) {
    expandedOrderId.value = null
    return
  }
  expandedOrderId.value = orderRow.id
  if (splitsCache[orderRow.id]) return
  splitsLoading.value = true
  try {
    const rows = await mktOrderApi.splits(orderRow.id)
    splitsCache[orderRow.id] = Array.isArray(rows) ? rows : []
  } finally {
    splitsLoading.value = false
  }
}

const voidDialog = reactive({ visible: false, row: null, reason: '', busy: false })

async function openVoidDialog(orderRow) {
  if (!splitsCache[orderRow.id] && mktOrderApi.splits) {
    try {
      const rows = await mktOrderApi.splits(orderRow.id)
      splitsCache[orderRow.id] = Array.isArray(rows) ? rows : []
    } catch { /* ignore precheck failure */ }
  }
  if (hasWithdrawnSplit(orderRow.id)) {
    return ElMessage.warning('该订单已有提现关联佣金，不能直接作废冲正')
  }
  Object.assign(voidDialog, { visible: true, row: orderRow, reason: '', busy: false })
}

async function submitVoidOrder() {
  if (voidDialog.busy || !voidDialog.row) return
  const reason = voidDialog.reason.trim()
  if (!reason) return ElMessage.warning('请填写作废冲正原因')
  voidDialog.busy = true
  try {
    const targetId = voidDialog.row.id
    await mktOrderApi.void(targetId, { reason })
    delete splitsCache[targetId]
    if (expandedOrderId.value === targetId) expandedOrderId.value = null
    voidDialog.visible = false
    ElMessage.success('订单已作废冲正')
    await Promise.all([loadContractOrders(), loadSummary()])
  } catch (e) {
    ElMessage.error(e?.message || '作废冲正失败')
  } finally {
    voidDialog.busy = false
  }
}

// ==================== 合同佣金分配（按每单具体金额） ====================
const alloc = reactive({ visible: false, view: null, saving: false, commissions: [], commissionLoading: false, addId: null,
  form: { totalPerOrder: 0, members: [] } })
// P4 团队里还没出现在分成表里的正常成员，可以加进来分佣
const addableMembers = computed(() => {
  const used = new Set(alloc.form.members.map(m => String(m.promoterId)))
  return (alloc.view?.teamMembers || []).filter(m => !used.has(String(m.id)))
})
function addMember() {
  if (alloc.form.members.length >= MAX_SPLIT_MEMBERS) {
    return ElMessage.warning(`单个合同最多支持 ${MAX_SPLIT_MEMBERS} 人参与分佣`)
  }
  const m = (alloc.view?.teamMembers || []).find(x => String(x.id) === String(alloc.addId))
  if (!m) return
  alloc.form.members.push({ promoterId: m.id, name: m.name || '未命名伙伴', positionCode: m.positionCode, status: 1, isOwner: false, former: false, added: true, amountPerOrder: 0 })
  alloc.addId = null
}
function removeMember(row) { alloc.form.members = alloc.form.members.filter(m => m !== row) }
const fixed3 = (v) => Number(v || 0).toFixed(3)
const toMills = (v) => Math.round(Number(v || 0) * 1000)
const round3 = (v) => Math.round(Number(v || 0) * 1000) / 1000
// 平台服务费精确为 10%，业务员（P0）固定 0.020 元/单，其余金额按 0.001 元精度直接填写。
const PLATFORM_PCT = 10
const SALESPERSON_FIXED_FEE = 0.02
const allocSalespersonFee = computed(() => Number(alloc.view?.salespersonCommissionPerOrder ?? SALESPERSON_FIXED_FEE))
const totalMills = computed(() => toMills(alloc.form.totalPerOrder))
const serviceFeeUnits = computed(() => totalMills.value) // 10% of 0.001 = 0.0001
const salespersonFeeUnits = computed(() => Math.round(allocSalespersonFee.value * 10000))
const allocMaxMemberUnits = computed(() => totalMills.value * 10 - serviceFeeUnits.value - salespersonFeeUnits.value)
const allocMaxMemberNumber = computed(() => Math.max(0, Math.floor(allocMaxMemberUnits.value / 10)) / 1000)
const allocMaxMember = computed(() => allocMaxMemberNumber.value.toFixed(3))
const allocSumMills = computed(() => alloc.form.members.reduce((a, m) => a + toMills(m.amountPerOrder), 0))
const allocSum = computed(() => (allocSumMills.value / 1000).toFixed(3))
const allocTotal = computed(() => fixed3(alloc.form.totalPerOrder))
const allocServiceFee = computed(() => (serviceFeeUnits.value / 10000).toFixed(4))
const allocUnallocatedUnits = computed(() => allocMaxMemberUnits.value - allocSumMills.value * 10)
const allocUnallocated = computed(() => (allocUnallocatedUnits.value / 10000).toFixed(4))
const allocOver = computed(() => allocMaxMemberUnits.value < 0 || allocUnallocatedUnits.value < 0)

async function openAlloc(row) {
  Object.assign(alloc, { visible: true, view: null, saving: false, commissions: [], commissionLoading: false, addId: null, form: { totalPerOrder: 0, members: [] } })
  try {
    const view = await mktOrderApi.contractPricing({ contractId: row.contractId })
    alloc.view = view
    alloc.form.totalPerOrder = Number(view.totalPerOrder ?? view.lockedTotalPerOrder ?? 0)
    alloc.form.members = (view.chain || []).map(m => ({
      promoterId: m.promoterId, name: m.name, positionCode: m.positionCode,
      status: m.status, isOwner: !!m.isOwner || (view.ownerPromoterId != null && m.promoterId === view.ownerPromoterId), former: !!m.former,
      amountPerOrder: Number(m.amountPerOrder || 0)
    }))
    if (view.canEdit && !view.configured) {
      // 未配置过：扣除 10% 平台服务费和 0.020 元业务员佣金后的可分配金额默认给 P4 本人，其余成员为 0。
      const owner = alloc.form.members.find(m => m.isOwner)
      if (owner) owner.amountPerOrder = allocMaxMemberNumber.value
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
  if (!(Number(alloc.form.totalPerOrder) > 0)) return ElMessage.warning('请先填写每单总佣金')
  if (allocMaxMemberUnits.value < 0) return ElMessage.warning('每单总佣金不足以覆盖 10% 平台服务费和 0.020 元/单业务员佣金')
  if (allocOver.value) return ElMessage.warning(`成员分配金额合计不能超过 ${allocMaxMember.value} 元/单（已扣除 ${PLATFORM_PCT}% 平台服务费及 ${fixed3(allocSalespersonFee.value)} 元/单业务员佣金）`)
  const owner = alloc.form.members.find(m => m.isOwner)
  const beneficiaries = alloc.form.members
    .filter(m => !m.isOwner && round3(m.amountPerOrder) > 0)
    .map(m => ({ promoterId: m.promoterId, amountPerOrder: round3(m.amountPerOrder) }))
  alloc.saving = true
  try {
    const view = await mktOrderApi.saveContractPricing({
      contractId: alloc.view.contractId,
      totalPerOrder: round3(alloc.form.totalPerOrder),
      ownerPerOrder: owner ? round3(owner.amountPerOrder) : 0,
      beneficiaries
    })
    alloc.view = view
    ElMessage.success('佣金分配已保存，之后的出库单按新分配计佣')
  } finally { alloc.saving = false }
}

onMounted(() => { loadSummary(); loadOptions() })

defineExpose({
  alloc,
  orderDrawer,
  voidDialog,
  expandedOrderId,
  splitsCache,
  addableMembers,
  allocSum,
  allocMaxMember,
  allocSalespersonFee,
  allocServiceFee,
  allocUnallocated,
  allocOver,
  openAlloc,
  addMember,
  removeMember,
  saveAlloc,
  openOrderDrawer,
  toggleSplits,
  openVoidDialog,
  submitVoidOrder
})
</script>

<style scoped>
.hint { color: var(--el-text-color-secondary); font-size: 12px; }
.sub { color: var(--el-text-color-secondary); font-size: 12px; }
.basis-note { color: var(--el-text-color-regular); font-size: 12px; margin-top: 4px; }
.mb { margin-bottom: 12px; }
.mt12 { margin-top: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.pager-hint { margin-top: 8px; }
.dim { color: var(--el-text-color-placeholder); }
.ml4 { margin-left: 4px; }
.alloc-sum { margin-top: 8px; }
.add-member { display: flex; align-items: center; gap: 8px; margin-top: 10px; flex-wrap: wrap; }
.neg { color: var(--el-color-danger); }
.drawer-head-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.drawer-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.splits-box {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--bg-subtle, #f8fafc);
}
.splits-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
}
</style>
