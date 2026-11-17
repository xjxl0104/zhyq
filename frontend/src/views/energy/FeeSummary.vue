<template>
  <div class="page-container">
    <FeeMonthPicker v-if="!month" ref="picker" kind="summary" @select="(m) => (month = m)" />

    <template v-else>
      <div class="detail-head">
        <el-button @click="back"><el-icon><ArrowLeft /></el-icon>返回年月表</el-button>
        <h3>{{ monthLabel }} · 缴费汇总</h3>
        <el-tag v-if="period" :type="locked ? 'success' : 'info'">{{ locked ? '已确认出账' : '草稿' }}</el-tag>
        <span v-if="period?.generatedTime" class="gen-time">通知单生成于 {{ period.generatedTime }}</span>
      </div>

      <el-alert v-if="period?.noticeStale && !locked" type="warning" show-icon :closable="false" style="margin-bottom: 10px"
                title="生成通知单之后，读数、总量、垃圾总额或区域/用户又有改动，下面的通知单是旧的。请重新生成后再核对、确认出账。">
        <el-button type="warning" size="small" :loading="generating" @click="generate">立即重新生成</el-button>
      </el-alert>

      <FeeBillFiles v-if="period" :period-id="period.id" :kinds="[BILL_KINDS.electric, BILL_KINDS.water, BILL_KINDS.garbage]" />

      <div v-if="period" class="summary-grid">
        <div class="summary-item"><span>电:南网 + 光伏</span><strong>{{ num(Number(period.electricGridUsage || 0) + Number(period.electricSolarUsage || 0)) }} 度</strong></div>
        <div class="summary-item"><span>电:公摊度数</span><strong>{{ num(period.electricPublicUsage) }}</strong></div>
        <div class="summary-item"><span>水:总表 / 公摊</span><strong>{{ num(period.waterUsedTotal) }} / {{ num(period.waterPublicUsage) }}</strong></div>
        <div class="summary-item"><span>垃圾:生活 / 工业</span><strong>¥{{ money(period.domesticGarbageAmount) }} / ¥{{ money(period.industrialGarbageAmount) }}</strong></div>
        <div class="summary-item"><span>通知单</span><strong>{{ notices.length }} 张</strong></div>
        <div class="summary-item total"><span>本期应收合计</span><strong>¥{{ money(allTotals.totalDue) }}</strong></div>
        <div v-if="filterActive" class="summary-item hl"><span>当前检索合计（{{ viewNotices.length }} 张）</span><strong>¥{{ money(totals.totalDue) }}</strong></div>
      </div>

      <div class="table-card">
        <div class="toolbar">
          <el-button type="primary" :disabled="locked" :loading="generating" @click="generate">
            {{ notices.length ? '重新生成通知单' : '生成通知单' }}
          </el-button>
          <el-button @click="openHeader">通知单抬头</el-button>
          <el-button :disabled="!notices.length" @click="exportExcel"><el-icon><Download /></el-icon>导出 Excel</el-button>
          <el-button :disabled="!notices.length" @click="printAll"><el-icon><Printer /></el-icon>{{ filterActive ? "打印检索结果" : "打印全部" }}</el-button>
          <el-button :disabled="!selected.length" @click="printSelected">打印选中</el-button>
          <el-button type="danger" plain :disabled="!selected.length" @click="deleteSelected">
            <el-icon><Delete /></el-icon>删除选中<span v-if="selected.length">（{{ selected.length }}）</span>
          </el-button>
          <el-button type="danger" plain :disabled="!notices.length" @click="deleteAll">一键删除全部</el-button>
          <el-button v-if="!locked" type="success" :disabled="!notices.length || period?.noticeStale" @click="confirm">确认出账</el-button>
          <el-button v-else type="warning" @click="revoke">撤销确认</el-button>
        </div>
        <div class="toolbar-tip">先在「电费 / 水费 / 垃圾处理费」填好总量与读数，这里按租户生成通知单（一个租户一张，名下各区域的水电垃圾费按区域分行列在同一张单上）、核对、打印；确认出账后每个租户生成一张能源费账单，可在财务收款。删掉的通知单重新生成时会按读数重新出现，要永久去掉某个区域/用户请到电费、水费页删除对应区域。</div>
        <el-alert v-for="(w, i) in warnings" :key="i" :title="w" type="warning" :closable="false" show-icon style="margin-bottom: 8px" />

        <!-- 检索:按公司、楼层;下面的明细表、通知单列表和各合计都跟着检索结果走 -->
        <FeeFilterBar :model="filter" :companies="opts.companies" :floors="opts.floors">
          检索结果 {{ viewNotices.length }} / {{ notices.length }} 张通知单 · 应收合计 ¥{{ money(totals.totalDue) }}
        </FeeFilterBar>

        <!-- 应收收费明细表:行 = 费用项目,列 = 每户 -->
        <el-table :data="matrix" v-loading="loading || listLoading" border size="small" class="matrix">
          <el-table-column prop="seq" label="序号" width="60" fixed />
          <el-table-column prop="label" label="费用项目" width="130" fixed>
            <template #default="{ row }"><span :class="{ strong: row.strong }">{{ row.label }}</span></template>
          </el-table-column>
          <el-table-column v-for="n in viewNotices" :key="n.id" :min-width="150" align="right">
            <template #header>
              <div class="col-head" :title="n.tenantName">
                <div class="col-main">{{ shortName(n.tenantName) }}</div>
                <div class="col-sub">{{ n.unitName }}</div>
              </div>
            </template>
            <template #default="{ row }">
              <span :class="{ strong: row.strong }">{{ money(n[row.key]) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="合计金额/元" width="140" align="right" fixed="right">
            <template #default="{ row }"><strong>{{ money(totals[row.key]) }}</strong></template>
          </el-table-column>
        </el-table>
        <div v-if="!loading && !listLoading && !notices.length" class="empty-tip">本月还没有生成通知单（或已全部删除）。</div>

        <!-- 各户通知单 -->
        <h4 class="section-title">缴费通知单（{{ viewNotices.length }}{{ filterActive ? ` / ${notices.length}` : '' }} 张）</h4>
        <el-table ref="noticeTable" :data="viewNotices" border size="small" row-key="id" :row-class-name="({ row }) => bandClass(row)"
                  show-summary :summary-method="noticeSummary" @selection-change="(rows) => (selected = rows)">
          <el-table-column type="selection" width="45" />
          <el-table-column type="index" label="序号" width="55" />
          <el-table-column prop="noticeNo" label="单据编号" width="190" />
          <el-table-column prop="unitName" label="厂房/单元" min-width="170" show-overflow-tooltip />
          <el-table-column prop="tenantName" label="租户/公司名称" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.tenantName }}
              <el-tag v-if="!row.tenantRefId" size="small" type="warning" effect="plain" style="margin-left: 4px">未关联合同</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="参与公摊" width="85" align="center">
            <template #default="{ row }">{{ row.sharePublic ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="费用小计" width="120" align="right"><template #default="{ row }">¥{{ money(row.subtotal) }}</template></el-table-column>
          <el-table-column label="已付" width="100" align="right"><template #default="{ row }">{{ money(row.paidAmount) }}</template></el-table-column>
          <el-table-column label="前期欠费" width="100" align="right"><template #default="{ row }">{{ money(row.priorArrears) }}</template></el-table-column>
          <el-table-column label="本期应收合计" width="130" align="right"><template #default="{ row }"><strong>¥{{ money(row.totalDue) }}</strong></template></el-table-column>
          <el-table-column label="账单" width="80" align="center">
            <template #default="{ row }"><el-tag v-if="row.billId" size="small" type="success" effect="plain">已出账</el-tag><span v-else>-</span></template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="preview(row)">预览</el-button>
              <el-button link type="primary" @click="printOne(row)">打印</el-button>
              <el-button link type="primary" :disabled="locked" @click="edit(row)">调整</el-button>
              <el-button link type="danger" @click="deleteOne(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- 通知单抬头 -->
    <el-dialog v-model="headerDialog.visible" title="通知单抬头与计费期间" width="620px">
      <el-form :model="headerForm" label-width="120px">
        <el-form-item label="园区名称"><el-input v-model="headerForm.parkName" /></el-form-item>
        <el-form-item label="单据编号前缀"><el-input v-model="headerForm.noticePrefix" placeholder="如 DIPARK" style="width: 200px" /></el-form-item>
        <el-form-item label="计费期间">
          <el-date-picker v-model="headerForm.periodStart" type="date" value-format="YYYY-MM-DD" style="width: 160px" />
          <span style="margin: 0 8px">~</span>
          <el-date-picker v-model="headerForm.periodEnd" type="date" value-format="YYYY-MM-DD" style="width: 160px" />
        </el-form-item>
        <el-form-item label="开具日期"><el-date-picker v-model="headerForm.issueDate" type="date" value-format="YYYY-MM-DD" style="width: 160px" /></el-form-item>
        <el-form-item label="缴费截止日"><el-date-picker v-model="headerForm.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 160px" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="headerForm.contactName" style="width: 200px" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="headerForm.contactPhone" style="width: 200px" /></el-form-item>
        <el-form-item label="收款账户/方式"><el-input v-model="headerForm.paymentAccount" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="headerDialog.visible = false">取消</el-button>
        <el-button type="primary" :disabled="locked" :loading="state.saving" @click="saveHeader">保存</el-button>
      </template>
    </el-dialog>

    <!-- 调整通知单:基本信息 + 各行备注 + 新增收费项,一个弹窗改完 -->
    <el-dialog v-model="editDialog.visible" :title="`调整通知单 - ${editForm.unitName || ''}`" width="1040px" top="4vh" destroy-on-close>
      <el-form :model="editForm" label-width="110px" class="edit-form">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="租户/公司名称"><el-input v-model="editForm.tenantName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="厂房/单元"><el-input v-model="editForm.unitName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="已付金额"><el-input-number v-model="editForm.paidAmount" :min="0" :precision="2" :controls="false" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="前期欠费"><el-input-number v-model="editForm.priorArrears" :min="0" :precision="2" :controls="false" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="2" placeholder="印在通知单表头「备注」一栏" /></el-form-item></el-col>
        </el-row>
      </el-form>

      <div class="items-head">
        <strong>收费明细</strong>
        <span class="form-tip">水费、电费、垃圾费由读数自动算出，金额不能改，备注可改；下面可以随时新增收费项（维修费、违约金、减免等），金额可为负数。</span>
        <el-button type="primary" size="small" @click="addManual"><el-icon><Plus /></el-icon>新增收费项</el-button>
      </div>
      <el-table :data="editItems" border size="small" class="items-table" :row-class-name="({ row }) => (row.auto ? 'item-auto' : 'item-manual')">
        <el-table-column label="序号" width="58" align="center">
          <template #default="{ row }">{{ row.seq }}</template>
        </el-table-column>
        <el-table-column label="费用项目" min-width="170">
          <template #default="{ row }">
            <span v-if="row.auto" :class="{ 'sub-line': row.detail }">{{ row.detail ? '　' : '' }}{{ row.feeItem }}</span>
            <el-input v-else v-model="row.feeItem" size="small" placeholder="如 设备维修费" maxlength="64" />
          </template>
        </el-table-column>
        <el-table-column label="数量" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.auto">{{ row.quantity == null ? '' : num(row.quantity, 4) }}</span>
            <el-input-number v-else v-model="row.quantity" size="small" :precision="2" :controls="false" style="width: 96px" @change="calcAmount(row)" />
          </template>
        </el-table-column>
        <el-table-column label="单位" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.auto">{{ row.unit }}</span>
            <el-input v-else v-model="row.unit" size="small" placeholder="元" maxlength="16" />
          </template>
        </el-table-column>
        <el-table-column label="单价(元)" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.auto">{{ row.unitPrice == null ? '' : num(row.unitPrice, 4) }}</span>
            <el-input-number v-else v-model="row.unitPrice" size="small" :precision="4" :controls="false" style="width: 96px" @change="calcAmount(row)" />
          </template>
        </el-table-column>
        <el-table-column label="金额(元)" width="125" align="right">
          <template #default="{ row }">
            <span v-if="row.auto">{{ row.detail || row.amount == null ? '' : money(row.amount) }}</span>
            <el-input-number v-else v-model="row.amount" size="small" :precision="2" :controls="false" style="width: 110px" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="240">
          <template #default="{ row }">
            <span v-if="row.detail" class="sub-line">{{ row.remark }}</span>
            <el-input v-else v-model="row.remark" size="small" maxlength="255" placeholder="备注" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ row, $index }">
            <el-button v-if="!row.auto" link type="danger" size="small" @click="editItems.splice($index, 1)">删除</el-button>
            <span v-else class="muted">自动</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="edit-total">
        <span>费用小计 <strong>¥{{ money(editSubtotal) }}</strong></span>
        <span>已付 − ¥{{ money(editForm.paidAmount) }}</span>
        <span>前期欠费 + ¥{{ money(editForm.priorArrears) }}</span>
        <span class="due">本期应收合计 <strong>¥{{ money(editDue) }}</strong></span>
      </div>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="editDialog.saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 预览 -->
    <el-dialog v-model="previewDialog.visible" width="1100px" top="4vh" :title="previewDialog.title" destroy-on-close>
      <iframe class="preview-frame" :srcdoc="previewDialog.html" />
      <template #footer>
        <el-button @click="previewDialog.visible = false">关闭</el-button>
        <el-button type="primary" @click="printOne(previewDialog.notice)">打印</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onActivated, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Delete, Download, Plus, Printer } from '@element-plus/icons-vue'
