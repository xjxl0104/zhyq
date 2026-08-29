<template>
  <div class="page-container">
    <el-tabs v-model="activeTab" class="flow-tabs">
      <el-tab-pane label="流程设置" name="config" />
      <el-tab-pane label="我的待办" name="todo" />
    </el-tabs>

    <!-- ==================== 流程设置 ==================== -->
    <template v-if="activeTab === 'config'">
      <div class="table-card">
        <div class="toolbar">
          <span class="section-title">审批流程</span>
          <el-button type="primary" @click="openDefDialog()"><el-icon><Plus /></el-icon>新增流程</el-button>
        </div>
        <el-table :data="definitions" v-loading="loading" border stripe
                  highlight-current-row @current-change="selectDefinition">
          <el-table-column type="index" label="#" width="55" />
          <el-table-column prop="name" label="流程名称" min-width="180" />
          <el-table-column label="绑定业务" width="140">
            <template #default="{ row }">{{ bizTypeLabel(row.bizType) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openDefDialog(row)">编辑</el-button>
              <el-button link type="primary" @click.stop="selectDefinition(row)">配置节点</el-button>
              <el-popconfirm title="确认删除该流程及其节点?" @confirm="removeDefinition(row)">
                <template #reference><el-button link type="danger" @click.stop>删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <el-alert type="info" :closable="false" style="margin-top: 12px"
                  title="同一业务类型同时只应有一条启用流程;停用全部流程后,预算/采购申请提交将不建审批链,可由审批中心人工处理。" />
      </div>

      <div v-if="currentDef" class="table-card" style="margin-top: 16px">
        <div class="toolbar">
          <span class="section-title">
            「{{ currentDef.name }}」的审批节点
            <span class="hint">— 按顺序依次审批,可上下调整顺序</span>
          </span>
          <div>
            <el-button @click="addNode"><el-icon><Plus /></el-icon>添加节点</el-button>
            <el-button type="primary" @click="saveNodes">保存节点</el-button>
          </div>
        </div>
        <el-table :data="nodes" border>
          <el-table-column label="顺序" width="70" align="center">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="节点名称" min-width="160">
            <template #default="{ row }"><el-input v-model="row.name" placeholder="如:部门负责人审批" /></template>
          </el-table-column>
          <el-table-column label="审批人类型" width="150">
            <template #default="{ row }">
              <el-select v-model="row.approverType" style="width: 100%" @change="row.approverValue = ''">
                <el-option label="指定用户" value="user" />
                <el-option label="指定角色" value="role" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="审批人" min-width="200">
            <template #default="{ row }">
              <el-select v-if="row.approverType === 'user'" v-model="row.approverValue"
                         placeholder="选择用户" filterable style="width: 100%">
                <el-option v-for="u in users" :key="u.id" :label="`${u.nickname || u.username}(${u.username})`"
                           :value="u.username" />
              </el-select>
              <el-select v-else v-model="row.approverValue" placeholder="选择角色" filterable style="width: 100%">
                <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.code" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ $index }">
              <el-button link :disabled="$index === 0" @click="moveNode($index, -1)">上移</el-button>
              <el-button link :disabled="$index === nodes.length - 1" @click="moveNode($index, 1)">下移</el-button>
              <el-button link type="danger" @click="nodes.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="nodes.length === 0" class="empty-tip">还没有审批节点,点「添加节点」开始配置</div>
      </div>
    </template>

    <!-- ==================== 我的待办 ==================== -->
    <template v-else>
      <div class="search-bar">
        <el-form :inline="true">
          <el-form-item label="审批人">
            <el-input v-model="assignee" placeholder="留空看全部待办" clearable style="width: 200px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadTodo"><el-icon><Search /></el-icon>查询</el-button>
            <el-button @click="assignee = ''; loadTodo()">看全部</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="table-card">
        <div class="toolbar">
          <span class="section-title">
            待我审批
            <span class="hint">— 节点审批人配的是角色时,这里填角色码(如 dept_manager)也能查到</span>
          </span>
        </div>
        <el-table :data="todoList" v-loading="todoLoading" border stripe>
          <el-table-column type="index" label="#" width="55" />
          <el-table-column label="单据类型" width="120">
            <template #default="{ row }">
              <el-tag :type="bizTagType(row.bizType)">{{ bizTypeLabel(row.bizType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="单据" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.bizTitle || `#${row.bizId}` }}</template>
          </el-table-column>
          <el-table-column label="审批环节" width="100" align="center">
            <template #default="{ row }">第{{ row.seq }}步</template>
          </el-table-column>
          <el-table-column prop="assignee" label="指派给" width="140" />
          <el-table-column prop="createTime" label="到达时间" width="180" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="success" @click="openAct(row, 'approve')">通过</el-button>
              <el-button link type="danger" @click="openAct(row, 'reject')">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!todoLoading && todoList.length === 0" class="empty-tip">没有待办</div>
      </div>

      <el-dialog v-model="actVisible" :title="actType === 'approve' ? '审批通过' : '审批驳回'" width="480px">
        <el-form label-width="80px">
          <el-form-item label="单据">{{ actRow.bizTitle || `#${actRow.bizId}` }}</el-form-item>
          <el-form-item label="审批意见">
            <el-input v-model="opinion" type="textarea" :rows="3"
                      :placeholder="actType === 'approve' ? '同意' : '请填写驳回原因'" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="actVisible = false">取消</el-button>
          <el-button :type="actType === 'approve' ? 'success' : 'danger'" @click="doAct">确定</el-button>
        </template>
      </el-dialog>
    </template>

    <!-- 流程定义新增/编辑 -->
    <el-dialog v-model="defDialogVisible" :title="defForm.id ? '编辑流程' : '新增流程'" width="480px">
      <el-form :model="defForm" label-width="90px" ref="defFormRef" :rules="defRules">
        <el-form-item label="流程名称" prop="name">
          <el-input v-model="defForm.name" placeholder="如:预算申请审批流" />
        </el-form-item>
        <el-form-item label="绑定业务" prop="bizType">
          <el-select v-model="defForm.bizType" style="width: 100%">
            <el-option v-for="b in bizTypes" :key="b.value" :label="b.label" :value="b.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="defForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="defDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDefinition">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { wfDefinitionApi, wfTaskApi } from '@/api/workflow'
import { userApi, roleApi } from '@/api/system'
import { budgetApi } from '@/api/budget'
import { purRequestApi } from '@/api/pur'

const activeTab = ref('config')

// 预算管理板块直接用到前两种;合同流程也在这张表里,一并可见可配
const bizTypes = [
  { value: 'budget', label: '预算申请', tag: 'primary' },
  { value: 'procurement', label: '采购申请', tag: 'warning' },
  { value: 'contract', label: '合同', tag: 'info' }
]
const bizTypeLabel = (v) => bizTypes.find((b) => b.value === v)?.label || v
const bizTagType = (v) => bizTypes.find((b) => b.value === v)?.tag || 'info'

// ==================== 流程设置 ====================
const loading = ref(false)
const definitions = ref([])
const currentDef = ref(null)
const nodes = ref([])
const users = ref([])
const roles = ref([])

async function loadDefinitions() {
  loading.value = true
  try {
    const res = await wfDefinitionApi.page({ pageNo: 1, pageSize: 100 })
    definitions.value = res.records || []
  } finally {
    loading.value = false
  }
}

async function selectDefinition(row) {
  if (!row) return
  currentDef.value = row
  nodes.value = await wfDefinitionApi.nodes(row.id)
}

function addNode() {
  nodes.value.push({ name: '', approverType: 'user', approverValue: '' })
}

function moveNode(index, delta) {
  const target = index + delta
  const arr = nodes.value
  ;[arr[index], arr[target]] = [arr[target], arr[index]]
}

async function saveNodes() {
  const invalid = nodes.value.find((n) => !n.name || !n.approverValue)
  if (invalid) {
    ElMessage.warning('每个节点都要填节点名称并选择审批人')
    return
  }
  await wfDefinitionApi.saveNodes(currentDef.value.id, nodes.value)
  ElMessage.success('节点已保存')
  await selectDefinition(currentDef.value)
}

const defFormRef = ref()
const defDialogVisible = ref(false)
const defaultDefForm = () => ({ id: null, name: '', bizType: 'budget', status: 1 })
const defForm = reactive(defaultDefForm())
const defRules = {
  name: [{ required: true, message: '请输入流程名称', trigger: 'blur' }],
  bizType: [{ required: true, message: '请选择绑定业务', trigger: 'change' }]
}

function openDefDialog(row) {
  Object.assign(defForm, defaultDefForm())
  if (row) Object.assign(defForm, row)
  defDialogVisible.value = true
}

async function submitDefinition() {
  await defFormRef.value.validate()
  if (defForm.id) {
    await wfDefinitionApi.update(defForm)
    ElMessage.success('已保存')
  } else {
    await wfDefinitionApi.add(defForm)
    ElMessage.success('新增成功')
  }
  defDialogVisible.value = false
  await loadDefinitions()
}

async function removeDefinition(row) {
  await wfDefinitionApi.remove(row.id)
  if (currentDef.value && currentDef.value.id === row.id) {
    currentDef.value = null
    nodes.value = []
  }
  ElMessage.success('已删除')
  await loadDefinitions()
}

// ==================== 我的待办 ====================
const todoLoading = ref(false)
const todoList = ref([])
const assignee = ref(localStorage.getItem('zhyq_username') || '')

async function loadTodo() {
  todoLoading.value = true
  try {
    const tasks = await wfTaskApi.myPending(assignee.value || undefined)
    // 单据标题按 bizType 回查各自业务接口(workflow 不反向依赖业务模块)
    await Promise.all((tasks || []).map(async (t) => {
      try {
        if (t.bizType === 'budget') {
          t.bizTitle = (await budgetApi.get(t.bizId))?.title
        } else if (t.bizType === 'procurement') {
          t.bizTitle = (await purRequestApi.get(t.bizId))?.title
        }
      } catch (e) { /* 单据取不到就只显示ID */ }
    }))
    todoList.value = tasks || []
  } finally {
    todoLoading.value = false
  }
}

const actVisible = ref(false)
const actType = ref('approve')
const actRow = ref({})
const opinion = ref('')

function openAct(row, type) {
  actRow.value = row
  actType.value = type
  opinion.value = type === 'approve' ? '同意' : ''
  actVisible.value = true
}

async function doAct() {
  if (actType.value === 'reject' && !opinion.value.trim()) {
    ElMessage.warning('驳回请填写原因')
    return
  }
  if (actType.value === 'approve') {
    await wfTaskApi.approve(actRow.value.taskId, opinion.value)
    ElMessage.success('已通过')
  } else {
    await wfTaskApi.reject(actRow.value.taskId, opinion.value)
    ElMessage.success('已驳回')
  }
  actVisible.value = false
  await loadTodo()
}

onMounted(async () => {
  await loadDefinitions()
  try { users.value = await userApi.list() } catch (e) { /* 忽略 */ }
  try { roles.value = await roleApi.list() } catch (e) { /* 忽略 */ }
  await loadTodo()
})
</script>

<style scoped>
.section-title { font-size: 15px; font-weight: 600; color: #303133; }
.hint { font-size: 13px; font-weight: 400; color: #909399; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.empty-tip { text-align: center; color: #909399; padding: 16px 0; }
.flow-tabs { margin-bottom: 4px; }
</style>
