<template><view class="page-wrap">
  <view class="card"><view class="wh-title">加盟协议</view><view v-if="loading" class="wh-note">正在读取协议…</view><view class="wh-note">{{ agreement.joinStatus === 4 ? '上传双方签署的加盟协议，由园区审核确认上线。' : '加盟协议与客户服务合同分别保存；已上线协议不可自行替换。' }}</view><WarehouseFiles v-model="agreementFiles" :max="1" :readonly="agreement.joinStatus !== 4 || busy || contractFileBusy" @busy="agreementFileBusy=$event"/><button v-if="agreement.joinStatus === 4" class="btn" :disabled="busy||fileBusy||!agreementFiles.length" :loading="busy" @click="saveAgreement">提交加盟协议</button></view>
  <view class="card"><view class="wh-row"><view class="wh-title">客户服务合同</view><button class="wh-secondary" :disabled="busy||fileBusy" @click="newDraft">起草直签合同</button></view><view class="wh-note">直签合同需上传签署件并提交园区备案审核，商家不能自行使合同生效。</view>
    <view v-if="error" class="wh-error" role="alert">{{ error }}</view>
    <view v-if="editing" class="contract-form">
      <view class="wh-item-title">{{ form.id ? '编辑合同草稿' : '新建直签合同' }}</view>
      <view class="wh-label">已承接客户</view>
      <view v-if="form.id || preselected" class="wh-field">{{ customerName }}</view>
      <picker v-else :range="acceptedCustomers" range-key="name" @change="pickCustomer"><view class="wh-field">{{ customerName || '选择已确认承接的客户' }}</view></picker>
      <view v-if="!acceptedCustomers.length&&!preselected&&!form.id" class="wh-note">暂无可选客户，请先到“承接客户”确认园区分派。</view>
      <view class="wh-label">服务类型</view><picker :range="services" range-key="label" :value="form.serviceType-1" @change="form.serviceType=Number($event.detail.value)+1"><view class="wh-field">{{services[form.serviceType-1]?.label}}</view></picker>
      <view class="wh-label">计费方式</view><picker :range="fees" range-key="label" :value="form.feeModel-1" @change="form.feeModel=Number($event.detail.value)+1"><view class="wh-field">{{fees[form.feeModel-1]?.label}}</view></picker>
      <view class="wh-label">合同开始日期</view><picker mode="date" :value="form.startDate" @change="form.startDate=$event.detail.value"><view class="wh-field">{{form.startDate||'选择日期'}}</view></picker>
      <view class="wh-label">合同结束日期</view><picker mode="date" :value="form.endDate" @change="form.endDate=$event.detail.value"><view class="wh-field">{{form.endDate||'选择日期'}}</view></picker>
      <view class="wh-label">{{ fees[form.feeModel-1]?.unit }}（元）</view><input class="wh-field" v-model="unitPrice" type="digit" placeholder="填写合同约定金额"/>
      <view class="wh-label">保证金（元，可为 0）</view><input class="wh-field" v-model="form.deposit" type="digit" placeholder="0.00"/>
      <view class="wh-label">结算周期</view><picker :range="cycles" :value="form.payCycle-1" @change="form.payCycle=Number($event.detail.value)+1"><view class="wh-field">{{cycles[form.payCycle-1]}}</view></picker>
      <view class="wh-label">合同说明</view><textarea class="wh-field" v-model="form.remark" maxlength="500" placeholder="最多 500 字"/>
      <view class="wh-label">签署件</view><WarehouseFiles v-model="contractFiles" :readonly="busy || agreementFileBusy" @busy="contractFileBusy=$event"/>
      <view class="wh-actions"><button class="wh-secondary" :disabled="busy||fileBusy" @click="editing=false">取消编辑</button><button class="btn" :disabled="busy||fileBusy" :loading="busy" @click="saveDraft">保存草稿</button></view>
    </view>
    <view v-if="loading" class="wh-note">正在读取合同…</view><view v-if="!loading&&!rows.length&&!error" class="wh-note">暂无服务合同，确认承接客户后可起草直签合同。</view>
    <view v-for="c in rows" :key="c.id" class="contract-row">
      <view class="contract-head">
        <view class="contract-name-wrap">
          <text class="contract-customer">{{c.customerName||'未命名客户'}}</text>
          <text class="contract-mode">{{c.signMode===2?'直签':'园区签'}}</text>
        </view>
        <text class="contract-badge">{{CONTRACT[c.status]||'待确认'}}</text>
      </view>
      <view class="contract-sub">
        <text class="contract-no">{{c.contractNo}}</text>
        <text class="contract-sep">·</text>
        <text>{{c.startDate||'—'}}~{{c.endDate||'—'}}</text>
        <text class="contract-sep">·</text>
        <text>保证金 ¥{{money(c.deposit)}}</text>
      </view>
      <view v-if="c.auditReason" class="contract-audit">审核说明：{{c.auditReason}}</view>
      <view v-if="parseFiles(c.files).length || (c.status===1&&c.signMode===2)" class="contract-foot">
        <view class="contract-files">
          <button v-for="(f, idx) in parseFiles(c.files)" :key="f.id || idx" class="contract-file-chip" @click="openContractFile(f)">{{shortFileName(f, idx)}}</button>
        </view>
        <view v-if="c.status===1&&c.signMode===2" class="contract-ops">
          <button class="contract-op-btn" :disabled="busy||fileBusy" @click="edit(c)">编辑</button>
          <button class="contract-op-btn primary" :disabled="busy||fileBusy" @click="submit(c)">提交审核</button>
        </view>
      </view>
    </view>
    <button v-if="error" class="wh-secondary" :disabled="loading" @click="load(page)">刷新重试</button>
  </view><view class="wh-actions wh-pager"><button class="wh-secondary" :disabled="page===1||loading" @click="load(page-1)">上一页</button><view class="wh-note">第 {{page}} 页 · 共 {{total}} 条</view><button class="wh-secondary" :disabled="page*20>=total||loading" @click="load(page+1)">下一页</button></view>
