<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">应收(含滞纳金)</div>
        <div class="stat-value">¥{{ money(stats.receivable) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">实收</div>
        <div class="stat-value success">¥{{ money(stats.received) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">需收</div>
        <div class="stat-value warning">¥{{ money(stats.needReceive) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">滞纳金</div>
        <div class="stat-value danger">¥{{ money(stats.lateFee) }}</div>
      </div>
    </div>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="账单号">
          <el-input v-model="query.code" placeholder="请输入账单号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="费用类型">
          <el-select v-model="query.feeType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="t in feeTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="方向">
          <el-select v-model="query.direction" placeholder="全部" clearable style="width: 120px">
            <el-option label="收款" :value="1" />
            <el-option label="付款" :value="2" />
          </el-select>
        </el-form-item>
        <!-- 按租客查账是台账最常用的入口(负责人 2026-09-04 要求把这一栏从「来源」换过来)。
             数据源用租客档案而不是收银台的 payable-tenants:后者只含有欠款账单的租客,
             全部结清的租客会从下拉里消失,反而查不到他的历史账单。来源仍在表格里显示 -->
        <el-form-item label="对方租客">
          <el-select v-model="query.tenantRefId" placeholder="全部" clearable filterable style="width: 220px">
            <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="只看到期">
          <el-switch v-model="query.onlyDue" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <!-- 这里原来放的是「重置」,但它做的是作废账单,不是清筛选条件。
               筛选栏里紧挨查询的位置只能放清筛选,破坏性操作已挪到上方工具条 -->
          <el-button @click="clearFilters">清空筛选</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区 -->
    <div class="table-card">
      <!-- 从流水/收据/发票/收款通知跳来时的定位提示。不给提示的话用户会以为账单只剩一条 -->
      <el-alert
        v-if="locatedBillId"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      >
        <template #title>
          仅显示关联账单 #{{ locatedBillId }}
          <el-button link type="primary" @click="clearLocate">显示全部账单</el-button>
        </template>
      </el-alert>
      <div class="toolbar">
        <el-button type="warning" @click="calcLateFee"><el-icon><Money /></el-icon>计算滞纳金</el-button>
        <!-- 原来叫「重置」且摆在筛选栏里,和收支流水页「清筛选」那个重置同名同位,
             实际做的却是作废账单。改名 + 挪到动作区,避免点错 -->
        <el-button type="danger" plain @click="resetPushedBills">重置推送账单</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="code" label="账单号" min-width="150" />
        <!-- 租客与协议编号合并成双行:两列平铺要 300px,表格就得横向滚动、
             右侧固定列会盖住内容;合并后 180px 且信息一个不少 -->
        <el-table-column prop="tenantName" label="对方租客 / 协议编号" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="cell-main">{{ row.tenantName || `租客 #${row.tenantRefId || '-'}` }}</div>
            <div v-if="row.agreementNo" class="cell-sub">{{ row.agreementNo }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="feeType" label="费用类型" width="100" />
        <el-table-column prop="source" label="来源" width="110">
          <template #default="{ row }">
            <el-tag :type="row.source === '应收登记表' ? 'success' : 'info'" effect="plain">
              {{ row.source || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="应收" width="120" align="right">
          <template #default="{ row }">¥{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="实收" width="120" align="right">
          <template #default="{ row }">¥{{ money(row.paidAmount) }}</template>
        </el-table-column>
        <el-table-column label="账期 / 应收日" width="200">
          <template #default="{ row }">
            <div class="cell-main">{{ row.periodStart || '-' }} ~ {{ row.periodEnd || '-' }}</div>
            <div class="cell-sub">应收日 {{ row.dueDate || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="逾期天数" width="90" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.overdueDays > 0 ? '#e5484d' : '' }">{{ row.overdueDays || 0 }}</span>
          </template>
        </el-table-column>
        <!-- 状态与操作一起固定右侧:列多时横向滚动会让状态被固定的操作列压住看不见。
             固定列必须连续,故把状态挪到逾期天数之后 -->
        <el-table-column label="状态" width="100" fixed="right">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <!-- 免租期账单应收就是 0,没钱可收:给「核销」而不是「收款」,
                 否则收款弹窗里填什么金额都会被后端拒(0 不让收、0.01 又超额) -->
            <el-button v-if="canWriteOff(row)" link type="warning" @click="confirmWriteOff(row)">核销</el-button>
            <el-button v-else link type="primary" @click="openPay(row)">收款</el-button>
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="success" @click="openInvoice(row)">开票</el-button>
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

    <!-- 收款弹窗 -->
    <el-dialog v-model="payDialog.visible" title="收款" width="440px">
      <el-form :model="payForm" label-width="90px" ref="payFormRef" :rules="payRules">
        <el-form-item label="账单号">
          <el-input :model-value="payDialog.bill?.code" disabled />
        </el-form-item>
        <el-form-item label="应收">
          <el-input :model-value="money(payDialog.bill?.amount)" disabled />
        </el-form-item>
        <el-form-item label="滞纳金" v-if="Number(payDialog.bill?.lateFee) > 0">
          <el-input :model-value="money(payDialog.bill?.lateFee)" disabled />
        </el-form-item>
        <el-form-item label="已收">
          <el-input :model-value="money(payDialog.bill?.paidAmount)" disabled />
        </el-form-item>
        <el-form-item label="本次收款" prop="amount">
          <!-- min 曾是 0.01:免租期账单剩余应收为 0 时,输入框把金额顶到 0.01,
               后端「不得超过剩余应收」再拒一次,账单就永远结不掉。放开到 0 并锁上限 -->
          <el-input-number v-model="payForm.amount" :min="0" :max="payMax" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="支付方式" prop="payMethod">
          <el-select v-model="payForm.payMethod" placeholder="请选择" style="width: 100%">
            <el-option v-for="m in payMethods" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="payDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitPay">确认收款</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗(收款记录) -->
    <el-dialog v-model="detailDialog.visible" title="账单详情 - 收款记录" width="640px">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="账单号">{{ detailDialog.bill?.code }}</el-descriptions-item>
        <el-descriptions-item label="对方租客">{{ detailDialog.bill?.tenantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="协议编号">{{ detailDialog.bill?.agreementNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="费用类型">{{ detailDialog.bill?.feeType }}</el-descriptions-item>
        <el-descriptions-item label="应收">¥{{ money(detailDialog.bill?.amount) }}</el-descriptions-item>
        <el-descriptions-item label="实收">¥{{ money(detailDialog.bill?.paidAmount) }}</el-descriptions-item>
        <el-descriptions-item label="账期">{{ detailDialog.bill?.periodStart || '-' }} ~ {{ detailDialog.bill?.periodEnd || '-' }}</el-descriptions-item>
        <el-descriptions-item label="应收日">{{ detailDialog.bill?.dueDate || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="payRecords" border stripe size="small">
        <el-table-column prop="payNo" label="支付流水号" min-width="180" />
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">
            <span :class="{ reversal: Number(row.amount) < 0 }">¥{{ money(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="payMethod" label="方式" width="90" />
        <!-- 红冲留痕:撤销不删记录,原单标「已撤销」、另有一张负额红冲单,谁在什么时候撤的都查得到 -->
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.voidStatus === 1" type="info" effect="plain">已撤销</el-tag>
            <el-tag v-else-if="row.voidStatus === 2" type="danger" effect="plain">红冲单</el-tag>
            <el-tag v-else type="success" effect="plain">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="payTime" label="收款时间" min-width="170" />
        <el-table-column label="操作" width="80" v-if="capabilities.paymentVoid">
          <template #default="{ row }">
            <el-button v-if="!row.voidStatus" link type="danger" @click="confirmVoid(row)">撤销</el-button>
            <span v-else class="void-note">{{ row.voidReason || '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 开票弹窗 -->
    <el-dialog v-model="invoiceDialog.visible" title="开具发票" width="480px">
      <el-form :model="invoiceForm" label-width="90px" ref="invoiceFormRef" :rules="invoiceRules">
        <el-form-item label="发票抬头" prop="title">
          <el-input v-model="invoiceForm.title" />
        </el-form-item>
        <el-form-item label="税号">
          <el-input v-model="invoiceForm.taxNo" />
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="invoiceForm.amount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="发票类型">
          <el-select v-model="invoiceForm.invoiceType" style="width: 100%">
            <el-option label="普票" value="普票" />
            <el-option label="专票" value="专票" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="invoiceDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitInvoice">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { billApi, paymentApi, invoiceApi } from '@/api/finance'
import { tenantApi } from '@/api/tenant'
import { billOwe } from './cashierModel'

// 保证金按实际费用类型拆分:登记表生成的就是「租金保证金/物业保证金」两类
const feeTypes = ['租金', '物业费', '租金保证金', '物业保证金', '能源费', '服务费', '一次性']
// 账单来源。'应收登记表' = 由应收明细登记表生成(带协议编号与登记明细口径的租客名)
const payMethods = ['现金', '转账', 'POS', '微信', '支付宝', '聚合']
const statusMap = {
  1: { label: '草稿', type: 'info' },
  2: { label: '待审核', type: 'warning' },
  3: { label: '待收付', type: 'primary' },
  4: { label: '部分结清', type: 'warning' },
  5: { label: '已结清', type: 'success' },
  6: { label: '逾期', type: 'danger' },
  7: { label: '退款中', type: 'warning' },
  8: { label: '作废', type: 'info' }
}

function money(v) {
  if (v == null) return '0.00'
  return Number(v).toFixed(2)
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const stats = reactive({ receivable: 0, received: 0, needReceive: 0, lateFee: 0, overdueCount: 0 })
const EMPTY_FILTERS = { code: '', feeType: null, status: null, direction: null, tenantRefId: null, billId: null, onlyDue: false }
const query = reactive({ pageNo: 1, pageSize: 10, ...EMPTY_FILTERS })
// 租客下拉用档案全量,结清的租客也要能查到他的历史账单
const tenants = ref([])

const router = useRouter()
// 从流水/收据/发票/收款通知点「关联账单」跳来时,url 上带 billId,把列表筛成那一张
const route = useRoute()
const locatedBillId = ref(null)
function applyRouteLocate() {
  const raw = Number(route.query.billId)
  if (!Number.isFinite(raw) || raw <= 0) return
  query.billId = raw
  query.pageNo = 1
  locatedBillId.value = raw
}
function clearLocate() {
  query.billId = null
  locatedBillId.value = null
  query.pageNo = 1
  const rest = { ...route.query }
  delete rest.billId
  router.replace({ path: route.path, query: rest })
  load()
}

async function load() {
  loading.value = true
  try {
    const res = await billApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
async function loadStats() {
  const res = await billApi.stats()
  Object.assign(stats, res)
}
/**
 * 重置账单:推送口径出错时的重来通道。
 * 作废所有「由应收明细登记表推送、且未收款未开票」的账单(已收款/已开票保留),
 * 之后回登记表逐行点「生成账单」重新推送即可。
 */
async function resetPushedBills() {
  try {
    await ElMessageBox.confirm(
      '将作废所有由应收明细登记表推送、且未收款未开票的账单(已收款/已开票的保留)。重置后请回登记表重新点「生成账单」推送。确定重置?',
      '重置账单',
      { type: 'warning', confirmButtonText: '确定重置', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  const res = await billApi.reset()
  ElMessage.success(`已重置:作废 ${res.deleted} 张账单,保留 ${res.kept} 张(已收款/已开票)`)
  Object.assign(query, { pageNo: 1, ...EMPTY_FILTERS })
  locatedBillId.value = null
  refresh()
}
// 查询永远回第 1 页:留在第 3 页换条件多半是一屏空白
function search() {
  query.pageNo = 1
  return load()
}
function clearFilters() {
  Object.assign(query, { pageNo: 1, ...EMPTY_FILTERS })
  locatedBillId.value = null
  ElMessage.success('已清空筛选条件，显示全部账单')
  return load()
}
async function refresh() {
  await Promise.all([load(), loadStats()])
}

async function calcLateFee() {
  const count = await billApi.calcLateFee()
  ElMessage.success(`已处理 ${count} 条逾期账单`)
  refresh()
}

// 按钮可见性由后端权限决定,不在前端猜
const capabilities = ref({ lateFeeAdjust: false, writeOff: false, paymentVoid: false })

// 收款
const payFormRef = ref()
const payDialog = reactive({ visible: false, bill: null })
const payForm = reactive({ billId: null, amount: 0, payMethod: '转账', payNo: '' })
const payRules = {
  amount: [{ required: true, message: '请输入收款金额', trigger: 'blur' }],
  payMethod: [{ required: true, message: '请选择支付方式', trigger: 'change' }]
}
function openPay(row) {
  payDialog.visible = true
  payDialog.bill = row
  // 每次打开生成一个幂等键:重复点击"确定"或网络重试不会重复入账
  const payNo = 'SK' + Date.now() + Math.random().toString(36).slice(2, 10)
  // 默认收满剩余欠款(本金 + 滞纳金 - 实收),与后端剩余可收同口径
  Object.assign(payForm, { billId: row.id, amount: billOwe(row), payMethod: '转账', payNo })
}
// 收款上限 = 剩余应收(本金 + 滞纳金 - 实收),与后端超额校验同口径
const payMax = computed(() => billOwe(payDialog.bill || {}))

async function submitPay() {
  await payFormRef.value.validate()
  // 剩余应收为 0 的账单走核销通道:后端收款接口不收 0 元,硬提交只会拿到一句报错
  if (payMax.value === 0) {
    payDialog.visible = false
    return confirmWriteOff(payDialog.bill)
  }
  await paymentApi.pay({ billId: payForm.billId, amount: payForm.amount, payMethod: payForm.payMethod, payNo: payForm.payNo })
  ElMessage.success('收款成功')
  payDialog.visible = false
  refresh()
}

// 零元核销:免租期/抵扣期账单应收 0,只推状态不产生资金记录
function canWriteOff(row) {
  return capabilities.value.writeOff && row.status !== 5 && billOwe(row) === 0
}
async function confirmWriteOff(row) {
  if (!row) return
  let remark = ''
  try {
    const input = await ElMessageBox.prompt(
      `账单 ${row.code} 本期应收 ¥0.00(免租期/抵扣期),核销后直接标记为已结清,不产生收款记录。`,
      '零元核销',
      { confirmButtonText: '确认核销', cancelButtonText: '取消', inputPlaceholder: '核销说明(可留空)', inputValue: '' }
    )
    remark = input.value || ''
  } catch {
    return
  }
  await paymentApi.writeOff({ billId: row.id, remark })
  ElMessage.success('已核销,账单标记为已结清')
  refresh()
}

// 撤销收款(红冲):点错了能退回来,记录不删
async function confirmVoid(row) {
  let reason = ''
  try {
    const input = await ElMessageBox.prompt(
      `将撤销支付流水号 ${row.payNo}(¥${money(row.amount)})。原单标记为已撤销并生成一张负额红冲单,账单实收同步回退。`,
      '撤销收款',
      { confirmButtonText: '确认撤销', cancelButtonText: '再想想', type: 'warning', inputPlaceholder: '撤销原因(建议填写)', inputValue: '' }
    )
    reason = input.value || ''
  } catch {
    return
  }
  await paymentApi.voidPay(row.id, { reason })
  ElMessage.success('已撤销,已生成红冲单')
  // 弹窗还开着:重拉这张账单的收款记录,让用户当场看见红冲单
  payRecords.value = await paymentApi.list(row.id)
  refresh()
}

// 详情(收款记录)
const detailDialog = reactive({ visible: false, bill: null })
const payRecords = ref([])
async function openDetail(row) {
  detailDialog.visible = true
  detailDialog.bill = row
  payRecords.value = await paymentApi.list(row.id)
}

// 开票
const invoiceFormRef = ref()
const invoiceDialog = reactive({ visible: false, bill: null })
const invoiceForm = reactive({ billId: null, tenantRefId: null, title: '', taxNo: '', amount: 0, invoiceType: '普票', status: 1 })
const invoiceRules = {
  title: [{ required: true, message: '请输入发票抬头', trigger: 'blur' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }]
}
function openInvoice(row) {
  invoiceDialog.visible = true
  invoiceDialog.bill = row
  Object.assign(invoiceForm, {
    billId: row.id, tenantRefId: row.tenantRefId, title: '', taxNo: '',
    amount: Number(row.amount), invoiceType: '普票', status: 1
  })
}
async function submitInvoice() {
  await invoiceFormRef.value.validate()
  await invoiceApi.add(invoiceForm)
  ElMessage.success('开票申请已提交')
  invoiceDialog.visible = false
}

async function remove(id) {
  await billApi.remove(id)
  ElMessage.success('删除成功')
  refresh()
}

onMounted(async () => {
  // 先落定位条件再取数,顺序反了会先查全量再被覆盖
  applyRouteLocate()
  // 能力位取不到不该拖垮整页:拿不到就按"没权限"渲染,列表照常出
  // 能力位/租客名册取不到都不该拖垮整页:拿不到就少一个按钮、少一个下拉,列表照常出
  const [caps, tenantList] = await Promise.allSettled([
    billApi.capabilities(), tenantApi.list(), refresh()
  ])
  if (caps.status === 'fulfilled' && caps.value) capabilities.value = caps.value
  if (tenantList.status === 'fulfilled') tenants.value = tenantList.value || []
})
</script>

<style scoped>
/* 双行单元格:主信息正常字号,次要信息小一号灰字,行高压紧不撑高表格 */
.cell-main { line-height: 1.4; }
.cell-sub { margin-top: 2px; font-size: 12px; line-height: 1.3; color: var(--el-text-color-secondary); }
.stat-row { display: flex; gap: 16px; margin-bottom: 16px; }
.stat-row .stat-card {
  flex: 1; background: var(--bg-card); border-radius: var(--radius);
  border: 1px solid var(--border); padding: 20px 22px;
  transition: border-color .18s, transform .18s;
}
.stat-row .stat-card:hover { border-color: var(--border-strong); transform: translateY(-1px); }
.stat-label { color: var(--text-secondary); font-size: 13px; margin-bottom: 8px; }
.stat-value {
  font-size: 25px; font-weight: 650; color: var(--text-title);
  letter-spacing: -0.5px; font-variant-numeric: tabular-nums;
}
.stat-value.success { color: #16a34a; }
.stat-value.warning { color: #ea9a13; }
.stat-value.danger { color: #e5484d; }
.pager { margin-top: 16px; justify-content: flex-end; }
/* 红冲单金额为负,标红提醒这是一笔退回而不是收入 */
.reversal { color: #e5484d; font-weight: 600; }
.void-note { font-size: 12px; color: var(--el-text-color-secondary); }
</style>
