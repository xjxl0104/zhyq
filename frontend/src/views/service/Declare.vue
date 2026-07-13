<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="标题">
          <el-input v-model="query.title" placeholder="申报项目" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.dtype" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="t in dtypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增申报</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="title" label="申报项目" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="120">
          <template #default="{ row }"><el-tag type="info">{{ row.dtype || '-' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="tenantRefId" label="申报企业ID" width="100" />
        <el-table-column prop="materials" label="材料清单" min-width="220" show-overflow-tooltip />
        <el-table-column label="截止日期" width="130">
          <template #default="{ row }">
            <span :class="{ 'deadline-warn': isDeadlineNear(row.deadline) }">{{ row.deadline || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusMap[row.status] || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-if="row.status === 1" @click="submitDeclare(row.id)">提交</el-button>
            <el-button link type="success" v-if="row.status === 2" @click="pass(row.id)">通过</el-button>
            <el-button link type="danger" v-if="row.status === 2" @click="fail(row.id)">未通过</el-button>
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
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
        <el-form-item label="申报项目" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.dtype" style="width: 100%" filterable allow-create>
            <el-option v-for="t in dtypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="申报企业ID"><el-input-number v-model="form.tenantRefId" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="材料清单"><el-input v-model="form.materials" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="form.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="(label, val) in statusMap" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { declareApi } from '@/api/service'

// 1材料准备 2已提交 3已通过 4未通过
const statusMap = { 1: '准备', 2: '已提交', 3: '已通过', 4: '未通过' }
const dtypeOptions = ['高企认定', '专精特新', '研发补贴', '人才补贴']

function statusTagType(status) {
  const map = { 1: 'info', 2: 'primary', 3: 'success', 4: 'danger' }
  return map[status] || 'info'
}

function isDeadlineNear(deadline) {
  if (!deadline) return false
  const diff = (new Date(deadline) - new Date()) / (1000 * 60 * 60 * 24)
  return diff >= 0 && diff <= 7
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, title: '', dtype: null, status: null })

async function load() {
  loading.value = true
  try {
    const res = await declareApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, title: '', dtype: null, status: null })
  load()
}

async function submitDeclare(id) {
  await declareApi.submit(id)
  ElMessage.success('已提交')
  load()
}
async function pass(id) {
  await declareApi.pass(id)
  ElMessage.success('已通过')
  load()
}
async function fail(id) {
  await declareApi.fail(id)
  ElMessage.success('已标记未通过')
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const defaultForm = () => ({ id: null, title: '', dtype: '', tenantRefId: null, materials: '', deadline: null, status: 1, remark: '' })
const form = reactive(defaultForm())
const rules = {
  title: [{ required: true, message: '请输入申报项目', trigger: 'blur' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑申报' : '新增申报'
  if (row) Object.assign(form, row)
  else Object.assign(form, defaultForm())
}
async function submitForm() {
  await formRef.value.validate()
  if (form.id) await declareApi.update(form)
  else await declareApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await declareApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
.deadline-warn { color: var(--el-color-danger); font-weight: 600; }
</style>