</view></template>
<script>
import { appShareMixin } from '@/utils/share'

export default { mixins: [appShareMixin] }
</script>

<script setup>
import {reactive,ref,computed} from 'vue';import {onLoad,onShow} from '@dcloudio/uni-app';import {warehouseApi} from '@/api/warehouse';import WarehouseFiles from '@/components/WarehouseFiles.vue';import {CONTRACT,parseFiles,money} from '@/utils/warehouse-ui'
const rows=ref([]),page=ref(1),total=ref(0),loading=ref(false),busy=ref(false),agreementFileBusy=ref(false),contractFileBusy=ref(false),error=ref(''),editing=ref(false),preselected=ref(false),unitPrice=ref(''),contractFiles=ref([]),agreementFiles=ref([]),acceptedCustomers=ref([]),selectedName=ref('')
const fileBusy=computed(()=>agreementFileBusy.value||contractFileBusy.value),agreementSnapshot=ref('[]')
const agreement=reactive({});const empty=()=>({id:null,customerId:null,serviceType:2,feeModel:2,startDate:'',endDate:'',deposit:'0',payCycle:3,remark:'',priceTable:'{}'});const form=reactive(empty())
const services=[{label:'仓储'},{label:'代发'},{label:'仓配'}],fees=[{label:'仓租',key:'storage',unit:'仓储费用'},{label:'按票',key:'perOrder',unit:'每票单价'},{label:'按件',key:'perItem',unit:'每件单价'},{label:'包月',key:'monthly',unit:'每月金额'}],cycles=['每周','每半月','每月']
const customerName=computed(()=>selectedName.value||acceptedCustomers.value.find(c=>c.id===form.customerId)?.name||'')
async function loadCustomers(){
  const customers=[];let next=1,totalCount=0
  do{
    const result=await warehouseApi.customers({pageNo:next,pageSize:100})
    const records=result.records||[];customers.push(...records);totalCount=Number(result.total||0);next++
    if(!records.length)break
  }while(customers.length<totalCount)
  return customers.filter(c=>c.warehouseAssignmentStatus===2&&c.status!==3)
}
async function load(next=page.value){
  if(loading.value)return
  loading.value=true;error.value=''
  try{
    const [r,a,c]=await Promise.all([warehouseApi.contracts({pageNo:next,pageSize:20}),warehouseApi.agreement(),loadCustomers()])
    rows.value=r.records||[];total.value=r.total||0;page.value=next;Object.assign(agreement,a)
    // Returning from preview or the file chooser must not replace an unsaved attachment.
    if(JSON.stringify(agreementFiles.value)===agreementSnapshot.value){
      agreementFiles.value=a.file?[a.file]:[];agreementSnapshot.value=JSON.stringify(agreementFiles.value)
    }
    acceptedCustomers.value=c
  }catch(e){error.value=e.message||'合同读取失败，请重试'}finally{loading.value=false}
}
function newDraft(){if(busy.value||fileBusy.value)return;Object.assign(form,empty());preselected.value=false;selectedName.value='';unitPrice.value='';contractFiles.value=[];editing.value=true;error.value=''}
function pickCustomer(e){form.customerId=acceptedCustomers.value[Number(e.detail.value)]?.id;selectedName.value=''}
function edit(c){if(busy.value||fileBusy.value)return;Object.assign(form,empty(),c);selectedName.value=c.customerName;preselected.value=true;contractFiles.value=parseFiles(c.files);try{unitPrice.value=String(JSON.parse(c.priceTable||'{}')[fees[c.feeModel-1]?.key]??'')}catch{unitPrice.value=''}editing.value=true;error.value=''}
async function saveDraft(){if(busy.value||fileBusy.value)return;error.value='';if(!form.customerId){error.value='请选择已承接客户';return}if(!form.startDate||!form.endDate||form.endDate<=form.startDate){error.value='结束日期必须晚于开始日期';return}if(!/^\d+(\.\d{1,4})?$/.test(String(unitPrice.value))||Number(unitPrice.value)<=0||Number(unitPrice.value)>99999999.99){error.value='请填写大于 0、不超过 99999999.99 且最多四位小数的合同单价';return}if(!/^\d+(\.\d{1,2})?$/.test(String(form.deposit))){error.value='保证金需为非负金额，最多两位小数';return}busy.value=true;try{const payload={...form,deposit:Number(form.deposit),priceTable:JSON.stringify({...JSON.parse(form.priceTable||'{}'),[fees[form.feeModel-1].key]:Number(unitPrice.value)}),files:JSON.stringify(contractFiles.value)};if(form.id)await warehouseApi.updateContract(form.id,payload);else await warehouseApi.createContract(payload);editing.value=false;uni.showToast({title:'草稿已保存',icon:'success'});await load(1)}catch(e){error.value=e.message||'保存失败，请检查填写内容'}finally{busy.value=false}}
async function submit(c){if(busy.value||fileBusy.value)return;if(!parseFiles(c.files).length){error.value='请先编辑合同并上传签署件';return}busy.value=true;error.value='';try{await warehouseApi.submitContract(c.id);await load(page.value);uni.showToast({title:'已提交园区审核',icon:'success'})}catch(e){error.value=e.message||'提交失败，请重试'}finally{busy.value=false}}
async function saveAgreement(){if(busy.value||fileBusy.value||!agreementFiles.value.length)return;busy.value=true;error.value='';try{await warehouseApi.uploadAgreement('file:'+agreementFiles.value[0].id);agreementSnapshot.value=JSON.stringify(agreementFiles.value);uni.showToast({title:'协议已提交',icon:'success'})}catch(e){error.value=e.message||'协议提交失败，请重试'}finally{busy.value=false}}
function shortFileName(file, idx){const raw=String(file?.name||file?.originalName||'').trim();if(!raw||/^tmp_[0-9a-f]{12,}/i.test(raw))return `签署件${idx>0?idx+1:''}`;return raw.length>16?raw.slice(0,14)+'…':raw}
async function openContractFile(file){error.value='';try{await warehouseApi.openFile(file.id,file.name||file.originalName)}catch(e){error.value=e.message||'文件打开失败，请重试'}}
onLoad(q=>{if(q?.customerId&&/^[1-9]\d*$/.test(q.customerId)){form.customerId=Number(q.customerId);selectedName.value=q.customerName||'';preselected.value=true;editing.value=true}});onShow(()=>{if(!busy.value&&!fileBusy.value)return load()})
</script><style scoped>@import '../../styles/warehouse.css';
.contract-form{padding:24rpx 0;border-top:1rpx solid #e6eaf3;border-bottom:1rpx solid #e6eaf3;margin-top:24rpx}
.contract-row{padding:18rpx 0;border-bottom:1rpx solid #ebeef6}
.contract-row:last-child{border-bottom:0;padding-bottom:6rpx}
.contract-head{display:flex;align-items:center;justify-content:space-between;gap:12rpx}
.contract-name-wrap{display:flex;align-items:center;gap:10rpx;min-width:0;flex:1}
.contract-customer{font-size:28rpx;font-weight:600;color:#17203e;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.contract-mode{flex-shrink:0;font-size:20rpx;color:#52617c;background:#f2f5fb;padding:2rpx 10rpx;border-radius:6rpx}
.contract-badge{flex-shrink:0;font-size:22rpx;color:#2e47cc;background:#eef1ff;padding:4rpx 12rpx;border-radius:8rpx;font-weight:500}
.contract-sub{margin-top:8rpx;display:flex;flex-wrap:wrap;align-items:center;gap:6rpx;font-size:23rpx;color:#58617d;line-height:1.45}
.contract-no{font-family:monospace;color:#33415c}
.contract-sep{color:#b0b8cc}
.contract-audit{margin-top:8rpx;font-size:23rpx;color:#a3293e;background:#fff4f5;padding:8rpx 12rpx;border-radius:8rpx;line-height:1.45}
.contract-foot{margin-top:10rpx;display:flex;align-items:center;justify-content:space-between;gap:12rpx;flex-wrap:wrap}
.contract-files{display:flex;align-items:center;flex-wrap:wrap;gap:10rpx;min-width:0;flex:1}
.contract-file-chip{margin:0;padding:4rpx 14rpx;font-size:22rpx;line-height:1.5;color:#2e47cc;background:#f4f7ff;border:1rpx solid #dbe3ff;border-radius:8rpx;max-width:340rpx;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.contract-file-chip::after,.contract-op-btn::after{border:0}
.contract-ops{display:flex;align-items:center;gap:10rpx;flex-shrink:0}
.contract-op-btn{margin:0;padding:6rpx 16rpx;font-size:22rpx;line-height:1.5;color:#2e47cc;background:#eef1ff;border-radius:8rpx}
.contract-op-btn.primary{background:#3857f5;color:#fff}
</style>
