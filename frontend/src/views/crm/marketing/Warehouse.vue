<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="名称/编码"><el-input v-model="query.keyword" clearable style="width: 180px" /></el-form-item>
        <el-form-item label="加盟状态">
          <el-select v-model="query.joinStatus" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="(t, v) in JOIN" :key="v" :label="t" :value="Number(v)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" /><div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增云仓(提交加盟申请)</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="name" label="名称" min-width="180"><template #default="{row}"><div>{{row.name}}</div><div class="project-note">{{projectLabel(row.projectId)}}</div></template></el-table-column>
        <el-table-column prop="region" label="区域" width="100" />
        <el-table-column prop="contact" label="联系人" width="100" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column label="加盟状态" width="110">
          <template #default="{ row }"><el-tag :type="joinType(row.joinStatus)">{{ JOIN[row.joinStatus] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="订单接入" width="140"><template #default="{ row }">{{ row.orderMode === 'manual' ? '人工导入出库单' : '外部 ERP' }}</template></el-table-column>
        <el-table-column label="ERP" width="110">
          <template #default="{ row }"><el-tag :type="erpType(row.erpStatus)" size="small">{{ ERP[row.erpStatus] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="结算周期" width="90"><template #default="{ row }">{{ CYCLE[row.settleCycle] }}</template></el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button :disabled="acting" link type="primary" @click="openSteps(row)">进度</el-button>
            <el-button :disabled="acting" link type="primary" @click="openDialog(row)">编辑</el-button>
            <template v-if="row.joinStatus === 2">
              <el-button :disabled="acting" link type="success" @click="act(row, 'passQualification', '资质审核通过')">审核通过</el-button>
              <el-button :disabled="acting" link type="danger" @click="withReason(row, 'rejectQualification', '驳回资质')">驳回</el-button>
            </template>
            <el-button v-else-if="row.joinStatus === 3" link type="success" @click="act(row, 'useManual', '已启用人工导入模式')">采用人工导入订单</el-button>
            <el-button v-else-if="row.joinStatus === 4" link type="success" @click="signAgreement(row)">上传协议并上线</el-button>
            <el-button v-else-if="row.joinStatus === 5" link type="warning" @click="withReason(row, 'pause', '暂停云仓')">暂停</el-button>
            <el-button v-else-if="row.joinStatus === 6" link type="success" @click="act(row, 'resume', '已恢复')">恢复</el-button>
            <el-button v-if="[5, 6].includes(row.joinStatus)" link type="danger" @click="withReason(row, 'exit', '云仓退出')">退出</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="编码" prop="code"><el-input v-model="form.code" placeholder="WH-HZ-001" :disabled="!!form.id" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="区域" prop="region"><el-input v-model="form.region" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="结算周期">
            <el-select v-model="form.settleCycle" style="width: 100%"><el-option v-for="(t, v) in CYCLE" :key="v" :label="t" :value="Number(v)" /></el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="联系人" prop="contact"><el-input v-model="form.contact" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="电话" prop="phone"><el-input v-model="form.phone" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="面积 ㎡"><el-input-number v-model="form.areaSqm" :min="0" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="日处理单量"><el-input-number v-model="form.dailyCapacity" :min="0" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="地址" prop="address"><el-input v-model="form.address" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="品类"><el-input v-model="form.categories" placeholder="家居 / 服饰 / 食品 …" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="云仓费率表">
            <div><span>每票（元）</span><el-input-number v-model="cost.perOrder" :min="0" :precision="2" placeholder="未约定" /><span>每件（元）</span><el-input-number v-model="cost.perItem" :min="0" :precision="2" placeholder="未约定" /><p class="hint">园区应付云仓的成本单价；请填写真实约定，用于生成结算单。</p></div>
          </el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="steps.visible" :title="`加盟进度 · ${steps.row?.name || ''}`" size="480px">
      <el-steps direction="vertical" :active="activeStep" finish-status="success">
        <el-step v-for="s in steps.list" :key="s.step" :title="STEP[s.step]" :status="stepStatus(s)"
                 ><template #description><p>{{ stepDesc(s) }}</p><WarehouseAttachments :model-value="parseFiles(s.attachments)" readonly /></template></el-step>
      </el-steps>
      <p class="hint">审核后可选择人工导入出库单，再提交已签署协议；外部 ERP 接入单独配置。</p>
    </el-drawer>
    <WarehouseAgreementUpload v-model:visible="agreement.visible" :warehouse="agreement.row" @saved="load" />
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktWarehouseApi } from '@/api/marketing'
import WarehouseAgreementUpload from '@/components/WarehouseAgreementUpload.vue'
import WarehouseAttachments from '@/components/WarehouseAttachments.vue'
import { useProjectStore } from '@/stores/project'
const projectStore = useProjectStore()
const projectLabel = id => id == null ? '待分配园区' : (projectStore.projects.find(p => p.id === id)?.name || `园区 #${id}`)
function parseFiles(raw){try{return JSON.parse(raw||'[]').filter(f=>f.id)}catch{return []}}

