<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="物品">
          <el-input v-model="query.item" placeholder="请输入物品" clearable style="width: 180px" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增放行单</el-button>
        <el-button @click="expireCheck"><el-icon><Refresh /></el-icon>失效检查</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="passNo" label="放行单号" min-width="130" />
        <el-table-column prop="item" label="物品" min-width="160" show-overflow-tooltip />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column prop="carrier" label="携带人" min-width="110" />
        <el-table-column prop="plateNo" label="车牌" min-width="110" />
        <el-table-column prop="authorizer" label="授权人" min-width="100" />
        <el-table-column prop="validUntil" label="有效期" width="170" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusMap[row.status] || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-popconfirm v-if="row.status === 1" title="确认放行?" @confirm="verify(row.id)">
              <template #reference><el-button link type="success">放行</el-button></template>
            </el-popconfirm>
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
        <el-form-item label="放行单号" prop="passNo"><el-input v-model="form.passNo" /></el-form-item>
        <el-form-item label="物品" prop="item"><el-input v-model="form.item" /></el-form-item>
        <el-form-item label="数量"><el-input-number v-model="form.qty" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="携带人"><el-input v-model="form.carrier" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="车牌"><el-input v-model="form.plateNo" /></el-form-item>
        <el-form-item label="授权人"><el-input v-model="form.authorizer" /></el-form-item>
        <el-form-item label="有效期">
          <el-date-picker v-model="form.validUntil" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="(label, val) in statusMap" :key="val" :label="label" :value="Number(val)" />
          </el-select>
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
import { passApi } from '@/api/service'

// 1待核验 2已放行 3已失效
const statusMap = { 1: '待核验', 2: '已放行', 3: '已失效' }

function statusTagType(status) {
  const map = { 1: 'warning', 2: 'success', 3: 'info' }
  return map[status] || 'info'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, item: '', status: null })

async function load() {
  loading.value = true
  try {
    const res = await passApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, item: '', status: null })
  load()
}

async function verify(id) {
  await passApi.verify(id)
  ElMessage.success('已放行')
  load()
}
async function expireCheck() {
  const count = await passApi.expireCheck()
  ElMessage.success(`失效检查完成,处理 ${count} 条`)
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const defaultForm = () => ({ id: null, passNo: '', item: '', qty: 1, carrier: '', phone: '', plateNo: '', authorizer: '', validUntil: null, status: 1 })
const form = reactive(defaultForm())
const rules = {
  passNo: [{ required: true, message: '请输入放行单号', trigger: 'blur' }],
  item: [{ required: true, message: '请输入物品', trigger: 'blur' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑放行单' : '新增放行单'
  if (row) Object.assign(form, row)
  else Object.assign(form, defaultForm())
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await passApi.update(form)
  else await passApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await passApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
</style>
