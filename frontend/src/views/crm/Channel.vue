<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="名称">
          <el-input v-model="query.name" placeholder="请输入渠道名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="query.contact" placeholder="请输入联系人" clearable style="width: 160px" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增渠道</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="name" label="渠道名称" min-width="160" />
        <el-table-column prop="contact" label="联系人" min-width="100" />
        <el-table-column prop="phone" label="电话" min-width="130" />
        <el-table-column label="佣金比例" width="110">
          <template #default="{ row }">{{ row.commissionRate }}%</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170" />
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
        <el-form-item label="渠道名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="佣金比例">
          <el-input-number v-model="form.commissionRate" :min="0" :max="100" :precision="2" :step="1" />
          <span class="unit">%</span>
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
import { channelApi } from '@/api/crm'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, name: '', contact: '', status: null })

async function load() {
  loading.value = true
  try {
    const res = await channelApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, name: '', contact: '', status: null })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const emptyForm = () => ({ id: null, name: '', contact: '', phone: '', commissionRate: 0, status: 1, remark: '' })
const form = reactive(emptyForm())
const rules = {
  name: [{ required: true, message: '请输入渠道名称', trigger: 'blur' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑渠道' : '新增渠道'
  if (row) Object.assign(form, row)
  else Object.assign(form, emptyForm())
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await channelApi.update(form)
  else await channelApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await channelApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.unit { margin-left: 8px; color: #909399; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