import { feePeriodApi } from '@/api/energy'
import { useFeePeriod, money, num, downloadBlob, BILL_KINDS, applyFilter, filterOptions, floorBandClass, shortName } from './feePeriod'
import FeeFilterBar from './components/FeeFilterBar.vue'
import { noticeHtml, NOTICE_CSS, printNotices } from './noticePrint'
import FeeMonthPicker from './components/FeeMonthPicker.vue'
import FeeBillFiles from './components/FeeBillFiles.vue'

const notices = ref([])
const selected = ref([])
const warnings = ref([])
const listLoading = ref(false)
const generating = ref(false)
const picker = ref(null)
const noticeTable = ref(null)
const { month, period, loading, state, save, reload, back } = useFeePeriod(loadNotices)
const locked = computed(() => period.value?.status === 'CONFIRMED')
const monthLabel = computed(() => (month.value ? `${month.value.slice(0, 4)}年${Number(month.value.slice(5))}月` : ''))

async function loadNotices() {
  if (!period.value) return
  listLoading.value = true
  try {
    notices.value = await feePeriodApi.notices(period.value.id) || []
    selected.value = []
  } finally {
    listLoading.value = false
  }
}
watch(month, (v) => { filter.company = ''; filter.floor = ''; if (!v) { notices.value = []; warnings.value = [] } })

