<template>
  <div class="page-container">
    <el-alert :title="isBill ? '按园区签合同汇总出库服务费：云仓核对 → 财务登记实际到账 → 生成云仓应付结算。' : '按已收款账单和云仓成本费率生成应付结算：云仓确认 → 财务线下付款 → 上传真实凭证。'" type="info" :closable="false" show-icon class="notice" />
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="云仓"><el-select v-model="query.warehouseId" filterable clearable placeholder="全部云仓" class="selector"><el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部状态" class="selector"><el-option v-for="(text,key) in statuses" :key="key" :label="text" :value="Number(key)" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </div>
    <div class="table-card">
      <div class="toolbar"><el-button v-if="canEdit" type="primary" :loading="choicesLoading" @click="openGenerate">{{ isBill ? '生成 / 补充服务费账单' : '生成 / 补充云仓结算' }}</el-button><el-button v-if="isBill && canGenerate" :loading="generatingFixed" @click="generateFixed">补齐到期固定月费</el-button><el-button v-if="isBill && canPay" :disabled="choicesLoading" @click="openDirect">登记直签平台费到账</el-button><el-button v-if="isBill" @click="showDirectRecords">平台费到账记录</el-button></div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" class="notice"><el-button link @click="load">重新加载</el-button></el-alert>
      <el-table :data="rows" v-loading="loading" border stripe empty-text="暂无单据。先完成有效合同、订单导入和费用配置，再生成本期账单。">
        <el-table-column prop="id" label="单据ID" width="85" />
        <el-table-column label="云仓" min-width="140"><template #default="{row}">{{ warehouseName(row.warehouseId) }}</template></el-table-column>
        <el-table-column v-if="isBill" prop="contractId" label="合同ID" width="90" />
        <el-table-column v-else prop="batchNo" label="批次号" min-width="170" />
        <el-table-column label="账期" min-width="210"><template #default="{row}">{{ row.periodStart }} 至 {{ row.periodEnd }}</template></el-table-column>
        <el-table-column :label="isBill ? '应收服务费（元）' : '应付云仓（元）'" width="160" align="right"><template #default="{row}">{{ money(row.amount) }}</template></el-table-column>
        <el-table-column label="状态" width="120"><template #default="{row}"><el-tag :type="tagType(row.status)">{{ statuses[row.status] || '未知状态' }}</el-tag></template></el-table-column>
        <el-table-column :prop="isBill ? 'disputeReason' : 'frozenReason'" label="争议 / 处理说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="240" fixed="right"><template #default="{row}">
          <el-button link type="primary" @click="showLines(row)">明细</el-button>
          <el-button v-if="row.status === 3 && canPay" link type="success" @click="openPayment(row)">{{ isBill ? '登记到账' : '登记付款' }}</el-button>
          <el-button v-if="canEdit && (row.status === 5 || (!isBill && row.status === 6))" link type="warning" @click="resolve(row)">处理后重发</el-button>
          <el-button v-if="isBill ? row.receiptProof : row.payProof" link @click="downloadProof(isBill ? row.receiptProof : row.payProof)">凭证</el-button>
        </template></el-table-column>
      </el-table>
      <el-pagination class="pager" layout="total,prev,pager,next,sizes" :total="total" v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" :page-sizes="[20,50,100]" @change="load" />
    </div>
    <el-dialog v-model="generation.visible" :title="isBill ? '生成服务费账单' : '生成云仓结算'" width="min(540px, 94vw)">
      <el-form label-position="top">
        <el-form-item :label="isBill ? '服务合同' : '云仓'" required><el-select v-model="generation.targetId" filterable class="full"><el-option v-for="item in isBill ? parkContracts : warehouses" :key="item.id" :label="isBill ? `${item.contractNo} · 合同 #${item.id}` : item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="账期（含起止日）" required><el-date-picker v-model="generation.period" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" class="full" /></el-form-item>
        <p class="hint">{{ isBill ? '仅汇总本合同已确认且未入账的出库单；同一账期再次生成会补充新订单，没有新增则返回原单。' : '只使用已实际收款且未结算的账单；同一账期可补充后续收款，金额按云仓应付费率计算并固定保存。' }}</p>
      </el-form>
      <template #footer><el-button @click="generation.visible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="saving" @click="generate">确认生成</el-button></template>
    </el-dialog>
    <el-dialog v-model="payment.visible" :title="isBill ? '登记真实到账' : '登记线下付款'" width="min(580px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="核对金额（元）"><strong>{{ money(payment.row?.amount) }}</strong></el-form-item>
        <el-form-item label="银行流水号 / 交易单号" required><el-input v-model="payment.number" maxlength="64" /></el-form-item>
        <el-form-item label="真实交易凭证" required><FileUpload v-model="payment.files" :biz-type="isBill ? 'mkt_bill' : 'mkt_settlement'" :biz-id="payment.row?.id" accept=".pdf,.jpg,.jpeg,.png" /></el-form-item>
        <p class="hint">请在实际资金到账或付款完成后登记。此操作只记录凭证，不发起自动转账。</p>
      </el-form>
      <template #footer><el-button @click="payment.visible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="saving || !payment.number.trim() || !payment.files.length" @click="recordPayment">确认登记</el-button></template>
    </el-dialog>
    <el-dialog v-model="direct.visible" title="登记直签平台费到账" width="min(580px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="云仓直签合同（含已到期待收尾款）" required><el-select v-model="direct.contractId" filterable class="full" @change="direct.files=[]; direct.period=[]"><el-option v-for="c in directContracts" :key="c.id" :label="`${c.contractNo} · ${warehouseName(c.warehouseId)}${c.status === 7 ? ' · 已到期' : ''}`" :value="c.id" /></el-select></el-form-item>
        <el-form-item label="费用所属期（须在合同期限内）" required><el-date-picker v-model="direct.period" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" class="full" /></el-form-item>
        <el-form-item label="园区实际收到的平台费（元）" required><el-input v-model="direct.amount" inputmode="decimal" placeholder="不填写云仓客户营业额" /></el-form-item>
        <el-form-item label="到账流水号" required><el-input v-model="direct.paymentNo" maxlength="64" /></el-form-item>
        <el-form-item v-if="direct.contractId" label="到账凭证" required><FileUpload v-model="direct.files" biz-type="mkt_direct_payment" :biz-id="direct.contractId" accept=".pdf,.jpg,.jpeg,.png" /></el-form-item>
        <p class="hint">按实际平台费和合同评级计佣；相同流水号只入账一次。</p>
      </el-form>
      <template #footer><el-button @click="direct.visible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="saving" @click="recordDirect">确认到账并计佣</el-button></template>
    </el-dialog>
    <el-drawer v-model="details.visible" :title="details.title" size="min(760px, 95vw)">
      <el-table :data="details.rows" border v-loading="details.loading"><el-table-column v-for="c in details.columns" :key="c.prop" :prop="c.prop" :label="c.label" min-width="120" /><el-table-column label="金额（元）" align="right" width="150"><template #default="{row}">{{ money(row.amount) }}</template></el-table-column><el-table-column v-if="!isBill && !details.direct" label="费率快照" min-width="220"><template #default="{row}">{{ snapshotText(row.snapshotJson) }}</template></el-table-column></el-table>
    </el-drawer>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktBillApi, mktSettlementApi, mktWarehouseApi, mktContractApi } from '@/api/marketing'
