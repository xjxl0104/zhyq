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
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
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
        <el-table-column prop="deposit" label="保证金" width="100" align="right" />
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
              <el-button v-if="row.signMode === 2" link type="success" @click="act(row, 'effectDirect', '直签备案生效')">备案生效</el-button>
              <el-button link type="danger" @click="reasonAct(row, 'void', '作废合同')">作废</el-button>
            </template>
            <template v-else-if="row.status === 2">
              <el-button link type="success" @click="auditPass(row)">审核通过</el-button>
              <el-button link type="danger" @click="auditReject(row)">驳回</el-button>
              <el-button v-if="row.signMode === 2" link type="success" @click="act(row, 'effectDirect', '直签备案生效')">备案生效</el-button>
            </template>
            <template v-else-if="row.status === 3">
              <el-button link type="success" @click="signOffline(row)">上传签署件 → 生效</el-button>
              <el-button link type="danger" @click="reasonAct(row, 'void', '作废合同')">作废</el-button>
            </template>
            <template v-else-if="row.status === 4">
              <el-button link type="success" @click="act(row, 'perform', '已进入履约')">首期款到账 → 履约</el-button>
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
          <el-col :span="12"><el-form-item label="客户 ID" prop="customerId"><el-input-number v-model="form.customerId" :min="1" style="width: 100%" :disabled="!!form.id" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="签约方式">
            <el-select v-model="form.signMode" style="width: 100%" placeholder="留空取客户设置"><el-option label="园区签" :value="1" /><el-option label="云仓直签" :value="2" /></el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="承接云仓" prop="warehouseId">
            <el-select v-model="form.warehouseId" style="width: 100%" placeholder="只列已上线且 ERP 联通的仓">
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
            <el-input v-model="form.priceTable" placeholder='客户付园区,JSON:{"perOrder":10,"perItem":0.5,"storage":0,"monthly":0}' />
            <div class="hint">出库单佣金基数 = perOrder × 包裹数 + perItem × 件数(园区自算,不信任 ERP 金额)。</div>
          </el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

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
            <el-descriptions-item label="单价表" :span="2"><code>{{ detail.row.priceTable }}</code></el-descriptions-item>
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
import { mktContractApi, mktWarehouseApi, mktTemplateApi } from '@/api/marketing'

const ST = { 1: '草稿', 2: '待审核', 3: '待客户签', 4: '已生效', 5: '履约中', 6: '变更中', 7: '到期', 8: '终止', 9: '作废' }
const SIGN = { 1: '园区签', 2: '云仓直签' }
const SVC = { 1: '仓储', 2: '代发', 3: '仓配' }
const FEE = { 1: '仓租', 2: '单票', 3: '按件', 4: '包月' }
const stType = (s) => ({ 4: 'success', 5: 'success', 6: 'warning', 7: 'info', 8: 'danger', 9: 'danger' }[s] || '')

const loading = ref(false)
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
function reset() { Object.assign(query, { pageNo: 1, keyword: '', status: null, signMode: null }); load() }

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const emptyForm = () => ({ customerId: null, signMode: null, warehouseId: null, serviceType: 2, feeModel: 2, templateId: null, startDate: null, endDate: null, deposit: 0, payCycle: 3, priceTable: '{"perOrder":10,"perItem":0.5,"storage":0,"monthly":0}', remark: '' })
const form = reactive(emptyForm())
const rules = {
  customerId: [{ required: true, message: '请填客户 ID', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择承接云仓', trigger: 'change' }]
}
async function openDialog(row) {
  if (!warehouses.value.length) warehouses.value = await mktWarehouseApi.online()
  if (!templates.value.length) { try { templates.value = await mktTemplateApi.list() } catch (e) { /* 可为空 */ } }
  dialog.visible = true
  dialog.title = row ? `编辑 ${row.contractNo}` : '起草云仓服务合同'
  Object.assign(form, row ? { ...row } : emptyForm())
}
async function submit() {
  await formRef.value.validate()
  try { JSON.parse(form.priceTable) } catch (e) { return ElMessage.error('单价表不是合法 JSON') }
  if (form.id) await mktContractApi.update(form)
  else await mktContractApi.create(form)
  ElMessage.success('已保存'); dialog.visible = false; load()
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
async function signOffline(row) {
  const { value } = await ElMessageBox.prompt('签署件附件 ID 列表(JSON 数组,如 [12,13])', '上传盖章件', { inputValue: '[]' })
  await mktContractApi.signOffline(row.id, { files: value }); ElMessage.success('合同已生效,佣金已冻结生成'); load()
}
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
.pager { margin-top: 16px; justify-content: flex-end; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.4; margin-top: 4px; }
</style>