let firstActivation = true
onActivated(async () => {
  if (firstActivation) { firstActivation = false; return }
  if (!month.value) picker.value?.refresh()
  else { await reload(); await loadNotices() }
})

const MATRIX_ROWS = [
  { seq: '1', key: 'waterFee', label: '水费' },
  { seq: '2', key: 'waterPublicFee', label: '公摊水费' },
  { seq: '3', key: 'electricFee', label: '电费' },
  { seq: '4', key: 'electricPublicFee', label: '公摊电费' },
  { seq: '5', key: 'domesticGarbageFee', label: '生活垃圾清运费' },
  { seq: '6', key: 'industrialGarbageFee', label: '工业垃圾清运费' },
  { seq: '7', key: 'otherFee', label: '其他费用' },
  { seq: '一', key: 'subtotal', label: '费用小计', strong: true },
  { seq: '二', key: 'paidAmount', label: '已付金额' },
  { seq: '三', key: 'priorArrears', label: '前期欠费' },
  { seq: '四', key: 'totalDue', label: '本期应收合计', strong: true }
]
// 检索:按公司、楼层筛通知单;每一层交替深浅色。明细表、合计、通知单列表都用筛出来的这批
const filter = reactive({ company: '', floor: '' })
const companyOf = (n) => n.tenantName
const unitOf = (n) => n.unitName
const opts = computed(() => filterOptions(notices.value, companyOf, unitOf))
const filterActive = computed(() => !!(filter.company || filter.floor))
const viewNotices = computed(() => applyFilter(notices.value, filter, companyOf, unitOf))
const bandClass = computed(() => floorBandClass(notices.value, (n) => n.noticeNo))
const matrix = computed(() => (viewNotices.value.length ? MATRIX_ROWS : []))
// 合计列 = 各户金额逐个相加(分为单位取整,避免浮点尾数),与通知单列表、导出 Excel 同源
const sumOf = (list) => {
  const t = {}
  for (const r of MATRIX_ROWS) {
    t[r.key] = list.reduce((s, n) => s + Math.round(Number(n[r.key] || 0) * 100), 0) / 100
  }
  return t
}
const totals = computed(() => sumOf(viewNotices.value))
const allTotals = computed(() => sumOf(notices.value))
// 通知单列表底部合计行(跟着检索走)
function noticeSummary({ columns, data }) {
  const t = sumOf(data)
  return columns.map((col, i) => {
    if (i === 1) return '合计'
    switch (col.label) {
      case '费用小计': return '¥' + money(t.subtotal)
      case '已付': return money(t.paidAmount)
      case '前期欠费': return money(t.priorArrears)
      case '本期应收合计': return '¥' + money(t.totalDue)
      default: return ''
    }
  })
}
async function generate() {
  generating.value = true
  try {
    const r = await feePeriodApi.generate(period.value.id)
    warnings.value = r.warnings || []
    ElMessage.success(`已生成 ${r.notices?.length || 0} 张通知单`)
    await reload()
    await loadNotices()
  } finally {
    generating.value = false
  }
}

