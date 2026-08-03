<template>
  <div class="page-container receivable-page">
    <div class="page-header">
      <div>
        <h2>应收明细登记表</h2>
        <p>以园区租金及物业管理费基础资料为权威口径</p>
      </div>
      <div class="toolbar">
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
      <el-form-item label="租户"><el-input v-model="query.tenantName" clearable /></el-form-item>
      <el-form-item label="空间"><el-input v-model="query.spaceName" clearable /></el-form-item>
      <el-form-item label="协议编号"><el-input v-model="query.agreementNo" clearable /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable style="width: 130px">
          <el-option label="草稿" value="DRAFT" /><el-option label="待核对" value="PENDING_REVIEW" />
          <el-option label="已确认" value="CONFIRMED" /><el-option label="已生效" value="ACTIVE" />
        </el-select>
      </el-form-item>
      <el-form-item><el-button type="primary" @click="load">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
    </el-form>

    <div class="table-card">
      <el-table :data="list" v-loading="loading" border stripe height="calc(100vh - 300px)" @row-dblclick="openDetail">
        <el-table-column v-for="column in receivableColumns" :key="column.prop"
          :prop="column.prop" :label="column.label" :width="column.width"
          :fixed="column.fixed" :align="column.align" show-overflow-tooltip>
          <template #default="{ row }">{{ formatReceivableCell(row, column) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" fixed="right">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="capabilities.edit && ['DRAFT', 'PENDING_REVIEW'].includes(row.status)" link type="primary" @click="openEditor(row)">编辑</el-button>
            <el-button v-if="capabilities.generate" link type="success" @click="generate(row)">生成账单</el-button>
            <el-popconfirm v-if="capabilities.deleteData" title="确认删除该登记表?" @confirm="remove(row)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
        :total="total" v-model:current-page="query.pageNo" v-model:page-size="query.pageSize"
        :page-sizes="[20, 50, 100]" @change="load" />
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { receivableApi } from '@/api/receivable'
import ReceivableDetailDrawer from './components/ReceivableDetailDrawer.vue'
import ReceivableImportDialog from './components/ReceivableImportDialog.vue'
import { formatReceivableCell, receivableColumns } from './receivableModel'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 20, tenantName: '', spaceName: '', agreementNo: '', status: '' })
const importVisible = ref(false)
const detailVisible = ref(false)
const detailId = ref(null)
const editor = reactive({ visible: false, form: {} })
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
function reset() { Object.assign(query, { pageNo: 1, tenantName: '', spaceName: '', agreementNo: '', status: '' }); load() }
function openDetail(row) { detailId.value = row.id; detailVisible.value = true }
function openEditor(row) { editor.form = row ? { ...row } : { monthlyRent: 0, monthlyProperty: 0, monthlyTotal: 0 }; editor.visible = true }
async function saveEditor() { editor.form.id ? await receivableApi.update(editor.form) : await receivableApi.add(editor.form); editor.visible = false; ElMessage.success('保存成功'); load() }
async function generate(row) { await ElMessageBox.confirm('将按已确认规则生成分账账单，是否继续？'); const result = await receivableApi.generate(row.id); ElMessage.success(`已生成 ${result?.inserted || 0} 条，跳过 ${result?.skipped || 0} 条`) }
async function remove(row) { await receivableApi.remove(row.id); ElMessage.success('删除成功'); load() }
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
</script>

<style scoped>
.page-header { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:16px; }
.page-header h2 { margin:0 0 6px; }.page-header p { margin:0; color:var(--el-text-color-secondary); }
.toolbar { display:flex; gap:8px; }.filter-bar,.table-card { padding:16px; background:#fff; border-radius:8px; margin-bottom:12px; }
.batch-bar { display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; padding:10px 14px; border:1px solid #e1f3d8; border-radius:8px; background:#f0f9eb; color:#529b2e; }
.pager { margin-top:16px; justify-content:flex-end; }
</style>
