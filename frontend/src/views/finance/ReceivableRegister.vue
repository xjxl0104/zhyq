<template>
  <div class="page-container receivable-page">
    <div class="page-header">
      <div>
        <h2>应收明细登记表</h2>
        <p>以园区租金及物业管理费基础资料为权威口径</p>
      </div>
      <div class="toolbar">
        <!-- 28 列全开必然横向滚动、右侧固定列会压住内容;列设置让用户选到不横滚为止 -->
        <el-popover placement="bottom-end" :width="360" trigger="click">
          <template #reference>
            <el-button>列设置 ({{ visibleCols.length }}/{{ receivableColumns.length }})</el-button>
          </template>
          <div class="col-picker">
            <div class="col-picker-bar">
              <el-button link type="primary" @click="applyPreset('common')">常用列</el-button>
              <el-button link type="primary" @click="applyPreset('all')">全选</el-button>
              <span class="col-picker-hint">列越少越不容易被右侧固定列挡住</span>
            </div>
            <el-checkbox-group v-model="visibleCols" class="col-picker-list" @change="onColsChange">
              <el-checkbox v-for="column in receivableColumns" :key="column.prop"
                           :value="column.prop" :disabled="LOCKED_COLUMN_PROPS.includes(column.prop)">
                {{ column.label }}
              </el-checkbox>
            </el-checkbox-group>
          </div>
        </el-popover>
        <el-button v-if="capabilities.exportData" @click="downloadExport">导出 Excel</el-button>
        <el-button v-if="capabilities.importData" type="primary" @click="importVisible = true">导入工作簿</el-button>
        <el-button v-if="capabilities.add" type="success" @click="openEditor()">新增</el-button>
      </div>
    </div>

    <div v-if="capabilities.confirm && lastCompletedBatch" class="batch-bar">
      <span>最近确认导入：批次 #{{ lastCompletedBatch.id }}，{{ lastCompletedBatch.importedRows || 0 }} 行</span>
      <el-button link type="danger" @click="rollbackLastBatch">撤销该批次</el-button>
    </div>

    <el-form :inline="true" :model="query" class="filter-bar">
      <el-form-item label="视图">
        <el-radio-group v-model="viewMode" @change="onViewChange">
          <el-radio-button value="list">登记明细</el-radio-button>
          <el-radio-button value="monthly">月度营收</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="viewMode === 'monthly'" label="月份">
        <el-date-picker v-model="monthFilter" type="month" placeholder="选择月份"
                        format="YYYY年M月" value-format="YYYY-MM" style="width: 160px"
                        @change="loadMonthlySummary" />
      </el-form-item>
      <el-form-item label="租户"><el-input v-model="query.tenantName" clearable /></el-form-item>
      <template v-if="viewMode === 'list'">
        <el-form-item label="空间"><el-input v-model="query.spaceName" clearable /></el-form-item>
        <el-form-item label="协议编号"><el-input v-model="query.agreementNo" clearable /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width: 130px">
            <el-option label="草稿" value="DRAFT" /><el-option label="待核对" value="PENDING_REVIEW" />
            <el-option label="已确认" value="CONFIRMED" /><el-option label="已生效" value="ACTIVE" />
          </el-select>
        </el-form-item>
      </template>
      <el-form-item><el-button type="primary" @click="viewMode === 'list' ? load() : loadMonthlySummary()">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
    </el-form>

    <!-- 月度汇总统计卡片 -->
    <div v-if="viewMode === 'monthly' && monthlySummary" class="summary-cards">
      <div class="summary-card">
        <div class="card-label">月份</div>
        <div class="card-value">{{ monthlySummary.month }}</div>
      </div>
      <div class="summary-card">
        <div class="card-label">在租户数</div>
        <div class="card-value">{{ monthlySummary.count }}</div>
      </div>
      <div class="summary-card rent">
        <div class="card-label">应收租金</div>
        <div class="card-value">¥{{ formatMoney(monthlySummary.totalRent) }}</div>
      </div>
      <div class="summary-card property">
        <div class="card-label">应收物业费</div>
        <div class="card-value">¥{{ formatMoney(monthlySummary.totalProperty) }}</div>
      </div>
      <div class="summary-card total">
        <div class="card-label">应收合计</div>
        <div class="card-value">¥{{ formatMoney(monthlySummary.grandTotal) }}</div>
      </div>
    </div>

    <div class="table-card" v-if="viewMode === 'list'">
      <el-table :data="list" v-loading="loading" border stripe height="calc(100vh - 300px)" @row-dblclick="openDetail">
        <el-table-column v-for="column in shownColumns" :key="column.prop"
          :prop="column.prop" :label="column.label" :min-width="column.minWidth"
          :fixed="column.fixed" :align="column.align" resizable show-overflow-tooltip>
          <template #default="{ row }">{{ formatReceivableCell(row, column) }}</template>
        </el-table-column>
        <!-- 登记表是账单的源头:每行生成到哪了必须看得见,点数字进详情看账单明细 -->
        <el-table-column label="账单" width="90" fixed="right">
          <template #default="{ row }">
            <el-tag v-if="row.billCount > 0" type="success" effect="plain"
                    style="cursor: pointer" role="button" tabindex="0"
                    @click="openDetail(row)" @keydown.enter="openDetail(row)">{{ row.billCount }} 张</el-tag>
            <el-tag v-else-if="['CONFIRMED', 'ACTIVE'].includes(row.status)" type="info" effect="plain">未生成</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" fixed="right">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <!-- 操作收进下拉:5 个按钮平铺要 300px,右侧固定列越宽、被它盖住的内容越多。
             只留最常用的「详情」,其余进「更多」,固定区从 490px 降到 300px -->
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-dropdown v-if="hasRowActions(row)" trigger="click" @command="cmd => onRowAction(cmd, row)">
              <el-button link type="primary">更多<el-icon class="el-icon--right"><arrow-down /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="capabilities.edit && ['DRAFT', 'PENDING_REVIEW'].includes(row.status)" command="edit">编辑</el-dropdown-item>
                  <el-dropdown-item v-if="capabilities.generate" command="generate">生成账单</el-dropdown-item>
                  <!-- 收缴政策,不是合同真相:已确认/已生效的登记也可调,后端普通编辑仍锁死 -->
                  <el-dropdown-item v-if="capabilities.edit" command="lateFee">滞纳金</el-dropdown-item>
                  <el-dropdown-item v-if="capabilities.deleteData" command="delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
        :total="total" v-model:current-page="query.pageNo" v-model:page-size="query.pageSize"
        :page-sizes="[20, 50, 100]" @change="load" />
    </div>

    <!-- 月度应收表格 -->
    <div class="table-card" v-if="viewMode === 'monthly'">
      <el-table :data="monthlyItems" v-loading="monthlyLoading" border stripe height="calc(100vh - 380px)"
                show-summary :summary-method="monthlySummaryMethod">
        <el-table-column prop="tenantName" label="租户" min-width="160" show-overflow-tooltip />
        <el-table-column prop="spaceName" label="空间" min-width="140" show-overflow-tooltip />
        <el-table-column prop="agreementNo" label="协议编号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="freePeriod" label="免租期限" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ row.freePeriod || '-' }}</template>
        </el-table-column>
        <el-table-column prop="discount" label="优惠/调整" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ row.discount || '-' }}</template>
        </el-table-column>
        <el-table-column prop="monthlyRent" label="当月应收租金" width="140" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.monthlyRent) }}</template>
        </el-table-column>
        <el-table-column prop="monthlyProperty" label="当月应收物业费" width="150" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.monthlyProperty) }}</template>
        </el-table-column>
        <el-table-column prop="monthlyTotal" label="当月应收合计" width="140" align="right">
          <template #default="{ row }"><span class="amount-highlight">¥{{ formatMoney(row.monthlyTotal) }}</span></template>
        </el-table-column>
        <el-table-column label="合同期限" width="200">
          <template #default="{ row }">{{ row.contractStart }} ~ {{ row.contractEnd }}</template>
        </el-table-column>
        <el-table-column prop="collectionTiming" label="收款约定" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ row.collectionTiming || '-' }}</template>
        </el-table-column>
        <el-table-column prop="dueDate" label="本期应收日" width="120" />
      </el-table>
    </div>

    <ReceivableImportDialog v-model="importVisible" @confirmed="afterImport" />
    <ReceivableDetailDrawer v-model="detailVisible" :register-id="detailId" :can-view-account="capabilities.accountView" />

    <el-dialog v-model="editor.visible" :title="editor.form.id ? '编辑应收登记表' : '新增应收登记表'" width="720px">
      <el-form :model="editor.form" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="协议编号"><el-input v-model="editor.form.agreementNoRaw" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="状态"><el-input model-value="草稿（由系统控制）" disabled /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="租户"><el-input v-model="editor.form.tenantNameRaw" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="楼层/空间"><el-input v-model="editor.form.spaceNameRaw" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="月租金"><el-input-number v-model="editor.form.monthlyRent" :precision="2" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="月物业费"><el-input-number v-model="editor.form.monthlyProperty" :precision="2" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="月合计"><el-input-number v-model="editor.form.monthlyTotal" :precision="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="editor.visible = false">取消</el-button><el-button type="primary" @click="saveEditor">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="lateFee.visible" title="滞纳金起算日" width="460px">
      <el-form label-width="110px">
        <el-form-item label="租户"><span>{{ lateFee.tenantName }}</span></el-form-item>
        <el-form-item label="起算日">
          <el-date-picker v-model="lateFee.date" type="date" value-format="YYYY-MM-DD"
                          placeholder="留空 = 默认口径" clearable style="width: 100%" />
        </el-form-item>
        <div class="late-fee-tip">
          该日之前不计滞纳金（账单仍照实标逾期）；从该日起按万分之五/天计。
          留空恢复默认口径（应收日与建单日取较晚者）。保存后立即对全部逾期账单重算。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="lateFee.visible = false">取消</el-button>
        <el-button type="primary" :loading="lateFee.saving" @click="saveLateFee">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { receivableApi } from '@/api/receivable'
