<template>
  <el-drawer :model-value="modelValue" :title="drawerTitle" size="72%"
             @update:model-value="(v) => emit('update:modelValue', v)" @open="load">
    <div class="table-card">
      <div class="toolbar">
        <span class="section-title">
          采购申请
          <span class="hint">— 提交后按「审批流程」页配置的审批人逐级审批</span>
        </span>
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增采购申请</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="requestNo" label="申请单号" min-width="150" />
        <el-table-column prop="title" label="采购事由" min-width="160" show-overflow-tooltip />
        <el-table-column prop="supplier" label="供应商" min-width="120" show-overflow-tooltip />
        <el-table-column prop="applicant" label="申请人" width="90" />
        <el-table-column prop="department" label="申请部门" width="100" />
        <el-table-column label="总金额" width="110" align="right">
          <template #default="{ row }">¥{{ row.totalAmount }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="canEdit(row)" link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="canEdit(row)" link type="success" @click="doSubmit(row)">提交审批</el-button>
            <el-button v-if="row.status === 3" link type="success" @click="doComplete(row)">完成</el-button>
            <el-popconfirm v-if="[1, 2, 3].includes(row.status)" title="确认取消该申请?" @confirm="doCancel(row)">
              <template #reference><el-button link type="warning">取消</el-button></template>
            </el-popconfirm>
            <el-popconfirm v-if="[1, 4, 6].includes(row.status)" title="确认删除该申请?" @confirm="doRemove(row)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 新增/编辑采购申请 -->
    <el-dialog v-model="dialogVisible" append-to-body
               :title="form.id ? '编辑采购申请' : '新增采购申请'" width="860px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="采购事由" prop="title">
              <el-input v-model="form.title" placeholder="采购事由/标题" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联计划">
              <el-input :model-value="planLabel" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商"><el-input v-model="form.supplier" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="申请人"><el-input v-model="form.applicant" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="申请部门"><el-input v-model="form.department" /></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
          </el-col>
        </el-row>

        <div class="items-toolbar">
          <span class="section-title">采购明细</span>
          <el-button size="small" type="primary" @click="addItem"><el-icon><Plus /></el-icon>添加一行</el-button>
        </div>
        <el-table :data="form.items" border size="small">
          <el-table-column label="物品名称" min-width="140">
            <template #default="{ row }"><el-input v-model="row.itemName" placeholder="物品名称" /></template>
          </el-table-column>
          <el-table-column label="规格型号" min-width="120">
            <template #default="{ row }"><el-input v-model="row.spec" placeholder="规格型号" /></template>
          </el-table-column>
          <el-table-column label="单位" width="90">
            <template #default="{ row }"><el-input v-model="row.unit" placeholder="单位" /></template>
          </el-table-column>
          <el-table-column label="数量" width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.qty" :min="0.01" :precision="2" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="单价" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="2" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="100" align="right">
            <template #default="{ row }">¥{{ itemAmount(row) }}</template>
          </el-table-column>
          <el-table-column label="" width="60">
            <template #default="{ $index }">
              <el-button link type="danger" @click="form.items.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="total-line">明细合计:¥{{ itemsTotal }}</div>

        <el-form-item label="附件" style="margin-top: 12px">
          <FileUpload v-model="attachFiles" :biz-type="BIZ_TYPE" :biz-id="form.id" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">保存草稿</el-button>
        <el-button type="success" @click="saveAndSubmit">保存并提交审批</el-button>
      </template>
    </el-dialog>

    <!-- 采购申请详情 -->
    <el-dialog v-model="detailVisible" append-to-body title="采购申请详情" width="780px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="申请单号">{{ detail.requestNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMap[detail.status]?.type || 'info'">{{ statusMap[detail.status]?.label }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="采购事由">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="关联计划">{{ planLabel }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.supplier || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.applicant || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请部门">{{ detail.department || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总金额">¥{{ detail.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="section-title" style="margin: 14px 0 8px">采购明细</div>
      <el-table :data="detail.items" border size="small">
        <el-table-column prop="itemName" label="物品名称" min-width="140" />
        <el-table-column prop="spec" label="规格型号" min-width="120" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="qty" label="数量" width="90" />
        <el-table-column prop="unitPrice" label="单价" width="100" align="right" />
        <el-table-column prop="amount" label="金额" width="100" align="right" />
      </el-table>

      <div class="section-title" style="margin: 14px 0 8px">附件</div>
      <div v-if="detailFiles.length === 0" class="empty-tip">暂无附件</div>
      <el-table v-else :data="detailFiles" border size="small">
        <el-table-column prop="originalName" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column prop="ext" label="格式" width="90" />
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ (row.fileSize / 1024).toFixed(1) }} KB</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click="downloadFile(row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="section-title" style="margin: 14px 0 8px">审批轨迹</div>
      <div v-if="trail.length === 0" class="empty-tip">尚未提交审批</div>
      <el-timeline v-else>
        <el-timeline-item v-for="t in trail" :key="t.id"
                          :type="taskStatusMap[t.status]?.type || 'info'"
                          :timestamp="t.actTime || '待处理'">
          第{{ t.seq }}步 · 审批人 {{ t.assignee || '-' }} ·
          <el-tag size="small" :type="taskStatusMap[t.status]?.type || 'info'">
            {{ taskStatusMap[t.status]?.label || t.status }}
          </el-tag>
          <div v-if="t.opinion" class="opinion">意见:{{ t.opinion }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { reactive, ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { purRequestApi } from '@/api/pur'
import { wfTaskApi } from '@/api/workflow'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'

// 附件 bizType 是 pur_request;审批链 bizType 是 procurement(V40 已建流程定义)
const BIZ_TYPE = 'pur_request'
const WF_BIZ_TYPE = 'procurement'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  plan: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue'])

const planId = computed(() => props.plan?.id || null)
const planLabel = computed(() => (props.plan ? `${props.plan.title}(${props.plan.period})` : '-'))
const drawerTitle = computed(() => `采购申请 · ${planLabel.value}`)

const statusMap = {
  1: { label: '草稿', type: 'info' },
  2: { label: '审批中', type: 'warning' },
  3: { label: '已通过', type: 'primary' },
  4: { label: '已驳回', type: 'danger' },
  5: { label: '已完成', type: 'success' },
  6: { label: '已取消', type: 'info' }
}
const taskStatusMap = {
  1: { label: '待审', type: 'warning' },
  2: { label: '通过', type: 'success' },
  3: { label: '驳回', type: 'danger' }
}
// 草稿/已驳回可编辑并重新提交
const canEdit = (row) => row.status === 1 || row.status === 4

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10 })

async function load() {
  if (!planId.value) return
  loading.value = true
  try {
    const res = await purRequestApi.page({ ...query, planId: planId.value })
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

// 换一条计划再打开抽屉时,回到第一页重新取数
watch(planId, () => { query.pageNo = 1; list.value = []; total.value = 0 })

// 新增/编辑
const formRef = ref()
const dialogVisible = ref(false)
const attachFiles = ref([])
const defaultForm = () => ({
  id: null, title: '', supplier: '', applicant: '', department: '', remark: '', items: []
})
const form = reactive(defaultForm())
const rules = { title: [{ required: true, message: '请输入采购事由', trigger: 'blur' }] }

function itemAmount(row) {
  return ((row.qty || 0) * (row.unitPrice || 0)).toFixed(2)
}
const itemsTotal = computed(() =>
  form.items.reduce((sum, row) => sum + (row.qty || 0) * (row.unitPrice || 0), 0).toFixed(2)
)
function addItem() {
  form.items.push({ itemName: '', spec: '', unit: '', qty: 1, unitPrice: 0 })
}

async function openDialog(row) {
  Object.assign(form, defaultForm())
  attachFiles.value = []
  if (row) {
    Object.assign(form, await purRequestApi.get(row.id))
    if (!form.items || form.items.length === 0) addItem()
    try { attachFiles.value = await fileApi.list(BIZ_TYPE, row.id) } catch (e) { /* 忽略 */ }
  } else {
    addItem()
  }
  dialogVisible.value = true
}

/** 校验并保存(始终挂到当前计划下),回填待关联附件,返回申请ID */
async function persist() {
  await formRef.value.validate()
  if (form.items.length === 0) {
    ElMessage.warning('请至少添加一条采购明细')
    return null
  }
  const payload = { ...form, planId: planId.value }
  let requestId = form.id
  if (form.id) {
    await purRequestApi.update(payload)
  } else {
    requestId = await purRequestApi.add(payload)
  }
  // 先传后回填:把新上传(bizId 为空)的附件关联到本申请
  const pendingIds = (attachFiles.value || []).filter((f) => f && f.id && !f.bizId).map((f) => f.id)
  if (pendingIds.length > 0) {
    try { await fileApi.attach(BIZ_TYPE, requestId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  return requestId
}

async function submitForm() {
  const requestId = await persist()
  if (!requestId) return
  ElMessage.success('已保存草稿')
  dialogVisible.value = false
  await load()
}

async function saveAndSubmit() {
  const requestId = await persist()
  if (!requestId) return
  await purRequestApi.submit(requestId)
  ElMessage.success('已提交审批')
  dialogVisible.value = false
  await load()
}

// 详情
const detailVisible = ref(false)
const detail = ref({})
const detailFiles = ref([])
const trail = ref([])
async function openDetail(row) {
  detail.value = await purRequestApi.get(row.id)
  detailFiles.value = []
  trail.value = []
  try { detailFiles.value = await fileApi.list(BIZ_TYPE, row.id) } catch (e) { /* 忽略 */ }
  try {
    const res = await wfTaskApi.instancePage({ bizType: WF_BIZ_TYPE, bizId: row.id, pageNo: 1, pageSize: 1 })
    const inst = (res.records || [])[0]
    if (inst) trail.value = await wfTaskApi.instanceTasks(inst.id)
  } catch (e) { /* 未起流程则无轨迹 */ }
  detailVisible.value = true
}

async function downloadFile(row) {
  try {
    const res = await fileApi.download(row.id)
    const blobUrl = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = row.originalName || 'file'
    a.click()
    URL.revokeObjectURL(blobUrl)
  } catch (e) {
    ElMessage.error('下载失败')
  }
}

async function doSubmit(row) {
  await purRequestApi.submit(row.id)
  ElMessage.success('已提交审批')
  await load()
}
async function doComplete(row) {
  await purRequestApi.complete(row.id)
  ElMessage.success('已标记完成')
  await load()
}
async function doCancel(row) {
  await purRequestApi.cancel(row.id)
  ElMessage.success('已取消')
  await load()
}
async function doRemove(row) {
  await purRequestApi.remove(row.id)
  ElMessage.success('已删除')
  await load()
}
</script>

<style scoped>
.section-title { font-size: 15px; font-weight: 600; color: #303133; }
.hint { font-size: 13px; font-weight: 400; color: #909399; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.items-toolbar { display: flex; justify-content: space-between; align-items: center; margin: 8px 0; }
.total-line { text-align: right; margin-top: 8px; font-weight: 600; color: #303133; }
.pager { margin-top: 16px; justify-content: flex-end; }
.empty-tip { text-align: center; color: #909399; padding: 12px 0; }
.opinion { color: #606266; font-size: 13px; margin-top: 4px; }
</style>
