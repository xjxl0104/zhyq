<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="合同号/客户"><el-input v-model="query.keyword" clearable style="width: 180px" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(t, v) in ST" :key="v" :label="t" :value="Number(v)" />
          </el-select>
        </el-form-item>
        <el-form-item label="签约方式">
          <el-select v-model="query.signMode" placeholder="全部" clearable style="width: 120px">
            <el-option label="园区签" :value="1" /><el-option label="云仓直签" :value="2" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>起草合同</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="contractNo" label="合同号" width="170" />
        <el-table-column prop="customerName" label="客户" min-width="140" />
        <el-table-column label="签约" width="90"><template #default="{ row }">{{ SIGN[row.signMode] }}</template></el-table-column>
        <el-table-column label="服务" width="80"><template #default="{ row }">{{ SVC[row.serviceType] }}</template></el-table-column>
        <el-table-column prop="warehouseName" label="承接云仓" width="130" />
        <el-table-column prop="grade" label="评级" width="60" align="center" />
        <el-table-column label="期限" width="200"><template #default="{ row }">{{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}</template></el-table-column>
        <el-table-column label="保证金(元)" width="110" align="right"><template #default="{ row }">{{ money(row.deposit) }}</template></el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="stType(row.status)">{{ ST[row.status] }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="contractVersion" label="版本" width="60" align="center" />
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <template v-if="row.status === 1">
              <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
              <el-button link type="success" @click="act(row, 'submit', '已提交审核')">提交</el-button>

            </template>
            <template v-else-if="row.status === 2">
              <el-button v-if="row.signMode === 1" link type="success" @click="auditPass(row)">审核通过</el-button>
              <el-button link type="danger" @click="auditReject(row)">驳回</el-button>
              <el-button v-if="row.signMode === 2" link type="success" @click="act(row, 'effectDirect', '直签备案生效')">备案生效</el-button>
            </template>
            <template v-else-if="row.status === 3">
              <el-button link type="success" @click="signOffline(row)">上传签署件 → 生效</el-button>
              <el-button link type="danger" @click="reasonAct(row, 'void', '作废合同')">作废</el-button>
            </template>
            <template v-else-if="row.status === 4">
              <el-button link type="success" @click="act(row, 'perform', '已进入履约')">开始履约</el-button>
              <el-button link type="danger" @click="reasonAct(row, 'terminate', '终止合同')">终止</el-button>
            </template>
            <template v-else-if="row.status === 5">
              <el-button link type="primary" @click="reasonAct(row, 'amend', '发起变更')">变更</el-button>
              <el-button link type="danger" @click="reasonAct(row, 'terminate', '终止合同')">终止</el-button>
            </template>
            <el-button v-else-if="row.status === 6" link type="success" @click="act(row, 'amendDone', '变更完成')">变更完成</el-button>
            <el-button v-else-if="row.status === 7" link type="success" @click="renew(row)">续签</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 起草 / 编辑 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="680px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="客户" prop="customerId"><el-select v-model="form.customerId" filterable remote :remote-method="searchCustomers" :loading="customersLoading" :disabled="!!form.id" placeholder="搜索已由云仓承接的客户" @change="selectCustomer"><el-option v-for="c in customers" :key="c.id" :value="c.id" :label="c.name" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="签约方式">
            <el-select v-model="form.signMode" :disabled="!!form.id" style="width: 100%" placeholder="签约方式"><el-option label="园区签" :value="1" /><el-option label="云仓直签" :value="2" /></el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="承接云仓" prop="warehouseId">
            <el-select v-model="form.warehouseId" disabled style="width: 100%" placeholder="按客户已承接云仓填写">
              <el-option v-for="w in warehouses" :key="w.id" :label="`${w.code} ${w.name}`" :value="w.id" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="服务类型">
            <el-select v-model="form.serviceType" style="width: 100%"><el-option v-for="(t, v) in SVC" :key="v" :label="t" :value="Number(v)" /></el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="计费模式">
            <el-select v-model="form.feeModel" style="width: 100%"><el-option v-for="(t, v) in FEE" :key="v" :label="t" :value="Number(v)" /></el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="模板">
            <el-select v-model="form.templateId" style="width: 100%" clearable><el-option v-for="t in templates" :key="t.id" :label="`${t.name} v${t.tplVersion}`" :value="t.id" /></el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="起始日"><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="结束日"><el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="保证金"><el-input-number v-model="form.deposit" :min="0" :precision="2" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款周期">
            <el-select v-model="form.payCycle" style="width: 100%"><el-option label="周" :value="1" /><el-option label="半月" :value="2" /><el-option label="月" :value="3" /></el-select>
          </el-form-item></el-col>
          <el-col :span="24"><el-form-item label="单价表">
            <div><div v-for="(label,key) in PRICE" :key="key" class="price-row"><span>{{ label }}（元）</span><el-input-number v-model="prices[key]" :min="0" :max="99999999" :precision="2" placeholder="未约定" /></div><div class="hint">填写客户实际合同单价，至少一项大于零。请勿将云仓成本费率填入此处。</div></div>
          </el-form-item></el-col>
          <el-col :span="24"><el-form-item label="签署附件"><WarehouseAttachments v-model="attachments" :warehouse-id="form.warehouseId" @busy="uploading=$event" /></el-form-item></el-col><el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" maxlength="500" show-word-limit /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="uploading" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="signing.visible" title="上传签署件并生效" width="560px"><p>{{ signing.row?.contractNo }} · {{ signing.row?.customerName }}</p><WarehouseAttachments v-model="signing.files" :warehouse-id="signing.row?.warehouseId" @busy="uploading=$event" /><template #footer><el-button @click="signing.visible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="uploading || !signing.files.length" @click="confirmSign">确认签署并生效</el-button></template></el-dialog>
    <!-- 详情 -->
    <el-drawer v-model="detail.visible" :title="detail.row?.contractNo" size="640px">
      <el-tabs v-if="detail.row">
        <el-tab-pane label="条款">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="客户">{{ detail.row.customerName || detail.row.customerId }}</el-descriptions-item>
            <el-descriptions-item label="签约方式">{{ SIGN[detail.row.signMode] }}</el-descriptions-item>
            <el-descriptions-item label="承接云仓">{{ detail.row.warehouseName || detail.row.warehouseId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="成交伙伴">{{ detail.row.partnerId ? '#' + detail.row.partnerId : '—' }}</el-descriptions-item>
            <el-descriptions-item label="评级">{{ detail.row.grade || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ ST[detail.row.status] }}</el-descriptions-item>
            <el-descriptions-item label="期限" :span="2">{{ detail.row.startDate }} ~ {{ detail.row.endDate }}</el-descriptions-item>
            <el-descriptions-item label="单价表" :span="2">{{ formatPrices(detail.row.priceTable) }}</el-descriptions-item><el-descriptions-item label="签署附件" :span="2"><WarehouseAttachments :model-value="parseFiles(detail.row.files)" readonly /></el-descriptions-item>
            <el-descriptions-item label="生效时间">{{ detail.row.effectiveAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="签署">{{ detail.row.signedAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="审核意见" :span="2">{{ detail.row.auditReason || '-' }}</el-descriptions-item>
            <el-descriptions-item label="终止原因" :span="2">{{ detail.row.terminateReason || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane label="版本">
          <el-table :data="detail.versions" size="small" border>
            <el-table-column prop="verNo" label="版本" width="70" />
            <el-table-column prop="changeNote" label="说明" />
            <el-table-column prop="changedBy" label="操作人" width="100" />
            <el-table-column prop="createTime" label="时间" width="160" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktContractApi, mktWarehouseApi, mktTemplateApi, mktCustomerApi } from '@/api/marketing'
import { money } from '@/utils/format'
import WarehouseAttachments from '@/components/WarehouseAttachments.vue'

const ST = { 1: '草稿', 2: '待审核', 3: '待客户签', 4: '已生效', 5: '履约中', 6: '变更中', 7: '到期', 8: '终止', 9: '作废' }
const SIGN = { 1: '园区签', 2: '云仓直签' }
const SVC = { 1: '仓储', 2: '代发', 3: '仓配' }
const FEE = { 1: '仓租', 2: '单票', 3: '按件', 4: '包月' }
const stType = (s) => ({ 1: 'info', 2: 'warning', 3: 'warning', 4: 'success', 5: 'primary', 6: 'warning', 7: 'info', 8: 'danger', 9: 'danger' }[s] || 'info')

const loading = ref(false), saving = ref(false), uploading = ref(false), customersLoading = ref(false)
const customers = ref([]), attachments = ref([])
const prices = reactive({storage:undefined,perOrder:undefined,perItem:undefined,monthly:undefined})
const PRICE = {storage:'仓储',perOrder:'每票',perItem:'每件',monthly:'包月'}
function parseFiles(raw){try{return JSON.parse(raw||'[]').map(f=>typeof f==='number'?{id:f,name:'附件 '+f}:f).filter(f=>f.id)}catch{return []}}
function formatPrices(raw){try{return Object.entries(JSON.parse(raw||'{}')).map(([k,v])=>(PRICE[k]||k)+' '+money(v)+'元').join('，')||'未设置'}catch{return '未设置'}}
async function searchCustomers(keyword=''){customersLoading.value=true;try{const data=await mktCustomerApi.page({pageNo:1,pageSize:100,keyword,warehouseAssignmentStatus:2});customers.value=data.records.filter(c=>c.warehouseAssignmentStatus===2)}finally{customersLoading.value=false}}
function selectCustomer(id){const c=customers.value.find(c=>c.id===id);form.warehouseId=c?.assignedWarehouseId;form.signMode=c?.signMode||1}
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, keyword: '', status: null, signMode: null })
const warehouses = ref([])
const templates = ref([])

async function load() {
  loading.value = true
  try {
    const res = await mktContractApi.page(query)
    list.value = res.records; total.value = res.total
  } finally { loading.value = false }
}
function search() { query.pageNo = 1; load() }
function reset() { Object.assign(query, { pageNo: 1, keyword: '', status: null, signMode: null }); load() }

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const emptyForm = () => ({ id: null, customerId: null, signMode: null, warehouseId: null, serviceType: 2, feeModel: 2, templateId: null, startDate: null, endDate: null, deposit: 0, payCycle: 3, priceTable: '{}', files:'[]', remark: '' })
const form = reactive(emptyForm())
// 行数据里还带 customerName/status 等只读字段,只拷表单里有的键,避免它们跟着 PUT 回去
const pickForm = (row) => Object.fromEntries(Object.entries(row).filter(([k]) => k in form))
const rules = {
  customerId: [{ required: true, message: '请选择已承接的客户', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择承接云仓', trigger: 'change' }]
}
async function openDialog(row) {
  if (!warehouses.value.length) warehouses.value = await mktWarehouseApi.online()
  if (!templates.value.length) { try { templates.value = await mktTemplateApi.list() } catch (e) { /* 可为空 */ } }
  dialog.visible = true
  dialog.title = row ? `编辑 ${row.contractNo}` : '起草云仓服务合同'
  Object.assign(form, emptyForm(), row ? pickForm(row) : {})
  attachments.value=parseFiles(form.files);let p={};try{p=JSON.parse(form.priceTable||'{}')}catch{}
  Object.keys(prices).forEach(k=>prices[k]=p[k])
  await searchCustomers();if(row&&!customers.value.some(c=>c.id===row.customerId))customers.value.push({id:row.customerId,name:row.customerName})
}
async function submit() {
  if(saving.value||uploading.value)return
  if(!await formRef.value.validate().catch(()=>false))return
  if(!form.startDate||!form.endDate||form.endDate<=form.startDate)return ElMessage.error('请填写正确的合同起止日期')
  const terms=Object.fromEntries(Object.entries(prices).filter(([,v])=>v!=null));if(!Object.values(terms).some(v=>v>0))return ElMessage.error('请填写至少一项真实正单价')
  form.priceTable=JSON.stringify(terms);form.files=JSON.stringify(attachments.value)
  saving.value=true
  try { if(form.id)await mktContractApi.update(form);else await mktContractApi.create(form);ElMessage.success('已保存');dialog.visible=false;await load() } finally { saving.value=false }
}

async function act(row, fn, okMsg) { await mktContractApi[fn](row.id); ElMessage.success(okMsg); load() }
async function reasonAct(row, fn, title) {
  const { value } = await ElMessageBox.prompt('请填写原因(写入审计)', title, { inputPattern: /\S+/, inputErrorMessage: '原因必填' })
  await mktContractApi[fn](row.id, { reason: value, changeNote: value }); ElMessage.success('已处理'); load()
}
async function auditPass(row) { await mktContractApi.audit(row.id, { pass: true }); ElMessage.success('已通过,待客户签'); load() }
async function auditReject(row) {
  const { value } = await ElMessageBox.prompt('驳回原因', '驳回', { inputPattern: /\S+/, inputErrorMessage: '原因必填' })
  await mktContractApi.audit(row.id, { pass: false, reason: value }); ElMessage.success('已驳回'); load()
}
const signing = reactive({visible:false,row:null,files:[]})
function signOffline(row){signing.row=row;signing.files=parseFiles(row.files);signing.visible=true}
async function confirmSign(){if(saving.value||!signing.files.length)return;saving.value=true;try{await mktContractApi.signOffline(signing.row.id,{files:JSON.stringify(signing.files)});ElMessage.success('合同已生效');signing.visible=false;await load()}finally{saving.value=false}}

async function renew(row) {
  const { value: start } = await ElMessageBox.prompt('新起始日 YYYY-MM-DD', '续签', { inputPattern: /^\d{4}-\d{2}-\d{2}$/, inputErrorMessage: '格式 YYYY-MM-DD' })
  const { value: end } = await ElMessageBox.prompt('新结束日 YYYY-MM-DD', '续签', { inputPattern: /^\d{4}-\d{2}-\d{2}$/, inputErrorMessage: '格式 YYYY-MM-DD' })
  await mktContractApi.renew(row.id, { startDate: start, endDate: end }); ElMessage.success('已续签'); load()
}

const detail = reactive({ visible: false, row: null, versions: [] })
async function openDetail(row) {
  detail.row = await mktContractApi.get(row.id)
  detail.versions = await mktContractApi.versions(row.id)
  detail.visible = true
}

onMounted(load)
</script>

<style scoped>
.price-row{display:flex;align-items:center;gap:16px;margin-bottom:8px}.price-row span{min-width:90px}
.pager { margin-top: 16px; justify-content: flex-end; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.4; margin-top: 4px; }
</style>