const headerDialog = reactive({ visible: false })
const headerForm = reactive({})
function openHeader() {
  const p = period.value
  Object.assign(headerForm, {
    parkName: p.parkName, noticePrefix: p.noticePrefix, periodStart: p.periodStart, periodEnd: p.periodEnd,
    issueDate: p.issueDate, dueDate: p.dueDate, contactName: p.contactName, contactPhone: p.contactPhone,
    paymentAccount: p.paymentAccount
  })
  headerDialog.visible = true
}
async function saveHeader() {
  await save({ ...headerForm })
  headerDialog.visible = false
  ElMessage.success(notices.value.length ? '已保存；单据编号前缀与计费期间要点「重新生成通知单」才会更新到各户' : '已保存')
}

const editDialog = reactive({ visible: false, saving: false })
const editForm = reactive({})
// 弹窗里的明细行:自动行(只读金额,可改备注)+ 手工行(整行可改);detail = 分表子行,只读
const editItems = ref([])
let originalRemarks = {}
function edit(row) {
  Object.assign(editForm, {
    id: row.id, tenantName: row.tenantName, unitName: row.unitName, paidAmount: Number(row.paidAmount || 0),
    priorArrears: Number(row.priorArrears || 0), remark: row.remark || ''
  })
  originalRemarks = {}
  editItems.value = (row.items || []).map((it) => {
    const manual = it.manualItem || it.itemType === 'OTHER' || it.itemType === 'CUSTOM'
    if (!manual) originalRemarks[it.id] = it.remark || ''
    return {
      id: it.id, auto: !manual, detail: /_DETAIL$/.test(it.itemType), seq: it.seq, feeItem: it.feeItem,
      quantity: it.quantity ?? null, unit: it.unit || '', unitPrice: it.unitPrice ?? null,
      amount: it.amount == null ? null : Number(it.amount), remark: it.remark || ''
    }
  })
  editDialog.visible = true
}
function addManual() {
  editItems.value.push({ id: null, auto: false, detail: false, seq: '', feeItem: '', quantity: null, unit: '元', unitPrice: null, amount: null, remark: '' })
}
// 填了数量和单价就自动算金额;之后仍可直接改金额
function calcAmount(row) {
  if (row.quantity != null && row.unitPrice != null) row.amount = Math.round(Number(row.quantity) * Number(row.unitPrice) * 100) / 100
}
const cents = (v) => Math.round(Number(v || 0) * 100)
const editSubtotal = computed(() => editItems.value.reduce((s, r) => (r.detail ? s : s + cents(r.amount)), 0) / 100)
const editDue = computed(() => (cents(editSubtotal.value) - cents(editForm.paidAmount) + cents(editForm.priorArrears)) / 100)
// 序号:手工行接在自动行后面顺延
watch(() => editItems.value.length, () => {
  const top = editItems.value.filter((r) => r.auto && !r.detail).length
  editItems.value.filter((r) => !r.auto).forEach((r, i) => { r.seq = String(top + i + 1) })
})
async function saveEdit() {
  const manual = editItems.value.filter((r) => !r.auto)
  for (const [i, r] of manual.entries()) {
    if (!String(r.feeItem || '').trim()) return ElMessage.warning(`第 ${i + 1} 个新增收费项还没填费用项目`)
    if (r.amount == null && !(r.quantity != null && r.unitPrice != null)) return ElMessage.warning(`新增收费项「${r.feeItem}」还没填金额（或数量 × 单价）`)
  }
  const itemRemarks = {}
  editItems.value.filter((r) => r.auto && !r.detail && (r.remark || '') !== originalRemarks[r.id]).forEach((r) => { itemRemarks[r.id] = r.remark || '' })
  editDialog.saving = true
  try {
    await feePeriodApi.editNotice({
      id: editForm.id, tenantName: editForm.tenantName, unitName: editForm.unitName, paidAmount: editForm.paidAmount,
      priorArrears: editForm.priorArrears, remark: editForm.remark, itemRemarks,
      manualItems: manual.map((r) => ({ feeItem: r.feeItem, unit: r.unit, quantity: r.quantity, unitPrice: r.unitPrice, amount: r.amount, remark: r.remark }))
    })
    editDialog.visible = false
    ElMessage.success('已保存')
    await reload()
    await loadNotices()
  } finally {
    editDialog.saving = false
  }
}

