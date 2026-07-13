<template>
  <div class="page-container">
    <!-- 统计卡 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never"><div class="stat"><div class="stat-num">{{ stats.total || 0 }}</div><div class="stat-label">总数</div></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never"><div class="stat"><div class="stat-num">{{ typeCount('专利') }}</div><div class="stat-label">专利</div></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never"><div class="stat"><div class="stat-num">{{ typeCount('商标') }}</div><div class="stat-label">商标</div></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never"><div class="stat"><div class="stat-num">{{ typeCount('软著') }}</div><div class="stat-label">软著</div></div></el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never"><div class="stat"><div class="stat-num">{{ statusCount('已授权') }}</div><div class="stat-label">已授权</div></div></el-card>
      </el-col>
    </el-row>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="名称">
          <el-input v-model="query.title" placeholder="知产名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.ipType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="t in ipTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增知产</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="title" label="名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }"><el-tag type="info">{{ row.ipType }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="agency" label="代理机构" min-width="140" />
        <el-table-column prop="applyDate" label="申请日期" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusMap[row.status] || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
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
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
        <el-form-item label="名称" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.ipType" style="width: 100%">
            <el-option v-for="t in ipTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="申报企业ID"><el-input-number v-model="form.tenantRefId" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="代理机构"><el-input v-model="form.agency" /></el-form-item>
        <el-form-item label="申请日期">
          <el-date-picker v-model="form.applyDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="(label, val) in statusMap" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ipApi } from '@/api/service'

// 1申请中 2已受理 3已授权 4已驳回
const statusMap = { 1: '申请中', 2: '已受理', 3: '已授权', 4: '已驳回' }
const ipTypeOptions = ['专利', '商标', '版权', '软著']

function statusTagType(status) {
  const map = { 1: 'warning', 2: 'primary', 3: 'success', 4: 'danger' }
  return map[status] || 'info'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, title: '', ipType: null, status: null })
const stats = reactive({ total: 0, byType: [], byStatus: [] })

function typeCount(type) {
  const item = stats.byType.find(i => i.ipType === type)
  return item ? item.count : 0
}
function statusCount(label) {
  const item = stats.byStatus.find(i => i.status === label)
  return item ? item.count : 0
}

async function loadStats() {
  const res = await ipApi.stats()
  stats.total = res.total
  stats.byType = res.byType || []
  stats.byStatus = res.byStatus || []
}

async function load() {
  loading.value = true
  try {
    const res = await ipApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, title: '', ipType: null, status: null })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const defaultForm = () => ({ id: null, title: '', ipType: '专利', tenantRefId: null, agency: '', applyDate: null, status: 1 })
const form = reactive(defaultForm())
const rules = {
  title: [{ required: true, message: '请输入知产名称', trigger: 'blur' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑知产' : '新增知产'
  if (row) Object.assign(form, row)
  else Object.assign(form, defaultForm())
}
async function submitForm() {
  await formRef.value.validate()
  if (form.id) await ipApi.update(form)
  else await ipApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
  loadStats()
}
async function remove(id) {
  await ipApi.remove(id)
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
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
.stat-row { margin-bottom: 16px; }
.stat { text-align: center; padding: 8px 0; }
.stat-num { font-size: 28px; font-weight: 600; color: var(--el-color-primary); }
.stat-label { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 13px; }
</style>
