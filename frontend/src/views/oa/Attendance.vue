<template>
  <div class="page-container">
    <el-alert type="info" :closable="false" show-icon class="tip-alert"
              title="考勤数据来自第三方平台同步,本页仅展示与人工补录" />

    <!-- 日期选择 -->
    <div class="search-bar">
      <el-form :inline="true">
        <el-form-item label="考勤日期">
          <el-date-picker v-model="curDate" type="date" value-format="YYYY-MM-DD"
                          :clearable="false" style="width: 160px" @change="onDateChange" />
        </el-form-item>
      </el-form>
    </div>

    <!-- 统计卡 -->
    <div class="stat-row">
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">正常</div>
        <div class="stat-value normal">{{ stats.normal }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">迟到</div>
        <div class="stat-value late">{{ stats.late }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">早退</div>
        <div class="stat-value early">{{ stats.early }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">缺勤</div>
        <div class="stat-value absent">{{ stats.absent }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">外勤</div>
        <div class="stat-value out">{{ stats.out }}</div>
      </el-card>
    </div>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="姓名">
          <el-input v-model="query.userName" placeholder="请输入姓名" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.attStatus" placeholder="全部" clearable style="width: 130px">
            <el-option label="正常" value="正常" />
            <el-option label="迟到" value="迟到" />
            <el-option label="早退" value="早退" />
            <el-option label="缺勤" value="缺勤" />
            <el-option label="外勤" value="外勤" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增补录</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="userName" label="姓名" min-width="110" />
        <el-table-column prop="attDate" label="日期" width="110" />
        <el-table-column prop="checkin" label="签到时间" width="170" />
        <el-table-column prop="checkout" label="签退时间" width="170" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.attStatus)">{{ row.attStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
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
        <el-form-item label="姓名" prop="userName"><el-input v-model="form.userName" /></el-form-item>
        <el-form-item label="日期" prop="attDate">
          <el-date-picker v-model="form.attDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="签到时间">
          <el-date-picker v-model="form.checkin" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="签退时间">
          <el-date-picker v-model="form.checkout" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="attStatus">
          <el-select v-model="form.attStatus" style="width: 100%">
            <el-option label="正常" value="正常" />
            <el-option label="迟到" value="迟到" />
            <el-option label="早退" value="早退" />
            <el-option label="缺勤" value="缺勤" />
            <el-option label="外勤" value="外勤" />
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
import { attendanceApi } from '@/api/oa'

function today() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

const curDate = ref(today())

const statusTagMap = { 正常: 'success', 迟到: 'warning', 早退: 'warning', 缺勤: 'danger', 外勤: 'primary' }
function statusTag(v) {
  return statusTagMap[v] || 'info'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, userName: '', attStatus: null, attDate: curDate.value })

const stats = reactive({ normal: 0, late: 0, early: 0, absent: 0, out: 0 })

async function loadStats() {
  const res = await attendanceApi.stats(curDate.value)
  Object.assign(stats, res)
}

async function load() {
  loading.value = true
  try {
    query.attDate = curDate.value
    const res = await attendanceApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function onDateChange() {
  query.pageNo = 1
  load()
  loadStats()
}
function reset() {
  Object.assign(query, { pageNo: 1, userName: '', attStatus: null })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, userName: '', attDate: '', checkin: '', checkout: '', attStatus: '正常', remark: '' })
const rules = {
  userName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  attDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  attStatus: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑考勤' : '新增补录'
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: null, userName: '', attDate: curDate.value, checkin: '', checkout: '', attStatus: '正常', remark: '' })
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await attendanceApi.update(form)
  else await attendanceApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
  loadStats()
}
async function remove(id) {
  await attendanceApi.remove(id)
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
.tip-alert { margin-bottom: 16px; }
.stat-row { display: flex; gap: 16px; margin-bottom: 16px; }
.stat-card { flex: 1; }
.stat-label { color: var(--text-secondary); font-size: 13px; }
.stat-value { font-size: 26px; font-weight: 600; margin-top: 8px; }
.stat-value.normal { color: #67c23a; }
.stat-value.late { color: #e6a23c; }
.stat-value.early { color: #e6a23c; }
.stat-value.absent { color: #f56c6c; }
.stat-value.out { color: #409eff; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
