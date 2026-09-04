<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="账期">
          <el-date-picker v-model="query.period" type="month" placeholder="全部"
                          format="YYYY年M月" value-format="YYYY-MM" clearable style="width: 170px" />
        </el-form-item>
        <el-form-item label="能源类型">
          <el-select v-model="query.energyType" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="t in energyTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已确认出账" value="CONFIRMED" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>录入月度发票</el-button>
        <span class="toolbar-tip">
          按合同《附件二》口径：总表用量 − 各分表用量 = 公共区域用量 + 损耗，按各家实际用量占比摊回。
        </span>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="period" label="账期" width="110" />
        <el-table-column label="能源类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.energyType === '电' ? 'warning' : 'primary'">{{ row.energyType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="invoiceUsage" label="发票总用量" width="140" align="right" />
        <el-table-column label="发票不含税总额" width="160" align="right">
          <template #default="{ row }">¥{{ money(row.invoiceAmountExTax) }}</template>
        </el-table-column>
        <el-table-column label="税率" width="90" align="right">
          <template #default="{ row }">{{ row.taxRate }}%</template>
        </el-table-column>
        <el-table-column label="不含税单价" width="140" align="right">
          <template #default="{ row }">{{ unitPrice(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'CONFIRMED' ? 'success' : 'info'">
              {{ row.status === 'CONFIRMED' ? '已确认出账' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :loading="calculating === row.id" @click="calculate(row)">测算分摊</el-button>
            <el-button link type="primary" @click="openDetail(row)">明细</el-button>
            <el-button v-if="row.status !== 'CONFIRMED'" link type="success" @click="confirm(row)">确认出账</el-button>
            <el-button v-else link type="warning" @click="revoke(row)">撤销确认</el-button>
            <el-button v-if="row.status !== 'CONFIRMED'" link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm v-if="row.status !== 'CONFIRMED'" title="确认删除该账期?" @confirm="remove(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 录入/编辑月度发票 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="560px">
      <el-form :model="form" label-width="150px" ref="formRef" :rules="rules">
        <el-form-item label="账期" prop="period">
          <el-date-picker v-model="form.period" type="month" format="YYYY年M月" value-format="YYYY-MM"
                          :disabled="!!form.id" style="width: 100%" />
        </el-form-item>
        <el-form-item label="能源类型" prop="energyType">
          <el-select v-model="form.energyType" :disabled="!!form.id" style="width: 100%">
            <el-option v-for="t in energyTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="发票总用量" prop="invoiceUsage">
          <el-input-number v-model="form.invoiceUsage" :min="0" :precision="4" style="width: 100%" />
          <div class="form-tip">对外电费/水费发票上的总用量，是公共区域用量的被减数。</div>
        </el-form-item>
        <el-form-item label="发票不含税总额" prop="invoiceAmountExTax">
          <el-input-number v-model="form.invoiceAmountExTax" :min="0" :precision="2" style="width: 100%" />
          <div class="form-tip">不含税单价 = 本项 ÷ 发票总用量（公式①）。</div>
        </el-form-item>
        <el-form-item label="税率" prop="taxRate">
          <el-input-number v-model="form.taxRate" :min="0" :max="100" :precision="2" style="width: 100%" />
          <div class="form-tip">百分数，如 13 表示 13%。</div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分摊明细:把《附件二》的中间量全摊开,方便财务逐条核 -->
    <el-drawer v-model="detail.visible" :title="detail.title" size="76%">
      <div v-if="detail.summary" class="summary-grid">
        <div class="summary-item"><span>发票总用量</span><strong>{{ num(detail.summary.invoiceUsage) }}</strong></div>
        <div class="summary-item"><span>不含税单价 ①</span><strong>{{ num(detail.summary.unitPriceExTax) }}</strong></div>
        <div class="summary-item"><span>租户抄表合计</span><strong>{{ num(detail.summary.tenantUsage) }}</strong></div>
        <div class="summary-item"><span>物业抄表合计</span><strong>{{ num(detail.summary.propertyUsage) }}</strong></div>
        <div class="summary-item hl"><span>公共区域用量+损耗</span><strong>{{ num(detail.summary.publicUsage) }}</strong></div>
        <div class="summary-item hl"><span>分摊系数 ②</span><strong>{{ num(detail.summary.allocCoefficient) }}</strong></div>
        <div class="summary-item"><span>自用费用合计</span><strong>¥{{ money(detail.summary.totalOwnFee) }}</strong></div>
        <div class="summary-item"><span>公摊费用合计 ③</span><strong>¥{{ money(detail.summary.totalAllocFee) }}</strong></div>
        <div class="summary-item total"><span>应收合计 ④</span><strong>¥{{ money(detail.summary.totalFee) }}</strong></div>
      </div>
      <el-alert v-for="(w, i) in (detail.summary?.warnings || [])" :key="i" :title="w"
                type="warning" :closable="false" show-icon style="margin-bottom: 8px" />
      <el-table :data="detail.rows" v-loading="detail.loading" border stripe size="small" max-height="480">
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="meterCode" label="表计编号" min-width="130" />
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="row.meterRole === 'MAIN' ? 'danger' : row.meterRole === 'PROPERTY' ? 'warning' : 'success'"
                    effect="plain">{{ roleLabel(row.meterRole) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tenantName" label="租户" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.tenantName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ownUsage" label="自身用量" width="110" align="right" />
        <el-table-column prop="allocUsage" label="分摊用量" width="110" align="right" />
        <el-table-column label="自用费用" width="120" align="right">
          <template #default="{ row }">¥{{ money(row.ownFee) }}</template>
        </el-table-column>
        <el-table-column label="公摊费用" width="120" align="right">
          <template #default="{ row }">¥{{ money(row.allocFee) }}</template>
        </el-table-column>
        <el-table-column label="合计" width="130" align="right">
          <template #default="{ row }"><strong>¥{{ money(row.totalFee) }}</strong></template>
        </el-table-column>
        <el-table-column label="账单" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.billId" type="success" effect="plain">已出账</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!detail.loading && !detail.rows.length" class="empty-tip">
        还没有分摊结果，点列表里的「测算分摊」先算一次。
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { utilityBillApi } from '@/api/energy'

const energyTypes = ['电', '水']
const ROLE_LABELS = { TENANT: '租户分表', MAIN: '园区总表', PROPERTY: '物业公司表' }
const roleLabel = (v) => ROLE_LABELS[v] || v

const loading = ref(false)
const list = ref([])
const total = ref(0)
const calculating = ref(null)
const EMPTY_QUERY = { period: null, energyType: null, status: null }
const query = reactive({ pageNo: 1, pageSize: 10, ...EMPTY_QUERY })

const money = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const num = (v) => (v == null ? '-' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 8 }))
// 列表里直接把单价算出来,省得用户为了看单价还要点进明细
function unitPrice(row) {
  const usage = Number(row.invoiceUsage || 0)
  if (!usage) return '-'
  return (Number(row.invoiceAmountExTax || 0) / usage).toFixed(6)
}

async function load() {
  loading.value = true
  try {
    const res = await utilityBillApi.page(query)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}
function search() { query.pageNo = 1; return load() }
function reset() { Object.assign(query, { pageNo: 1, ...EMPTY_QUERY }); return load() }

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const EMPTY_FORM = {
  id: null, period: null, energyType: '电',
  invoiceUsage: 0, invoiceAmountExTax: 0, taxRate: 13, remark: ''
}
const form = reactive({ ...EMPTY_FORM })
const rules = {
  period: [{ required: true, message: '请选择账期', trigger: 'change' }],
  energyType: [{ required: true, message: '请选择能源类型', trigger: 'change' }],
  invoiceUsage: [{ required: true, message: '请填写发票总用量', trigger: 'blur' }]
}
function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑月度发票' : '录入月度发票'
  Object.assign(form, row ? { ...EMPTY_FORM, ...row } : { ...EMPTY_FORM })
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await utilityBillApi.update(form)
  else await utilityBillApi.add(form)
  ElMessage.success('保存成功，接着点「测算分摊」')
  dialog.visible = false
  load()
}
async function remove(id) {
  await utilityBillApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

// 测算可反复重跑(后端幂等,每次先清旧结果),算完直接把明细摊开给用户看
async function calculate(row) {
  calculating.value = row.id
  try {
    const summary = await utilityBillApi.calculate(row.id)
    Object.assign(detail, {
      visible: true, loading: false, summary,
      rows: summary.items || [],
      title: `${row.period} ${row.energyType}费分摊明细（${summary.meterCount} 块分表）`
    })
    ElMessage.success(`测算完成：分摊系数 ${Number(summary.allocCoefficient).toFixed(6)}`)
    load()
  } finally {
    calculating.value = null
  }
}

const detail = reactive({ visible: false, loading: false, title: '', summary: null, rows: [] })
async function openDetail(row) {
  Object.assign(detail, {
    visible: true, loading: true, summary: null, rows: [],
    title: `${row.period} ${row.energyType}费分摊明细`
  })
  try {
    detail.rows = await utilityBillApi.allocations(row.id) || []
  } finally {
    detail.loading = false
  }
}

async function confirm(row) {
  await ElMessageBox.confirm(
    `将按 ${row.period} 的分摊结果给每个租户生成一张能源费账单（物业公司自己的分表不出账）。确认后该账期锁定，需要改动要先撤销确认。`,
    '确认出账', { type: 'warning', confirmButtonText: '确认出账', cancelButtonText: '取消' })
  const r = await utilityBillApi.confirm(row.id)
  ElMessage.success(`已出账 ${r.inserted} 张，跳过 ${r.skipped} 张（总表/物业表/无租户/零金额）`)
  load()
}

async function revoke(row) {
  await ElMessageBox.confirm(
    '将删除该账期由分摊生成的账单并放回草稿，以便重算。已收过款的账单不会被删，会直接拦下来让你先红冲。',
    '撤销确认', { type: 'warning' })
  const r = await utilityBillApi.revoke(row.id)
  ElMessage.success(`已撤销 ${r.removed} 张账单，账期回到草稿`)
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 14px; margin-bottom: 14px; }
.toolbar-tip { font-size: 12px; color: var(--el-text-color-secondary); }
.pager { margin-top: 16px; justify-content: flex-end; }
.form-tip { margin-top: 4px; font-size: 12px; line-height: 1.5; color: var(--el-text-color-secondary); }
.summary-grid {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 10px; margin-bottom: 14px;
}
.summary-item {
  padding: 12px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px;
  display: flex; flex-direction: column; gap: 6px; background: var(--el-fill-color-blank);
}
.summary-item span { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-item strong { font-size: 17px; font-variant-numeric: tabular-nums; }
.summary-item.hl strong { color: var(--el-color-primary); }
.summary-item.total strong { color: var(--el-color-danger); }
.empty-tip { text-align: center; color: var(--el-text-color-secondary); padding: 24px 0; font-size: 13px; }
</style>