const previewDialog = reactive({ visible: false, title: '', html: '', notice: null })
function preview(row) {
  previewDialog.notice = row
  previewDialog.title = `${row.noticeNo}  ${row.tenantName}（${row.unitName}）`
  previewDialog.html = `<!doctype html><html><head><meta charset="utf-8"><style>${NOTICE_CSS}</style></head><body>${noticeHtml(period.value, row)}</body></html>`
  previewDialog.visible = true
}
function doPrint(list) {
  if (!printNotices(period.value, list)) ElMessage.warning('浏览器拦截了打印窗口，请允许弹出窗口后重试')
}
const printOne = (row) => doPrint([row])
const printAll = () => doPrint(viewNotices.value)
const printSelected = () => doPrint(selected.value)
async function exportExcel() {
  const res = await feePeriodApi.export(period.value.id)
  downloadBlob(res, `${period.value.period}应收收费明细表.xlsx`)
}

// ---- 删除通知单:单张 / 多选 / 一键全部
function deleteHint() {
  return locked.value
    ? '本期已确认出账：删除通知单会同时删除它生成的账单（已收过款的会被拦下，需先红冲）。'
    : '删掉的通知单重新生成时会按读数重新出现；要永久去掉某个区域/用户请到电费、水费页删除对应区域。'
}
async function afterDelete(r) {
  ElMessage.success(`已删除 ${r.deleted} 张通知单${r.bills ? `，并删除 ${r.bills} 张账单` : ''}`)
  await reload()
  await loadNotices()
  noticeTable.value?.clearSelection()
}
async function deleteOne(row) {
  await ElMessageBox.confirm(`确定删除「${row.unitName} ${row.tenantName}」的通知单 ${row.noticeNo}？\n${deleteHint()}`,
    '删除通知单', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
  await afterDelete(await feePeriodApi.deleteNotices(period.value.id, [row.id]))
}
async function deleteSelected() {
  await ElMessageBox.confirm(`确定删除选中的 ${selected.value.length} 张通知单？\n${deleteHint()}`,
    '删除选中的通知单', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
  await afterDelete(await feePeriodApi.deleteNotices(period.value.id, selected.value.map((n) => n.id)))
}
async function deleteAll() {
  await ElMessageBox.prompt(`将删除 ${monthLabel.value} 的全部 ${notices.value.length} 张通知单，无法恢复。\n${deleteHint()}\n请输入“删除”二字确认：`,
    '一键删除全部通知单', { type: 'error', confirmButtonText: '全部删除', cancelButtonText: '取消', inputPattern: /^删除$/, inputErrorMessage: '请输入“删除”二字' })
  await afterDelete(await feePeriodApi.deleteAllNotices(period.value.id))
}

async function confirm() {
  await ElMessageBox.confirm(
    `将按本期 ${notices.value.length} 张通知单给每个租户各生成一张能源费账单（金额 = 费用小计；没有关联在租合同的户会跳过），账期随后冻结。确定出账？`,
    '确认出账', { type: 'warning', confirmButtonText: '确认出账', cancelButtonText: '取消' })
  const r = await feePeriodApi.confirm(period.value.id)
  ElMessage.success(`已出账 ${r.inserted} 张，跳过 ${r.skipped} 张`)
  await reload()
  await loadNotices()
}
async function revoke() {
  await ElMessageBox.confirm('将删除本期由通知单生成且尚未收款的账单，账期回到草稿以便重算。已收过款的会直接拦下。', '撤销确认', { type: 'warning' })
  const r = await feePeriodApi.revoke(period.value.id)
  ElMessage.success(`已撤销 ${r.removed} 张账单`)
  await reload()
  await loadNotices()
}
</script>

<style scoped>
.detail-head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.detail-head h3 { margin: 0; font-size: 16px; }
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
.toolbar-tip { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 12px; line-height: 1.6; }
.gen-time { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 10px; margin-bottom: 12px; }
.summary-item { padding: 10px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; display: flex; flex-direction: column; gap: 4px; background: var(--el-fill-color-blank); }
.summary-item span { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-item strong { font-size: 16px; font-variant-numeric: tabular-nums; }
.summary-item.total strong { color: var(--el-color-danger); }
.matrix :deep(td) { font-variant-numeric: tabular-nums; }
.col-head { line-height: 1.3; text-align: center; }
.col-main { font-weight: 600; }
.col-sub { font-size: 11px; font-weight: 400; color: var(--el-text-color-secondary); white-space: normal; word-break: break-all; max-width: 150px; margin: 0 auto; }
.strong { font-weight: 700; }
.section-title { margin: 18px 0 8px; font-size: 14px; }
.empty-tip { text-align: center; color: var(--el-text-color-secondary); padding: 20px 0; font-size: 13px; }
.form-tip { margin-top: 4px; font-size: 12px; line-height: 1.5; color: var(--el-text-color-secondary); }
.edit-form { margin-bottom: 4px; }
.items-head { display: flex; align-items: center; gap: 12px; margin: 6px 0 10px; flex-wrap: wrap; }
.items-head .form-tip { flex: 1; margin: 0; min-width: 260px; }
.items-table :deep(tr.item-manual) { background-color: var(--el-color-warning-light-9); }
.sub-line { color: var(--el-text-color-secondary); font-size: 12px; }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
.edit-total { display: flex; justify-content: flex-end; align-items: baseline; gap: 22px; margin-top: 12px; font-size: 13px; font-variant-numeric: tabular-nums; }
.edit-total .due strong { font-size: 18px; color: var(--el-color-danger); }
.preview-frame { width: 100%; height: 70vh; border: 1px solid var(--el-border-color-lighter); background: #fff; }
</style>
