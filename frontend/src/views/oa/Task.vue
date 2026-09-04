<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="任务标题">
          <el-input v-model="query.title" placeholder="请输入标题" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="query.owner" placeholder="请输入负责人" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(label, val) in statusMap" :key="val" :label="label" :value="Number(val)" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增任务</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="title" label="任务标题" min-width="180" />
        <el-table-column prop="owner" label="负责人" min-width="100" />
        <el-table-column label="优先级" width="90">
          <template #default="{ row }">
            <el-tag :type="priorityTagType(row.priority)">{{ priorityMap[row.priority] || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="source" label="来源" min-width="100" />
        <el-table-column prop="dueDate" label="截止时间" width="170" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusMap[row.status] || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" :disabled="row.status === 3" @click="done(row.id)">完成</el-button>
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
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="520px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="任务标题" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.owner" /></el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" style="width: 100%">
            <el-option v-for="(label, val) in priorityMap" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="form.source" placeholder="请选择" clearable style="width: 100%">
            <el-option v-for="s in sources" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="form.dueDate" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="(label, val) in statusMap" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="附件">
          <FileUpload v-model="attachFiles" biz-type="oa_task" :biz-id="form.id" />
        </el-form-item>
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
import { taskApi } from '@/api/oa'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'

// 1待处理 2处理中 3已完成
const statusMap = { 1: '待处理', 2: '处理中', 3: '已完成' }
const priorityMap = { 1: '低', 2: '中', 3: '高' }
const sources = ['招商', '物业', '巡检', '手工']

function statusTagType(status) {
  const map = { 1: 'warning', 2: 'primary', 3: 'success' }
  return map[status] || 'info'
}
function priorityTagType(priority) {
  const map = { 1: 'info', 2: 'warning', 3: 'danger' }
  return map[priority] || 'info'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, title: '', owner: '', status: null })

async function load() {
  loading.value = true
  try {
    const res = await taskApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, title: '', owner: '', status: null })
  load()
}

async function done(id) {
  await taskApi.done(id)
  ElMessage.success('已完成')
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, title: '', owner: '', priority: 2, source: null, dueDate: null, content: '', status: 1 })
const attachFiles = ref([])
const rules = {
  title: [{ required: true, message: '请输入任务标题', trigger: 'blur' }]
}

async function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑任务' : '新增任务'
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: null, title: '', owner: '', priority: 2, source: null, dueDate: null, content: '', status: 1 })
  attachFiles.value = []
  if (row) {
    try { attachFiles.value = await fileApi.list('oa_task', row.id) } catch (e) { /* 忽略 */ }
  }
}
async function submit() {
  await formRef.value.validate()
  let taskId = form.id
  if (form.id) await taskApi.update(form)
  else taskId = await taskApi.add(form)
  const pendingIds = (attachFiles.value || []).filter(f => f && f.id && !f.bizId).map(f => f.id)
  if (taskId && pendingIds.length) {
    try { await fileApi.attach('oa_task', taskId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await taskApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
</style>
