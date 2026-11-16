<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true">
        <el-form-item label="账期">
          <el-date-picker v-model="month" type="month" format="YYYY年M月" value-format="YYYY-MM"
                          :clearable="false" style="width: 150px" />
        </el-form-item>
        <el-form-item v-if="period">
          <el-tag :type="locked ? 'success' : 'info'">{{ locked ? '已确认出账' : '草稿' }}</el-tag>
          <span v-if="period.generatedTime" class="gen-time">通知单生成于 {{ period.generatedTime }}</span>
        </el-form-item>
      </el-form>
    </div>

    <div v-if="period" class="summary-grid">
      <div class="summary-item"><span>电:南网 + 光伏</span><strong>{{ num(Number(period.electricGridUsage || 0) + Number(period.electricSolarUsage || 0)) }} 度</strong></div>
      <div class="summary-item"><span>电:公摊度数</span><strong>{{ num(period.electricPublicUsage) }}</strong></div>
      <div class="summary-item"><span>水:总表 / 公摊</span><strong>{{ num(period.waterUsedTotal) }} / {{ num(period.waterPublicUsage) }}</strong></div>
      <div class="summary-item"><span>垃圾:生活 / 工业</span><strong>¥{{ money(period.domesticGarbageAmount) }} / ¥{{ money(period.industrialGarbageAmount) }}</strong></div>
      <div class="summary-item"><span>通知单</span><strong>{{ notices.length }} 张</strong></div>
      <div class="summary-item total"><span>本期应收合计</span><strong>¥{{ money(totals.totalDue) }}</strong></div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" :disabled="locked" :loading="generating" @click="generate">
          {{ notices.length ? '重新生成通知单' : '生成通知单' }}
        </el-button>
        <el-button @click="openHeader">通知单抬头</el-button>
        <el-button :disabled="!notices.length" @click="exportExcel"><el-icon><Download /></el-icon>导出 Excel</el-button>
        <el-button :disabled="!notices.length" @click="printAll"><el-icon><Printer /></el-icon>打印全部</el-button>
        <el-button v-if="!locked" type="success" :disabled="!notices.length" @click="confirm">确认出账</el-button>
        <el-button v-else type="warning" @click="revoke">撤销确认</el-button>
        <span class="toolbar-tip">先在「电费 / 水费 / 垃圾处理费」填好总量与读数，这里生成通知单、核对、打印；确认出账后每户生成一张能源费账单，可在财务收款。</span>
      </div>
      <el-alert v-for="(w, i) in warnings" :key="i" :title="w" type="warning" :closable="false" show-icon style="margin-bottom: 8px" />

      <!-- 应收收费明细表:行 = 费用项目,列 = 每户 -->
      <el-table :data="matrix" v-loading="loading || listLoading" border size="small" class="matrix">
        <el-table-column prop="seq" label="序号" width="60" fixed />
        <el-table-column prop="label" label="费用项目" width="130" fixed>
          <template #default="{ row }"><span :class="{ strong: row.strong }">{{ row.label }}</span></template>
        </el-table-column>
        <el-table-column v-for="n in notices" :key="n.id" :min-width="150" align="right">
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
      <div v-if="!loading && !listLoading && !notices.length" class="empty-tip">本月还没有生成通知单。</div>

      <!-- 各户通知单 -->
      <h4 class="section-title">缴费通知单（{{ notices.length }} 张）</h4>
      <el-table :data="notices" border stripe size="small">
        <el-table-column type="index" label="序号" width="55" />
        <el-table-column prop="noticeNo" label="单据编号" width="190" />
        <el-table-column prop="unitName" label="厂房/单元" width="120" />
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
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="preview(row)">预览</el-button>
            <el-button link type="primary" @click="printOne(row)">打印</el-button>
            <el-button link type="primary" :disabled="locked" @click="edit(row)">调整</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

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

    <!-- 单张调整:已付/前期欠费/其他费用 -->
    <el-dialog v-model="editDialog.visible" :title="`调整通知单 - ${editForm.unitName || ''}`" width="520px">
      <el-form :model="editForm" label-width="110px">
        <el-form-item label="租户/公司名称"><el-input v-model="editForm.tenantName" /></el-form-item>
        <el-form-item label="厂房/单元"><el-input v-model="editForm.unitName" /></el-form-item>
        <el-form-item label="已付金额"><el-input-number v-model="editForm.paidAmount" :min="0" :precision="2" :controls="false" /></el-form-item>
        <el-form-item label="前期欠费"><el-input-number v-model="editForm.priorArrears" :min="0" :precision="2" :controls="false" /></el-form-item>
        <el-form-item label="其他费用">
          <el-input-number v-model="editForm.otherFee" :precision="2" :controls="false" />
          <el-input v-model="editForm.otherFeeNote" placeholder="名目，如 税金" style="width: 160px; margin-left: 8px" />
          <div class="form-tip">如充电桩的 1% 税金等线下表里额外的一行；计入费用小计。</div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 预览 -->
    <el-dialog v-model="previewDialog.visible" width="1100px" top="4vh" :title="previewDialog.title" destroy-on-close>
      <iframe ref="previewFrame" class="preview-frame" :srcdoc="previewDialog.html" />
      <template #footer>
        <el-button @click="previewDialog.visible = false">关闭</el-button>
        <el-button type="primary" @click="printOne(previewDialog.notice)">打印</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Printer } from '@element-plus/icons-vue'
