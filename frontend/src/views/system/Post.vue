<template>
  <div class="page-container">
    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增岗位</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="name" label="岗位名称" min-width="140" />
        <el-table-column prop="code" label="岗位编码" min-width="140" />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除?" @confirm="remove(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" @change="load" />
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="480px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="岗位名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="岗位编码" prop="code"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio><el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
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
import { postApi } from '@/api/system'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10 })

async function load() {
  loading.value = true
  try { const res = await postApi.page(query); list.value = res.records; total.value = res.total }
  finally { loading.value = false }
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, name: '', code: '', sort: 0, status: 1, remark: '' })
const rules = {
  name: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入岗位编码', trigger: 'blur' }]
}
function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑岗位' : '新增岗位'
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: null, name: '', code: '', sort: 0, status: 1, remark: '' })
}
async function submit() {
  await formRef.value.validate()
  form.id ? await postApi.update(form) : await postApi.add(form)
  ElMessage.success('保存成功'); dialog.visible = false; load()
}
async function remove(id) { await postApi.remove(id); ElMessage.success('删除成功'); load() }
onMounted(load)
</script>
<style scoped>.pager { margin-top: 16px; justify-content: flex-end; }</style>
