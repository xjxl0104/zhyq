<template>
  <div class="page-container">
    <!-- 合同类型页签 -->
    <el-tabs v-model="typeTab" @tab-change="onTypeTab">
      <el-tab-pane label="全部合同" name="all" />
      <el-tab-pane label="正式合同" name="1" />
      <el-tab-pane label="意向合同" name="2" />
      <el-tab-pane label="草稿合同" name="3" />
      <el-tab-pane label="电子合同" name="4" />
      <el-tab-pane label="优惠合同" name="5" />
      <el-tab-pane label="成本合同" name="6" />
      <el-tab-pane label="合作合同" name="7" />
    </el-tabs>

    <!-- 查询区 -->
    <details class="search-bar" :open="!isMobile">
      <summary class="search-bar__summary">查询与筛选</summary>
      <el-form :inline="true" :model="query">
        <el-form-item label="合同编号">
          <el-input v-model="query.code" placeholder="请输入合同编号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="租客">
          <el-select v-model="query.tenantRefId" placeholder="全部" clearable filterable style="width: 180px">
            <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="clearFilter">清空条件</el-button>
          <!-- 与账单页同口径:重置 = 真清数据,不是清筛选条件 -->
          <el-button type="danger" plain @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </details>

    <!-- 表格区 -->
    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增合同</el-button>
        <el-button @click="importDialog.visible = true"><el-icon><Upload /></el-icon>导入合同</el-button>
        <el-button type="success" plain @click="triggerPhoto"><el-icon><Camera /></el-icon>拍照录入</el-button>
        <input ref="photoInput" type="file" accept="image/*" capture="environment" hidden @change="onPhoto" />
      </div>
      <MobileRecordList v-if="isMobile" :items="list" :loading="loading" empty-text="暂无合同">
        <template #title="{ row }">
          <span>{{ row.code }}</span>
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
        <template #default="{ row, index }">
          <div class="mobile-record-summary">
            <strong>{{ tenantName(row.tenantRefId) }}</strong>
            <span>{{ projectName(row.projectId) }}</span>
          </div>
          <div class="mobile-record-meta">{{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}</div>
          <details class="mobile-record-details">
            <summary :aria-label="`查看合同 ${row.code} 全部信息`">查看全部信息</summary>
            <dl class="mobile-record-fields">
              <div class="mobile-record-field"><dt>序号</dt><dd>{{ index + 1 }}</dd></div>
              <div class="mobile-record-field mobile-record-field--wide"><dt>合同编号</dt><dd>{{ row.code || '-' }}</dd></div>
              <div class="mobile-record-field"><dt>租客</dt><dd>{{ tenantName(row.tenantRefId) }}</dd></div>
              <div class="mobile-record-field"><dt>园区</dt><dd>{{ projectName(row.projectId) }}</dd></div>
              <div class="mobile-record-field mobile-record-field--wide"><dt>起止日期</dt><dd>{{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}</dd></div>
              <div class="mobile-record-field"><dt>租赁单价</dt><dd>{{ row.rentPrice }} 元/㎡</dd></div>
              <div class="mobile-record-field"><dt>面积</dt><dd>{{ row.rentArea }} ㎡</dd></div>
              <div class="mobile-record-field"><dt>保证金</dt><dd>{{ money(row.deposit) }} 元</dd></div>
              <div class="mobile-record-field"><dt>状态</dt><dd>{{ statusText(row.status) }}</dd></div>
            </dl>
          </details>
        </template>
        <template #actions="{ row }">
          <el-button :aria-label="`查看合同 ${row.code} 详情`" link type="primary" @click="showDetail(row)">详情</el-button>
          <el-button v-if="row.status === 1" :aria-label="`编辑合同 ${row.code}`" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm v-if="row.status === 1" title="确认提交审批?" @confirm="submit(row.id)">
            <template #reference><el-button :aria-label="`提交合同 ${row.code} 审批`" link type="warning">提交审批</el-button></template>
          </el-popconfirm>
          <el-button v-if="row.status === 2" :aria-label="`办理合同 ${row.code} 审批`" link type="success" @click="openApproval(row.id)">办理审批</el-button>
          <el-popconfirm v-if="[5, 8].includes(row.status)" title="确认退租?房源将被释放" @confirm="terminate(row.id)">
            <template #reference><el-button :aria-label="`办理合同 ${row.code} 退租`" link type="danger">退租</el-button></template>
          </el-popconfirm>
          <el-popconfirm v-if="row.status === 1" title="确认删除?" @confirm="remove(row.id)">
            <template #reference><el-button :aria-label="`删除合同 ${row.code}`" link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </MobileRecordList>
      <el-table v-else :data="list" v-loading="loading" border stripe
                show-summary :summary-method="pageSummary">
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="code" label="合同编号" min-width="150" />
        <el-table-column label="租客" min-width="150">
          <template #default="{ row }">{{ tenantName(row.tenantRefId) }}</template>
        </el-table-column>
        <el-table-column label="园区" min-width="130">
          <template #default="{ row }">{{ projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column label="合同类型" width="100">
          <template #default="{ row }">{{ contractTypeText(row.contractType) }}</template>
        </el-table-column>
        <el-table-column label="起止日期" min-width="200">
          <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
        </el-table-column>
        <el-table-column label="租赁单价" width="110">
          <template #default="{ row }">{{ row.rentPrice }} 元/㎡</template>
        </el-table-column>
        <el-table-column prop="rentArea" label="面积" width="100">
          <template #default="{ row }">{{ row.rentArea }} ㎡</template>
        </el-table-column>
        <el-table-column prop="deposit" label="保证金" width="110">
          <template #default="{ row }">{{ money(row.deposit) }} 元</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button v-if="row.status === 1" link type="primary" @click="openDialog(row)">编辑</el-button>
            <!-- 草稿:提交审批 -->
            <el-popconfirm v-if="row.status === 1" title="确认提交审批?" @confirm="submit(row.id)">
              <template #reference><el-button link type="warning">提交审批</el-button></template>
            </el-popconfirm>
            <!-- 待审核:进入实际指派的审批任务 -->
            <el-button v-if="row.status === 2" link type="success" @click="openApproval(row.id)">办理审批</el-button>
            <!-- 执行中:退租 -->
            <el-popconfirm v-if="[5, 8].includes(row.status)" title="确认退租?房源将被释放" @confirm="terminate(row.id)">
              <template #reference><el-button link type="danger">退租</el-button></template>
            </el-popconfirm>
            <el-popconfirm v-if="row.status === 1" title="确认删除?" @confirm="remove(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="isMobile" class="mobile-page-summary" aria-live="polite">
        <strong>{{ pageTotals.label }}</strong>
        <span>面积 {{ pageTotals.rentArea }}</span>
        <span>保证金 {{ pageTotals.deposit }}</span>
      </div>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 表单弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="680px">
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="合同编号" prop="code">
              <el-input v-model="form.code" :disabled="!!form.id" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合同类型">
              <el-select v-model="form.contractType" style="width: 100%">
                <el-option label="正式" :value="1" />
                <el-option label="意向" :value="2" />
                <el-option label="草稿" :value="3" />
                <el-option label="电子" :value="4" />
                <el-option label="优惠" :value="5" />
                <el-option label="成本" :value="6" />
                <el-option label="合作" :value="7" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="租客" prop="tenantRefId">
              <el-select v-model="form.tenantRefId" placeholder="选择租客" filterable style="width: 100%">
                <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="园区" prop="projectId">
              <el-select v-model="form.projectId" placeholder="选择园区" style="width: 100%">
                <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="起始日期" prop="startDate">
              <!-- 填了起租日就按「合同设置」的默认租期自动推到期日;已填过到期日则不覆盖 -->
              <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD"
                              style="width: 100%" @change="onStartDateChange" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期" prop="endDate">
              <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="租赁单价">
              <el-input v-model.number="form.rentPrice" placeholder="元/㎡/月" @change="onRentBasisChange" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物业单价">
              <el-input v-model.number="form.propertyPrice" placeholder="元/㎡/月" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="租赁面积">
              <el-input v-model.number="form.rentArea" placeholder="㎡" @change="onRentBasisChange" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="保证金">
              <el-input v-model.number="form.deposit" placeholder="元" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计租方式">
              <el-select v-model="form.chargeMode" style="width: 100%">
                <el-option label="按面积单价" :value="1" />
                <el-option label="固定金额" :value="2" />
                <el-option label="阶梯单价" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="付款周期">
              <el-select v-model="form.payCycle" style="width: 100%">
                <el-option label="月付(1)" :value="1" />
                <el-option label="季付(3)" :value="3" />
                <el-option label="半年付(6)" :value="6" />
                <el-option label="年付(12)" :value="12" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="免租月数">
              <el-input v-model.number="form.freeMonths" placeholder="月" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="附件">
              <FileUpload v-model="attachFiles" biz-type="contract" :biz-id="form.id" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importDialog.visible" title="导入合同" width="520px" @closed="resetImportFiles">
      <el-tabs v-model="importDialog.mode">
        <el-tab-pane label="合同台账" name="sheet">
          <el-alert type="info" :closable="false" show-icon>
            支持 Excel（.xlsx/.xls）或 CSV。导入结果会先保存为草稿，请在列表中核对后再提交审批。
          </el-alert>
          <el-upload class="import-upload" drag :auto-upload="false" :limit="1"
                     accept=".xlsx,.xls,.csv" :on-change="onImportFile" :on-remove="() => importFile = null">
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到这里，或 <em>点击选择</em></div>
            <template #tip><div class="el-upload__tip">表头示例：合同编号、租客、园区、起始日期、结束日期、租赁单价、租赁面积、保证金</div></template>
          </el-upload>
        </el-tab-pane>
        <el-tab-pane label="PDF 合同原件" name="pdf">
          <el-alert type="info" :closable="false" show-icon>
            上传 PDF 原件后会打开新增合同草稿。请补齐合同信息并保存，PDF 将自动关联到该合同；在“合作合同”页签导入时，会自动设为合作合同。
          </el-alert>
          <el-upload class="import-upload" drag :auto-upload="false" :limit="1"
                     accept="application/pdf,.pdf" :on-change="onPdfFile" :on-remove="() => pdfFile = null">
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽 PDF 到这里，或 <em>点击选择</em></div>
            <template #tip><div class="el-upload__tip">单个文件不超过 100MB</div></template>
          </el-upload>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="importDialog.visible = false">取消</el-button>
        <el-button v-if="importDialog.mode === 'sheet'" type="primary" :loading="importDialog.loading" :disabled="!importFile" @click="doImport">开始导入</el-button>
        <el-button v-else type="primary" :loading="importDialog.loading" :disabled="!pdfFile" @click="doPdfImport">使用 PDF 新增合同</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { money } from '@/utils/format'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, UploadFilled, Camera } from '@element-plus/icons-vue'
import { contractApi } from '@/api/contract'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'
import { tenantApi } from '@/api/tenant'
import { projectApi } from '@/api/building'
import MobileRecordList from '@/components/MobileRecordList.vue'
import { useResponsive } from '@/composables/useResponsive'

const router = useRouter()
const { isMobile } = useResponsive()

// 租客/园区 名称映射(下拉 + 表格名称解析)
const tenants = ref([])
const projects = ref([])
const tenantName = (id) => tenants.value.find(t => t.id === id)?.name ?? (id ?? '-')
const projectName = (id) => projects.value.find(p => p.id === id)?.name ?? (id ?? '-')
async function loadRefs() {
  try {
    const [t, p] = await Promise.all([tenantApi.list(), projectApi.list()])
    tenants.value = t || []
    projects.value = p || []
  } catch (e) { /* 下拉数据失败不阻塞列表 */ }
}

const statusOptions = [
  { value: 1, label: '草稿' },
  { value: 2, label: '待审核' },
  { value: 3, label: '待签署' },
  { value: 4, label: '待执行' },
  { value: 5, label: '执行中' },
  { value: 6, label: '变更中' },
  { value: 7, label: '退租中' },
  { value: 8, label: '已到期' },
  { value: 9, label: '已终止' },
  { value: 10, label: '已归档' }
]
const statusMap = statusOptions.reduce((m, s) => (m[s.value] = s.label, m), {})
function statusText(v) { return statusMap[v] || '-' }
const contractTypeMap = { 1: '正式', 2: '意向', 3: '草稿', 4: '电子', 5: '优惠', 6: '成本', 7: '合作' }
function contractTypeText(v) { return contractTypeMap[v] || '-' }
function statusTagType(v) {
  // 待处理=warning / 执行中=primary / 成功=success / 终止异常=danger / 归档=info
  if ([2, 3, 4, 7].includes(v)) return 'warning'
  if ([5, 6].includes(v)) return 'primary'
  if (v === 8) return 'success'
  if (v === 9) return 'danger'
  if (v === 10) return 'info'
  return 'info' // 草稿
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const typeTab = ref('all')
const query = reactive({ pageNo: 1, pageSize: 10, code: '', tenantRefId: null, status: null, contractType: null })

/**
 * 本页合计 —— 列表是分页的,只能合计当前页已加载的行,故标「本页合计」而非「合计」,
 * 避免被当成全量总数去对账。手机和桌面共用汇总值，桌面按字段匹配列，避免增减列后错位。
 * 租赁单价是费率不是金额,合计无意义,留空。
 */
const pageTotals = computed(() => {
  const sum = (f) => list.value.reduce((acc, r) => acc + Number(r[f] || 0), 0)
  return {
    label: `本页合计 · ${list.value.length} 份`,
    rentArea: money(sum('rentArea')) + ' ㎡',
    deposit: money(sum('deposit')) + ' 元'
  }
})

function pageSummary({ columns }) {
  return columns.map((col) => {
    if (col.type === 'index') return pageTotals.value.label
    if (col.property === 'rentArea') return pageTotals.value.rentArea
    if (col.property === 'deposit') return pageTotals.value.deposit
    return ''
  })
}

function onTypeTab(name) {
  query.pageNo = 1
  query.contractType = name === 'all' ? null : Number(name)
  load()
}

async function load() {
  loading.value = true
  try {
    const res = await contractApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
/** 清空筛选条件(不动数据):页签选的合同类型保留,只清编号/租客/状态并回到第一页 */
function clearFilter() {
  Object.assign(query, { pageNo: 1, code: '', tenantRefId: null, status: null })
  load()
}

/**
 * 重置合同:录错/演示数据的重来通道。与账单页「重置」同一语义 ——
 * 作废未产生实收的全部合同,名下账单收过款的整份保留。
 */
async function reset() {
  try {
    await ElMessageBox.confirm(
      '将作废所有「未产生任何实收」的合同(名下账单收过款的整份保留)。' +
      '同时把关联房源放回可租、解除应收登记表的合同关联。确定重置?',
      '重置合同',
      { type: 'warning', confirmButtonText: '确定重置', cancelButtonText: '取消' }
    )
  } catch (e) {
    return // 用户取消
  }
  const res = await contractApi.reset()
  ElMessage.success(`已重置:作废 ${res.deleted} 份合同，保留 ${res.kept} 份(有实收)`)
  Object.assign(query, { pageNo: 1 })
  load()
}

const formRef = ref()
const photoInput = ref()
const importFile = ref(null)
const pdfFile = ref(null)
const importDialog = reactive({ visible: false, loading: false, mode: 'sheet' })
function onImportFile(uploadFile) { importFile.value = uploadFile.raw }
function onPdfFile(uploadFile) { pdfFile.value = uploadFile.raw }
function resetImportFiles() {
  importFile.value = null
  pdfFile.value = null
  importDialog.mode = 'sheet'
}
function triggerPhoto() { photoInput.value?.click() }
async function attachUploadedContractFile(file, description) {
  const body = new FormData()
  body.append('file', file)
  body.append('bizType', 'contract')
  const uploaded = await fileApi.upload(body)
  await openDialog()
  form.remark = `${description}：${file.name}（请核对合同字段后保存）`
  attachFiles.value = [uploaded]
}
async function onPhoto(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  try {
    await attachUploadedContractFile(file, '拍照附件')
    ElMessage.success('照片已加入合同草稿，请核对字段后保存')
  } catch (e) { /* 请求拦截器已提示 */ }
}
async function doImport() {
  if (!importFile.value) return
  importDialog.loading = true
  try {
    const body = new FormData(); body.append('file', importFile.value)
    const result = await contractApi.importFile(body)
    const errors = result.errors?.length ? `，${result.errors.length} 行需检查` : ''
    ElMessage.success(`成功导入 ${result.imported} 份，跳过 ${result.skipped} 份${errors}`)
    importDialog.visible = false; importFile.value = null; load()
  } finally { importDialog.loading = false }
}
async function doPdfImport() {
  if (!pdfFile.value) return
  importDialog.loading = true
  try {
    const file = pdfFile.value
    importDialog.visible = false
    await attachUploadedContractFile(file, '导入 PDF 合同原件')
    ElMessage.success('PDF 已加入合同草稿，请核对字段后保存')
  } finally { importDialog.loading = false }
}
const dialog = reactive({ visible: false, title: '' })
const emptyForm = () => ({
  id: null, code: '', contractType: 1, tenantRefId: null, projectId: null,
  startDate: '', endDate: '', rentPrice: 0, propertyPrice: 0, rentArea: 0,
  deposit: 0, chargeMode: 1, payCycle: 3, freeMonths: 0, remark: ''
})
const form = reactive(emptyForm())
const attachFiles = ref([])
const rules = {
  code: [{ required: true, message: '请输入合同编号（留空保存时按设置自动生成）', trigger: 'blur' }],
  tenantRefId: [{ required: true, message: '请选择租客', trigger: 'change' }],
  projectId: [{ required: true, message: '请选择园区', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择起始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }]
}

// 合同设置里的默认值,openDialog 时取一次;取不到就用表单自带的空值,不挡住新增
const settingDefaults = reactive({ termYears: null, depositMonths: null })

async function openDialog(row) {
  if (row && row.status !== 1) return ElMessage.warning('仅草稿合同可编辑，已提交合同请使用对应业务流程')
  dialog.visible = true
  dialog.title = row ? '编辑合同' : '新增合同'
  attachFiles.value = []
  if (row) {
    Object.assign(form, row)
    // 编辑:载入已关联附件
    try { attachFiles.value = await fileApi.list('contract', row.id) } catch (e) { /* 忽略 */ }
    return
  }
  Object.assign(form, emptyForm())
  // 从“合作合同”页签新增或导入 PDF 时，默认写入合作合同类型。
  if (typeTab.value === '7') form.contractType = 7
  // 新增:编号与租期/保证金规则都来自「合同设置」,用户不必对着空框猜格式
  try {
    const d = await contractApi.defaults()
    if (d?.code) form.code = d.code
    settingDefaults.termYears = d?.termYears ?? null
    settingDefaults.depositMonths = d?.depositMonths ?? null
  } catch (e) { /* 设置取不到不影响手工填写 */ }
}

// 起租日一填,按设置里的默认租期推到期日;用户改过到期日就不覆盖
function onStartDateChange(value) {
  if (!value || form.id || !settingDefaults.termYears || form.endDate) return
  const start = new Date(value)
  const end = new Date(start.getFullYear() + settingDefaults.termYears, start.getMonth(), start.getDate())
  end.setDate(end.getDate() - 1)   // 6 年租期 2026-07-01 起 → 2032-06-30 止
  form.endDate = `${end.getFullYear()}-${String(end.getMonth() + 1).padStart(2, '0')}-${String(end.getDate()).padStart(2, '0')}`
}

// 月租金 = 单价 × 面积;保证金 = 月租金 × 设置里的月数。用户手改过保证金就不再覆盖
function onRentBasisChange() {
  if (form.id || !settingDefaults.depositMonths) return
  const monthly = Number(form.rentPrice || 0) * Number(form.rentArea || 0)
  if (monthly > 0 && !form.deposit) {
    form.deposit = Math.round(monthly * settingDefaults.depositMonths * 100) / 100
  }
}
async function submitForm() {
  await formRef.value.validate()
  let contractId = form.id
  if (form.id) {
    await contractApi.update(form)
  } else {
    contractId = await contractApi.add(form)
  }
  // 先传后回填:把新上传(bizId 为空)的附件关联到本合同
  const pendingIds = (attachFiles.value || [])
    .filter(f => f && f.id && !f.bizId)
    .map(f => f.id)
  if (contractId && pendingIds.length) {
    try { await fileApi.attach('contract', contractId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await contractApi.remove(id)
  ElMessage.success('删除成功')
  load()
}
async function submit(id) {
  await contractApi.submit(id)
  ElMessage.success('已提交审批')
  load()
}
function openApproval(id) { router.push({ path: '/oa/approval', query: { bizType: 'contract', bizId: String(id) } }) }
async function terminate(id) {
  await contractApi.terminate(id)
  ElMessage.success('已退租')
  load()
}

function showDetail(row) {
  router.push(`/contract/detail/${row.id}`)
}

onMounted(() => { loadRefs(); load() })
</script>

<style scoped>
.mobile-page-summary { display: grid; gap: 6px; margin-top: 16px; padding-top: 12px; border-top: 1px solid var(--border); overflow-wrap: anywhere; }
.pager { margin-top: 16px; justify-content: flex-end; }
.import-upload { margin-top: 20px; }
.import-upload :deep(.el-upload), .import-upload :deep(.el-upload-dragger) { width: 100%; }
</style>
