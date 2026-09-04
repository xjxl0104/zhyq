<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增预约</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="visitorName" label="访客姓名" min-width="120" />
        <el-table-column prop="phone" label="手机号" min-width="130" />
        <el-table-column prop="host" label="受访人" min-width="110" />
        <el-table-column prop="visitTime" label="来访时间" width="170" />
        <el-table-column prop="reason" label="事由" min-width="140" show-overflow-tooltip />
        <el-table-column prop="plateNo" label="车牌" min-width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusMap[row.status] || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" :disabled="row.status !== 1" @click="approve(row.id)">审批通过</el-button>
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
        <el-form-item label="访客姓名" prop="visitorName"><el-input v-model="form.visitorName" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="受访人"><el-input v-model="form.host" /></el-form-item>
        <el-form-item label="来访时间">
          <el-date-picker v-model="form.visitTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="事由"><el-input v-model="form.reason" /></el-form-item>
        <el-form-item label="车牌"><el-input v-model="form.plateNo" /></el-form-item>
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
import { visitorApi } from '@/api/service'

// 1待审批 2已通过 3已到场 4已离场 5已失效
const statusMap = { 1: '待审批', 2: '已通过', 3: '已到场', 4: '已离场', 5: '已失效' }

function statusTagType(status) {
  const map = { 1: 'warning', 2: 'primary', 3: 'success', 4: 'info', 5: 'danger' }
  return map[status] || 'info'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, status: null })

async function load() {
  loading.value = true
  try {
    const res = await visitorApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, status: null })
  load()
}

async function approve(id) {
  await visitorApi.approve(id)
  ElMessage.success('已通过')
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, visitorName: '', phone: '', host: '', visitTime: null, reason: '', plateNo: '', status: 1 })
const rules = {
  visitorName: [{ required: true, message: '请输入访客姓名', trigger: 'blur' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑预约' : '新增预约'
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: null, visitorName: '', phone: '', host: '', visitTime: null, reason: '', plateNo: '', status: 1 })
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await visitorApi.update(form)
  else await visitorApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await visitorApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
</style>
