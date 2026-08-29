<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true">
        <el-form-item label="预算名称">
          <el-input v-model="query.title" placeholder="预算名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item :label="periodLabel">
          <el-input v-model="query.period" :placeholder="periodPlaceholder" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="申请部门">
          <el-input v-model="query.department" placeholder="申请部门" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="Number(k)" />
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
        <span class="section-title">{{ budgetTypeLabel }}</span>
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增{{ budgetTypeLabel }}</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="budgetNo" label="预算编号" min-width="140" />
        <el-table-column prop="title" label="预算名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="period" :label="periodLabel" width="100" />
        <el-table-column prop="department" label="申请部门" width="120" />
        <el-table-column prop="applicant" label="申请人" width="90" />
        <el-table-column label="预算金额" width="130" align="right">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="canEdit(row)" link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="canEdit(row)" link type="success" @click="doSubmit(row)">提交申请</el-button>
            <el-button v-if="row.status === 3" link type="success" @click="doArchive(row)">归档</el-button>
            <el-popconfirm v-if="[1, 2, 3].includes(row.status)" title="确认取消该预算?" @confirm="doCancel(row)">
              <template #reference><el-button link type="warning">取消</el-button></template>
            </el-popconfirm>
            <el-popconfirm v-if="[1, 4, 6].includes(row.status)" title="确认删除该预算?" @confirm="doRemove(row)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 新增/编辑 -->
    <el-dialog v-model="dialogVisible" :title="`${form.id ? '编辑' : '新增'}${budgetTypeLabel}`" width="680px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="预算名称" prop="title">
              <el-input v-model="form.title" :placeholder="titlePlaceholder" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="periodLabel" prop="period">
              <el-date-picker v-if="budgetType === 1" v-model="form.period" type="year" value-format="YYYY"
                              :placeholder="periodPlaceholder" style="width: 100%" />
              <el-date-picker v-else v-model="form.period" type="month" value-format="YYYY-MM"
                              :placeholder="periodPlaceholder" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预算金额">
              <el-input-number v-model="form.amount" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="申请部门"><el-input v-model="form.department" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="申请人"><el-input v-model="form.applicant" /></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="预算计划">
          <FileUpload v-model="attachFiles" :biz-type="BIZ_TYPE" :biz-id="form.id" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">保存草稿</el-button>
        <el-button type="success" @click="saveAndSubmit">保存并提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" :title="`${budgetTypeLabel}详情`" width="760px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="预算编号">{{ detail.budgetNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMap[detail.status]?.type || 'info'">{{ statusMap[detail.status]?.label }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="预算名称" :span="2">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item :label="periodLabel">{{ detail.period }}</el-descriptions-item>
        <el-descriptions-item label="预算金额">¥{{ detail.amount }}</el-descriptions-item>
        <el-descriptions-item label="申请部门">{{ detail.department || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.applicant || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最近审批人">{{ detail.approver || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最近审批时间">{{ detail.approveTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="section-title" style="margin: 14px 0 8px">预算计划文件</div>
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
      <div v-if="trail.length === 0" class="empty-tip">尚未提交预算申请</div>
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
  </div>
</template>

<script setup>
import { reactive, ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { budgetApi } from '@/api/budget'
import { wfTaskApi } from '@/api/workflow'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'

// 附件 bizType 与审批链 bizType 同名,都是 budget
const BIZ_TYPE = 'budget'

const route = useRoute()
// 一组件多路由:年度预算/月度预算共用本页,靠 route.meta.budgetType 区分
const budgetType = computed(() => route.meta.budgetType || 1)
const budgetTypeLabel = computed(() => ({ 1: '年度预算', 2: '月度预算' }[budgetType.value]))
const periodLabel = computed(() => ({ 1: '年度', 2: '月份' }[budgetType.value]))
const periodPlaceholder = computed(() => ({ 1: '如 2026', 2: '如 2026-03' }[budgetType.value]))
const titlePlaceholder = computed(() =>
  ({ 1: '如:2026年度园区运营总预算', 2: '如:2026年3月月度预算' }[budgetType.value]))

const statusMap = {
  1: { label: '草稿', type: 'info' },
  2: { label: '审批中', type: 'warning' },
  3: { label: '已通过', type: 'primary' },
  4: { label: '已驳回', type: 'danger' },
  5: { label: '已归档', type: 'success' },
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
const query = reactive({ pageNo: 1, pageSize: 10, title: '', period: '', department: '', status: null })

async function load() {
  loading.value = true
  try {
    const res = await budgetApi.page({ ...query, budgetType: budgetType.value })
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}
function search() { query.pageNo = 1; load() }
function reset() {
  query.title = ''; query.period = ''; query.department = ''; query.status = null
  search()
}

// 切换年度/月度菜单时,同一组件复用需重新加载
watch(budgetType, () => { reset() })

// 新增/编辑
const formRef = ref()
const dialogVisible = ref(false)
const attachFiles = ref([])
const defaultForm = () => ({
  id: null, title: '', period: '', department: '', applicant: '', amount: 0, remark: ''
})
const form = reactive(defaultForm())
const rules = {
  title: [{ required: true, message: '请输入预算名称', trigger: 'blur' }],
  period: [{ required: true, message: '请选择周期', trigger: 'change' }]
}

async function openDialog(row) {
  Object.assign(form, defaultForm())
  attachFiles.value = []
  if (row) {
    Object.assign(form, await budgetApi.get(row.id))
    try { attachFiles.value = await fileApi.list(BIZ_TYPE, row.id) } catch (e) { /* 忽略 */ }
  }
  dialogVisible.value = true
}

/** 校验并保存,把待关联附件回填到本预算,返回预算ID */
async function persist() {
  await formRef.value.validate()
  const payload = { ...form, budgetType: budgetType.value }
  let budgetId = form.id
  if (form.id) {
    await budgetApi.update(payload)
  } else {
    budgetId = await budgetApi.add(payload)
  }
  // 先传后回填:把新上传(bizId 为空)的附件关联到本预算
  const pendingIds = (attachFiles.value || []).filter((f) => f && f.id && !f.bizId).map((f) => f.id)
  if (pendingIds.length > 0) {
    // 回填失败不阻断保存,但必须告警:静默吞掉会让用户看到"已保存"却找不到附件
    try {
      await fileApi.attach(BIZ_TYPE, budgetId, pendingIds)
    } catch (e) {
      ElMessage.warning('预算已保存,但附件关联失败,请在编辑里重新上传')
    }
  }
  return budgetId
}

async function submitForm() {
  await persist()
  ElMessage.success('已保存草稿')
  dialogVisible.value = false
  await load()
}

async function saveAndSubmit() {
  const budgetId = await persist()
  await budgetApi.submit(budgetId)
  ElMessage.success('已提交预算申请')
  dialogVisible.value = false
  await load()
}

// 详情
const detailVisible = ref(false)
const detail = ref({})
const detailFiles = ref([])
const trail = ref([])
async function openDetail(row) {
  detail.value = await budgetApi.get(row.id)
  detailFiles.value = []
  trail.value = []
  try { detailFiles.value = await fileApi.list(BIZ_TYPE, row.id) } catch (e) { /* 忽略 */ }
  try {
    const res = await wfTaskApi.instancePage({ bizType: BIZ_TYPE, bizId: row.id, pageNo: 1, pageSize: 1 })
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
  await budgetApi.submit(row.id)
  ElMessage.success('已提交预算申请')
  await load()
}
async function doArchive(row) {
  await budgetApi.archive(row.id)
  ElMessage.success('已归档')
  await load()
}
async function doCancel(row) {
  await budgetApi.cancel(row.id)
  ElMessage.success('已取消')
  await load()
}
async function doRemove(row) {
  await budgetApi.remove(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.section-title { font-size: 15px; font-weight: 600; color: #303133; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.empty-tip { text-align: center; color: #909399; padding: 12px 0; }
.opinion { color: #606266; font-size: 13px; margin-top: 4px; }
</style>
