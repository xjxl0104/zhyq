<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="职位">
          <el-input v-model="query.postName" placeholder="请输入职位" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="query.dept" placeholder="请输入部门" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="招聘中" :value="1" />
            <el-option label="已关闭" :value="2" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增招聘</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="postName" label="职位" min-width="150" />
        <el-table-column prop="dept" label="部门" min-width="120" />
        <el-table-column prop="headcount" label="名额" width="80" />
        <el-table-column prop="salaryRange" label="薪资范围" width="120" />
        <el-table-column prop="applicants" label="投递数" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '招聘中' : '已关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" :disabled="row.status !== 1" @click="apply(row.id)">模拟投递</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认关闭该职位?" @confirm="close(row.id)">
              <template #reference>
                <el-button link type="warning" :disabled="row.status !== 1">关闭职位</el-button>
              </template>
            </el-popconfirm>
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
        <el-form-item label="职位" prop="postName"><el-input v-model="form.postName" /></el-form-item>
        <el-form-item label="部门"><el-input v-model="form.dept" /></el-form-item>
        <el-form-item label="名额"><el-input-number v-model="form.headcount" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="薪资范围"><el-input v-model="form.salaryRange" placeholder="如:8k-12k" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">招聘中</el-radio>
            <el-radio :value="2">已关闭</el-radio>
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
import { recruitApi } from '@/api/oa'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, postName: '', dept: '', status: null })

async function load() {
  loading.value = true
  try {
    const res = await recruitApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, postName: '', dept: '', status: null })
  load()
}

async function close(id) {
  await recruitApi.close(id)
  ElMessage.success('职位已关闭')
  load()
}
async function apply(id) {
  await recruitApi.apply(id)
  ElMessage.success('投递成功')
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const defaultForm = () => ({ id: null, postName: '', dept: '', headcount: 1, salaryRange: '', status: 1, remark: '' })
const form = reactive(defaultForm())
const rules = {
  postName: [{ required: true, message: '请输入职位', trigger: 'blur' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑招聘' : '新增招聘'
  if (row) Object.assign(form, row)
  else Object.assign(form, defaultForm())
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await recruitApi.update(form)
  else await recruitApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await recruitApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
</style>
