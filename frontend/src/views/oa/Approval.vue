<template>
  <div class="page-container">
    <!-- 统计卡 -->
    <div class="stat-row">
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">待审批</div>
        <div class="stat-value pending">{{ stats.pending }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">已通过</div>
        <div class="stat-value approved">{{ stats.approved }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">已驳回</div>
        <div class="stat-value rejected">{{ stats.rejected }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">总数</div>
        <div class="stat-value">{{ stats.total }}</div>
      </el-card>
    </div>

    <!-- Tab 切换 -->
    <el-tabs v-model="activeTab" @tab-change="onTabChange" class="approval-tabs">
      <el-tab-pane label="待我审批" name="pending" />
      <el-tab-pane label="已通过" name="approved" />
      <el-tab-pane label="已驳回" name="rejected" />
      <el-tab-pane label="全部" name="all" />
    </el-tabs>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="标题">
          <el-input v-model="query.title" placeholder="请输入标题" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="query.bizType" placeholder="全部" clearable style="width: 140px">
            <el-option label="合同" value="contract" />
            <el-option label="退款" value="refund" />
            <el-option label="调账" value="adjust" />
            <el-option label="退租" value="terminate" />
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
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column label="业务类型" width="100">
          <template #default="{ row }">
            <el-tag :type="bizTypeTag(row.bizType)">{{ bizTypeText(row.bizType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applyBy" label="申请人" width="100" />
        <el-table-column prop="createTime" label="申请时间" width="170" />
        <el-table-column prop="approveBy" label="审批人" width="100" />
        <el-table-column prop="approveTime" label="审批时间" width="170" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="opinion" label="审批意见" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 2">
              <el-button link type="success" @click="openAudit(row, 'approve')">通过</el-button>
              <el-button link type="warning" @click="openAudit(row, 'reject')">驳回</el-button>
            </template>
            <el-popconfirm title="确认删除?" @confirm="remove(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" @change="load" />
    </div>

    <!-- 审批意见弹窗 -->
    <el-dialog v-model="audit.visible" :title="audit.mode === 'approve' ? '审批通过' : '审批驳回'" width="480px">
      <div class="audit-title">单据:{{ audit.row.title }}</div>
      <el-form label-width="80px">
        <el-form-item label="审批意见">
          <el-input v-model="audit.opinion" type="textarea" :rows="3"
                    :placeholder="audit.mode === 'approve' ? '同意(可不填)' : '请填写驳回原因'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="audit.visible = false">取消</el-button>
        <el-button :type="audit.mode === 'approve' ? 'success' : 'warning'" @click="submitAudit">
          {{ audit.mode === 'approve' ? '通过' : '驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { approvalApi } from '@/api/oa'

// 业务类型
const bizTypeMap = {
  contract: { label: '合同', tag: 'primary' },
  refund: { label: '退款', tag: 'warning' },
  adjust: { label: '调账', tag: 'info' },
  terminate: { label: '退租', tag: 'danger' }
}
function bizTypeText(v) {
  return bizTypeMap[v] ? bizTypeMap[v].label : (v || '-')
}
function bizTypeTag(v) {
  return bizTypeMap[v] ? bizTypeMap[v].tag : 'info'
}

// 审批状态:1草稿 2审批中 3已通过 4已驳回 5已撤回 6已终止
const statusOptions = [
  { value: 1, label: '草稿', type: 'info' },
  { value: 2, label: '审批中', type: 'warning' },
  { value: 3, label: '已通过', type: 'success' },
  { value: 4, label: '已驳回', type: 'danger' },
  { value: 5, label: '已撤回', type: 'info' },
  { value: 6, label: '已终止', type: 'info' }
]
function statusText(v) {
  const s = statusOptions.find(o => o.value === v)
  return s ? s.label : '-'
}
function statusType(v) {
  const s = statusOptions.find(o => o.value === v)
  return s ? s.type : 'info'
}

// tab -> status 过滤
const tabStatus = { pending: 2, approved: 3, rejected: 4, all: null }
const activeTab = ref('pending')

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, title: '', bizType: null, status: 2 })

const stats = reactive({ pending: 0, approved: 0, rejected: 0, total: 0 })

async function loadStats() {
  const res = await approvalApi.stats()
  Object.assign(stats, res)
}

async function load() {
  loading.value = true
  try {
    const res = await approvalApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function onTabChange(name) {
  query.pageNo = 1
  query.status = tabStatus[name]
  load()
}
function reset() {
  Object.assign(query, { pageNo: 1, title: '', bizType: null, status: tabStatus[activeTab.value] })
  load()
}

// 通过/驳回
const audit = reactive({ visible: false, mode: 'approve', row: {}, opinion: '' })

function openAudit(row, mode) {
  audit.visible = true
  audit.mode = mode
  audit.row = row
  audit.opinion = ''
}
async function submitAudit() {
  const fn = audit.mode === 'approve' ? approvalApi.approve : approvalApi.reject
  await fn(audit.row.id, { opinion: audit.opinion })
  ElMessage.success(audit.mode === 'approve' ? '已通过' : '已驳回')
  audit.visible = false
  load()
  loadStats()
}

async function remove(id) {
  await approvalApi.remove(id)
  ElMessage.success('删除成功')
  load()
  loadStats()
}

onMounted(() => {
  query.status = tabStatus[activeTab.value]
  load()
  loadStats()
})
</script>

<style scoped>
.stat-row { display: flex; gap: 16px; margin-bottom: 16px; }
.stat-card { flex: 1; }
.stat-label { color: var(--text-secondary); font-size: 13px; }
.stat-value { font-size: 26px; font-weight: 600; margin-top: 8px; }
.stat-value.pending { color: #e6a23c; }
.stat-value.approved { color: #67c23a; }
.stat-value.rejected { color: #f56c6c; }
.approval-tabs { margin-bottom: 8px; }
.audit-title { color: #606266; margin-bottom: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
