<template>
  <div class="page-container mkt-page">
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="伙伴">
          <PromoterSelect v-model="query.promoterId" clearable placeholder="搜索姓名/手机/邀请码" style="width: 240px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(t, v) in ST" :key="v" :label="t" :value="Number(v)" />
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
        <el-button v-if="canManual" type="primary" @click="openManual"><el-icon><Plus /></el-icon>代伙伴申请提现</el-button>
        <router-link :to="{ path: '/oa/approval', query: { bizType: 'withdrawal' } }"><el-button>我的提现审批待办</el-button></router-link>
        <el-button type="warning" plain @click="openInvoiceVerify">🧾 6%增值税专票税局验真</el-button>
        <WithdrawalApproverSettings v-if="isAdmin" />
        <span class="hint">流程：核对伙伴及收款账户 → 提交给指定审批人 → 审批通过后由财务线下付款并上传凭证。提交申请不会自动打款。</span>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="withdrawalNo" label="提现单号" width="170" />
        <el-table-column label="伙伴" min-width="160"><template #default="{ row }"><b>{{ row.promoterName || '未命名伙伴' }}</b><div class="hint">编号 #{{ row.promoterId }}<template v-if="row.promoterPhone"> · {{ row.promoterPhone }}</template></div></template></el-table-column>
        <el-table-column label="税前(元)" width="110" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
        <el-table-column label="税额(元)" width="100" align="right"><template #default="{ row }">{{ money(row.taxAmount) }}</template></el-table-column>
        <el-table-column label="税后实付(元)" width="120" align="right"><template #default="{ row }">{{ money(row.netAmount) }}</template></el-table-column>
        <el-table-column label="税务" width="90"><template #default="{ row }">{{ TAX[row.taxMode] }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="stType(row.status)">{{ ST[row.status] }}</el-tag></template></el-table-column>
        <el-table-column label="审批进度" min-width="160"><template #default="{ row }"><template v-if="row.approval"><div>{{ APPROVAL[row.approval.status] || '—' }}<template v-if="row.approval.status === 1"> · {{ row.approval.nodeName }}</template></div><div class="hint">{{ row.approval.status === 1 ? '待 ' + (row.approval.assignee || '—') + ' 办理' : (row.approval.opinion || '') }}</div></template><span v-else class="hint">未走审批流</span></template></el-table-column>
        <el-table-column label="申请时收款账户" min-width="190"><template #default="{ row }">{{ row.accountName || '待补资料' }} · 尾号 {{ row.accountTail || '—' }}<div class="hint">{{ row.bankName }}</div></template></el-table-column>
        <el-table-column prop="payNo" label="打款流水号" width="150" />
        <el-table-column prop="auditBy" label="审核人" width="90" />
        <el-table-column prop="payAt" label="打款时间" width="160" />
        <el-table-column prop="rejectReason" label="驳回原因" min-width="120" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.canApprove === true" link type="success" @click="approve(row)">审批通过</el-button>
            <el-button v-if="row.canReject === true" link type="danger" @click="reject(row)">驳回</el-button>
            <el-button v-if="row.canPay === true" link type="success" @click="pay(row)">登记线下打款</el-button>
            <router-link v-if="row.status === 1 && row.canApprove === true" :to="{ path: '/oa/approval', query: { bizType: 'withdrawal', bizId: row.id } }"><el-button link type="primary">审批详情</el-button></router-link>
            <el-button v-if="row.payProof" link @click="downloadProof(row)">查看凭证</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <el-dialog v-model="payment.visible" title="核对账户并登记线下打款" width="min(580px,94vw)" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" @update:model-value="value => { if (!value) closePayment() }">
      <el-alert v-if="payment.error" :title="payment.error" type="error" :closable="false"><el-button link @click="pay(payment.row)">重新读取账户</el-button></el-alert>
      <el-descriptions :column="1" border v-loading="payment.loading"><el-descriptions-item label="收款人">{{ payment.account?.name }}</el-descriptions-item><el-descriptions-item label="收款账号">{{ payment.account?.accountNo }}</el-descriptions-item><el-descriptions-item label="银行/类型">{{ payment.account?.bankName || ACCOUNT_TYPE[payment.account?.accountType] }}</el-descriptions-item><el-descriptions-item label="税后实付">{{ money(payment.row?.netAmount) }} 元</el-descriptions-item></el-descriptions>
      <el-form label-position="top" class="payment-form"><el-form-item label="银行流水号 / 转账单号" required><el-input v-model="payment.payNo" maxlength="64" /></el-form-item><el-form-item label="实际付款凭证" required><FileUpload v-model="payment.files" biz-type="mkt_withdrawal" :biz-id="payment.row?.id" accept=".pdf,.jpg,.jpeg,.png" /></el-form-item></el-form>
      <p class="hint">请按以上申请时账户完成真实付款后登记，系统不会自动转账。</p>
      <template #footer><el-button :disabled="saving" @click="closePayment">取消</el-button><el-button type="primary" :loading="saving" :disabled="saving || payment.loading || !payment.account || !payment.payNo.trim() || !payment.files.length" @click="savePayment">确认已完成付款</el-button></template>
    </el-dialog>

    <el-dialog v-model="manual.visible" title="代伙伴申请提现" width="min(720px,94vw)" :close-on-click-modal="!manual.saving" :close-on-press-escape="!manual.saving" :show-close="!manual.saving" @closed="closeManual">
      <el-form label-width="110px">
        <el-form-item label="选择伙伴" required>
          <el-select v-model="manual.promoterId" filterable remote clearable :remote-method="searchManualPartners" :loading="manual.searching" :disabled="manual.saving" placeholder="搜索姓名、手机号或邀请码" style="width: 100%" @change="loadManualContext">
            <el-option v-for="p in manual.options" :key="p.id" :value="p.id" :label="partnerOptionLabel(p)"><div class="partner-option"><span>{{ p.name || '未命名伙伴' }} · {{ p.phone || '未绑定手机号' }}</span><span class="hint">#{{ p.id }} · {{ p.positionCode || '未设置称号' }}</span></div></el-option>
          </el-select>
          <div class="hint">先搜索并选中伙伴，再核对下方身份和申请时收款账户。</div>
        </el-form-item>
      </el-form>
      <el-alert v-if="manual.searchError" :title="manual.searchError" type="error" :closable="false" />
      <div v-loading="manual.loading" class="manual-context">
        <el-alert v-if="manual.error" :title="manual.error" type="error" :closable="false"><el-button link @click="loadManualContext(manual.promoterId)">重新读取</el-button></el-alert>
        <template v-if="manual.context">
          <el-descriptions title="伙伴身份" :column="2" border>
            <el-descriptions-item label="姓名">{{ manual.context.promoter.name || '未填写' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ manual.context.promoter.phone || '未绑定' }}</el-descriptions-item>
            <el-descriptions-item label="伙伴编号">#{{ manual.context.promoter.id }}</el-descriptions-item>
            <el-descriptions-item label="称号">{{ manual.context.promoter.positionCode || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="邀请码">{{ manual.context.promoter.inviteCode || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="伙伴状态">{{ PROMOTER_STATUS[manual.context.promoter.status] || '未知' }}</el-descriptions-item>
          </el-descriptions>
          <el-descriptions title="申请时收款账户" :column="2" border class="context-section">
            <el-descriptions-item label="收款户名">{{ manual.context.account?.name || '未填写' }}</el-descriptions-item>
            <el-descriptions-item label="账户类型">{{ ACCOUNT_TYPE[manual.context.account?.accountType] || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="收款账号">{{ manual.context.account?.accountTail ? '尾号 ' + manual.context.account.accountTail : '未填写' }}</el-descriptions-item>
            <el-descriptions-item label="开户银行">{{ manual.context.account?.bankName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="资料状态">{{ ACCOUNT_REVIEW[manual.context.account?.reviewStatus] || '未完善' }}</el-descriptions-item>
          </el-descriptions>
          <el-descriptions title="余额与审批" :column="2" border class="context-section">
            <el-descriptions-item label="可提现余额"><b>{{ money(manual.context.availableBalance) }} 元</b></el-descriptions-item>
            <el-descriptions-item label="待审批/待打款">{{ money(manual.context.pendingAmount) }} 元（{{ manual.context.pendingCount || 0 }} 笔）</el-descriptions-item>
            <el-descriptions-item label="冻结中">{{ money(manual.context.frozenAmount) }} 元</el-descriptions-item>
            <el-descriptions-item label="指定审批人">{{ manual.context.approval?.assigneeName || manual.context.approval?.assignee || '未配置' }}</el-descriptions-item>
            <el-descriptions-item label="审批节点" :span="2">{{ manual.context.approval?.nodeName || manual.context.approval?.name || '未配置' }}</el-descriptions-item>
          </el-descriptions>
          <el-alert v-if="manual.context.blockedReason" :title="manual.context.blockedReason" type="warning" :closable="false" class="context-section" />
          <p v-if="!manual.context.account || manual.context.account.reviewStatus !== 1" class="hint">请伙伴在小程序「我的 → 收款资料」补充本人收款账户，由后台「伙伴管理」核验通过后再申请。<router-link v-if="canReviewAccount" to="/crm/marketing/promoter">前往伙伴管理</router-link></p>
          <el-form label-width="110px" class="context-section">
            <el-form-item label="提现金额" required><el-input-number v-model="manual.amount" :min="0" :max="Number(manual.context.availableBalance) || 0" :precision="2" :disabled="manual.saving || !manual.context.canApply" style="width: 220px" @change="manual.confirmed = false" /><span class="amount-unit">元</span></el-form-item>
          </el-form>
          <el-checkbox v-model="manual.confirmed" :disabled="manual.saving || !manual.context.canApply">已核对伙伴身份、收款户名、账户尾号及提现金额</el-checkbox>
          <p class="hint">收款账户会随本次申请保存。申请成功后进入审批，实际付款金额以审批单中的税后实付金额为准。</p>
        </template>
      </div>
      <template #footer><el-button :disabled="manual.saving" @click="closeManual">取消</el-button><el-button type="primary" :loading="manual.saving" :disabled="!canSubmitManual" @click="submitManual">提交审批</el-button></template>
    </el-dialog>

    <el-dialog v-model="invVerify.visible" title="🧾 P4事业部对公提现 · 6%增值税专用发票OCR与税务总局查验" width="min(580px,95vw)">
      <el-form label-position="top">
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">
          <el-form-item label="发票号码（8位或20位全电票号）" required><el-input v-model="invVerify.invoiceNo" placeholder="例如：26442000000088912345" /></el-form-item>
          <el-form-item label="开票日期" required><el-input v-model="invVerify.invoiceDate" placeholder="YYYY-MM-DD" /></el-form-item>
        </div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">
          <el-form-item label="货物或应税劳务名称（品目）" required><el-input v-model="invVerify.itemName" /></el-form-item>
          <el-form-item label="价税合计金额（元）" required><el-input v-model="invVerify.totalAmount" inputmode="decimal" placeholder="10600.00" /></el-form-item>
        </div>
        <el-form-item label="购买方园区纳税人识别号（选填，填后强制比对抬头税号）"><el-input v-model="invVerify.buyerTaxNo" placeholder="如：91440100XXXXXXXXXX" /></el-form-item>
      </el-form>
      <el-alert v-if="invVerify.result" :type="invVerify.result.verifyStatus === 'VERIFIED_VALID' ? 'success' : invVerify.result.verifyStatus === 'STANDBY_MANUAL' ? 'warning' : 'error'" :closable="false" :title="invVerify.result.verifyMsg" />
      <template #footer>
        <el-button @click="invVerify.visible = false">关闭</el-button>
        <el-button type="primary" :loading="invVerify.busy" @click="submitInvoiceVerify">执行合规与真伪核验</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktWithdrawalApi, mktConnectorHubApi } from '@/api/marketing'
import FileUpload from '@/components/FileUpload.vue'
import WithdrawalApproverSettings from './WithdrawalApproverSettings.vue'
import PromoterSelect from './PromoterSelect.vue'
import { startFileDownload } from '@/utils/fileDownload'
import { money } from '@/utils/format'
import { hasPermission } from '@/utils/permission'

const ST = { 1: '审批中', 2: '已审批待打款', 3: '已打款', 4: '已驳回' }
const APPROVAL = { 1: '审批中', 2: '审批通过', 3: '审批驳回' }
const TAX = { 1: '个税代扣', 2: '灵工代征' }
const stType = (s) => ({ 1: 'warning', 2: 'primary', 3: 'success', 4: 'danger' }[s] || 'info')

const saving = ref(false)
const auditBusy = ref(false)
const canManual = computed(() => hasPermission('crm:marketing:withdrawal:audit'))
const isAdmin = computed(() => hasPermission('ROLE_admin'))
const canReviewAccount = computed(() => hasPermission('crm:marketing:account:audit'))
const ACCOUNT_TYPE={1:'微信',2:'银行卡',3:'支付宝'}
const ACCOUNT_REVIEW = { 0: '待审核', 1: '已通过', 2: '已退回' }
const PROMOTER_STATUS = { 1: '正常', 2: '冻结', 3: '待审核', 4: '已退出' }
const payment=reactive({visible:false,row:null,account:null,payNo:'',files:[],loading:false,error:''})
let paymentRequest = 0
function closePayment() { paymentRequest++; payment.visible=false; payment.account=null; payment.loading=false }
onBeforeUnmount(() => { paymentRequest++; manualRequest++; manualSearchRequest++ })
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, promoterId: null, status: null })

async function load() {
  loading.value = true
  try {
    const res = await mktWithdrawalApi.page(query)
    list.value = res.records; total.value = res.total
  } finally { loading.value = false }
}
function search() { query.pageNo = 1; load() }
function reset() { Object.assign(query, { pageNo: 1, promoterId: null, status: null }); load() }

const manual = reactive({ visible: false, promoterId: null, amount: null, context: null, options: [], confirmed: false, loading: false, searching: false, saving: false, error: '', searchError: '' })
let manualRequest = 0, manualSearchRequest = 0
const canSubmitManual = computed(() => {
  const amount = Number(manual.amount)
  return manual.visible && !manual.loading && !manual.saving && !manual.error && manual.context?.canApply === true
    && Number(manual.context.promoter?.id) === Number(manual.promoterId) && manual.confirmed
    && Number.isFinite(amount) && amount >= 0.01 && amount <= Number(manual.context.availableBalance)
})
function partnerOptionLabel(p) { return `${p.name || '未命名伙伴'} · ${p.phone || '未绑定手机号'} · #${p.id}` }
function openManual() {
  if (manual.saving || !canManual.value) return
  manualRequest++; manualSearchRequest++
  Object.assign(manual, { visible: true, promoterId: null, amount: null, context: null, options: [], confirmed: false, loading: false, searching: false, error: '', searchError: '' })
  return searchManualPartners('')
}
function closeManual() {
  if (manual.saving) return
  manualRequest++; manualSearchRequest++
  Object.assign(manual, { visible: false, context: null, options: [], confirmed: false, loading: false, searching: false, error: '', searchError: '' })
}
async function searchManualPartners(keyword) {
  if (!manual.visible || manual.saving) return
  const request = ++manualSearchRequest
  manual.searching = true; manual.searchError = ''
  try {
    const options = await mktWithdrawalApi.manualOptions(String(keyword || '').trim())
    if (request === manualSearchRequest && manual.visible) manual.options = options || []
  } catch (e) {
    if (request === manualSearchRequest && manual.visible) { manual.options = []; manual.searchError = e.message || '伙伴搜索失败，请重试' }
  } finally { if (request === manualSearchRequest) manual.searching = false }
}
async function loadManualContext(promoterId) {
  if (manual.saving || !manual.visible) return
  const request = ++manualRequest
  Object.assign(manual, { promoterId: promoterId || null, amount: null, context: null, confirmed: false, error: '', loading: !!promoterId })
  if (!promoterId) return
  try {
    const context = await mktWithdrawalApi.manualContext(promoterId)
    if (request === manualRequest && manual.visible && Number(manual.promoterId) === Number(promoterId)) {
      if (Number(context?.promoter?.id) !== Number(promoterId)) throw new Error('伙伴资料不一致，请重新选择伙伴')
      manual.context = context
    }
  } catch (e) {
    if (request === manualRequest && manual.visible) manual.error = e.message || '伙伴及收款资料读取失败，请重试'
  } finally { if (request === manualRequest) manual.loading = false }
}
async function submitManual() {
  if (!canSubmitManual.value) return
  manual.saving = true
  try {
    await mktWithdrawalApi.manual({ promoterId: manual.promoterId, amount: manual.amount })
    ElMessage.success('提现申请已提交审批'); manual.saving = false; closeManual(); await load()
  } catch (e) {
    manual.error = e.message || '申请失败，请重新读取伙伴资料后重试'
    manual.confirmed = false
  } finally { manual.saving = false }
}
async function approve(row) {
  if (auditBusy.value || row.canApprove !== true) return
  auditBusy.value = true
  try {
    const detail = await mktWithdrawalApi.get(row.id)
    if (Number(detail.id) !== Number(row.id)) return ElMessage.error('提现单据不一致，请刷新后重新核对')
    if (detail.canApprove !== true) return ElMessage.warning('该申请已处理或不属于您的审批待办，请刷新列表')
    try { await ElMessageBox.confirm(`伙伴：${detail.promoterName || '未命名'}（#${detail.promoterId}，${detail.promoterPhone || '未绑定手机号'}）\n收款人：${detail.accountName || '未填写'}，${detail.bankName || ACCOUNT_TYPE[detail.accountType] || ''}，尾号 ${detail.accountTail || '未填写'}\n申请金额 ${money(detail.amount)} 元，税额 ${money(detail.taxAmount)} 元，税后实付 ${money(detail.netAmount)} 元。\n通过后进入待打款。`, '核对提现申请并审批', { type: 'warning', customClass: 'withdrawal-confirm' }) } catch { return }
    await mktWithdrawalApi.approve(row.id); ElMessage.success('当前审批节点已通过'); await load()
  } finally { auditBusy.value = false }
}
async function reject(row) {
  if (auditBusy.value || row.canReject !== true) return
  auditBusy.value = true
  try {
    const detail = await mktWithdrawalApi.get(row.id)
    if (Number(detail.id) !== Number(row.id)) return ElMessage.error('提现单据不一致，请刷新后重新核对')
    if (detail.canReject !== true) return ElMessage.warning('该申请已处理或不属于您的审批待办，请刷新列表')
    let answer
    try { answer = await ElMessageBox.prompt(`伙伴 ${detail.promoterName || '#' + detail.promoterId}，申请 ${money(detail.amount)} 元。请填写驳回原因，款项将回到可提现余额。`, '驳回提现', { inputPattern: /\S+/, inputErrorMessage: '原因必填' }) } catch { return }
    await mktWithdrawalApi.reject(row.id, { reason: answer.value.trim() }); ElMessage.success('已驳回'); await load()
  } finally { auditBusy.value = false }
}
async function pay(row) {
  if (saving.value || !row) return
  const request = ++paymentRequest
  Object.assign(payment,{visible:true,row,account:null,payNo:'',files:[],loading:true,error:''})
  try {
    const account = await mktWithdrawalApi.payAccount(row.id)
    if (request === paymentRequest && payment.visible && payment.row?.id === row.id) payment.account=account
  } catch (e) {
    if (request === paymentRequest && payment.visible) payment.error=e.message || '收款账户读取失败，请重试'
  } finally { if (request === paymentRequest) payment.loading=false }
}
async function savePayment(){
  if(saving.value || payment.loading || !payment.visible || !payment.account || !payment.payNo.trim() || !payment.files.length)return
  const id=payment.row.id
  saving.value=true
  try{await mktWithdrawalApi.pay(id,{payNo:payment.payNo.trim(),payProof:`file:${payment.files[0].id}`});closePayment();ElMessage.success('付款凭证已保存');await load()}finally{saving.value=false}
}
async function downloadProof(row){const id=row.payProof?.match(/^file:(\d+)$/)?.[1];if(id)await startFileDownload(id,'提现付款凭证')}
const invVerify = reactive({ visible: false, invoiceNo: '', invoiceDate: new Date().toISOString().slice(0, 10), itemName: '*现代服务*招商推广服务费', totalAmount: '', buyerTaxNo: '', busy: false, result: null })
function openInvoiceVerify() { invVerify.result = null; invVerify.visible = true }
async function submitInvoiceVerify() {
  if (invVerify.busy) return
  invVerify.busy = true
  try {
    invVerify.result = await mktConnectorHubApi.verifyInvoice({
      invoiceNo: invVerify.invoiceNo, invoiceDate: invVerify.invoiceDate,
      itemName: invVerify.itemName, totalAmount: invVerify.totalAmount, buyerTaxNo: invVerify.buyerTaxNo
    })
  } catch (_) { /* request.js统一弹错 */ } finally { invVerify.busy = false }
}
onMounted(load)
</script>

<style scoped>
.payment-form{margin-top:20px}
.toolbar { display: flex; flex-wrap:wrap; align-items: center; gap: 12px; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.manual-context { min-height: 48px; }
.context-section { margin-top: 20px; }
.partner-option { display: flex; justify-content: space-between; gap: 16px; }
.amount-unit { margin-left: 8px; }
</style>
<style>
.withdrawal-confirm .el-message-box__message { white-space: pre-line; }
</style>
