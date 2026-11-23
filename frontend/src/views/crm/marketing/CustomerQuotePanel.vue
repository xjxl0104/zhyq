<template>
  <section class="panel">
    <h3>云仓报价</h3>
    <el-alert v-if="failure" type="error" :title="failure" :closable="false" />
    <p v-if="state?.stage === 'lead'">线索客户。推荐伙伴可在小程序客户详情中转为意向客户。</p>
    <p v-else-if="state && !state.submittedAt">意向客户，等待客户扫码填写报价需求。</p>
    <template v-if="state?.submittedAt">
      <p>需求提交时间 {{ String(state.submittedAt).replace('T',' ') }}</p>
      <el-collapse><el-collapse-item title="查看客户填写的报价需求" name="demand">
        <el-descriptions :column="1" border><el-descriptions-item v-for="[key,label] in visibleFields" :key="key" :label="label">{{ formatField(key,state.requirements[key]) }}</el-descriptions-item></el-descriptions>
      </el-collapse-item></el-collapse>
      <p v-if="lowest"><strong>票价核对参考</strong>　{{ lowest.warehouse }} · {{ lowest.offer.carrier }}，{{ range(lowest.offer,'perParcel','元/票') }}。在已算出票价的方案中，这一项的区间上限最低。仓租和增值服务另计。</p>
      <h4>各云仓预估报价</h4>
      <p v-if="comparison && !comparison.warehouses.length">当前园区还没有已上线的加盟云仓。</p>
      <article v-for="(warehouse,index) in comparison?.warehouses" :key="warehouse.warehouseId">
        <h4>方案 {{index + 1}} · {{ warehouse.warehouseName }}</h4>
        <p v-if="!warehouse.version">云仓尚未录入价卡</p>
        <el-collapse><el-collapse-item v-for="(offer,i) in warehouse.offers" :key="i" :name="i">
          <template #title><strong>{{offer.carrier}}</strong><span class="summary">{{ status[offer.status] }} · {{ range(offer,'perParcel','元/票') }}</span></template>
          <dl>
            <template v-for="[key,label] in units" :key="key"><dt>{{label}}</dt><dd>{{range(offer.unitPrices,key,'元/票')}}</dd></template>
            <dt>预估票价</dt><dd>{{range(offer,'perParcel','元/票')}}</dd>
            <dt>30 天仓租</dt><dd>{{range(offer,'storageMonthly','元')}}</dd>
            <template v-for="item in offer.selectedServiceRates" :key="item.label"><dt>{{item.label}}</dt><dd>{{item.rate == null ? '待报价' : item.rate + ' ' + item.unit}}</dd></template>
          </dl>
          <p v-if="offer.scopeNote">{{offer.scopeNote}}</p>
          <ul v-if="offer.missing?.length"><li v-for="reason in offer.missing" :key="reason">{{reason}}</li></ul>
          <p v-for="note in offer.notes" :key="note" class="muted">{{note}}</p>
        </el-collapse-item></el-collapse>
      </article>
      <h4>AI 评估建议</h4>
      <p>{{ advice?.message || (loading ? '正在读取…' : '等待评估') }}</p>
      <p v-for="line in advice?.suggestions" :key="line">{{line}}</p>
    </template>
    <el-button link type="primary" :loading="loading" @click="load">刷新报价</el-button>
  </section>
</template>
<script setup>
import { ref,computed,onMounted,onBeforeUnmount } from 'vue'
import {quoteApi} from '@/api/warehouseQuotes'
const props=defineProps({customerId:{type:[Number,String],required:true},projectId:{type:[Number,String],required:true}})
const state=ref(null), comparison=ref(null), advice=ref(null), loading=ref(false), failure=ref('')
const fields=Object.entries({salesPlatforms:'销售平台',salesPlatformOther:'其他销售平台',productCategories:'销售品类',productCategoryOther:'其他品类',palletCount:'约占卡板数',weightRanges:'包裹实重档（kg）',heavyWeightKg:'典型重件重量（kg）',weightMix:'重量分布',skuCount:'SKU 数量',dailyOrders:'日均单量',peakOrders:'大促单量',materialProviders:'纸箱耗材提供方',erpSystems:'使用系统',erpOther:'其他系统',couriers:'指定承运商',courierOther:'其他承运商',services:'增值服务',serviceQuantities:'增值服务用量',invoice:'开票要求',itemsPerParcel:'每票商品件数',batchPercent:'批量单占比（%）',packingNote:'包装说明'})
const visibleFields=computed(()=>fields.filter(([key])=>state.value?.requirements?.[key]!=null))
const lowest=computed(()=>{
  const quoted=(comparison.value?.warehouses || []).flatMap(warehouse=>warehouse.offers
    .filter(offer=>offer.perParcelMin!=null && offer.perParcelMax!=null)
    .map(offer=>({warehouse:warehouse.warehouseName,offer})))
  if (quoted.length<2) return null
  return quoted.sort((a,b)=>Number(a.offer.perParcelMax)-Number(b.offer.perParcelMax) || Number(a.offer.perParcelMin)-Number(b.offer.perParcelMin))[0]
})
const units=[['handling','打包操作'],['materials','标准包材'],['freight','快递运费']]
const status={estimate:'可估算',partial:'部分待确认',missing:'缺少快递价'}
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
  if (response.status==='pending') timer=setTimeout(()=>refreshAdvice().catch(()=>{}),6000)
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
.summary { margin-left:12px } dl { display:grid;grid-template-columns:1fr 1fr;gap:5px } dd { margin:0 } li { color:var(--el-color-warning-dark-2) }
</style>