import ReceivableDetailDrawer from './components/ReceivableDetailDrawer.vue'
import ReceivableImportDialog from './components/ReceivableImportDialog.vue'
import {
  COMMON_COLUMN_PROPS, LOCKED_COLUMN_PROPS, formatReceivableCell,
  loadVisibleColumns, receivableColumns, saveVisibleColumns
} from './receivableModel'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 20, tenantName: '', spaceName: '', agreementNo: '', status: '' })
const viewMode = ref('list')
const monthFilter = ref(new Date().toISOString().slice(0, 7)) // default current month
const monthlyLoading = ref(false)
const monthlyItems = ref([])
const monthlySummary = ref(null)
const importVisible = ref(false)
const detailVisible = ref(false)
const detailId = ref(null)
const editor = reactive({ visible: false, form: {} })
const lateFee = reactive({ visible: false, saving: false, id: null, tenantName: '', date: null })
const visibleCols = ref(loadVisibleColumns())
// 按 receivableColumns 的原始顺序渲染,不跟着勾选顺序走
const shownColumns = computed(() => receivableColumns.filter(c => visibleCols.value.includes(c.prop)))
function onColsChange() { saveVisibleColumns(visibleCols.value) }
function applyPreset(kind) {
  visibleCols.value = kind === 'all'
    ? receivableColumns.map(c => c.prop)
    : [...COMMON_COLUMN_PROPS]
  onColsChange()
}
const capabilities = ref({ query: false, add: false, edit: false, importData: false, confirm: false, generate: false, exportData: false, deleteData: false, accountView: false })
const lastCompletedBatch = ref(null)
const statusMap = { DRAFT: '草稿', PENDING_REVIEW: '待核对', CONFIRMED: '已确认', ACTIVE: '已生效', TERMINATED: '已终止' }
const statusLabel = (status) => statusMap[status] || status || '-'
const statusType = (status) => status === 'ACTIVE' || status === 'CONFIRMED' ? 'success' : status === 'PENDING_REVIEW' ? 'warning' : 'info'

