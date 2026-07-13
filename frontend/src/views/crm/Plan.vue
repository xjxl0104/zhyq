<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="标题">
          <el-input v-model="query.title" placeholder="请输入标题" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="周期">
          <el-input v-model="query.period" placeholder="如 2026-Q3" clearable style="width: 140px" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增计划</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="owner" label="负责人" width="110" />
        <el-table-column prop="period" label="周期" width="110" />
        <el-table-column label="目标额" width="140" align="right">
          <template #default="{ row }">¥{{ fmtMoney(row.targetAmount) }}</template>
        </el-table-column>
        <el-table-column label="已达成" width="140" align="right">
          <template #default="{ row }">¥{{ fmtMoney(row.achievedAmount) }}</template>
        </el-table-column>
        <el-table-column label="完成率" min-width="180">
          <template #default="{ row }">
            <el-progress :percentage="rate(row)" :color="rateColor(row)" :stroke-width="14" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm v-if="row.status === 1" title="确认标记完成?" @confirm="finish(row.id)">
              <template #reference><el-button link type="success">标记完成</el-button></template>
            </el-popconfirm>
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
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.owner" /></el-form-item>
        <el-form-item label="周期"><el-input v-model="form.period" placeholder="如 2026-Q3" /></el-form-item>
        <el-form-item label="目标签约额">
          <el-input-number v-model="form.targetAmount" :min="0" :step="10000" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="已达成">
          <el-input-number v-model="form.achievedAmount" :min="0" :step="10000" :precision="2" style="width: 100%" />
        </el-form-item>
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
import { planApi } from '@/api/crm'

const statusOptions = [
  { value: 1, label: '进行中', type: 'primary' },
  { value: 2, label: '已完成', type: 'success' }
]
function statusText(v) {
  const s = statusOptions.find(o => o.value === v)
  return s ? s.label : '-'
}
function statusType(v) {
  const s = statusOptions.find(o => o.value === v)
  return s ? s.type : 'info'
}
const fmtMoney = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

function rate(row) {
  const target = Number(row.targetAmount || 0)
  if (target <= 0) return 0
  const r = (Number(row.achievedAmount || 0) / target) * 100
  return Math.min(Math.round(r * 10) / 10, 100)
}
function rateColor(row) {
  const target = Number(row.targetAmount || 0)
  const r = target <= 0 ? 0 : (Number(row.achievedAmount || 0) / target) * 100
  return r >= 100 ? '#16a34a' : '#4f46e5'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, title: '', period: '', status: null })

async function load() {
  loading.value = true
  try {
    const res = await planApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, title: '', period: '', status: null })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const emptyForm = () => ({ id: null, title: '', owner: '', period: '', targetAmount: 0, achievedAmount: 0, status: 1, remark: '' })
const form = reactive(emptyForm())
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑计划' : '新增计划'
  if (row) Object.assign(form, row)
  else Object.assign(form, emptyForm())
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await planApi.update(form)
  else await planApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await planApi.remove(id)
  ElMessage.success('删除成功')
  load()
}
async function finish(id) {
  await planApi.finish(id)
  ElMessage.success('已标记完成')
  load()
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