import FileUpload from '@/components/FileUpload.vue'
import { money } from '@/utils/format'
import { startFileDownload } from '@/utils/fileDownload'
import { hasPermission } from '@/utils/permission'
const canGenerate=hasPermission('crm:marketing:bill:edit')
const generatingFixed=ref(false)
const props=defineProps({kind:{type:String,required:true}})
const isBill=computed(()=>props.kind==='bill'); const api=computed(()=>isBill.value?mktBillApi:mktSettlementApi)
const statuses=computed(()=>isBill.value?{2:'待云仓确认',3:'已确认待收款',4:'已收款',5:'有争议'}:{2:'待云仓确认',3:'已确认待付款',4:'已付款',5:'有争议',6:'对账冻结'})
const canEdit=computed(()=>hasPermission(`crm:marketing:${isBill.value?'bill':'settlement'}:edit`))
const canPay=computed(()=>hasPermission(`crm:marketing:${isBill.value?'bill':'settlement'}:pay`))
const tagType=s=>({2:'warning',3:'primary',4:'success',5:'danger',6:'danger'}[s]||'info')
const query=reactive({pageNo:1,pageSize:20,warehouseId:null,status:null}), rows=ref([]),total=ref(0),loading=ref(false),saving=ref(false),error=ref('')
const warehouses=ref([]),contracts=ref([]),choicesLoading=ref(false),parkContracts=computed(()=>contracts.value.filter(c=>c.signMode===1&&[4,5,6,7].includes(c.status))),directContracts=computed(()=>contracts.value.filter(c=>c.signMode===2&&[4,5,6,7].includes(c.status)))
const warehouseName=id=>warehouses.value.find(w=>w.id===id)?.name||`云仓 #${id}`
async function load(){loading.value=true;error.value='';try{const r=await api.value.page(query);rows.value=r.records||[];total.value=r.total||0}catch(e){error.value=e.message||'单据加载失败，请重试'}finally{loading.value=false}}
function search(){query.pageNo=1;load()} function reset(){Object.assign(query,{pageNo:1,warehouseId:null,status:null});load()}
const generation=reactive({visible:false,targetId:null,period:[]}),payment=reactive({visible:false,row:null,number:'',files:[]}),direct=reactive({visible:false,contractId:null,amount:'',paymentNo:'',files:[],period:[]})
async function allChoices(fetchPage){
  const records=[]
  for(let pageNo=1;;pageNo++){
    const result=await fetchPage({pageNo,pageSize:100})
    const page=result.records||[]
    records.push(...page)
    if(pageNo*100>=Number(result.total||0)||!page.length)break
  }
  return [...new Map(records.map(row=>[row.id,row])).values()]
}
async function loadChoices(){choicesLoading.value=true;try{const [w,c]=await Promise.all([allChoices(mktWarehouseApi.page),allChoices(mktContractApi.page)]);warehouses.value=w;contracts.value=c}finally{choicesLoading.value=false}}
async function openGenerate(){if(choicesLoading.value)return;await loadChoices();Object.assign(generation,{visible:true,targetId:null,period:[]})}
async function generate(){if(!generation.targetId||generation.period?.length!==2)return ElMessage.warning('请选择合同/云仓与完整账期');saving.value=true;try{await api.value.generate({[isBill.value?'contractId':'warehouseId']:generation.targetId,periodStart:generation.period[0],periodEnd:generation.period[1]});generation.visible=false;ElMessage.success('已生成');await load()}finally{saving.value=false}}
async function generateFixed(){
  try {
    await ElMessageBox.confirm('按合同固定月费补齐截至今天的到期账单，已生成账期会自动跳过。结果可在“财务管理 → 所有账单”查看。','补齐固定月费',{confirmButtonText:'确认生成',cancelButtonText:'取消'})
    generatingFixed.value=true
    const count=await mktBillApi.generateFixed()
    ElMessage.success(count>0?`已生成 ${count} 张固定月费账单，请到“所有账单”查看`:'到期固定月费已齐全，无新增账单')
  } catch(e) { if(e!=='cancel'&&e!=='close'&&e?.message) error.value=e.message }
  finally { generatingFixed.value=false }
}
function openPayment(row){Object.assign(payment,{visible:true,row,number:'',files:[]})}
async function recordPayment(){if(!payment.number.trim()||!payment.files.length)return;saving.value=true;try{const proof=`file:${payment.files[0].id}`;if(isBill.value)await mktBillApi.receive(payment.row.id,{receiptNo:payment.number.trim(),receiptProof:proof,amount:String(payment.row.amount)});else await mktSettlementApi.pay(payment.row.id,{payNo:payment.number.trim(),payProof:proof,amount:String(payment.row.amount)});payment.visible=false;ElMessage.success('资金记录已保存');await load()}finally{saving.value=false}}
async function resolve(row){try{const {value}=await ElMessageBox.prompt('请填写已经核实的处理结果。仍有对账差异的冻结单不能解除。','处理后请云仓重新确认',{inputPattern:/\S+/,inputErrorMessage:'请填写处理说明'});await api.value.resolve(row.id,{reason:value});ElMessage.success('已重新发送待确认');await load()}catch(e){if(e!=='cancel'&&e!=='close'&&e?.message)error.value=e.message}}
async function openDirect(){if(choicesLoading.value)return;await loadChoices();Object.assign(direct,{visible:true,contractId:null,amount:'',paymentNo:'',files:[],period:[]})}
async function recordDirect(){
  if(saving.value)return
  const c=directContracts.value.find(c=>c.id===direct.contractId)
  if(!c||!/^\d+(\.\d{1,2})?$/.test(direct.amount)||Number(direct.amount)<=0||!direct.paymentNo.trim()||!direct.files.length)return ElMessage.warning('请选择合同，填写正数金额、流水号并上传凭证')
  if(direct.period?.length!==2||direct.period[0]<c.startDate||direct.period[1]>c.endDate||direct.period[1]<direct.period[0])return ElMessage.warning('请选择合同期限内的费用所属期')
  saving.value=true
  try{await mktSettlementApi.directPayment({contractId:c.id,warehouseId:c.warehouseId,paymentNo:direct.paymentNo.trim(),amount:direct.amount,payProof:`file:${direct.files[0].id}`,periodStart:direct.period[0],periodEnd:direct.period[1]});direct.visible=false;ElMessage.success('平台费已登记，佣金已生成')}finally{saving.value=false}
}
const details=reactive({visible:false,title:'明细',rows:[],columns:[],loading:false,direct:false})
async function showLines(row){Object.assign(details,{visible:true,loading:true,direct:false,title:`单据 #${row.id} 明细`,columns:isBill.value?[{prop:'sourceNo',label:'出库单号'}]:[{prop:'billId',label:'来源账单ID'}],rows:[]});try{details.rows=await api.value.lines(row.id)}finally{details.loading=false}}
async function showDirectRecords(){Object.assign(details,{visible:true,loading:true,direct:true,title:'平台费到账记录（最近100条）',columns:[{prop:'paymentNo',label:'到账流水号'},{prop:'contractId',label:'合同ID'},{prop:'paidAt',label:'到账登记时间'}],rows:[]});try{details.rows=(await mktSettlementApi.directPayments({pageNo:1,pageSize:100})).records||[]}finally{details.loading=false}}
function snapshotText(value){try{const s=JSON.parse(value||'{}');return `单票 ${s.perOrder} × ${s.packages} 包裹；按件 ${s.perItem} × ${s.qty} 件`}catch{return '快照暂不可读'}}
async function downloadProof(proof){const id=String(proof).match(/^file:(\d+)$/)?.[1];if(id)await startFileDownload(id,'交易凭证')}
onMounted(()=>{load();allChoices(mktWarehouseApi.page).then(records=>warehouses.value=records).catch(()=>{})})
</script>
<style scoped>
.notice{margin-bottom:16px}.toolbar{display:flex;flex-wrap:wrap;gap:12px;margin-bottom:16px}.selector{width:220px}.full{width:100%}.hint{color:var(--el-text-color-secondary);line-height:1.7}.pager{margin-top:16px;justify-content:flex-end}.el-table{font-variant-numeric:tabular-nums}@media(max-width:640px){.selector{width:100%}.search-bar :deep(.el-form-item){display:flex}.pager{justify-content:flex-start;overflow:auto}}
</style>