import { feePeriodApi } from '@/api/energy'
import { useFeePeriod, money, num, downloadBlob } from './feePeriod'
import { noticeHtml, NOTICE_CSS, printNotices } from './noticePrint'

const notices = ref([])
const warnings = ref([])
const listLoading = ref(false)
const generating = ref(false)
const { month, period, loading, state, save, reload } = useFeePeriod(loadNotices)
const locked = computed(() => period.value?.status === 'CONFIRMED')

async function loadNotices() {
  if (!period.value) return
  listLoading.value = true
  try {
    notices.value = await feePeriodApi.notices(period.value.id) || []
  } finally {
    listLoading.value = false
  }
}

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
const matrix = computed(() => (notices.value.length ? MATRIX_ROWS : []))
const totals = computed(() => {
  const t = {}
  for (const r of MATRIX_ROWS) t[r.key] = notices.value.reduce((s, n) => s + Number(n[r.key] || 0), 0)
  return t
})
// 公司全称太长,列头用简称(与导出的 Excel 同一规则)
function shortName(name) {
  const s = String(name || '').replace(/(有限公司|股份有限公司|有限责任公司|公司)$/, '').replace(/^(广州|广东|深圳|佛山|中国)/, '').replace(/[（(].*?[)）]/g, '')
  return (s.length > 6 ? s.slice(0, 6) : s) || name
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

const editDialog = reactive({ visible: false })
const editForm = reactive({})
function edit(row) {
  Object.assign(editForm, {
    id: row.id, tenantName: row.tenantName, unitName: row.unitName, paidAmount: Number(row.paidAmount || 0),
    priorArrears: Number(row.priorArrears || 0), otherFee: Number(row.otherFee || 0), otherFeeNote: row.otherFeeNote || '',
    remark: row.remark || ''
  })
  editDialog.visible = true
}
async function saveEdit() {
  await feePeriodApi.updateNotice({ ...editForm })
  editDialog.visible = false
  ElMessage.success('已保存')
  await loadNotices()
}

const previewDialog = reactive({ visible: false, title: '', html: '', notice: null })
function preview(row) {
  previewDialog.notice = row
  previewDialog.title = `${row.noticeNo}  ${row.tenantName}（${row.unitName}）`
  previewDialog.html = `<!doctype html><html><head><meta charset="utf-8"><style>${NOTICE_CSS}</style></head><body>${noticeHtml(period.value, row)}</body></html>`
  previewDialog.visible = true
}
function printOne(row) {
  if (!printNotices(period.value, [row])) ElMessage.warning('浏览器拦截了打印窗口，请允许弹出窗口后重试')
}
function printAll() {
  if (!printNotices(period.value, notices.value)) ElMessage.warning('浏览器拦截了打印窗口，请允许弹出窗口后重试')
}
async function exportExcel() {
  const res = await feePeriodApi.export(period.value.id)
  downloadBlob(res, `${period.value.period}应收收费明细表.xlsx`)
}

async function confirm() {
  await ElMessageBox.confirm(
    `将按本期 ${notices.value.length} 张通知单给每个租户各生成一张能源费账单（金额 = 费用小计；没有关联在租合同的户会跳过），账期随后锁定。确定出账？`,
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
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.toolbar-tip { font-size: 12px; color: var(--el-text-color-secondary); }
.gen-time { margin-left: 10px; font-size: 12px; color: var(--el-text-color-secondary); }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 10px; margin-bottom: 12px; }
.summary-item { padding: 10px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; display: flex; flex-direction: column; gap: 4px; background: var(--el-fill-color-blank); }
.summary-item span { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-item strong { font-size: 16px; font-variant-numeric: tabular-nums; }
.summary-item.total strong { color: var(--el-color-danger); }
.matrix :deep(td) { font-variant-numeric: tabular-nums; }
.col-head { line-height: 1.3; text-align: center; }
.col-main { font-weight: 600; }
.col-sub { font-size: 11px; font-weight: 400; color: var(--el-text-color-secondary); }
.strong { font-weight: 700; }
.section-title { margin: 18px 0 8px; font-size: 14px; }
.empty-tip { text-align: center; color: var(--el-text-color-secondary); padding: 20px 0; font-size: 13px; }
.form-tip { margin-top: 4px; font-size: 12px; line-height: 1.5; color: var(--el-text-color-secondary); }
.preview-frame { width: 100%; height: 70vh; border: 1px solid var(--el-border-color-lighter); background: #fff; }
</style>
