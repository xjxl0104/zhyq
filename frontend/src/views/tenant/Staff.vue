<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="所属租客">
          <el-select v-model="query.tenantRefId" placeholder="全部租客" clearable filterable
                     style="width: 220px" @change="load">
            <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="query.name" placeholder="请输入姓名" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="在职状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="在职" :value="1" />
            <el-option label="离职" :value="0" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增员工</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="name" label="姓名" min-width="100" />
        <el-table-column prop="dept" label="部门" min-width="120" />
        <el-table-column prop="post" label="岗位" min-width="120" />
        <el-table-column prop="phone" label="手机号" min-width="130" />
        <el-table-column prop="plateNo" label="车牌" min-width="110" />
        <el-table-column prop="validEnd" label="有效期" min-width="120" />
        <el-table-column label="在职状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '在职' : '离职' }}
            </el-tag>
          </template>
        </el-table-column>
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
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="520px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="所属租客" prop="tenantRefId">
          <el-select v-model="form.tenantRefId" placeholder="请选择租客" filterable style="width: 100%">
            <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="姓名" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="部门"><el-input v-model="form.dept" /></el-form-item>
        <el-form-item label="岗位"><el-input v-model="form.post" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="门禁权限"><el-input v-model="form.accessPerm" /></el-form-item>
        <el-form-item label="车牌"><el-input v-model="form.plateNo" /></el-form-item>
        <el-form-item label="有效期">
          <el-date-picker v-model="form.validEnd" type="date" value-format="YYYY-MM-DD"
                          placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="在职状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">在职</el-radio>
            <el-radio :value="0">离职</el-radio>
          </el-radio-group>
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
import { staffApi, tenantApi } from '@/api/tenant'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const tenants = ref([])
const query = reactive({ pageNo: 1, pageSize: 10, tenantRefId: null, name: '', status: null })

async function loadTenants() {
  tenants.value = await tenantApi.list()
}
async function load() {
  loading.value = true
  try {
    const res = await staffApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, tenantRefId: null, name: '', status: null })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const defaultForm = () => ({
  id: null, tenantRefId: null, name: '', dept: '', post: '', phone: '',
  accessPerm: '', plateNo: '', validEnd: null, status: 1
})
const form = reactive(defaultForm())
const rules = {
  tenantRefId: [{ required: true, message: '请选择所属租客', trigger: 'change' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑员工' : '新增员工'
  Object.assign(form, defaultForm())
  if (row) Object.assign(form, row)
  else if (query.tenantRefId) form.tenantRefId = query.tenantRefId
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await staffApi.update(form)
  else await staffApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await staffApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(() => {
  loadTenants()
  load()
})
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