const JOIN = { 1: '申请', 2: '资质审核', 3: '待配置订单接入', 4: '待签协议', 5: '已上线', 6: '暂停', 7: '退出' }
const ERP = { 0: '未对接', 1: '已联通(沙箱)', 2: '已联通(正式)', 3: '断连' }
const CYCLE = { 1: '周', 2: '半月', 3: '月' }
const STEP = { 1: '提交申请', 2: '资质审核', 3: '确认订单接入方式', 4: '签加盟协议', 5: '上线' }
const joinType = (v) => ({ 1: 'warning', 2: 'warning', 3: 'primary', 4: 'warning', 5: 'success', 6: 'warning', 7: 'danger' }[v] || 'info')
const erpType = (v) => ({ 1: 'success', 2: 'success', 3: 'danger' }[v] || 'info')

const loading = ref(false), saving = ref(false), acting = ref(false), error = ref('')
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, keyword: '', joinStatus: null })

async function load() {
  loading.value = true; error.value=''
  try {
    const res = await mktWarehouseApi.page(query)
    list.value = res.records; total.value = res.total
  } catch(e){error.value=e.message||'云仓读取失败，请重试'} finally { loading.value = false }
}
function search() { query.pageNo = 1; load() }
function reset() { Object.assign(query, { pageNo: 1, keyword: '', joinStatus: null }); load() }

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const emptyForm = () => ({ id: null, code: '', name: '', region: '', contact: '', phone: '', address: '', areaSqm: null, dailyCapacity: null, categories: '', settleCycle: 3, feeModel: null })
const form = reactive(emptyForm())
const cost = reactive({ perOrder: undefined, perItem: undefined })
const pickForm = (row) => Object.fromEntries(Object.entries(row).filter(([k]) => k in form))
const rules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
    ,contact: [{ required: true, message: '请输入联系人', trigger: 'blur' }]
    ,phone: [{ required: true, pattern: /^1\d{10}$/, message: '请输入11位手机号', trigger: 'blur' }]
    ,region: [{ required: true, message: '请输入所在区域', trigger: 'blur' }]
    ,address: [{ required: true, message: '请输入详细地址', trigger: 'blur' }]
}
function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑云仓' : '新增云仓(提交加盟申请)'
  Object.assign(form, emptyForm(), row ? pickForm(row) : {})
  let price = {}; try { price = JSON.parse(row?.feeModel || "{}") } catch {}
  cost.perOrder = price.perOrder; cost.perItem = price.perItem
}
async function submit() {
  if(saving.value || !await formRef.value.validate().catch(()=>false))return
  form.feeModel=cost.perOrder==null&&cost.perItem==null?null:JSON.stringify({perOrder:cost.perOrder||0,perItem:cost.perItem||0})
  saving.value=true
  try{if(form.id)await mktWarehouseApi.update(form);else await mktWarehouseApi.apply(form);ElMessage.success('已保存');dialog.visible=false;await load()}finally{saving.value=false}
}
async function act(row,fn,okMsg){if(acting.value)return;acting.value=true;try{await mktWarehouseApi[fn](row.id,fn==='passQualification'?{version:row.version}:undefined);ElMessage.success(okMsg);await load()}finally{acting.value=false}}
async function withReason(row,fn,title){if(acting.value)return;try{const {value}=await ElMessageBox.prompt('请填写原因（写入审计）',title,{inputPattern:/\S+/,inputErrorMessage:'原因必填'});acting.value=true;await mktWarehouseApi[fn](row.id,{reason:value});ElMessage.success('已处理');await load()}catch(e){if(e!=='cancel'&&e!=='close')error.value=e.message||'操作失败'}finally{acting.value=false}}
const agreement = reactive({ visible: false, row: null })
function signAgreement(row) { agreement.row = row; agreement.visible = true }

const steps = reactive({ visible: false, row: null, list: [] })
async function openSteps(row) {
  steps.row = row
  steps.list = await mktWarehouseApi.steps(row.id)
  steps.visible = true
}
const activeStep = computed(() => steps.list.filter(s => s.status === 2).length)
const stepStatus = (s) => ({ 2: 'success', 3: 'error', 1: 'process' }[s.status] || 'wait')
const stepDesc = (s) => (s.status === 3 ? `驳回:${s.rejectReason || ''}` : s.doneTime ? `完成于 ${s.doneTime}` : s.status === 1 ? '进行中' : '')

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.project-note { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 16px; }
</style>