async function load() {
  loading.value = true
  try {
    const data = await receivableApi.page(query)
    list.value = data?.records || []
    total.value = data?.total || 0
  } finally { loading.value = false }
}
async function loadBatches() {
  if (!capabilities.value.confirm) return
  const batches = await receivableApi.batches()
  lastCompletedBatch.value = (batches || []).find(batch => batch.status === 'COMPLETED') || null
}
function afterImport() { load(); loadBatches() }
async function rollbackLastBatch() {
  await ElMessageBox.confirm('仅在尚未生成账单或后续财务数据时可以撤销。是否继续？')
  await receivableApi.rollback(lastCompletedBatch.value.id)
  ElMessage.success('导入批次已撤销')
  await Promise.all([load(), loadBatches()])
}
function reset() { Object.assign(query, { pageNo: 1, tenantName: '', spaceName: '', agreementNo: '', status: '' }); viewMode.value === 'list' ? load() : loadMonthlySummary() }

function formatMoney(value) {
  if (value == null) return '0.00'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadMonthlySummary() {
  if (!monthFilter.value) return
  monthlyLoading.value = true
  try {
    const data = await receivableApi.monthlySummary({ month: monthFilter.value, tenantName: query.tenantName, status: query.status })
    monthlySummary.value = data
    monthlyItems.value = data?.items || []
  } finally { monthlyLoading.value = false }
}

function onViewChange(mode) {
  if (mode === 'monthly') loadMonthlySummary()
  else load()
}

function monthlySummaryMethod({ columns }) {
  return columns.map((col, index) => {
    if (index === 0) return '合计'
    if (col.property === 'monthlyRent') return '¥' + formatMoney(monthlySummary.value?.totalRent)
    if (col.property === 'monthlyProperty') return '¥' + formatMoney(monthlySummary.value?.totalProperty)
    if (col.property === 'monthlyTotal') return '¥' + formatMoney(monthlySummary.value?.grandTotal)
    return ''
  })
}
function openDetail(row) { detailId.value = row.id; detailVisible.value = true }
function openEditor(row) { editor.form = row ? { ...row } : { monthlyRent: 0, monthlyProperty: 0, monthlyTotal: 0 }; editor.visible = true }
function openLateFee(row) {
  Object.assign(lateFee, { visible: true, saving: false, id: row.id, tenantName: row.tenantNameRaw || '-', date: row.lateFeeStartDate || null })
}
async function saveLateFee() {
  lateFee.saving = true
  try {
    await receivableApi.updateLateFeeStart(lateFee.id, lateFee.date || null)
    ElMessage.success(lateFee.date ? `滞纳金将从 ${lateFee.date} 起算，逾期账单已重算` : '已恢复默认滞纳金口径，逾期账单已重算')
    lateFee.visible = false
    await load()
  } finally { lateFee.saving = false }
}
async function saveEditor() { editor.form.id ? await receivableApi.update(editor.form) : await receivableApi.add(editor.form); editor.visible = false; ElMessage.success('保存成功'); load() }
async function generate(row) { await ElMessageBox.confirm('将按登记明细中的合同期限、免租期和收款约定生成或同步账单，是否继续？'); const result = await receivableApi.generate(row.id); ElMessage.success(`新增 ${result?.inserted || 0} 条，同步 ${result?.updated || 0} 条，跳过 ${result?.skipped || 0} 条`); await load() }
async function remove(row) {
  await ElMessageBox.confirm(`确认删除「${row.tenantNameRaw || '该登记表'}」这条应收登记？`, '确认删除', { type: 'warning' })
  await receivableApi.remove(row.id); ElMessage.success('删除成功'); load()
}
function hasRowActions(row) {
  return capabilities.value.generate || capabilities.value.deleteData || capabilities.value.edit
    || ['DRAFT', 'PENDING_REVIEW'].includes(row.status)
}
function onRowAction(command, row) {
  if (command === 'edit') return openEditor(row)
  if (command === 'generate') return generate(row)
  if (command === 'lateFee') return openLateFee(row)
  if (command === 'delete') return remove(row)
}
async function downloadExport() {
  const response = await receivableApi.export()
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a'); link.href = url; link.download = '应收明细登记表.xlsx'; link.click(); URL.revokeObjectURL(url)
}
onMounted(async () => {
  capabilities.value = await receivableApi.capabilities() || capabilities.value
  const tasks = []
  if (capabilities.value.query) tasks.push(load())
  if (capabilities.value.confirm) tasks.push(loadBatches())
  await Promise.allSettled(tasks)
})

// keep-alive 缓存下,从账单页跳回来要重拉列表,账单数/状态才不停留在离开前的快照。
// 首次挂载 onMounted 已经拉过,onActivated 的第一次触发跳过,避免双拉
let activatedBefore = false
onActivated(() => {
  if (!activatedBefore) { activatedBefore = true; return }
  if (capabilities.value.query) load()
})
</script>

<style scoped>
.page-header { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:16px; }
.page-header h2 { margin:0 0 6px; }.page-header p { margin:0; color:var(--el-text-color-secondary); }
.toolbar { display:flex; gap:8px; }.filter-bar,.table-card { padding:16px; background:#fff; border-radius:8px; margin-bottom:12px; }
.batch-bar { display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; padding:10px 14px; border:1px solid #e1f3d8; border-radius:8px; background:#f0f9eb; color:#529b2e; }
.pager { margin-top:16px; justify-content:flex-end; }
.summary-cards {
  display: flex; gap: 12px; margin-bottom: 12px; flex-wrap: wrap;
}
.summary-card {
  flex: 1; min-width: 140px; padding: 16px; background: #fff; border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
}
.summary-card .card-label { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 6px; }
.summary-card .card-value { font-size: 20px; font-weight: 600; color: var(--el-text-color-primary); }
.summary-card.rent .card-value { color: #e6a23c; }
.summary-card.property .card-value { color: #409eff; }
.summary-card.total .card-value { color: #67c23a; }
.amount-highlight { font-weight: 600; color: var(--el-color-primary); }
.col-picker-bar { display: flex; align-items: center; gap: 10px; padding-bottom: 8px; border-bottom: 1px solid var(--el-border-color-lighter); }
.col-picker-hint { margin-left: auto; font-size: 12px; color: var(--el-text-color-secondary); }
.col-picker-list { display: grid; grid-template-columns: 1fr 1fr; gap: 2px 8px; max-height: 340px; overflow-y: auto; padding-top: 8px; }
.late-fee-tip { margin: 4px 12px 0 110px; font-size: 12px; line-height: 1.6; color: var(--el-text-color-secondary); }
</style>
