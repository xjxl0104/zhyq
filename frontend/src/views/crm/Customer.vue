<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="名称">
          <el-input v-model="query.name" placeholder="请输入客户名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="意向等级">
          <el-select v-model="query.intentLevel" placeholder="全部" clearable style="width: 120px">
            <el-option label="A" value="A" />
            <el-option label="B" value="B" />
            <el-option label="C" value="C" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增客户</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="contact" label="联系人" width="100" />
        <el-table-column prop="phone" label="电话" min-width="130" />
        <el-table-column prop="industry" label="行业" width="110" />
        <el-table-column prop="demandArea" label="需求面积" width="110" />
        <el-table-column label="意向等级" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="levelType(row.intentLevel)">{{ row.intentLevel || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="owner" label="负责人" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceLeadId" label="来源线索ID" width="110" align="center" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <template v-if="row.status === 1">
              <el-popconfirm title="确认签约?" @confirm="sign(row.id)">
                <template #reference><el-button link type="success">签约</el-button></template>
              </el-popconfirm>
              <el-popconfirm title="确认标记流失?" @confirm="lose(row.id)">
                <template #reference><el-button link type="info">流失</el-button></template>
              </el-popconfirm>
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

    <!-- 表单弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="560px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="行业"><el-input v-model="form.industry" /></el-form-item>
        <el-form-item label="需求面积"><el-input v-model="form.demandArea" /></el-form-item>
        <el-form-item label="意向等级">
          <el-select v-model="form.intentLevel" placeholder="请选择" style="width: 100%">
            <el-option label="A" value="A" />
            <el-option label="B" value="B" />
            <el-option label="C" value="C" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.owner" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" placeholder="请选择" style="width: 100%">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
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
import { customerApi } from '@/api/crm'

const statusOptions = [
  { value: 1, label: '跟进中', type: 'primary' },
  { value: 2, label: '已签约', type: 'success' },
  { value: 3, label: '已流失', type: 'info' }
]
function statusText(v) {
  const s = statusOptions.find(o => o.value === v)
  return s ? s.label : '-'
}
function statusType(v) {
  const s = statusOptions.find(o => o.value === v)
  return s ? s.type : 'info'
}
function levelType(v) {
  if (v === 'A') return 'danger'
  if (v === 'B') return 'warning'
  if (v === 'C') return 'info'
  return 'info'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, name: '', intentLevel: null, status: null, owner: '' })

async function load() {
  loading.value = true
  try {
    const res = await customerApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, name: '', intentLevel: null, status: null, owner: '' })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const emptyForm = () => ({ id: null, name: '', contact: '', phone: '', industry: '', demandArea: '', intentLevel: 'B', owner: '', status: 1, remark: '' })
const form = reactive(emptyForm())
const rules = {
  name: [{ required: true, message: '请输入客户名称', trigger: 'blur' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑客户' : '新增客户'
  if (row) Object.assign(form, row)
  else Object.assign(form, emptyForm())
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await customerApi.update(form)
  else await customerApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await customerApi.remove(id)
  ElMessage.success('删除成功')
  load()
}
async function sign(id) {
  await customerApi.sign(id)
  ElMessage.success('已签约')
  load()
}
async function lose(id) {
  await customerApi.lose(id)
  ElMessage.success('已标记流失')
  load()
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
