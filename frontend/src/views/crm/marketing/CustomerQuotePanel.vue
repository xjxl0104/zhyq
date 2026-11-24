<template>
  <section class="panel">
    <h3>云仓报价评估与分配</h3>
    <el-alert v-if="failure" type="error" :title="failure" :closable="false" />
    <template v-if="state?.stage === 'project_required'">
      <el-alert type="warning" :closable="false" title="该客户尚未关联园区，确认归属后才能匹配本园区的云仓。" />
      <el-button v-if="canEdit" :loading="loading" @click="confirmProject">确认归属 {{ projectName || ('园区 #' + projectId) }}</el-button>
    </template>
    <p v-else-if="state?.stage === 'lead'">线索客户。推荐伙伴可在小程序客户详情中转为意向客户。</p>
    <p v-else-if="state && !state.submittedAt">意向客户，等待客户扫码填写报价需求。</p>
    <template v-if="state?.submittedAt">
      <p><strong>{{ assignmentStages[assignmentStatus] }}</strong>　需求提交于 {{ String(state.submittedAt).replace('T',' ') }}</p>
      <el-collapse v-model="demandOpen"><el-collapse-item title="查看客户填写的报价需求" name="demand">
        <el-descriptions :column="1" border><el-descriptions-item v-for="[key,label] in visibleFields" :key="key" :label="label">{{ formatField(key,state.requirements[key]) }}</el-descriptions-item></el-descriptions>
      </el-collapse-item></el-collapse>
      <h4>AI 比价报告</h4>
      <p v-if="comparison">园区上线 {{ comparison.warehouses.length }} 家 · 已维护价卡 {{ cardCount }} 家 · 本次有可比报价 {{ quotedCount }} 家。<strong v-if="quotedCount===1">仅一家云仓有可比报价，暂无跨仓比价。</strong></p>
      <p>{{ advice?.message || (loading ? '正在读取…' : '等待评估') }}</p>
      <ol v-if="advice?.suggestions?.length"><li v-for="line in advice.suggestions" :key="line">{{line}}</li></ol>
      <el-button v-if="canEdit && advice?.status === 'failed'" size="small" :loading="loading" @click="retryAdvice">重新评估</el-button>
      <p v-if="lowest"><strong>票价核对参考</strong>　{{ lowest.warehouse }} · {{ lowest.offer.carrier }}，{{ range(lowest.offer,'perParcel','元/票') }}。在范围可比、登记产能未明确不足的方案中，这一项的票价上限最低。仓租与增值服务另计。</p>
      <h4>各云仓预估报价</h4>
      <p v-if="comparison && !comparison.warehouses.length">当前园区还没有已上线的加盟云仓。</p>
      <div v-if="warehouseRows.length" class="table-scroll"><table class="comparison">
        <thead><tr><th>云仓</th><th>已核实的匹配项</th><th>缺项与限制</th></tr></thead>
        <tbody><tr v-for="w in warehouseRows" :key="w.id"><th>{{w.name}}</th><td>{{w.matches}}</td><td>{{w.gaps}}</td></tr></tbody>
      </table></div>
      <article v-for="(warehouse,index) in comparison?.warehouses" :key="warehouse.warehouseId">
        <h4>方案 {{index + 1}} · {{ warehouse.warehouseName }}</h4>
        <p v-if="!warehouse.version">云仓尚未录入价卡</p>
        <details v-if="warehouse.stageRates?.length"><summary>通达配条件套餐报价</summary>
          <p>客户未指定快递。本仓通达配套餐的实际承运商及适用条件须确认，套餐与分项计费二选一。</p>
          <dl><template v-for="line in warehouse.stageRates" :key="line.weight"><dt>{{line.weight}}</dt>
            <dd>{{line.rate}} {{line.unit}}<div>{{line.scopeNote}}</div></dd></template></dl>
        </details>
        <el-button v-if="canAssign && [0,3].includes(assignmentStatus)" size="small" :disabled="assignmentStatus===0 && (!advice || advice.status==='pending')" @click="$emit('assign',warehouse.warehouseId)">选择此仓分配</el-button>
        <el-collapse><el-collapse-item v-for="(offer,i) in warehouse.offers" :key="i" :name="i">
          <template #title><strong>{{offer.carrier}}</strong><span class="summary">{{ status[offer.status] }} · {{ range(offer,'perParcel','元/票') }}</span></template>
          <dl>
            <template v-for="[key,label] in units" :key="key"><dt>{{label}}</dt><dd>{{range(offer.unitPrices,key,'元/票')}}</dd></template>
            <dt>预估票价</dt><dd>{{range(offer,'perParcel','元/票')}}</dd>
            <dt>30 天仓租</dt><dd>{{range(offer,'storageMonthly','元')}}</dd>
            <dt>登记日处理能力</dt><dd>{{ offer.dailyCapacity == null ? '待确认' : offer.dailyCapacity + ' 单/天' }}</dd>
            <template v-for="item in offer.selectedServiceRates" :key="item.label"><dt>{{item.label}}</dt>
              <dd>{{item.rate == null ? '待报价' : item.rate + ' ' + item.unit}}
                <div v-if="item.terms?.included != null">包含 {{item.terms.included}} 件</div>
                <div v-if="item.terms?.next != null">续件 {{item.terms.next}} 元</div><div v-if="item.terms?.note">{{item.terms.note}}</div>
              </dd>
            </template>
          </dl>
          <details v-if="offer.freightTerms?.length"><summary>查看承运条款及附加费用</summary><p v-for="term in offer.freightTerms" :key="term">{{term}}</p></details>
          <p v-if="offer.scopeNote">{{offer.scopeNote}}</p>
          <ul v-if="offer.missing?.length"><li v-for="reason in offer.missing" :key="reason">{{reason}}</li></ul>
          <p v-for="note in offer.notes" :key="note" class="muted">{{note}}</p>
        </el-collapse-item></el-collapse>
      </article>
    </template>
    <el-button link type="primary" :loading="loading" @click="load">刷新报价</el-button>
  </section>
