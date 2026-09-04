<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="所属设备">
          <el-select v-model="query.deviceId" placeholder="全部设备" clearable filterable style="width: 200px">
            <el-option v-for="d in devices" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增通道</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column label="所属设备" min-width="150">
          <template #default="{ row }">{{ deviceName(row.deviceId) }}</template>
        </el-table-column>
        <el-table-column prop="channelNo" label="通道号" min-width="90" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusMap[row.status] || '未知' }}</el-tag>
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
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="480px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="所属设备" prop="deviceId">
          <el-select v-model="form.deviceId" placeholder="请选择设备" filterable style="width: 100%">
            <el-option v-for="d in devices" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="通道号" prop="channelNo">
          <el-input-number v-model="form.channelNo" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
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
import { channelApi } from '@/api/iot'
import { deviceApi } from '@/api/iot'

// 1在线 0离线
const statusMap = { 1: '在线', 0: '离线' }
function statusTagType(status) {
  return status === 1 ? 'success' : 'info'
}

const devices = ref([])
function deviceName(id) {
  const d = devices.value.find(item => item.id === id)
  return d ? d.name : id
}
async function loadDevices() {
  devices.value = await deviceApi.list()
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, deviceId: null, status: null })

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
  Object.assign(query, { pageNo: 1, deviceId: null, status: null })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, deviceId: null, channelNo: 1, name: '', status: 1 })
const rules = {
  deviceId: [{ required: true, message: '请选择所属设备', trigger: 'change' }],
  channelNo: [{ required: true, message: '请输入通道号', trigger: 'blur' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑通道' : '新增通道'
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: null, deviceId: null, channelNo: 1, name: '', status: 1 })
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

onMounted(() => {
  loadDevices()
  load()
})
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
</style>
