<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="标题">
          <el-input v-model="query.title" placeholder="请输入标题" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.stype" placeholder="全部" clearable style="width: 120px">
            <el-option label="投票" value="投票" />
            <el-option label="问卷" value="问卷" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="Number(k)" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.stype === '投票' ? 'primary' : 'success'">{{ row.stype }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="votes" label="参与人数" width="100" />
        <el-table-column prop="deadline" label="截止时间" width="170" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="row.status === 1" link type="warning" @click="closeSurvey(row)">结束</el-button>
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
        <el-form-item label="类型" prop="stype">
          <el-radio-group v-model="form.stype">
            <el-radio value="投票">投票</el-radio>
            <el-radio value="问卷">问卷</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="form.deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          placeholder="选择截止时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="选项">
          <div class="option-list">
            <div v-for="(opt, idx) in optionList" :key="idx" class="option-item">
              <el-input v-model="opt.label" placeholder="选项名称" />
              <el-button link type="danger" @click="removeOption(idx)"><el-icon><Delete /></el-icon></el-button>
            </div>
            <el-button link type="primary" @click="addOption"><el-icon><Plus /></el-icon>添加选项</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialog.visible" title="投票问卷详情" width="560px">
      <div class="detail-title">{{ detailDialog.row?.title }}</div>
      <div v-for="(opt, idx) in detailOptions" :key="idx" class="detail-option">
        <div class="detail-option-head">
          <span>{{ opt.label }}</span>
          <span>{{ opt.votes || 0 }} 票</span>
        </div>
        <el-progress :percentage="optionPercent(opt)" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { surveyApi } from '@/api/property'

const statusMap = {
  1: { label: '进行中', type: 'primary' },
  2: { label: '已结束', type: 'info' }
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, title: '', stype: '', status: null })

async function load() {
  loading.value = true
  try {
    const res = await surveyApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, title: '', stype: '', status: null })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, title: '', stype: '投票', deadline: '' })
const optionList = ref([{ label: '', votes: 0 }])
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  stype: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

function addOption() {
  optionList.value.push({ label: '', votes: 0 })
}
function removeOption(idx) {
  optionList.value.splice(idx, 1)
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑' : '新增'
  if (row) {
    Object.assign(form, row)
    try {
      const opts = JSON.parse(row.options || '[]')
      optionList.value = opts.length ? opts : [{ label: '', votes: 0 }]
    } catch {
      optionList.value = [{ label: '', votes: 0 }]
    }
  } else {
    Object.assign(form, { id: null, title: '', stype: '投票', deadline: '' })
    optionList.value = [{ label: '', votes: 0 }]
  }
}
async function submit() {
  await formRef.value.validate()
  const options = optionList.value.filter(o => o.label && o.label.trim())
  const data = { ...form, options: JSON.stringify(options) }
  if (form.id) await surveyApi.update(data)
  else await surveyApi.add(data)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await surveyApi.remove(id)
  ElMessage.success('删除成功')
  load()
}
async function closeSurvey(row) {
  await surveyApi.close(row.id)
  ElMessage.success('已结束')
  load()
}

const detailDialog = reactive({ visible: false, row: null })
const detailOptions = ref([])
function openDetail(row) {
  detailDialog.visible = true
  detailDialog.row = row
  try {
    detailOptions.value = JSON.parse(row.options || '[]')
  } catch {
    detailOptions.value = []
  }
}
const totalVotes = computed(() => detailOptions.value.reduce((sum, o) => sum + (o.votes || 0), 0))
function optionPercent(opt) {
  const total = totalVotes.value
  if (!total) return 0
  return Math.round(((opt.votes || 0) / total) * 100)
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.option-list { width: 100%; }
.option-item { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.detail-title { font-size: 16px; font-weight: 600; margin-bottom: 16px; }
.detail-option { margin-bottom: 14px; }
.detail-option-head { display: flex; justify-content: space-between; margin-bottom: 6px; color: var(--text-secondary); }
</style>