</template>
<script setup>
import { ref,computed,onMounted,onBeforeUnmount } from 'vue'
import {quoteApi} from '@/api/warehouseQuotes'
const props=defineProps({customerId:{type:[Number,String],required:true},projectId:{type:[Number,String],required:true},
  projectName:String,canEdit:Boolean,canAssign:Boolean,assignmentStatus:{type:Number,default:0}})
const emit=defineEmits(['assign','project-confirmed'])
const assignmentStages={0:'待后台分配',1:'已分配 · 等待云仓承接',2:'云仓已承接',3:'云仓已拒绝 · 待重新分配'}
const state=ref(null), comparison=ref(null), advice=ref(null), loading=ref(false), failure=ref(''), demandOpen=ref(['demand'])
const fields=Object.entries({salesPlatforms:'销售平台',salesPlatformOther:'其他销售平台',productCategories:'销售品类',productCategoryOther:'其他品类',palletCount:'约占卡板数',weightRanges:'包裹实重档（kg）',heavyWeightKg:'典型重件重量（kg）',weightMix:'重量分布',skuCount:'SKU 数量',dailyOrders:'日均单量',peakOrders:'大促单量',materialProviders:'纸箱耗材提供方',erpSystems:'使用系统',erpOther:'其他系统',couriers:'指定承运商',courierOther:'其他承运商',services:'增值服务',serviceQuantities:'增值服务用量',invoice:'开票要求',itemsPerParcel:'每票商品件数',batchPercent:'批量单占比（%）',packingNote:'包装说明'})
const visibleFields=computed(()=>fields.filter(([key])=>state.value?.requirements?.[key]!=null))
const lowest=computed(()=>{
  const quoted=(comparison.value?.warehouses || []).flatMap(warehouse=>warehouse.offers
    .filter(offer=>offer.perParcelMin!=null && offer.perParcelMax!=null && !offer.regionalCoveragePartial && offer.capacityFit!=='exceeded')
    .map(offer=>({warehouseId:warehouse.warehouseId,warehouse:warehouse.warehouseName,offer})))
  if (new Set(quoted.map(q=>q.warehouseId)).size<2) return null
  return quoted.sort((a,b)=>Number(a.offer.perParcelMax)-Number(b.offer.perParcelMax) || Number(a.offer.perParcelMin)-Number(b.offer.perParcelMin))[0]
})
const units=[['handling','打包操作'],['materials','标准包材'],['freight','快递运费']]
const status={estimate:'可估算',partial:'部分待确认',missing:'缺少快递价'}
const cardCount=computed(()=>(comparison.value?.warehouses || []).filter(w=>w.version>0).length)
const quotedCount=computed(()=>(comparison.value?.warehouses || []).filter(w=>w.version>0 && (w.stageRates?.length || w.offers.some(o=>
  o.status!=='missing' && (o.perParcelMin!=null || o.unitPrices?.freightMin!=null || o.unitPrices?.handlingMin!=null || o.storageMonthlyMin!=null || o.selectedServiceRates?.some(r=>r.rate!=null))))).length)
