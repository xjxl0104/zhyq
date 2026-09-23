<template>
  <div class="page-container">
    <el-alert v-if="error" :title="error" type="error" :closable="false" /><div class="table-card">
      <div class="toolbar">
        <span class="title">加盟申请(未上线的云仓)</span>
        <el-radio-group v-model="filter" size="small" @change="query.pageNo=1;load()">
          <el-radio-button :value="0">全部在途</el-radio-button>
          <el-radio-button :value="2">待资质审核</el-radio-button>
          <el-radio-button :value="3">待配置订单接入</el-radio-button>
          <el-radio-button :value="4">待签协议</el-radio-button>
        </el-radio-group>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="contact" label="联系人" width="100" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column label="当前步骤" width="130">
          <template #default="{ row }"><el-tag :type="row.joinStatus === 3 ? 'primary' : 'warning'">{{ JOIN[row.joinStatus] }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createTime" label="申请时间" width="160" />
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button :disabled="acting" link type="primary" @click="showApplication(row)">查看资料</el-button>
            <template v-if="row.joinStatus === 2">
              <el-button :disabled="acting" link type="success" @click="act(row, 'passQualification')">审核通过</el-button>
              <el-button :disabled="acting" link type="danger" @click="withReason(row, 'rejectQualification', '驳回资质')">驳回</el-button>
            </template>
            <el-button v-else-if="row.joinStatus === 3" link type="success" @click="act(row, 'useManual')">采用人工导入订单</el-button>
            <el-button v-else-if="row.joinStatus === 4" link type="success" @click="signAgreement(row)">上传协议并上线</el-button>
            <router-link to="/crm/marketing/warehouse" class="link">去云仓管理</router-link>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next"
                     :total="total" v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" @change="load" />
    </div>
    <el-drawer v-model="application.visible" title="加盟资料" size="560px"><div v-loading="application.loading"><el-descriptions v-if="application.row" :column="1" border><el-descriptions-item v-for="(label,key) in fields" :key="key" :label="label">{{application.row[key]??'—'}}</el-descriptions-item></el-descriptions><div v-for="step in application.steps" :key="step.step"><p>{{STEP[step.step]}} · {{step.rejectReason || (step.status===2?'已完成':step.status===1?'处理中':'待处理')}}</p><WarehouseAttachments :model-value="parseFiles(step.attachments)" readonly /></div></div></el-drawer>
    <WarehouseAgreementUpload v-model:visible="agreement.visible" :warehouse="agreement.row" @saved="load" />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktWarehouseApi } from '@/api/marketing'
import WarehouseAgreementUpload from '@/components/WarehouseAgreementUpload.vue'
import WarehouseAttachments from '@/components/WarehouseAttachments.vue'
const application=reactive({visible:false,loading:false,row:null,steps:[]})
const fields={name:'云仓名称',region:'区域',address:'地址',contact:'联系人',phone:'手机号',areaSqm:'面积（㎡）',dailyCapacity:'日处理单量',categories:'经营品类',remark:'补充说明'}
const STEP={1:'申请材料',2:'资质审核',3:'订单接入方式',4:'加盟协议',5:'上线'}
function parseFiles(raw){try{return JSON.parse(raw||'[]').filter(f=>f.id)}catch{return []}}
async function showApplication(row){application.row=row;application.steps=[];application.visible=true;application.loading=true;try{application.steps=await mktWarehouseApi.steps(row.id)}finally{application.loading=false}}

const JOIN = { 1: '申请', 2: '资质审核', 3: '待配置订单接入', 4: '待签协议' }
// Element Plus 的 radio 不把 null 当合法值,「全部」用 0 占位
const filter = ref(0)
const loading = ref(false), acting=ref(false), error=ref('')
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 20 })

async function load() {
  loading.value = true;error.value=''
  try {
    const res = await mktWarehouseApi.page({ ...query, joinStatus: filter.value || undefined, inProgress: filter.value ? undefined : 1 })
    list.value = res.records; total.value = res.total
  } catch(e){error.value=e.message||'加盟申请读取失败，请重试'} finally { loading.value = false }
}
async function act(row,fn){if(acting.value)return;acting.value=true;try{await mktWarehouseApi[fn](row.id,fn==='passQualification'?{version:row.version}:undefined);ElMessage.success('已处理');await load()}finally{acting.value=false}}
async function withReason(row,fn,title){if(acting.value)return;try{const {value}=await ElMessageBox.prompt('请填写原因',title,{inputPattern:/\S+/,inputErrorMessage:'原因必填'});acting.value=true;await mktWarehouseApi[fn](row.id,{reason:value});ElMessage.success('已处理');await load()}catch(e){if(e!=='cancel'&&e!=='close')error.value=e.message||'操作失败'}finally{acting.value=false}}
const agreement = reactive({ visible: false, row: null })
function signAgreement(row) { agreement.row = row; agreement.visible = true }
onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 16px; }
.title { font-weight: 600; margin-right: auto; }
.link { margin-left: 8px; font-size: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
