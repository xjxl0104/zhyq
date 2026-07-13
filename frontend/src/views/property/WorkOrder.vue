<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">待派单</div>
        <div class="stat-value warning">{{ stats.pending || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">处理中</div>
        <div class="stat-value primary">{{ stats.processing || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已完成</div>
        <div class="stat-value success">{{ stats.done || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">总工单</div>
        <div class="stat-value">{{ stats.total || 0 }}</div>
      </div>
    </div>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="工单号">
          <el-input v-model="query.code" placeholder="请输入工单号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.orderType" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="t in orderTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="紧急度">
          <el-select v-model="query.urgency" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="(v, k) in urgencyMap" :key="k" :label="v.label" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区 -->
    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增工单</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="code" label="工单号" min-width="150" />
        <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
        <el-table-column prop="location" label="位置" min-width="120" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column label="紧急度" width="90">
          <template #default="{ row }">
            <el-tag :type="urgencyMap[row.urgency]?.type || 'info'">
              {{ urgencyMap[row.urgency]?.label || row.urgency }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assignee" label="处理人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" link type="primary" @click="openDispatch(row)">派单</el-button>
            <el-button v-if="row.status === 2" link type="primary" @click="doAccept(row)">接单</el-button>
            <el-button v-if="row.status === 3" link type="warning" @click="doArrive(row)">到场</el-button>
            <el-button v-if="row.status === 3" link type="primary" @click="doFinish(row)">完成</el-button>
            <el-button v-if="row.status === 4" link type="success" @click="openVerify(row)">验收</el-button>
            <el-button v-if="row.status !== 6" link type="danger" @click="doClose(row)">关闭</el-button>
            <el-button link type="info" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 新增工单弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="560px">
      <el-form :model="form" label-width="80px" ref="formRef" :rules="rules">
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.orderType" style="width: 100%">
            <el-option v-for="t in orderTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="位置"><el-input v-model="form.location" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="form.category" placeholder="如:水电/空调/门窗" /></el-form-item>
        <el-form-item label="紧急度">
          <el-radio-group v-model="form.urgency">
            <el-radio :value="1">低</el-radio>
            <el-radio :value="2">中</el-radio>
            <el-radio :value="3">高</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 派单弹窗 -->
    <el-dialog v-model="dispatchDialog.visible" title="派单" width="420px">
      <el-form :model="dispatchForm" label-width="90px" ref="dispatchFormRef" :rules="dispatchRules">
        <el-form-item label="工单号">
          <el-input :model-value="dispatchDialog.row?.code" disabled />
        </el-form-item>
        <el-form-item label="处理人" prop="assignee">
          <el-input v-model="dispatchForm.assignee" placeholder="请输入处理人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitDispatch">确认派单</el-button>
      </template>
    </el-dialog>

    <!-- 验收弹窗 -->
    <el-dialog v-model="verifyDialog.visible" title="验收" width="420px">
      <el-form :model="verifyForm" label-width="90px">
        <el-form-item label="工单号">
          <el-input :model-value="verifyDialog.row?.code" disabled />
        </el-form-item>
        <el-form-item label="满意度">
          <el-rate v-model="verifyForm.score" :max="5" show-score />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="verifyDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitVerify">确认验收</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗(流转时间线) -->
    <el-dialog v-model="detailDialog.visible" title="工单详情" width="640px">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="工单号">{{ detail.order?.code }}</el-descriptions-item>
        <el-descriptions-item label="标题">{{ detail.order?.title }}</el-descriptions-item>
        <el-descriptions-item label="位置">{{ detail.order?.location || '-' }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ detail.order?.category || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理人">{{ detail.order?.assignee || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMap[detail.order?.status]?.type || 'info'">
            {{ statusMap[detail.order?.status]?.label || detail.order?.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="到场时间">{{ detail.order?.arriveTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ detail.order?.finishTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">流转记录</el-divider>
      <el-timeline v-if="detail.logs?.length">
        <el-timeline-item v-for="log in detail.logs" :key="log.id"
                          :timestamp="log.createTime" placement="top">
          <span style="font-weight: 600">{{ log.action }}</span>
          <span v-if="log.operator" style="color: var(--text-secondary)"> · {{ log.operator }}</span>
          <div style="color: #606266; margin-top: 4px">{{ log.content }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无流转记录" :image-size="80" />
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { workOrderApi } from '@/api/property'

const orderTypes = ['报修', '巡检', '告警']
const urgencyMap = {
  1: { label: '低', type: 'info' },
  2: { label: '中', type: 'warning' },
  3: { label: '高', type: 'danger' }
}
const statusMap = {
  1: { label: '待派单', type: 'warning' },
  2: { label: '待接单', type: 'warning' },
  3: { label: '处理中', type: 'primary' },
  4: { label: '待验收', type: 'primary' },
  5: { label: '已完成', type: 'success' },
  6: { label: '已关闭', type: 'info' },
  7: { label: '已超时', type: 'danger' }
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const stats = reactive({ pending: 0, processing: 0, done: 0, total: 0 })
const query = reactive({ pageNo: 1, pageSize: 10, code: '', orderType: null, status: null, urgency: null })

async function load() {
  loading.value = true
  try {
    const res = await workOrderApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
async function loadStats() {
  Object.assign(stats, await workOrderApi.stats())
}
async function refresh() {
  await Promise.all([load(), loadStats()])
}
function reset() {
  Object.assign(query, { pageNo: 1, code: '', orderType: null, status: null, urgency: null })
  load()
}

// 新增工单
const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const defaultForm = () => ({ id: null, title: '', orderType: '报修', location: '', category: '', urgency: 2, contact: '', contactPhone: '', remark: '' })
const form = reactive(defaultForm())
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }]
}
function openDialog() {
  dialog.visible = true
  dialog.title = '新增工单'
  Object.assign(form, defaultForm())
}
async function submit() {
  await formRef.value.validate()
  await workOrderApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  refresh()
}

// 派单
const dispatchFormRef = ref()
const dispatchDialog = reactive({ visible: false, row: null })
const dispatchForm = reactive({ assignee: '' })
const dispatchRules = { assignee: [{ required: true, message: '请输入处理人', trigger: 'blur' }] }
function openDispatch(row) {
  dispatchDialog.visible = true
  dispatchDialog.row = row
  dispatchForm.assignee = ''
}
async function submitDispatch() {
  await dispatchFormRef.value.validate()
  await workOrderApi.dispatch(dispatchDialog.row.id, { assignee: dispatchForm.assignee })
  ElMessage.success('派单成功')
  dispatchDialog.visible = false
  refresh()
}

// 接单/到场/完成/关闭
async function doAccept(row) {
  await workOrderApi.accept(row.id)
  ElMessage.success('已接单')
  refresh()
}
async function doArrive(row) {
  await workOrderApi.arrive(row.id)
  ElMessage.success('已登记到场')
  refresh()
}
async function doFinish(row) {
  const { value } = await ElMessageBox.prompt('请输入处理结果', '处理完成', {
    confirmButtonText: '确定', cancelButtonText: '取消', inputType: 'textarea'
  }).catch(() => ({ value: undefined }))
  if (value === undefined) return
  await workOrderApi.finish(row.id, { content: value })
  ElMessage.success('已提交完成')
  refresh()
}
async function doClose(row) {
  await ElMessageBox.confirm('确认关闭该工单?', '提示', { type: 'warning' }).catch(() => 'cancel')
    .then(async (r) => {
      if (r === 'cancel') return
      await workOrderApi.close(row.id)
      ElMessage.success('工单已关闭')
      refresh()
    })
}

// 验收
const verifyDialog = reactive({ visible: false, row: null })
const verifyForm = reactive({ score: 5 })
function openVerify(row) {
  verifyDialog.visible = true
  verifyDialog.row = row
  verifyForm.score = 5
}
async function submitVerify() {
  await workOrderApi.verify(verifyDialog.row.id, { score: verifyForm.score })
  ElMessage.success('验收完成')
  verifyDialog.visible = false
  refresh()
}

// 详情
const detailDialog = reactive({ visible: false })
const detail = reactive({ order: null, logs: [] })
async function openDetail(row) {
  const res = await workOrderApi.get(row.id)
  detail.order = res.order
  detail.logs = res.logs || []
  detailDialog.visible = true
}

onMounted(refresh)
</script>

<style scoped>
.stat-row { display: flex; gap: 16px; margin-bottom: 16px; }
.stat-card {
  flex: 1; background: var(--bg-card); border-radius: var(--radius);
  border: 1px solid var(--border); padding: 20px 22px;
  transition: border-color .18s, transform .18s;
}
.stat-label { color: var(--text-secondary); font-size: 13px; margin-bottom: 8px; }
.stat-value { font-size: 24px; font-weight: 600; color: var(--text-title); }
.stat-value.primary { color: #4f46e5; }
.stat-value.success { color: #16a34a; }
.stat-value.warning { color: #ea9a13; }
.stat-value.danger { color: #e5484d; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
