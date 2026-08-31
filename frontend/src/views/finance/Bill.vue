<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">应收</div>
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
        <!-- 来源筛选:区分"由应收明细登记表生成"的账单与历史/演示账单。
             登记表账单才带协议编号与登记明细口径的租客名 -->
        <el-form-item label="来源">
          <el-select v-model="query.source" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="s in sources" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="只看到期">
          <el-switch v-model="query.onlyDue" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
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
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="code" label="账单号" min-width="150" />
        <el-table-column prop="tenantName" label="对方租客" min-width="160">
          <template #default="{ row }">{{ row.tenantName || `租客 #${row.tenantRefId || '-'}` }}</template>
        </el-table-column>
        <el-table-column prop="agreementNo" label="协议编号" min-width="140">
          <template #default="{ row }">{{ row.agreementNo || '-' }}</template>
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
        <el-table-column label="账期" width="180">
          <template #default="{ row }">{{ row.periodStart || '-' }} ~ {{ row.periodEnd || '-' }}</template>
        </el-table-column>
        <el-table-column prop="dueDate" label="应收日" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="逾期天数" width="90" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.overdueDays > 0 ? '#e5484d' : '' }">{{ row.overdueDays || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPay(row)">收款</el-button>
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
        <el-form-item label="已收">
          <el-input :model-value="money(payDialog.bill?.paidAmount)" disabled />
        </el-form-item>
        <el-form-item label="本次收款" prop="amount">
          <el-input-number v-model="payForm.amount" :min="0.01" :precision="2" style="width: 100%" />
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
          <template #default="{ row }">¥{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="payMethod" label="方式" width="90" />
        <el-table-column prop="payTime" label="收款时间" min-width="170" />
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
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { billApi, paymentApi, invoiceApi } from '@/api/finance'

const feeTypes = ['租金', '物业费', '保证金', '能源费', '服务费', '一次性']
// 账单来源。'应收登记表' = 由应收明细登记表生成(带协议编号与登记明细口径的租客名)
const sources = ['应收登记表', '合同计划', '抄表', '人工', '商城', '预约', '工单', '接口']
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
const query = reactive({ pageNo: 1, pageSize: 10, code: '', feeType: null, status: null, direction: null, source: null, billId: null, onlyDue: false })

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
function reset() {
  Object.assign(query, { pageNo: 1, code: '', feeType: null, status: null, direction: null, source: null, billId: null, onlyDue: false })
  locatedBillId.value = null
  load()
}
async function refresh() {
  await Promise.all([load(), loadStats()])
}

async function calcLateFee() {
  const count = await billApi.calcLateFee()
  ElMessage.success(`已处理 ${count} 条逾期账单`)
  refresh()
}

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
  Object.assign(payForm, { billId: row.id, amount: Number(row.amount) - Number(row.paidAmount || 0), payMethod: '转账', payNo })
}
async function submitPay() {
  await payFormRef.value.validate()
  await paymentApi.pay({ billId: payForm.billId, amount: payForm.amount, payMethod: payForm.payMethod, payNo: payForm.payNo })
  ElMessage.success('收款成功')
  payDialog.visible = false
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

onMounted(() => {
  // 先落定位条件再取数,顺序反了会先查全量再被覆盖
  applyRouteLocate()
  refresh()
})
</script>

<style scoped>
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
</style>
