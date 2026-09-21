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

    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增云仓(提交加盟申请)</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="region" label="区域" width="100" />
        <el-table-column prop="contact" label="联系人" width="100" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column label="加盟状态" width="110">
          <template #default="{ row }"><el-tag :type="joinType(row.joinStatus)">{{ JOIN[row.joinStatus] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="ERP" width="110">
          <template #default="{ row }"><el-tag :type="erpType(row.erpStatus)" size="small">{{ ERP[row.erpStatus] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="结算周期" width="90"><template #default="{ row }">{{ CYCLE[row.settleCycle] }}</template></el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSteps(row)">进度</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <template v-if="row.joinStatus === 2">
              <el-button link type="success" @click="act(row, 'passQualification', '资质审核通过')">审核通过</el-button>
              <el-button link type="danger" @click="withReason(row, 'rejectQualification', '驳回资质')">驳回</el-button>
            </template>
            <el-button v-else-if="row.joinStatus === 3" link type="success" @click="act(row, 'markErp', '已标记 ERP 联通(人工)')">标记 ERP 已联通</el-button>
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
          <el-col :span="12"><el-form-item label="区域"><el-input v-model="form.region" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="结算周期">
            <el-select v-model="form.settleCycle" style="width: 100%"><el-option v-for="(t, v) in CYCLE" :key="v" :label="t" :value="Number(v)" /></el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="面积 ㎡"><el-input-number v-model="form.areaSqm" :min="0" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="日处理单量"><el-input-number v-model="form.dailyCapacity" :min="0" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="地址"><el-input v-model="form.address" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="品类"><el-input v-model="form.categories" placeholder="家居 / 服饰 / 食品 …" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="云仓费率表">
            <el-input v-model="form.feeModel" placeholder='园区应付云仓,JSON:{"perOrder":2.5,"perItem":0.3,"storage":0}' />
          </el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="steps.visible" :title="`加盟进度 · ${steps.row?.name || ''}`" size="480px">
      <el-steps direction="vertical" :active="activeStep" finish-status="success">
        <el-step v-for="s in steps.list" :key="s.step" :title="STEP[s.step]" :status="stepStatus(s)"
                 :description="stepDesc(s)" />
      </el-steps>
      <p class="hint">顺序(v7 拍板):先打通 ERP 再签协议。阶段 A 无真实 ERP,第 3 步由运营人工标记。</p>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktWarehouseApi } from '@/api/marketing'

const JOIN = { 1: '申请', 2: '资质审核', 3: 'ERP 对接中', 4: '待签协议', 5: '已上线', 6: '暂停', 7: '退出' }
const ERP = { 0: '未对接', 1: '已联通(沙箱)', 2: '已联通(正式)', 3: '断连' }
const CYCLE = { 1: '周', 2: '半月', 3: '月' }
const STEP = { 1: '提交申请', 2: '资质审核', 3: 'ERP 打通', 4: '签加盟协议', 5: '上线' }
const joinType = (v) => ({ 1: 'warning', 2: 'warning', 3: 'primary', 4: 'warning', 5: 'success', 6: 'warning', 7: 'danger' }[v] || 'info')
const erpType = (v) => ({ 1: 'success', 2: 'success', 3: 'danger' }[v] || 'info')

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, keyword: '', joinStatus: null })

async function load() {
  loading.value = true
  try {
    const res = await mktWarehouseApi.page(query)
    list.value = res.records; total.value = res.total
  } finally { loading.value = false }
}
function search() { query.pageNo = 1; load() }
function reset() { Object.assign(query, { pageNo: 1, keyword: '', joinStatus: null }); load() }

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const emptyForm = () => ({ id: null, code: '', name: '', region: '', contact: '', phone: '', address: '', areaSqm: null, dailyCapacity: null, categories: '', settleCycle: 3, feeModel: null })
const form = reactive(emptyForm())
const pickForm = (row) => Object.fromEntries(Object.entries(row).filter(([k]) => k in form))
const rules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}
function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑云仓' : '新增云仓(提交加盟申请)'
  Object.assign(form, emptyForm(), row ? pickForm(row) : {})
}
async function submit() {
  await formRef.value.validate()
  // fee_model 是 JSON 列,空串会被 MySQL 拒绝(ERROR 3140),留空一律传 null
  if (!form.feeModel) form.feeModel = null
  else { try { JSON.parse(form.feeModel) } catch (e) { return ElMessage.error('费用模型不是合法 JSON') } }
  if (form.id) await mktWarehouseApi.update(form)
  else await mktWarehouseApi.apply(form)
  ElMessage.success('已保存')
  dialog.visible = false
  load()
}

async function act(row, fn, okMsg) {
  await mktWarehouseApi[fn](row.id)
  ElMessage.success(okMsg)
  load()
}
async function withReason(row, fn, title) {
  const { value } = await ElMessageBox.prompt('请填写原因(写入审计)', title, { inputPattern: /\S+/, inputErrorMessage: '原因必填' })
  await mktWarehouseApi[fn](row.id, { reason: value })
  ElMessage.success('已处理')
  load()
}
async function signAgreement(row) {
  const { value } = await ElMessageBox.prompt('请填写协议签署件的文件路径或附件 ID', '上传加盟协议', { inputPattern: /\S+/, inputErrorMessage: '必填' })
  await mktWarehouseApi.signAgreement(row.id, { contractFile: value })
  ElMessage.success('已上线,ERP 切正式')
  load()
}

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
.hint { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 16px; }
</style>
