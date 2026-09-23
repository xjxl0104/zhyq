<template>
  <div class="page-container">
    <!-- 统计卡 -->
    <el-alert v-if="statsError" :title="statsError" type="warning" :closable="false" class="notice" />
    <div v-if="canQueryHeaders" class="stat-row">
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
      <el-tab-pane label="我的待办" name="tasks" />
      <el-tab-pane v-if="canQueryHeaders" label="审批中单据" name="pending" />
      <el-tab-pane v-if="canQueryHeaders" label="已通过单据" name="approved" />
      <el-tab-pane v-if="canQueryHeaders" label="已驳回单据" name="rejected" />
      <el-tab-pane v-if="canQueryHeaders" label="全部单据" name="all" />
    </el-tabs>

    <!-- 查询区 -->
    <details class="search-bar" :open="!isMobile">
      <summary class="search-bar__summary">查询与筛选</summary>
      <el-form :inline="true" :model="query">
        <el-form-item :label="isTasks ? '单据检索' : '标题'">
          <el-input v-model="query.title" :placeholder="isTasks ? '业务单据ID或已显示标题' : '请输入标题'" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="query.bizType" placeholder="全部" clearable style="width: 140px">
            <el-option label="合同" value="contract" />
            <el-option label="预算" value="budget" /><el-option label="采购" value="procurement" />
            <el-option label="退款" value="refund" />
            <el-option label="调账" value="adjust" />
            <el-option label="退租" value="terminate" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务单据ID"><el-input-number v-model="query.bizId" :min="1" :precision="0" controls-position="right" style="width:140px" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </details>

    <!-- 表格区 -->
    <el-alert v-if="activeError" :title="activeError" type="error" :closable="false" class="notice"><el-button link @click="load">重新加载</el-button></el-alert>
    <p v-if="isTasks" class="task-help">通过或驳回只办理当前获派节点。多级审批将在最后一个节点通过后完成；不需要流程配置权限。</p>
    <div class="table-card">
      <MobileRecordList v-if="isMobile" :items="list" :loading="loading" :empty-text="isTasks ? '暂无符合条件的待办；未轮到当前用户的节点不会显示' : '暂无审批记录'">
        <template #title="{ row }">
          <span>{{ row.title }}</span>
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
        <template #default="{ row }">
          <div class="mobile-record-summary">
            <el-tag size="small" :type="bizTypeTag(row.bizType)">{{ bizTypeText(row.bizType) }}</el-tag>
            <span>{{ isTasks ? '审批人 / 角色：' + row.assignee : row.applyBy || '申请人未填写' }}</span>
          </div>
          <div class="mobile-record-meta">{{ isTasks ? '分配时间' : '申请时间' }} {{ row.createTime || '-' }}</div>
          <div v-if="isTasks" class="mobile-record-meta">流程 #{{row.instanceId}} · 第 {{row.seq}} 步 · 业务单据 #{{row.bizId}}</div>
          <details class="mobile-record-details">
            <summary :aria-label="`查看审批 ${row.title} 全部信息`">查看全部信息</summary>
            <dl class="mobile-record-fields">
              <div class="mobile-record-field mobile-record-field--wide"><dt>标题</dt><dd>{{ row.title || '-' }}</dd></div>
              <div class="mobile-record-field"><dt>业务类型</dt><dd>{{ bizTypeText(row.bizType) }}</dd></div>
              <div class="mobile-record-field"><dt>状态</dt><dd>{{ statusText(row.status) }}</dd></div>
              <div class="mobile-record-field"><dt>申请人</dt><dd>{{ row.applyBy || '-' }}</dd></div>
              <div class="mobile-record-field"><dt>申请时间</dt><dd>{{ row.createTime || '-' }}</dd></div>
              <div class="mobile-record-field"><dt>审批人</dt><dd>{{ row.approveBy || '-' }}</dd></div>
              <div class="mobile-record-field"><dt>审批时间</dt><dd>{{ row.approveTime || '-' }}</dd></div>
              <div class="mobile-record-field mobile-record-field--wide"><dt>审批意见</dt><dd>{{ row.opinion || '-' }}</dd></div>
            </dl>
          </details>
        </template>
        <template #actions="{ row }">
          <template v-if="isTasks && row.taskId">
            <el-button :aria-label="`通过审批 ${row.title}`" link type="success" @click="openAudit(row, 'approve')">通过</el-button>
            <el-button :aria-label="`驳回审批 ${row.title}`" link type="warning" @click="openAudit(row, 'reject')">驳回</el-button>
          </template>
          <template v-if="!isTasks && row.canDirectApprove === true"><el-button link type="success" @click="openAudit(row,'approve','direct')">通过</el-button><el-button link type="warning" @click="openAudit(row,'reject','direct')">驳回</el-button></template>
          <el-button v-else-if="!isTasks && row.status === 2" link type="primary" @click="showTasks(row)">查看我的待办</el-button>
          <el-popconfirm v-if="!isTasks && isAdmin && row.status === 1" title="确认删除草稿审批单?" @confirm="remove(row.id)">
            <template #reference><el-button :aria-label="`删除审批 ${row.title}`" link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </MobileRecordList>
      <el-table v-else :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column label="业务类型" width="100">
          <template #default="{ row }">
            <el-tag :type="bizTypeTag(row.bizType)">{{ bizTypeText(row.bizType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bizId" label="业务单据ID" width="110" />
        <el-table-column v-if="isTasks" prop="seq" label="当前步骤" width="90" />
        <el-table-column v-if="isTasks" prop="assignee" label="审批人 / 角色" width="140" />
        <el-table-column v-else prop="applyBy" label="申请人" width="100" />
        <el-table-column prop="createTime" :label="isTasks ? '分配时间' : '申请时间'" width="170" />
        <el-table-column v-if="!isTasks" prop="approveBy" label="审批人" width="100" />
        <el-table-column v-if="!isTasks" prop="approveTime" label="审批时间" width="170" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="!isTasks" prop="opinion" label="审批意见" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="isTasks && row.taskId">
              <el-button link type="success" @click="openAudit(row, 'approve')">通过</el-button>
              <el-button link type="warning" @click="openAudit(row, 'reject')">驳回</el-button>
            </template>
            <template v-if="!isTasks && row.canDirectApprove === true"><el-button link type="success" @click="openAudit(row,'approve','direct')">通过</el-button><el-button link type="warning" @click="openAudit(row,'reject','direct')">驳回</el-button></template>
          <el-button v-else-if="!isTasks && row.status === 2" link type="primary" @click="showTasks(row)">查看我的待办</el-button>
          <el-popconfirm v-if="!isTasks && isAdmin && row.status === 1" title="确认删除草稿审批单?" @confirm="remove(row.id)">
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
    <el-dialog v-model="audit.visible" :title="(audit.source === 'task' ? '办理当前节点：' : '办理单据：') + (audit.mode === 'approve' ? '通过' : '驳回')" width="min(480px,94vw)" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
      <div class="audit-title">单据:{{ audit.row.title }}</div>
      <el-form label-width="80px">
        <el-form-item label="审批意见" :required="audit.mode === 'reject'">
          <el-input v-model="audit.opinion" type="textarea" :rows="3" maxlength="500" show-word-limit :disabled="saving"
                    :placeholder="audit.mode === 'approve' ? '同意(可不填)' : '请填写驳回原因'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="audit.visible = false">取消</el-button>
        <el-button :type="audit.mode === 'approve' ? 'success' : 'warning'" :loading="saving" :disabled="saving" @click="submitAudit">
          {{ audit.mode === 'approve' ? '通过' : '驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { hasPermission } from '@/utils/permission'
import { wfTaskApi } from '@/api/workflow'
import { ElMessage } from 'element-plus'
import { approvalApi } from '@/api/oa'
import MobileRecordList from '@/components/MobileRecordList.vue'
import { useResponsive } from '@/composables/useResponsive'

const { isMobile } = useResponsive()
const route = useRoute()
const isAdmin = hasPermission('ROLE_admin')
const canQueryHeaders = isAdmin || ['contract:query','budget:query','pur:request:query'].some(hasPermission)
const isTasks = computed(() => activeTab.value === 'tasks')

// 业务类型
const bizTypeMap = {
  contract: { label: '合同', tag: 'primary' },
  budget: { label: '预算', tag: 'primary' },
  procurement: { label: '采购', tag: 'warning' },
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

// 待办由后端按用户和角色授权。业务单头查询与待办加载互不阻断。
const tabStatus = { pending: 2, approved: 3, rejected: 4, all: null }
const activeTab = ref('tasks')
const query = reactive({ pageNo: 1, pageSize: 10, title: '', bizType: null, bizId: null, status: 2 })
const tasks = ref([]), headers = ref([]), headerTotal = ref(0)
const taskLoading = ref(false), headerLoading = ref(false), saving = ref(false)
const taskError = ref(''), headerError = ref(''), statsError = ref('')
let taskRequest=0, headerRequest=0
const stats = reactive({ pending: 0, approved: 0, rejected: 0, total: 0 })
const filteredTasks = computed(() => tasks.value.map(t => ({...t,id:t.taskId,status:2,
  title: headers.value.find(h => h.bizType === t.bizType && h.bizId === t.bizId)?.title || `${bizTypeText(t.bizType)} #${t.bizId} · 第 ${t.seq} 步`
})).filter(t => (!query.bizType || t.bizType === query.bizType) && (!query.bizId || Number(t.bizId) === Number(query.bizId)) && (!query.title || t.title.includes(query.title.trim()) || String(t.bizId).includes(query.title.trim()))))
const list = computed(() => isTasks.value ? filteredTasks.value.slice((query.pageNo-1)*query.pageSize,query.pageNo*query.pageSize) : headers.value)
const total = computed(() => isTasks.value ? filteredTasks.value.length : headerTotal.value)
const loading = computed(() => isTasks.value ? taskLoading.value : headerLoading.value)
const activeError = computed(() => isTasks.value ? taskError.value : headerError.value)
async function loadTasks() {
  const request=++taskRequest;taskLoading.value=true;taskError.value=''
  try { const result=await wfTaskApi.myPending();if(request===taskRequest)tasks.value=result || [] }
  catch(e) { if(request===taskRequest)taskError.value=e.message || '待办读取失败，请重试' }
  finally { if(request===taskRequest)taskLoading.value=false }
}
async function loadHeaders() {
  if(!canQueryHeaders)return
  const request=++headerRequest;headerLoading.value=true;headerError.value=''
  try { const res=await approvalApi.page({...query});if(request===headerRequest){headers.value=res.records || [];headerTotal.value=res.total || 0} }
  catch(e) { if(request===headerRequest)headerError.value=e.message || '审批单读取失败；仍可办理我的待办' }
  finally { if(request===headerRequest)headerLoading.value=false }
}
async function loadStats() {
  if(!canQueryHeaders)return
  try { Object.assign(stats,await approvalApi.stats());statsError.value='' }
  catch(e) { statsError.value=e.message || '单据统计暂不可用；仍可办理我的待办' }
}
function load() { return isTasks.value ? loadTasks() : loadHeaders() }
function refreshAll() { return Promise.all([loadTasks(),loadHeaders(),loadStats()]) }
function search() { query.pageNo=1;return load() }
function onTabChange(name) { query.pageNo=1;query.status=name==='tasks' ? 2 : tabStatus[name];return load() }
function reset() { Object.assign(query,{pageNo:1,title:'',bizType:null,bizId:null,status:activeTab.value==='tasks' ? 2 : tabStatus[activeTab.value]});return load() }
function applyRoute() {
  const id=String(route.query.bizId || '')
  Object.assign(query,{pageNo:1,title:'',bizType:typeof route.query.bizType === 'string' ? route.query.bizType : null,bizId:/^[1-9]\d*$/.test(id) ? Number(id) : null,status:2})
  activeTab.value='tasks'
}
function showTasks(row) { Object.assign(query,{pageNo:1,title:'',bizType:row.bizType,bizId:row.bizId});activeTab.value='tasks';return loadTasks() }
watch(() => [route.query.bizType,route.query.bizId],() => { applyRoute();refreshAll() })
const audit = reactive({ visible: false, mode: 'approve', row: {}, opinion: '', source: 'task' })
function openAudit(row,mode,source='task') {
  if(saving.value)return
  if(source==='task' ? (!row.taskId || !tasks.value.some(t=>t.taskId===row.taskId)) : row.canDirectApprove !== true)return
  Object.assign(audit,{visible:true,mode,row,opinion:'',source})
}
async function submitAudit() {
  if(saving.value || !audit.visible)return
  if(audit.source==='task' ? !audit.row.taskId : audit.row.canDirectApprove !== true)return
  const opinion=audit.opinion.trim()
  if(audit.mode==='reject' && !opinion)return ElMessage.warning('请填写驳回原因')
  saving.value=true
  try {
    if(audit.source==='task') {
      const fn=audit.mode==='approve' ? wfTaskApi.approve : wfTaskApi.reject
      await fn(audit.row.taskId,opinion)
    } else {
      const fn=audit.mode==='approve' ? approvalApi.approve : approvalApi.reject
      await fn(audit.row.id,{opinion})
    }
    ElMessage.success(audit.mode==='approve' ? (audit.source==='task' ? '当前节点已通过，最终结果以审批流程为准' : '单据已通过') : '已驳回')
    audit.visible=false
    await refreshAll()
  } finally { saving.value=false }
}
async function remove(id) {
  if(!isAdmin)return
  await approvalApi.remove(id);ElMessage.success('删除成功');await refreshAll()
}
onMounted(() => { applyRoute();refreshAll() })
onBeforeUnmount(() => { taskRequest++;headerRequest++ })
</script>

<style scoped>
.notice { margin-bottom: 12px; }
.task-help { color: var(--text-secondary); font-size: 13px; line-height: 1.6; margin-bottom: 12px; }
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
@media(max-width:640px){.stat-row{flex-wrap:wrap;gap:8px}.stat-card{flex:1 1 40%}.pager{justify-content:flex-start;overflow:auto}}
</style>