const warehouseRows=computed(()=>(comparison.value?.warehouses || []).map(w=>{
  const offers=w.offers || [], matches=[], gaps=[]
  const priced=offers.filter(o=>o.perParcelMax!=null)
  const freight=offers.filter(o=>o.unitPrices?.freightMax!=null)
  if(priced.length) matches.push(priced.length+' 个方案可估单票总价')
  if(w.stageRates?.length) matches.push('已报匹配重量段的通达配条件套餐')
  if(freight.length) matches.push(freight.map(o=>o.carrier).join('、')+'有运费报价')
  if(offers.some(o=>o.capacityFit==='fits')) matches.push('登记产能覆盖填报单量')
  if(!w.version) gaps.push('尚未录入价卡')
  else {
    if(!priced.length) gaps.push('暂无可核算的单票总价')
    if(offers.some(o=>o.regionalCoveragePartial)) gaps.push('仅覆盖部分地区或单量档')
    if(offers.some(o=>o.capacityFit==='exceeded')) gaps.push('登记产能低于客户需求')
    if(offers.some(o=>o.capacityFit==='uncertain')) gaps.push('高峰产能待确认')
    const missing=offers.filter(o=>o.status==='missing').map(o=>o.carrier)
    if(missing.length) gaps.push('未匹配承运商：'+missing.join('、'))
    if(offers.some(o=>o.missing?.length)) gaps.push('逐项缺价见下方明细')
  }
  return {id:w.warehouseId,name:w.warehouseName,matches:matches.join('；')||'暂无已核实项',gaps:gaps.join('；')||'已填项目未发现缺项'}
}))
function formatField(key,value){
  if (key==='serviceQuantities') return Object.entries(value).map(([k,v])=>({inbound:'入库',packing:'出库包装',returns:'逆向退仓'}[k] || k)+' '+v).join('；')
  if (key==='weightRanges') return value.map(v=>v==='0-0.3'?'≤0.3':v==='>3'?'＞3':'＞'+v.replace('-',' 至 ')).join('、')
  if (Array.isArray(value)) return value.join('、')
  return String(value).replace(/^>=/,'≥')
}
function range(row,key,unit){
  const low=row?.[key+'Min'], high=row?.[key+'Max']
  if (low==null || high==null) return '待确认'
  return (Number(low)===Number(high)?String(low):low+'–'+high)+' '+unit
}
let disposed=false, timer
async function refreshAdvice(){
  const response=await quoteApi.advice(props.customerId,props.projectId)
  if (disposed) return
  advice.value=response
  if (response.status==='pending') timer=setTimeout(()=>refreshAdvice().catch(e=>{if (!disposed) failure.value=e.message || '刷新评估结果失败，请点击刷新报价'}),6000)
}
async function retryAdvice(){
  if(loading.value) return
  failure.value=''; loading.value=true
  try { await quoteApi.retry(props.customerId,props.projectId); if(!disposed) await refreshAdvice() }
  catch(e){if(!disposed) failure.value=e.message || '发起重新评估失败'}
  finally {loading.value=false}
}
async function confirmProject(){
  if(loading.value) return
  failure.value=''; loading.value=true
  try { await quoteApi.confirmProject(props.customerId,props.projectId); if(!disposed) emit('project-confirmed') }
  catch(e){if(!disposed) failure.value=e.message || '归属确认失败'}
  finally {loading.value=false}
}
async function load(){
  if (loading.value) return
  clearTimeout(timer); failure.value=''; loading.value=true
  try {
    state.value=await quoteApi.intake(props.customerId,props.projectId)
    if (state.value?.submittedAt && !disposed){
      comparison.value=await quoteApi.comparison(props.customerId,props.projectId)
      await refreshAdvice()
    }
  } catch(e){failure.value=e.message || '读取报价失败'}
  finally{loading.value=false}
}
onMounted(load)
onBeforeUnmount(()=>{disposed=true;clearTimeout(timer)})
</script>
<style scoped>
.panel { margin:16px 0; padding:14px; border:1px solid var(--el-border-color); border-radius:8px }
h3,h4 { margin:10px 0 } article { padding:6px 0 } .summary,.muted { color:var(--el-text-color-secondary) }
.summary { margin-left:12px } dl { display:grid;grid-template-columns:1fr 1fr;gap:5px } dd { margin:0 } ul li { color:var(--el-color-warning-dark-2) } ol {line-height:1.8;padding-left:22px}
.table-scroll{overflow-x:auto}.comparison{border-collapse:collapse;width:100%;font-size:13px}.comparison th,.comparison td{border:1px solid var(--el-border-color);padding:8px;text-align:left;vertical-align:top;min-width:80px}
</style>
