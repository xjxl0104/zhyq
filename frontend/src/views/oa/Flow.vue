<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="流程名称">
          <el-input v-model="query.flowName" placeholder="请输入流程名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="query.bizType" placeholder="全部" clearable style="width: 140px">
            <el-option label="合同" value="contract" />
            <el-option label="退款" value="refund" />
            <el-option label="调账" value="adjust" />
            <el-option label="装修" value="decoration" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增流程</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="flowName" label="流程名称" min-width="140" />
        <el-table-column label="业务类型" width="100">
          <template #default="{ row }">
            <el-tag :type="bizTypeTag(row.bizType)">{{ bizTypeText(row.bizType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批链" min-width="280">
          <template #default="{ row }">
            <span v-for="(s, idx) in parseSteps(row.steps)" :key="idx" class="step-chain">
              <el-tag size="small" type="info">{{ s.step }}</el-tag>
              <span v-if="idx < parseSteps(row.steps).length - 1" class="step-arrow">→</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="toggle(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
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

    <!-- 表单弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="560px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="流程名称" prop="flowName"><el-input v-model="form.flowName" /></el-form-item>
        <el-form-item label="业务类型" prop="bizType">
          <el-select v-model="form.bizType" style="width: 100%">
            <el-option label="合同" value="contract" />
            <el-option label="退款" value="refund" />
            <el-option label="调账" value="adjust" />
            <el-option label="装修" value="decoration" />
          </el-select>
        </el-form-item>
        <el-form-item label="审批步骤">
          <div class="steps-editor">
            <div v-for="(s, idx) in stepList" :key="idx" class="step-row">
              <el-input v-model="s.step" placeholder="请输入审批节点名称" />
              <el-button link type="danger" @click="removeStep(idx)"><el-icon><Delete /></el-icon></el-button>
            </div>
            <el-button type="primary" link @click="addStep"><el-icon><Plus /></el-icon>添加节点</el-button>
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { flowApi } from '@/api/oa'

const bizTypeMap = {
  contract: { label: '合同', tag: 'primary' },
  refund: { label: '退款', tag: 'warning' },
  adjust: { label: '调账', tag: 'info' },
  decoration: { label: '装修', tag: 'success' }
}
function bizTypeText(v) {
  return bizTypeMap[v] ? bizTypeMap[v].label : (v || '-')
}
function bizTypeTag(v) {
  return bizTypeMap[v] ? bizTypeMap[v].tag : 'info'
}

function parseSteps(steps) {
  if (!steps) return []
  try {
    const arr = JSON.parse(steps)
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, flowName: '', bizType: null, status: null })

async function load() {
  loading.value = true
  try {
    const res = await flowApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, flowName: '', bizType: null, status: null })
  load()
}

async function toggle(row) {
  await flowApi.toggle(row.id)
  ElMessage.success('状态已切换')
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, flowName: '', bizType: 'contract', status: 1, remark: '' })
const stepList = ref([{ step: '' }])
const rules = {
  flowName: [{ required: true, message: '请输入流程名称', trigger: 'blur' }],
  bizType: [{ required: true, message: '请选择业务类型', trigger: 'change' }]
}

function addStep() {
  stepList.value.push({ step: '' })
}
function removeStep(idx) {
  stepList.value.splice(idx, 1)
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑流程' : '新增流程'
  if (row) {
    Object.assign(form, row)
    const steps = parseSteps(row.steps)
    stepList.value = steps.length ? steps.map(s => ({ step: s.step })) : [{ step: '' }]
  } else {
    Object.assign(form, { id: null, flowName: '', bizType: 'contract', status: 1, remark: '' })
    stepList.value = [{ step: '' }]
  }
}
async function submit() {
  await formRef.value.validate()
  const payload = { ...form, steps: JSON.stringify(stepList.value.filter(s => s.step && s.step.trim())) }
  if (form.id) await flowApi.update(payload)
  else await flowApi.add(payload)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await flowApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.step-chain { display: inline-flex; align-items: center; }
.step-arrow { margin: 0 6px; color: var(--text-secondary); }
.steps-editor { width: 100%; }
.step-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
</style>
