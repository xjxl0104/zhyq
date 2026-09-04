<template>
  <div class="page-container">
    <div class="table-card">
      <div class="toolbar">
        <span class="section-title">审批流程</span>
        <el-button type="primary" @click="openDefDialog()"><el-icon><Plus /></el-icon>新增流程</el-button>
      </div>
      <el-table :data="definitions" v-loading="loading" border stripe
                highlight-current-row @current-change="selectDefinition">
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="name" label="流程名称" min-width="180" />
        <el-table-column prop="bizType" label="绑定业务" width="140" />
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
                title="同一业务类型同时只应有一条启用流程;停用全部流程后,采购申请提交将不建审批链,可由审批中心人工处理。" />
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

    <el-dialog v-model="defDialogVisible" :title="defForm.id ? '编辑流程' : '新增流程'" width="480px">
      <el-form :model="defForm" label-width="90px" ref="defFormRef" :rules="defRules">
        <el-form-item label="流程名称" prop="name">
          <el-input v-model="defForm.name" placeholder="如:采购审批流" />
        </el-form-item>
        <el-form-item label="绑定业务" prop="bizType">
          <el-select v-model="defForm.bizType" style="width: 100%">
            <el-option label="采购申请" value="procurement" />
            <el-option label="合同" value="contract" />
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
import { wfDefinitionApi } from '@/api/workflow'
import { userApi, roleApi } from '@/api/system'

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

// 流程定义增删改
const defFormRef = ref()
const defDialogVisible = ref(false)
const defaultDefForm = () => ({ id: null, name: '', bizType: 'procurement', status: 1 })
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

onMounted(async () => {
  await loadDefinitions()
  try { users.value = await userApi.list() } catch (e) { /* 忽略 */ }
  try { roles.value = await roleApi.list() } catch (e) { /* 忽略 */ }
})
</script>

<style scoped>
.section-title { font-size: 15px; font-weight: 600; color: #303133; }
.hint { font-size: 13px; font-weight: 400; color: #909399; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.empty-tip { text-align: center; color: #909399; padding: 16px 0; }
</style>
