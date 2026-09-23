<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="伙伴 ID"><el-input-number v-model="query.promoterId" :min="1" controls-position="right" style="width: 140px" /></el-form-item>
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
        <el-button type="primary" @click="openManual"><el-icon><Plus /></el-icon>代伙伴申请提现</el-button>
        <span class="hint">收款资料须先审核通过。申请时固定收款账户，审核后由财务线下付款并上传真实凭证。</span>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="withdrawalNo" label="提现单号" width="170" />
        <el-table-column label="伙伴" width="110"><template #default="{ row }">{{ row.promoterName || '#' + row.promoterId }}</template></el-table-column>
        <el-table-column label="税前(元)" width="110" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
        <el-table-column label="税额(元)" width="100" align="right"><template #default="{ row }">{{ money(row.taxAmount) }}</template></el-table-column>
        <el-table-column label="税后实付(元)" width="120" align="right"><template #default="{ row }">{{ money(row.netAmount) }}</template></el-table-column>
        <el-table-column label="税务" width="90"><template #default="{ row }">{{ TAX[row.taxMode] }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="stType(row.status)">{{ ST[row.status] }}</el-tag></template></el-table-column>
        <el-table-column label="申请时收款账户" min-width="190"><template #default="{ row }">{{ row.accountName || '待补资料' }} · 尾号 {{ row.accountTail || '—' }}<div class="hint">{{ row.bankName }}</div></template></el-table-column>
        <el-table-column prop="payNo" label="打款流水号" width="150" />
        <el-table-column prop="auditBy" label="审核人" width="90" />
        <el-table-column prop="payAt" label="打款时间" width="160" />
        <el-table-column prop="rejectReason" label="驳回原因" min-width="120" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 1">
              <el-button link type="success" @click="approve(row)">审核通过</el-button>
              <el-button link type="danger" @click="reject(row)">驳回</el-button>
            </template>
            <template v-else-if="row.status === 2"><el-button link type="success" @click="pay(row)">登记线下打款</el-button><el-button link type="danger" @click="reject(row)">驳回</el-button></template>
            <el-button v-if="row.payProof" link @click="downloadProof(row)">查看凭证</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <el-dialog v-model="payment.visible" title="核对账户并登记线下打款" width="min(580px,94vw)">
      <el-descriptions :column="1" border v-loading="payment.loading"><el-descriptions-item label="收款人">{{ payment.account?.name }}</el-descriptions-item><el-descriptions-item label="收款账号">{{ payment.account?.accountNo }}</el-descriptions-item><el-descriptions-item label="银行/类型">{{ payment.account?.bankName || ACCOUNT_TYPE[payment.account?.accountType] }}</el-descriptions-item><el-descriptions-item label="税后实付">{{ money(payment.row?.netAmount) }} 元</el-descriptions-item></el-descriptions>
      <el-form label-position="top" class="payment-form"><el-form-item label="银行流水号 / 转账单号" required><el-input v-model="payment.payNo" maxlength="64" /></el-form-item><el-form-item label="实际付款凭证" required><FileUpload v-model="payment.files" biz-type="mkt_withdrawal" :biz-id="payment.row?.id" accept=".pdf,.jpg,.jpeg,.png" /></el-form-item></el-form>
      <p class="hint">请按以上申请时账户完成真实付款后登记，系统不会自动转账。</p>
      <template #footer><el-button @click="payment.visible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="saving || payment.loading || !payment.account || !payment.payNo.trim() || !payment.files.length" @click="savePayment">确认已完成付款</el-button></template>
    </el-dialog>

    <el-dialog v-model="manual.visible" title="代伙伴申请提现" width="460px">
      <el-form label-width="100px">
        <el-form-item label="伙伴 ID" required><el-input-number v-model="manual.promoterId" :min="1" style="width: 100%" @change="loadBalance" /></el-form-item>
        <el-form-item label="可提现余额"><b>{{ manual.balance ?? '-' }}</b></el-form-item>
        <el-form-item label="提现金额" required><el-input-number v-model="manual.amount" :min="0" :max="manual.balance || 0" :precision="2" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="manual.visible = false">取消</el-button><el-button type="primary" @click="submitManual">提交</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktWithdrawalApi } from '@/api/marketing'
import FileUpload from '@/components/FileUpload.vue'
import { startFileDownload } from '@/utils/fileDownload'
import { money } from '@/utils/format'

const ST = { 1: '待审核', 2: '已审核', 3: '已打款', 4: '已驳回' }
const TAX = { 1: '个税代扣', 2: '灵工代征' }
const stType = (s) => ({ 1: 'warning', 2: 'primary', 3: 'success', 4: 'danger' }[s] || 'info')

const saving = ref(false)
const ACCOUNT_TYPE={1:'微信',2:'银行卡',3:'支付宝'}
const payment=reactive({visible:false,row:null,account:null,payNo:'',files:[],loading:false})
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

const manual = reactive({ visible: false, promoterId: null, amount: null, balance: null })
function openManual() { Object.assign(manual, { visible: true, promoterId: null, amount: null, balance: null }) }
async function loadBalance() { if (manual.promoterId) manual.balance = await mktWithdrawalApi.balance(manual.promoterId) }
async function submitManual() {
  if (!manual.promoterId || !manual.amount) return ElMessage.error('请填写伙伴与金额')
  await mktWithdrawalApi.manual({ promoterId: manual.promoterId, amount: manual.amount })
  ElMessage.success('已提交'); manual.visible = false; load()
}
async function approve(row) { await mktWithdrawalApi.approve(row.id); ElMessage.success('已审核'); load() }
async function reject(row) {
  const { value } = await ElMessageBox.prompt('驳回原因(流水回到可提现)', '驳回提现', { inputPattern: /\S+/, inputErrorMessage: '原因必填' })
  await mktWithdrawalApi.reject(row.id, { reason: value }); ElMessage.success('已驳回'); load()
}
async function pay(row) {
  Object.assign(payment,{visible:true,row,account:null,payNo:'',files:[],loading:true})
  try { payment.account=await mktWithdrawalApi.payAccount(row.id) } finally { payment.loading=false }
}
async function savePayment(){
  if(saving.value || !payment.payNo.trim() || !payment.files.length)return
  saving.value=true
  try{await mktWithdrawalApi.pay(payment.row.id,{payNo:payment.payNo.trim(),payProof:`file:${payment.files[0].id}`});payment.visible=false;ElMessage.success('付款凭证已保存');await load()}finally{saving.value=false}
}
async function downloadProof(row){const id=row.payProof?.match(/^file:(\d+)$/)?.[1];if(id)await startFileDownload(id,'提现付款凭证')}
onMounted(load)
</script>

<style scoped>
.payment-form{margin-top:20px}
.toolbar { display: flex; flex-wrap:wrap; align-items: center; gap: 12px; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
