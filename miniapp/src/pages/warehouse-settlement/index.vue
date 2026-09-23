<template>
  <view>
    <view class="card">
      <view class="tabs"><button :class="{active:kind==='settlement'}" @click="changeKind('settlement')">云仓结算</button><button :class="{active:kind==='bill'}" @click="changeKind('bill')">服务费核对</button></view>
      <view class="muted guide">{{ kind==='settlement' ? '核对本期应付云仓金额。确认后由园区财务安排线下付款。' : '核对园区签合同的出库服务费；有疑问请先提交争议。' }}</view>
    </view>
    <view v-if="error" class="card"><view class="error">{{ error }}</view><button class="btn" @click="load(true)">重新加载</button></view>
    <view v-if="loading && !rows.length" class="card muted">正在加载单据…</view>
    <view v-else-if="!rows.length && !error" class="card"><view class="title">暂无{{kind==='settlement'?'结算单':'服务费账单'}}</view><view class="muted">园区生成账单后会在这里展示，无需重复提交申请。</view></view>
    <view v-for="s in rows" :key="s.id" class="card">
      <view class="row"><view class="document-title">{{s.batchNo || `服务费账单 #${s.id}`}}</view><text class="tag" :class="s.status===4?'ok':s.status>=5?'warn':''">{{statuses[s.status] || '状态待更新'}}</text></view>
      <view class="muted">{{s.periodStart}} 至 {{s.periodEnd}}</view>
      <view class="amount">¥ {{money(s.amount)}}</view>
      <view v-if="s.frozenReason || s.disputeReason" class="explanation">处理说明：{{s.frozenReason || s.disputeReason}}</view>
      <view v-if="s.payNo || s.receiptNo" class="muted explanation">{{kind==='settlement'?'付款':'收款'}}流水：{{s.payNo || s.receiptNo}}</view>
      <view class="actions"><button size="mini" @click="toggleDetails(s)">{{details[s.id]?'收起明细':'查看明细'}}</button><template v-if="s.status===2"><button size="mini" :disabled="busy!==null" :loading="busy===s.id" @click="confirm(s)">核对并确认</button><button size="mini" :disabled="busy!==null" @click="dispute(s)">提交争议</button></template></view>
      <view v-if="details[s.id]" class="details"><view v-for="line in details[s.id]" :key="line.id" class="detail-row"><view>{{kind==='bill'?line.sourceNo:`来源账单 #${line.billId}`}}</view><view>{{money(line.amount)}} 元</view><view v-if="kind==='settlement'" class="muted">{{snapshotText(line.snapshotJson)}}</view></view></view>
    </view>
    <view v-if="rows.length" class="footer"><button v-if="rows.length<total" :loading="loading" :disabled="loading" @click="load(false)">加载更多</button><text v-else class="muted">共 {{total}} 笔，已全部显示</text></view>
  </view>
</template>
<script setup>
import {computed,ref} from 'vue'
import {onShow,onPullDownRefresh} from '@dcloudio/uni-app'
import {warehouseApi} from '@/api/warehouse'
const kind=ref('settlement'),rows=ref([]),page=ref(1),total=ref(0),loading=ref(false),error=ref(''),busy=ref(null),details=ref({})
const statuses=computed(()=>kind.value==='settlement'?{2:'待确认',3:'待园区付款',4:'已付款',5:'争议处理中',6:'对账冻结'}:{2:'待确认',3:'待登记收款',4:'已收款',5:'争议处理中'})
const money=value=>Number(value||0).toFixed(2)
let requestNo=0
async function load(reset=true){const n=++requestNo;loading.value=true;error.value='';if(reset){page.value=1;details.value={}}try{const r=await (kind.value==='settlement'?warehouseApi.settlements:warehouseApi.bills)({pageNo:page.value,pageSize:20});if(n!==requestNo)return;rows.value=reset?(r.records||[]):rows.value.concat(r.records||[]);total.value=r.total||0;page.value+=1}catch(e){if(n===requestNo)error.value=e.message||'单据加载失败，请重试'}finally{if(n===requestNo)loading.value=false;uni.stopPullDownRefresh()}}
function changeKind(value){if(kind.value===value)return;kind.value=value;rows.value=[];load(true)}
const modal=options=>new Promise(resolve=>uni.showModal({...options,success:resolve,fail:()=>resolve({confirm:false})}))
async function confirm(row){const r=await modal({title:'确认账单',content:`已核对 ${row.periodStart} 至 ${row.periodEnd}，金额 ${money(row.amount)} 元？`});if(!r.confirm||busy.value!==null)return;busy.value=row.id;try{await (kind.value==='settlement'?warehouseApi.confirmSettlement:warehouseApi.confirmBill)(row.id);uni.showToast({title:'已确认'});await load(true)}finally{busy.value=null}}
async function dispute(row){const r=await modal({title:'提交账单争议',editable:true,placeholderText:'请说明有疑问的订单或金额'});if(!r.confirm)return;const reason=r.content?.trim();if(!reason){uni.showToast({title:'请填写争议原因',icon:'none'});return}if(busy.value!==null)return;busy.value=row.id;try{await(kind.value==='settlement'?warehouseApi.disputeSettlement:warehouseApi.disputeBill)(row.id,reason);uni.showToast({title:'争议已提交'});await load(true)}finally{busy.value=null}}
async function toggleDetails(row){if(details.value[row.id]){delete details.value[row.id];return}const result=await(kind.value==='settlement'?warehouseApi.settlementLines:warehouseApi.billLines)(row.id);details.value[row.id]=result||[]}
function snapshotText(raw){try{const s=JSON.parse(raw||'{}');return `单票 ${s.perOrder} × ${s.packages} 包裹；按件 ${s.perItem} × ${s.qty} 件`}catch{return '费率快照暂不可读'}}
onShow(()=>load(true));onPullDownRefresh(()=>load(true))
</script>
<style scoped>
.tabs{display:flex;gap:16rpx}.tabs button{flex:1;font-size:28rpx}.tabs .active{color:#23774b;background:#eef8f2}.guide{margin-top:20rpx;line-height:1.7}.document-title{min-width:0;overflow-wrap:anywhere;flex:1;margin-right:20rpx}.amount{font-size:44rpx;font-weight:600;margin:24rpx 0;font-variant-numeric:tabular-nums}.actions{display:flex;flex-wrap:wrap;gap:16rpx}.actions button{margin:0}.explanation{overflow-wrap:anywhere;line-height:1.7;margin-bottom:20rpx}.details{margin-top:24rpx;border-top:1px solid #e8ece9}.detail-row{padding:20rpx 0;border-bottom:1px solid #e8ece9;overflow-wrap:anywhere}.footer{text-align:center;padding:24rpx}.error{color:#a03424;margin-bottom:16rpx}
</style>
