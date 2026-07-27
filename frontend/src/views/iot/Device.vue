<template>
  <div class="page-container">
    <!-- 统计卡 -->
    <div class="stat-row">
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">设备总数</div>
        <div class="stat-value">{{ stats.total }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">在线</div>
        <div class="stat-value" style="color:#16a34a">{{ stats.online }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">离线</div>
        <div class="stat-value" style="color:#e5484d">{{ stats.offline }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">分类数</div>
        <div class="stat-value">{{ (stats.categoryList || []).length }}</div>
      </el-card>
    </div>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="设备编号">
          <el-input v-model="query.code" placeholder="请输入编号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.category" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增设备</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="code" label="设备编号" min-width="140" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="category" label="分类" min-width="100" />
        <el-table-column prop="vendor" label="厂商" min-width="120" />
        <el-table-column prop="location" label="位置" min-width="140" />
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
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="520px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="设备编号" prop="code">
          <el-input v-model="form.code" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="请选择" clearable style="width: 100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="厂商"><el-input v-model="form.vendor" /></el-form-item>
        <el-form-item label="位置"><el-input v-model="form.location" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="(label, val) in statusMap" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="附件">
          <FileUpload v-model="attachFiles" biz-type="device" :biz-id="form.id" />
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
import { deviceApi } from '@/api/iot'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'

const categories = ['门禁', '门锁', '停车', '充电桩', '摄像头', '传感器', '空开', '消防']
// 0未激活 1在线 2离线 3故障 4维护 5停用
const statusMap = { 0: '未激活', 1: '在线', 2: '离线', 3: '故障', 4: '维护', 5: '停用' }

function statusTagType(status) {
  const map = { 0: 'info', 1: 'success', 2: 'info', 3: 'danger', 4: 'warning', 5: 'info' }
  return map[status] || 'info'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, code: '', category: null, status: null })
const stats = reactive({ total: 0, online: 0, offline: 0, categoryList: [] })

async function loadStats() {
  const res = await deviceApi.stats()
  Object.assign(stats, res)
}
async function load() {
  loading.value = true
  try {
    const res = await deviceApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, code: '', category: null, status: null })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, code: '', name: '', category: null, vendor: '', location: '', status: 1 })
const attachFiles = ref([])
const rules = {
  code: [{ required: true, message: '请输入设备编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

async function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑设备' : '新增设备'
  attachFiles.value = []
  if (row) {
    Object.assign(form, row)
    try { attachFiles.value = await fileApi.list('device', row.id) } catch (e) { /* 忽略 */ }
  } else {
    Object.assign(form, { id: null, code: '', name: '', category: null, vendor: '', location: '', status: 1 })
  }
}
async function submit() {
  await formRef.value.validate()
  let deviceId = form.id
  if (form.id) await deviceApi.update(form)
  else deviceId = await deviceApi.add(form)
  const pendingIds = (attachFiles.value || []).filter(f => f && f.id && !f.bizId).map(f => f.id)
  if (deviceId && pendingIds.length) {
    try { await fileApi.attach('device', deviceId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
  loadStats()
}
async function remove(id) {
  await deviceApi.remove(id)
  ElMessage.success('删除成功')
  load()
  loadStats()
}

onMounted(() => {
  load()
  loadStats()
})
</script>

<style scoped>
.stat-row { display: flex; gap: 16px; margin-bottom: 16px; }
.stat-card { flex: 1; }
.stat-label { color: var(--text-secondary); font-size: 13px; }
.stat-value { font-size: 26px; font-weight: 600; margin-top: 8px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
</style>
