<template>
  <div class="page-container">
    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增模板</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="服务类型" width="100"><template #default="{ row }">{{ SVC[row.serviceType] }}</template></el-table-column>
        <el-table-column prop="tplVersion" label="版本" width="70" align="center" />
        <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="160" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除?" @confirm="remove(row.id)"><template #reference><el-button link type="danger">删除</el-button></template></el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next" :total="total" v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" @change="load" />
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="720px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="服务类型">
          <el-select v-model="form.serviceType"><el-option v-for="(t, v) in SVC" :key="v" :label="t" :value="Number(v)" /></el-select>
        </el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="变量"><el-input v-model="form.variables" placeholder='JSON,如 ["customerName","startDate","priceTable"]' /></el-form-item>
        <el-form-item label="正文"><el-input v-model="form.body" type="textarea" :rows="12" placeholder="支持 {{customerName}} 形式占位符" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog.visible = false">取消</el-button><el-button type="primary" @click="submit">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { mktTemplateApi } from '@/api/marketing'

const SVC = { 1: '仓储', 2: '代发', 3: '仓配' }
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10 })

async function load() {
  loading.value = true
  try { const res = await mktTemplateApi.page(query); list.value = res.records; total.value = res.total } finally { loading.value = false }
}
const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const emptyForm = () => ({ name: '', serviceType: 2, status: 1, variables: '[]', body: '' })
const form = reactive(emptyForm())
const rules = { name: [{ required: true, message: '请输入名称', trigger: 'blur' }] }
function openDialog(row) { dialog.visible = true; dialog.title = row ? '编辑模板' : '新增模板'; Object.assign(form, row ? { ...row } : emptyForm()) }
async function submit() {
  await formRef.value.validate()
  if (form.id) await mktTemplateApi.update(form); else await mktTemplateApi.add(form)
  ElMessage.success('已保存'); dialog.visible = false; load()
}
async function remove(id) { await mktTemplateApi.remove(id); ElMessage.success('已删除'); load() }
onMounted(load)
</script>

<style scoped>.pager { margin-top: 16px; justify-content: flex-end; }</style>
