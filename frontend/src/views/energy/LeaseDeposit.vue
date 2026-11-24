<template>
  <div class="page-container">
    <div class="summary-grid">
      <div class="summary-item">
        <span>在租合同</span>
        <strong>{{ activeContracts.length }} 份</strong>
      </div>
      <div class="summary-item">
        <span>应收保证金合计</span>
        <strong>¥{{ money(totals.due) }}</strong>
      </div>
      <div class="summary-item ok">
        <span>已缴保证金合计</span>
        <strong>¥{{ money(totals.paid) }}</strong>
      </div>
      <div class="summary-item warn">
        <span>待收保证金余额</span>
        <strong>¥{{ money(totals.unpaid) }}</strong>
      </div>
      <div class="summary-item">
        <span>已缴清 / 未缴清</span>
        <strong>{{ totals.fullCount }} / {{ totals.pendingCount }} 份</strong>
      </div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索租户名称 / 楼层区域 / 协议号" clearable style="width: 260px" />
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="全部状态" value="" />
          <el-option label="已缴纳" value="PAID" />
          <el-option label="部分缴纳" value="PARTIAL" />
          <el-option label="未缴纳" value="UNPAID" />
        </el-select>
        <el-checkbox v-model="showDisabled">显示停用合同</el-checkbox>
        <div class="spacer" />
        <el-button type="primary" plain @click="contractDialog = true">合同基础台账</el-button>
      </div>
      <div class="toolbar-tip">
        保证金按合同一次性收取（无需开具发票），系统按租户合同逐份管理租金保证金与物业保证金；点击「缴纳 / 回执」上传汇款回执或收据后即可标记为「已缴纳」。
      </div>

      <el-table :data="viewRows" v-loading="loading" border size="small" row-key="id"
                :row-class-name="rowClass" show-summary :summary-method="tableSummary">
        <el-table-column prop="seqNo" label="序号" width="55" align="center" />
        <el-table-column prop="tenantName" label="租户/公司名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="unitName" label="楼层/区域" min-width="175" show-overflow-tooltip />
        <el-table-column label="合同起止日" width="175" align="center">
          <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
        </el-table-column>

        <el-table-column label="租金保证金" min-width="160" align="right">
          <template #default="{ row }">
            <div class="dep-cell">
              <div>应收 <strong>¥{{ money(row.rentDeposit) }}</strong></div>
              <div class="dep-sub">
                <span>已缴 ¥{{ money(row.rentDepositPaid) }}</span>
                <el-tag :type="depTag(row.rentDepositStatus).type" size="small" effect="plain">
                  {{ depTag(row.rentDepositStatus).label }}
                </el-tag>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="物业保证金" min-width="160" align="right">
          <template #default="{ row }">
            <div v-if="hasPropDeposit(row)" class="dep-cell">
              <div>应收 <strong>¥{{ money(row.propertyDeposit) }}</strong></div>
              <div class="dep-sub">
                <span>已缴 ¥{{ money(row.propertyDepositPaid) }}</span>
                <el-tag :type="depTag(row.propertyDepositStatus).type" size="small" effect="plain">
                  {{ depTag(row.propertyDepositStatus).label }}
                </el-tag>
              </div>
            </div>
            <span v-else class="muted">无需单独缴纳</span>
          </template>
        </el-table-column>

        <el-table-column label="保证金合计(应收)" width="135" align="right">
          <template #default="{ row }"><strong>¥{{ money(rowTotalDue(row)) }}</strong></template>
        </el-table-column>

        <el-table-column label="保证金合计(已缴)" width="135" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-ok': rowTotalPaid(row) > 0 }">¥{{ money(rowTotalPaid(row)) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="depositTerms" label="缴纳约定" min-width="190" show-overflow-tooltip />

        <el-table-column label="缴纳状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="overallMeta(row).type" size="small">{{ overallMeta(row).label }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="回执" width="95" align="center">
          <template #default="{ row }">
            <el-button link :type="receiptTotal(row) > 0 ? 'success' : 'info'" @click="openDeposit(row)">
              {{ receiptTotal(row) > 0 ? `已传 ${receiptTotal(row)} 份` : '未上传' }}
            </el-button>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDeposit(row)">缴纳 / 回执</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 保证金缴纳登记与回执上传弹窗 -->
    <el-dialog v-model="dialog.visible" :title="`保证金缴纳登记与回执 - ${dialog.row?.tenantName || ''}`"
               width="780px" top="4vh" destroy-on-close :close-on-click-modal="false">
      <el-descriptions :column="2" border size="small" class="info-box">
        <el-descriptions-item label="租户/公司">{{ dialog.row?.tenantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="楼层/区域">{{ dialog.row?.unitName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="合同起止日">{{ dialog.row?.startDate }} ~ {{ dialog.row?.endDate }}</el-descriptions-item>
        <el-descriptions-item label="协议编号">{{ dialog.row?.agreementNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="缴纳约定" :span="2">{{ dialog.row?.depositTerms || '暂无特别约定' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 租金保证金区 -->
      <div class="section-card rent">
        <div class="sec-head">
          <strong>一、租金保证金</strong>
          <el-button type="success" plain size="small" @click="markFull('rent')">一键标记全额已缴</el-button>
        </div>
        <el-form :model="form" label-width="110px" size="small">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="应收金额(元)">
                <el-input-number v-model="form.rentDeposit" :min="0" :precision="2" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="已缴金额(元)">
                <el-input-number v-model="form.rentDepositPaid" :min="0" :precision="2" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="缴纳状态">
                <el-radio-group v-model="form.rentDepositStatus">
                  <el-radio :value="0">未缴纳</el-radio>
                  <el-radio :value="1">部分缴纳</el-radio>
                  <el-radio :value="2">已缴纳</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="缴纳日期">
                <el-date-picker v-model="form.rentDepositPaidDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <div class="receipt-box">
          <div class="receipt-head">
            <span>租金保证金回执（支持图片 / PDF / 压缩包，上传即保存）</span>
            <el-upload multiple :show-file-list="false" :http-request="(opt) => uploadReceipt('rent', opt)" :before-upload="beforeUpload">
              <el-button type="primary" plain size="small" :loading="uploading.rent > 0">上传回执</el-button>
            </el-upload>
          </div>
          <ul v-if="rentFiles.length" class="file-list">
            <li v-for="f in rentFiles" :key="f.id">
              <span class="file-name" :title="f.originalName">{{ f.originalName }}</span>
              <el-button link type="primary" size="small" @click="downloadFile(f)">下载</el-button>
              <el-button link type="danger" size="small" @click="removeFile('rent', f)">删除</el-button>
            </li>
          </ul>
          <div v-else class="muted">暂无租金保证金回执</div>
        </div>
      </div>

      <!-- 物业保证金区 -->
      <div class="section-card property">
        <div class="sec-head">
          <strong>二、物业保证金</strong>
          <el-button v-if="Number(form.propertyDeposit) > 0" type="success" plain size="small" @click="markFull('property')">一键标记全额已缴</el-button>
          <span v-else class="muted">如合同未单独约定物业保证金，可留空</span>
        </div>
        <el-form :model="form" label-width="110px" size="small">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="应收金额(元)">
                <el-input-number v-model="form.propertyDeposit" :min="0" :precision="2" :controls="false" placeholder="无则留空" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="已缴金额(元)">
                <el-input-number v-model="form.propertyDepositPaid" :min="0" :precision="2" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="缴纳状态">
                <el-radio-group v-model="form.propertyDepositStatus">
                  <el-radio :value="0">未缴纳</el-radio>
                  <el-radio :value="1">部分缴纳</el-radio>
                  <el-radio :value="2">已缴纳</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="缴纳日期">
                <el-date-picker v-model="form.propertyDepositPaidDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <div class="receipt-box">
          <div class="receipt-head">
            <span>物业保证金回执</span>
            <el-upload multiple :show-file-list="false" :http-request="(opt) => uploadReceipt('property', opt)" :before-upload="beforeUpload">
              <el-button type="primary" plain size="small" :loading="uploading.property > 0">上传回执</el-button>
            </el-upload>
          </div>
          <ul v-if="propertyFiles.length" class="file-list">
            <li v-for="f in propertyFiles" :key="f.id">
              <span class="file-name" :title="f.originalName">{{ f.originalName }}</span>
              <el-button link type="primary" size="small" @click="downloadFile(f)">下载</el-button>
              <el-button link type="danger" size="small" @click="removeFile('property', f)">删除</el-button>
            </li>
          </ul>
          <div v-else class="muted">暂无物业保证金回执</div>
        </div>
      </div>

      <el-form :model="form" label-width="90px" size="small" style="margin-top: 12px">
        <el-form-item label="缴纳备注">
          <el-input v-model="form.depositRemark" type="textarea" :rows="2" placeholder="可记录分期到账流水号、收据号或经办说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialog.visible = false">关闭</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="saveDeposit">保存登记</el-button>
      </template>
    </el-dialog>

    <LeaseContractDialog v-model="contractDialog" @changed="load" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { leaseFeeApi } from '@/api/energy'
import { fileApi } from '@/api/file'
import { startFileDownload } from '@/utils/fileDownload'
import { money } from './feePeriod'
import LeaseContractDialog from './components/LeaseContractDialog.vue'

const BIZ_RENT = 'lease_deposit_rent_receipt'
const BIZ_PROP = 'lease_deposit_property_receipt'

const loading = ref(false)
const list = ref([])
const keyword = ref('')
const statusFilter = ref('')
const showDisabled = ref(false)
const contractDialog = ref(false)

const activeContracts = computed(() => list.value.filter((r) => r.status === 1))
const hasPropDeposit = (r) => Number(r.propertyDeposit || 0) > 0
const rowTotalDue = (r) => Number(r.rentDeposit || 0) + Number(r.propertyDeposit || 0)
const rowTotalPaid = (r) => Number(r.rentDepositPaid || 0) + Number(r.propertyDepositPaid || 0)
const receiptTotal = (r) => Number(r.rentReceiptCount || 0) + Number(r.propertyReceiptCount || 0)

function overallState(r) {
  const rentNeed = Number(r.rentDeposit || 0) > 0
  const propNeed = hasPropDeposit(r)
  const rentSt = Number(r.rentDepositStatus || 0)
  const propSt = Number(r.propertyDepositStatus || 0)
  const rentDone = !rentNeed || rentSt === 2
  const propDone = !propNeed || propSt === 2
  if ((rentNeed || propNeed) && rentDone && propDone) return 'PAID'
  if (rentSt > 0 || propSt > 0 || rowTotalPaid(r) > 0) return 'PARTIAL'
  return 'UNPAID'
}

function overallMeta(r) {
  const s = overallState(r)
  if (s === 'PAID') return { label: '已缴纳', type: 'success' }
  if (s === 'PARTIAL') return { label: '部分缴纳', type: 'warning' }
  return { label: '未缴纳', type: 'info' }
}

function depTag(st) {
  const n = Number(st || 0)
  if (n === 2) return { label: '已缴纳', type: 'success' }
  if (n === 1) return { label: '部分缴纳', type: 'warning' }
  return { label: '未缴纳', type: 'info' }
}

const viewRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return list.value.filter((r) => {
    if (!showDisabled.value && r.status !== 1) return false
    if (statusFilter.value && overallState(r) !== statusFilter.value) return false
    if (!kw) return true
    return `${r.tenantName || ''} ${r.unitName || ''} ${r.agreementNo || ''}`.toLowerCase().includes(kw)
  })
})

const totals = computed(() => {
  const rows = activeContracts.value
  let due = 0, paid = 0, fullCount = 0
  for (const r of rows) {
    due += rowTotalDue(r)
    paid += rowTotalPaid(r)
    if (overallState(r) === 'PAID') fullCount++
  }
  return { due, paid, unpaid: Math.max(0, due - paid), fullCount, pendingCount: Math.max(0, rows.length - fullCount) }
})

function rowClass({ row }) {
  return overallState(row) === 'PAID' ? 'deposit-row-paid' : ''
}

function tableSummary({ columns, data }) {
  const sum = (fn) => data.reduce((s, r) => s + Number(fn(r) || 0), 0)
  return columns.map((col, i) => {
    if (i === 0) return '合计'
    switch (col.label) {
      case '保证金合计(应收)': return '¥' + money(sum(rowTotalDue))
      case '保证金合计(已缴)': return '¥' + money(sum(rowTotalPaid))
      default: return ''
    }
  })
}

async function load() {
  loading.value = true
  try {
    list.value = await leaseFeeApi.deposits(true) || []
  } finally {
    loading.value = false
  }
}

const dialog = reactive({ visible: false, saving: false, row: null })
const form = reactive({})
const rentFiles = ref([])
const propertyFiles = ref([])
const uploading = reactive({ rent: 0, property: 0 })

async function openDeposit(row) {
  dialog.row = row
  Object.assign(form, {
    rentDeposit: row.rentDeposit == null ? null : Number(row.rentDeposit),
    rentDepositPaid: Number(row.rentDepositPaid || 0),
    rentDepositStatus: Number(row.rentDepositStatus || 0),
    rentDepositPaidDate: row.rentDepositPaidDate || null,
    propertyDeposit: row.propertyDeposit == null ? null : Number(row.propertyDeposit),
    propertyDepositPaid: Number(row.propertyDepositPaid || 0),
    propertyDepositStatus: Number(row.propertyDepositStatus || 0),
    propertyDepositPaidDate: row.propertyDepositPaidDate || null,
    depositRemark: row.depositRemark || ''
  })
  rentFiles.value = []
  propertyFiles.value = []
  dialog.visible = true
  const [rf, pf] = await Promise.all([
    fileApi.list(BIZ_RENT, row.id).catch(() => []),
    fileApi.list(BIZ_PROP, row.id).catch(() => [])
  ])
  rentFiles.value = rf || []
  propertyFiles.value = pf || []
}

function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function markFull(kind) {
  if (kind === 'rent') {
    form.rentDepositStatus = 2
    form.rentDepositPaid = Number(form.rentDeposit || 0)
    if (!form.rentDepositPaidDate) form.rentDepositPaidDate = todayStr()
  } else {
    form.propertyDepositStatus = 2
    form.propertyDepositPaid = Number(form.propertyDeposit || 0)
    if (!form.propertyDepositPaidDate) form.propertyDepositPaidDate = todayStr()
  }
}

function beforeUpload(file) {
  if (file.size > 100 * 1024 * 1024) {
    ElMessage.error('回执文件不能超过 100MB')
    return false
  }
  return true
}

async function uploadReceipt(kind, { file }) {
  const id = dialog.row?.id
  if (!id) return
  const bizType = kind === 'rent' ? BIZ_RENT : BIZ_PROP
  uploading[kind]++
  try {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('bizType', bizType)
    fd.append('bizId', id)
    const saved = await fileApi.upload(fd, { timeout: 10 * 60 * 1000 })
    const target = kind === 'rent' ? rentFiles : propertyFiles
    target.value = [...target.value.filter((x) => String(x.id) !== String(saved.id)), saved]
    // 上传回执后如仍为未缴纳，自动勾选全额已缴，省去重复填写
    if (kind === 'rent' && form.rentDepositStatus === 0) markFull('rent')
    if (kind === 'property' && form.propertyDepositStatus === 0 && Number(form.propertyDeposit) > 0) markFull('property')
    ElMessage.success('回执已上传，点击「保存登记」同步缴纳状态')
  } finally {
    uploading[kind]--
  }
}

async function removeFile(kind, file) {
  await ElMessageBox.confirm(`确定删除回执「${file.originalName}」？`, '删除回执', { type: 'warning' })
  await fileApi.remove(file.id)
  if (kind === 'rent') rentFiles.value = rentFiles.value.filter((x) => String(x.id) !== String(file.id))
  else propertyFiles.value = propertyFiles.value.filter((x) => String(x.id) !== String(file.id))
}

async function downloadFile(file) {
  try { await startFileDownload(file.id, file.originalName) }
  catch (e) { ElMessage.error(e?.message || '下载失败') }
}

async function saveDeposit() {
  if (!dialog.row?.id) return
  dialog.saving = true
  try {
    await leaseFeeApi.updateDeposit(dialog.row.id, { ...form })
    ElMessage.success('保证金缴纳状态已保存')
    dialog.visible = false
    await load()
  } finally {
    dialog.saving = false
  }
}

onMounted(load)
</script>

<style scoped>
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 10px; margin-bottom: 12px; }
.summary-item { padding: 12px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; display: flex; flex-direction: column; gap: 4px; background: var(--el-fill-color-blank); }
.summary-item span { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-item strong { font-size: 18px; font-variant-numeric: tabular-nums; }
.summary-item.ok strong { color: var(--el-color-success); }
.summary-item.warn strong { color: var(--el-color-danger); }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; flex-wrap: wrap; }
.spacer { flex: 1; }
.toolbar-tip { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 12px; }
.dep-cell { display: flex; flex-direction: column; gap: 2px; font-variant-numeric: tabular-nums; }
.dep-sub { display: flex; justify-content: flex-end; align-items: center; gap: 6px; font-size: 12px; color: var(--el-text-color-secondary); }
.text-ok { color: var(--el-color-success); font-weight: 600; }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
.info-box { margin-bottom: 14px; }
.section-card { padding: 12px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; margin-bottom: 12px; }
.section-card.rent { border-left: 4px solid var(--el-color-primary); }
.section-card.property { border-left: 4px solid var(--el-color-success); }
.sec-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.receipt-box { border-top: 1px dashed var(--el-border-color); padding-top: 10px; margin-top: 4px; }
.receipt-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; font-size: 12px; color: var(--el-text-color-secondary); }
.file-list { list-style: none; padding: 0; margin: 0; }
.file-list li { display: flex; align-items: center; gap: 8px; padding: 5px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.file-name { flex: 1; min-width: 0; overflow-wrap: anywhere; font-size: 12px; }
:deep(.el-table__body tr.deposit-row-paid > td.el-table__cell) { background-color: var(--el-color-success-light-9) !important; }
:deep(.el-table__body tr.deposit-row-paid:hover > td.el-table__cell) { background-color: var(--el-color-success-light-8) !important; }
</style>
