<template>
  <div class="lease-summary-detail">
    <div class="detail-head">
      <el-button @click="$emit('back')"><el-icon><ArrowLeft /></el-icon>返回年月表</el-button>
      <h3>{{ monthLabel }} · 租金与物业管理费缴费汇总</h3>
      <el-tag v-if="period" :type="locked ? 'success' : 'info'">{{ locked ? '已确认出账' : '草稿' }}</el-tag>
      <span v-if="period?.generatedTime" class="gen-time">通知单生成于 {{ period.generatedTime }}</span>
    </div>

    <el-alert v-if="period?.noticeStale && !locked" type="warning" show-icon :closable="false" style="margin-bottom: 10px"
              title="生成通知单之后，合同参数、收取单位或减免系数又有改动，下面的通知单是旧的。请重新生成后再核对、确认出账。">
      <el-button type="warning" size="small" :loading="generating" @click="generate">立即重新生成</el-button>
    </el-alert>

    <FeeBillFiles v-if="period" :period-id="period.id" :kinds="[BILL_KINDS.rent, BILL_KINDS.property]" />

    <!-- 收取单位分开提示卡片 -->
    <div v-if="period" class="payee-bar">
      <div class="payee-card rent">
        <div class="payee-head">
          <el-tag type="primary" size="small">租金收取单位</el-tag>
          <strong>{{ period.rentPayeeName || '未设置（点下方「收取单位与抬头」配置）' }}</strong>
        </div>
        <div class="payee-acc">{{ period.rentPaymentAccount || '未填写租金收款账户/方式' }}</div>
      </div>
      <div class="payee-card property">
        <div class="payee-head">
          <el-tag type="success" size="small">物业管理费收取单位</el-tag>
          <strong>{{ period.propertyPayeeName || '未设置（点下方「收取单位与抬头」配置）' }}</strong>
        </div>
        <div class="payee-acc">{{ period.propertyPaymentAccount || '未填写物业管理费收款账户/方式' }}</div>
      </div>
    </div>

    <div v-if="period" class="summary-grid">
      <div class="summary-item"><span>租金通知单</span><strong>{{ rentNotices.length }} 张</strong></div>
      <div class="summary-item hl"><span>租金应收合计</span><strong>¥{{ money(rentTotalDue) }}</strong></div>
      <div class="summary-item"><span>物业费通知单</span><strong>{{ propertyNotices.length }} 张</strong></div>
      <div class="summary-item hl-green"><span>物业管理费应收合计</span><strong>¥{{ money(propertyTotalDue) }}</strong></div>
      <div class="summary-item total"><span>租金 + 物业费总计</span><strong>¥{{ money(rentTotalDue + propertyTotalDue) }}</strong></div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" :disabled="locked" :loading="generating" @click="generate">
          {{ notices.length ? '重新生成通知单' : '生成租金与物业费通知单' }}
        </el-button>
        <el-button @click="openHeader">收取单位与通知单抬头</el-button>
        <el-button :disabled="!notices.length" @click="exportExcel"><el-icon><Download /></el-icon>导出 Excel</el-button>
        <el-button :disabled="!rentNotices.length" @click="printBatch(rentNotices)"><el-icon><Printer /></el-icon>打印全部租金通知单</el-button>
        <el-button :disabled="!propertyNotices.length" @click="printBatch(propertyNotices)"><el-icon><Printer /></el-icon>打印全部物业费通知单</el-button>
        <el-button v-if="!locked" type="success" :disabled="!notices.length || period?.noticeStale" @click="confirm">确认出账</el-button>
        <el-button v-else type="warning" @click="revoke">撤销确认</el-button>
      </div>
      <div class="toolbar-tip">
        租金与物业管理费的收取单位不同，系统按租户自动分别生成「租金缴费通知单」和「物业管理费缴费通知单」（同一租户多份合同合并在一张单内分行列示）。确认出账后将分别生成「租金」和「物业费」两类财务账单。
      </div>

      <FeeFilterBar :model="filter" :companies="opts.companies" :floors="opts.floors">
        分租户汇总 {{ viewSummaryRows.length }} / {{ summaryRows.length }} 户 · 合计 ¥{{ money(viewSummaryRows.reduce((s, r) => s + r.totalDue, 0)) }}
      </FeeFilterBar>

      <!-- 表 1: 月度汇总 - 分租户 -->
      <h4 class="section-title">月度汇总（分租户）</h4>
      <el-table :data="viewSummaryRows" v-loading="loading" border size="small" show-summary :summary-method="tenantSummary">
        <el-table-column type="index" label="序号" width="55" />
        <el-table-column prop="tenantName" label="租户/公司名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="unitName" label="楼层/区域" min-width="200" show-overflow-tooltip />
        <el-table-column label="计租面积(㎡)" width="110" align="right">
          <template #default="{ row }">{{ num(row.leaseArea) }}</template>
        </el-table-column>
        <el-table-column label="应付租金(元)" width="135" align="right">
          <template #default="{ row }">¥{{ money(row.rentDue) }}</template>
        </el-table-column>
        <el-table-column label="租金缴费" width="85" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.rentNotice" :type="Number(row.rentNotice.paymentStatus) === 1 ? 'success' : 'info'" size="small">
              {{ Number(row.rentNotice.paymentStatus) === 1 ? '已缴' : '未缴' }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="应付物业管理费(元)" width="150" align="right">
          <template #default="{ row }">¥{{ money(row.propertyDue) }}</template>
        </el-table-column>
        <el-table-column label="物业费缴费" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.propertyNotice" :type="Number(row.propertyNotice.paymentStatus) === 1 ? 'success' : 'info'" size="small">
              {{ Number(row.propertyNotice.paymentStatus) === 1 ? '已缴' : '未缴' }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="应付合计(元)" width="140" align="right">
          <template #default="{ row }"><strong>¥{{ money(row.totalDue) }}</strong></template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && !notices.length" class="empty-tip">本月还没有生成租金与物业管理费通知单，请点击上方「生成租金与物业费通知单」。</div>

      <!-- 表 2: 租金缴费通知单 -->
      <div class="notice-group-head">
        <h4 class="section-title">
          租金缴费通知单（{{ viewRentNotices.length }} 张）
          <el-tag type="primary" effect="plain" size="small" style="margin-left: 8px">收取单位：{{ period?.rentPayeeName || '待配置' }}</el-tag>
        </h4>
      </div>
      <el-table :data="viewRentNotices" border size="small" row-key="id" class="notice-table"
                :row-class-name="({ row }) => Number(row.paymentStatus) === 1 ? 'notice-payment-paid' : ''"
                show-summary :summary-method="noticeSummary">
        <el-table-column type="index" label="序号" width="55" />
        <el-table-column prop="noticeNo" label="单据编号" width="190" />
        <el-table-column prop="tenantName" label="租户/公司名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="unitName" label="楼层/区域" min-width="170" show-overflow-tooltip />
        <el-table-column prop="payeeName" label="收取单位" min-width="150" show-overflow-tooltip />
        <el-table-column label="标准租金" width="120" align="right"><template #default="{ row }">¥{{ money(row.standardAmount) }}</template></el-table-column>
        <el-table-column label="费用小计" width="120" align="right"><template #default="{ row }">¥{{ money(row.subtotal) }}</template></el-table-column>
        <el-table-column label="本期应收合计" width="130" align="right"><template #default="{ row }"><strong>¥{{ money(row.totalDue) }}</strong></template></el-table-column>
        <el-table-column label="账单" width="76" align="center">
          <template #default="{ row }"><el-tag v-if="row.billId" size="small" type="success" effect="plain">已出账</el-tag><span v-else>-</span></template>
        </el-table-column>
        <el-table-column label="缴费状态" width="88" align="center">
          <template #default="{ row }"><el-tag :type="Number(row.paymentStatus) === 1 ? 'success' : 'info'" size="small">{{ Number(row.paymentStatus) === 1 ? '已缴费' : '未缴费' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" @click="openPayment(row)">缴费</el-button>
            <el-button link type="primary" @click="preview(row)">预览</el-button>
            <el-button link type="primary" @click="printBatch([row])">打印</el-button>
            <el-button link type="primary" :disabled="locked" @click="openEdit(row)">调整</el-button>
            <el-button link type="danger" @click="deleteOne(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 表 3: 物业管理费缴费通知单 -->
      <div class="notice-group-head">
        <h4 class="section-title">
          物业管理费缴费通知单（{{ viewPropertyNotices.length }} 张）
          <el-tag type="success" effect="plain" size="small" style="margin-left: 8px">收取单位：{{ period?.propertyPayeeName || '待配置' }}</el-tag>
        </h4>
      </div>
      <el-table :data="viewPropertyNotices" border size="small" row-key="id" class="notice-table"
                :row-class-name="({ row }) => Number(row.paymentStatus) === 1 ? 'notice-payment-paid' : ''"
                show-summary :summary-method="noticeSummary">
        <el-table-column type="index" label="序号" width="55" />
        <el-table-column prop="noticeNo" label="单据编号" width="190" />
        <el-table-column prop="tenantName" label="租户/公司名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="unitName" label="楼层/区域" min-width="170" show-overflow-tooltip />
        <el-table-column prop="payeeName" label="收取单位" min-width="150" show-overflow-tooltip />
        <el-table-column label="标准物业费" width="120" align="right"><template #default="{ row }">¥{{ money(row.standardAmount) }}</template></el-table-column>
        <el-table-column label="费用小计" width="120" align="right"><template #default="{ row }">¥{{ money(row.subtotal) }}</template></el-table-column>
        <el-table-column label="本期应收合计" width="130" align="right"><template #default="{ row }"><strong>¥{{ money(row.totalDue) }}</strong></template></el-table-column>
        <el-table-column label="账单" width="76" align="center">
          <template #default="{ row }"><el-tag v-if="row.billId" size="small" type="success" effect="plain">已出账</el-tag><span v-else>-</span></template>
        </el-table-column>
        <el-table-column label="缴费状态" width="88" align="center">
          <template #default="{ row }"><el-tag :type="Number(row.paymentStatus) === 1 ? 'success' : 'info'" size="small">{{ Number(row.paymentStatus) === 1 ? '已缴费' : '未缴费' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" @click="openPayment(row)">缴费</el-button>
            <el-button link type="primary" @click="preview(row)">预览</el-button>
            <el-button link type="primary" @click="printBatch([row])">打印</el-button>
            <el-button link type="primary" :disabled="locked" @click="openEdit(row)">调整</el-button>
            <el-button link type="danger" @click="deleteOne(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 收取单位与通知单抬头弹窗 -->
    <el-dialog v-model="headerDialog.visible" title="收取单位与通知单抬头（租金与物业费分开配置）" width="720px">
      <el-form :model="headerForm" label-width="135px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="园区名称"><el-input v-model="headerForm.parkName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="单据编号前缀"><el-input v-model="headerForm.noticePrefix" placeholder="如 DIPARK" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="计费期间起"><el-date-picker v-model="headerForm.periodStart" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="计费期间止"><el-date-picker v-model="headerForm.periodEnd" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="开具日期"><el-date-picker v-model="headerForm.issueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="缴费截止日"><el-date-picker v-model="headerForm.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
        </el-row>

        <el-divider content-position="left">租金收取单位与收款账户</el-divider>
        <el-form-item label="租金收取单位"><el-input v-model="headerForm.rentPayeeName" placeholder="如 广州XX置业有限公司" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="租金联系人"><el-input v-model="headerForm.rentContactName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="租金联系电话"><el-input v-model="headerForm.rentContactPhone" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="租金收款账户/方式"><el-input v-model="headerForm.rentPaymentAccount" type="textarea" :rows="3" placeholder="户名 / 开户行 / 银行账号" /></el-form-item>

        <el-divider content-position="left">物业管理费收取单位与收款账户</el-divider>
        <el-form-item label="物业管理费收取单位"><el-input v-model="headerForm.propertyPayeeName" placeholder="如 广州XX物业管理有限公司" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="物业费联系人"><el-input v-model="headerForm.propertyContactName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="物业费联系电话"><el-input v-model="headerForm.propertyContactPhone" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="物业费收款账户/方式"><el-input v-model="headerForm.propertyPaymentAccount" type="textarea" :rows="3" placeholder="户名 / 开户行 / 银行账号" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="headerDialog.visible = false">取消</el-button>
        <el-button type="primary" :disabled="locked" :loading="headerDialog.saving" @click="saveHeader">保存</el-button>
      </template>
    </el-dialog>

    <!-- 调整单张通知单 -->
    <el-dialog v-model="editDialog.visible" :title="`调整通知单 - ${editForm.noticeNo || ''}`" width="880px" destroy-on-close>
      <el-form :model="editForm" label-width="110px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="租户/公司名称"><el-input v-model="editForm.tenantName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="收取单位"><el-input v-model="editForm.payeeName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="已付金额"><el-input-number v-model="editForm.paidAmount" :min="0" :precision="2" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="前期欠费"><el-input-number v-model="editForm.priorArrears" :min="0" :precision="2" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="收款账户/方式"><el-input v-model="editForm.paymentAccount" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <div class="items-head">
        <strong>收费明细</strong>
        <el-button type="primary" size="small" @click="addManual"><el-icon><Plus /></el-icon>新增调整项</el-button>
      </div>
      <el-table :data="editItems" border size="small">
        <el-table-column prop="seq" label="序号" width="55" align="center" />
        <el-table-column label="费用项目" min-width="140">
          <template #default="{ row }">
            <span v-if="row.auto">{{ row.feeItem }}（{{ row.unitName }}）</span>
            <el-input v-else v-model="row.feeItem" size="small" placeholder="如 违约金/补差/减免" />
          </template>
        </el-table-column>
        <el-table-column label="金额(元)" width="130" align="right">
          <template #default="{ row }">
            <span v-if="row.auto">¥{{ money(row.amount) }}</span>
            <el-input-number v-else v-model="row.amount" size="small" :precision="2" :controls="false" style="width: 110px" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="200">
          <template #default="{ row }"><el-input v-model="row.remark" size="small" /></template>
        </el-table-column>
        <el-table-column label="操作" width="68" align="center">
          <template #default="{ row, $index }">
            <el-button v-if="!row.auto" link type="danger" size="small" @click="editItems.splice($index, 1)">删除</el-button>
            <span v-else class="muted">自动</span>
          </template>
        </el-table-column>
      </el-table>
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
        <el-button type="primary" @click="printBatch([previewDialog.notice])">打印</el-button>
      </template>
    </el-dialog>

    <LeaseNoticePayment v-model="paymentDialog.visible" :notice="paymentDialog.notice" @saved="paymentSaved" />
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Download, Plus, Printer } from '@element-plus/icons-vue'
import { leaseFeeApi } from '@/api/energy'
import { BILL_KINDS, applyFilter, downloadBlob, filterOptions, money, num } from '../feePeriod'
import { LEASE_NOTICE_CSS, leaseNoticeHtml, printLeaseNotices } from '../leaseNoticePrint'
import FeeBillFiles from './FeeBillFiles.vue'
import FeeFilterBar from './FeeFilterBar.vue'
import LeaseNoticePayment from './LeaseNoticePayment.vue'

const props = defineProps({ month: { type: String, required: true } })
defineEmits(['back'])

const period = ref(null)
const notices = ref([])
const loading = ref(false)
const generating = ref(false)

const locked = computed(() => period.value?.status === 'CONFIRMED')
const monthLabel = computed(() => `${props.month.slice(0, 4)}年${Number(props.month.slice(5))}月`)

const filter = reactive({ company: '', floor: '' })
const opts = computed(() => filterOptions(notices.value, (n) => n.tenantName, (n) => n.unitName))

const rentNotices = computed(() => notices.value.filter((n) => n.feeCategory === 'RENT'))
const propertyNotices = computed(() => notices.value.filter((n) => n.feeCategory === 'PROPERTY'))
const viewRentNotices = computed(() => applyFilter(rentNotices.value, filter, (n) => n.tenantName, (n) => n.unitName))
const viewPropertyNotices = computed(() => applyFilter(propertyNotices.value, filter, (n) => n.tenantName, (n) => n.unitName))

const rentTotalDue = computed(() => rentNotices.value.reduce((s, n) => s + Number(n.totalDue || 0), 0))
const propertyTotalDue = computed(() => propertyNotices.value.reduce((s, n) => s + Number(n.totalDue || 0), 0))

const summaryRows = computed(() => {
  const map = new Map()
  for (const n of notices.value) {
    const key = n.tenantName || '-'
    const cur = map.get(key) || { tenantName: key, unitName: n.unitName, leaseArea: Number(n.leaseArea || 0), rentDue: 0, propertyDue: 0, totalDue: 0, rentNotice: null, propertyNotice: null }
    if (n.feeCategory === 'RENT') { cur.rentDue += Number(n.totalDue || 0); cur.rentNotice = n }
    else { cur.propertyDue += Number(n.totalDue || 0); cur.propertyNotice = n }
    cur.totalDue = Math.round((cur.rentDue + cur.propertyDue) * 100) / 100
    map.set(key, cur)
  }
  return [...map.values()]
})
const viewSummaryRows = computed(() => applyFilter(summaryRows.value, filter, (r) => r.tenantName, (r) => r.unitName))

async function load() {
  loading.value = true
  try {
    period.value = await leaseFeeApi.open(props.month)
    notices.value = await leaseFeeApi.notices(period.value.id) || []
  } finally {
    loading.value = false
  }
}
watch(() => props.month, load, { immediate: true })

async function generate() {
  generating.value = true
  try {
    const r = await leaseFeeApi.generate(period.value.id)
    period.value = r.period
    notices.value = r.notices || []
    ElMessage.success(`已生成 ${notices.value.length} 张通知单（租金与物业管理费已按收取单位分开出单）`)
  } finally {
    generating.value = false
  }
}

const headerDialog = reactive({ visible: false, saving: false })
const headerForm = reactive({})
function openHeader() {
  Object.assign(headerForm, { ...period.value })
  headerDialog.visible = true
}
async function saveHeader() {
  headerDialog.saving = true
  try {
    period.value = await leaseFeeApi.update({ ...headerForm })
    headerDialog.visible = false
    ElMessage.success(notices.value.length ? '已保存；点「重新生成通知单」可将新收取单位同步到各户通知单' : '已保存')
  } finally {
    headerDialog.saving = false
  }
}

const editDialog = reactive({ visible: false, saving: false })
const editForm = reactive({})
const editItems = ref([])
function openEdit(row) {
  Object.assign(editForm, {
    id: row.id, noticeNo: row.noticeNo, tenantName: row.tenantName, unitName: row.unitName,
    payeeName: row.payeeName || '', paymentAccount: row.paymentAccount || '',
    paidAmount: Number(row.paidAmount || 0), priorArrears: Number(row.priorArrears || 0), remark: row.remark || ''
  })
  editItems.value = (row.items || []).map((it) => ({
    id: it.id, auto: !(it.manualFlag === 1 || it.itemType === 'CUSTOM'), seq: it.seq,
    feeItem: it.feeItem, unitName: it.unitName, amount: Number(it.amount || 0), remark: it.remark || ''
  }))
  editDialog.visible = true
}
function addManual() {
  editItems.value.push({ id: null, auto: false, seq: String(editItems.value.length + 1), feeItem: '', amount: 0, remark: '' })
}
async function saveEdit() {
  const manualItems = editItems.value.filter((r) => !r.auto).map((r) => ({ feeItem: r.feeItem, amount: r.amount, remark: r.remark }))
  const itemRemarks = {}
  editItems.value.filter((r) => r.auto && r.id).forEach((r) => { itemRemarks[r.id] = r.remark || '' })
  editDialog.saving = true
  try {
    await leaseFeeApi.editNotice({ ...editForm, itemRemarks, manualItems })
    editDialog.visible = false
    ElMessage.success('通知单已更新')
    await load()
  } finally {
    editDialog.saving = false
  }
}

const previewDialog = reactive({ visible: false, title: '', html: '', notice: null })
function preview(row) {
  previewDialog.notice = row
  previewDialog.title = `${row.feeCategory === 'RENT' ? '租金' : '物业管理费'}通知单预览 - ${row.tenantName}`
  previewDialog.html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><style>${LEASE_NOTICE_CSS}</style></head><body>${leaseNoticeHtml(period.value, row)}</body></html>`
  previewDialog.visible = true
}
function printBatch(list) {
  if (!list?.length) return
  if (!printLeaseNotices(period.value, list, true)) ElMessage.warning('浏览器拦截了打印窗口，请允许弹出窗口')
}

const paymentDialog = reactive({ visible: false, notice: null })
function openPayment(row) { paymentDialog.notice = row; paymentDialog.visible = true }
function paymentSaved({ id, paymentStatus }) {
  const row = notices.value.find((n) => String(n.id) === String(id))
  if (row) row.paymentStatus = paymentStatus
}

async function deleteOne(row) {
  await ElMessageBox.confirm(`确定删除通知单「${row.noticeNo} - ${row.tenantName}」？`, '删除通知单', { type: 'warning' })
  await leaseFeeApi.deleteNotices(period.value.id, [row.id])
  ElMessage.success('已删除')
  await load()
}

async function confirm() {
  await ElMessageBox.confirm('确认出账后，租金与物业管理费将分别生成财务账单并冻结本月数据。确定继续？', '确认出账', { type: 'warning' })
  const r = await leaseFeeApi.confirm(period.value.id)
  ElMessage.success(`已确认出账：生成账单 ${r.inserted} 张${r.skipped ? `（跳过 ${r.skipped} 张）` : ''}`)
  await load()
}

async function revoke() {
  await ElMessageBox.confirm('撤销确认将删除未收款的关联账单并解除本月冻结。确定继续？', '撤销确认', { type: 'warning' })
  await leaseFeeApi.revoke(period.value.id)
  ElMessage.success('已撤销确认')
  await load()
}

async function exportExcel() {
  const res = await leaseFeeApi.export(period.value.id)
  downloadBlob(res, `${period.value.period}租金与物业管理费明细表.xlsx`)
}

function tenantSummary({ columns, data }) {
  const sum = (f) => data.reduce((s, r) => s + Number(f(r) || 0), 0)
  return columns.map((col, i) => {
    if (i === 0) return '合计'
    switch (col.label) {
      case '计租面积(㎡)': return num(sum((r) => r.leaseArea))
      case '应付租金(元)': return '¥' + money(sum((r) => r.rentDue))
      case '应付物业管理费(元)': return '¥' + money(sum((r) => r.propertyDue))
      case '应付合计(元)': return '¥' + money(sum((r) => r.totalDue))
      default: return ''
    }
  })
}

function noticeSummary({ columns, data }) {
  const sum = (f) => data.reduce((s, r) => s + Number(f(r) || 0), 0)
  return columns.map((col, i) => {
    if (i === 0) return '合计'
    switch (col.label) {
      case '标准租金':
      case '标准物业费': return '¥' + money(sum((r) => r.standardAmount))
      case '费用小计': return '¥' + money(sum((r) => r.subtotal))
      case '本期应收合计': return '¥' + money(sum((r) => r.totalDue))
      default: return ''
    }
  })
}
</script>

<style scoped>
.detail-head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.detail-head h3 { margin: 0; font-size: 16px; }
.gen-time { font-size: 12px; color: var(--el-text-color-secondary); }
.payee-bar { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 12px; margin-bottom: 12px; }
.payee-card { padding: 10px 14px; border-radius: 8px; border: 1px solid var(--el-border-color-lighter); background: var(--el-fill-color-blank); }
.payee-card.rent { border-left: 4px solid var(--el-color-primary); }
.payee-card.property { border-left: 4px solid var(--el-color-success); }
.payee-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; flex-wrap: wrap; }
.payee-acc { font-size: 12px; color: var(--el-text-color-secondary); white-space: pre-wrap; line-height: 1.5; }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; margin-bottom: 12px; }
.summary-item { padding: 10px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; display: flex; flex-direction: column; gap: 4px; background: var(--el-fill-color-blank); }
.summary-item span { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-item strong { font-size: 17px; font-variant-numeric: tabular-nums; }
.summary-item.hl strong { color: var(--el-color-primary); }
.summary-item.hl-green strong { color: var(--el-color-success); }
.summary-item.total strong { color: var(--el-color-danger); }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; flex-wrap: wrap; }
.toolbar-tip { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 12px; }
.section-title { margin: 16px 0 8px; font-size: 14px; display: flex; align-items: center; }
.items-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.empty-tip { text-align: center; color: var(--el-text-color-secondary); padding: 18px 0; font-size: 13px; }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
.preview-frame { width: 100%; height: 72vh; border: 1px solid var(--el-border-color); border-radius: 6px; background: #fff; }
:deep(.notice-payment-paid) td { background: var(--el-color-success-light-9) !important; }
</style>
