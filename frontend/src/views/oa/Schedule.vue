<template>
  <div class="page-container">
    <!-- 本周日程时间线 -->
    <div class="table-card week-card">
      <div class="toolbar">
        <span class="section-title">本周日程</span>
      </div>
      <el-timeline v-if="weekList.length">
        <el-timeline-item v-for="item in weekList" :key="item.id"
                          :type="timelineType(item.stype)" :timestamp="item.startTime" placement="top">
          <div class="week-item">
            <span style="font-weight: 600">{{ item.title }}</span>
            <el-tag size="small" :type="typeTagType(item.stype)" style="margin-left: 8px">{{ item.stype }}</el-tag>
            <el-icon v-if="item.remind === 1" style="margin-left: 6px; color: #ea9a13"><Bell /></el-icon>
            <div class="week-meta">
              <span v-if="item.owner">负责人:{{ item.owner }}</span>
              <span v-if="item.location">地点:{{ item.location }}</span>
              <span>{{ item.startTime }} ~ {{ item.endTime }}</span>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="未来7天暂无日程" :image-size="80" />
    </div>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="标题">
          <el-input v-model="query.title" placeholder="请输入标题" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.stype" placeholder="全部" clearable style="width: 120px">
            <el-option label="会议" value="会议" />
            <el-option label="拜访" value="拜访" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="query.owner" placeholder="请输入负责人" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 全部日程表格 -->
    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增日程</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="typeTagType(row.stype)">{{ row.stype }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="owner" label="负责人" width="100" />
        <el-table-column prop="location" label="地点" min-width="120" show-overflow-tooltip />
        <el-table-column prop="startTime" label="开始时间" width="170" />
        <el-table-column prop="endTime" label="结束时间" width="170" />
        <el-table-column label="提醒" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.remind === 1" disabled />
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
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="560px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.stype" style="width: 100%">
            <el-option label="会议" value="会议" />
            <el-option label="拜访" value="拜访" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.owner" /></el-form-item>
        <el-form-item label="地点"><el-input v-model="form.location" /></el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="提醒">
          <el-switch v-model="form.remind" :active-value="1" :inactive-value="0" />
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
import { scheduleApi } from '@/api/oa'

function typeTagType(stype) {
  if (stype === '会议') return 'primary'
  if (stype === '拜访') return 'success'
  return 'info'
}
function timelineType(stype) {
  if (stype === '会议') return 'primary'
  if (stype === '拜访') return 'success'
  return 'info'
}

const weekList = ref([])
async function loadWeek() {
  weekList.value = await scheduleApi.week()
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, title: '', stype: null, owner: '' })

async function load() {
  loading.value = true
  try {
    const res = await scheduleApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, title: '', stype: null, owner: '' })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const defaultForm = () => ({ id: null, title: '', stype: '会议', owner: '', location: '', startTime: null, endTime: null, remind: 0, remark: '' })
const form = reactive(defaultForm())
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑日程' : '新增日程'
  if (row) Object.assign(form, row)
  else Object.assign(form, defaultForm())
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await scheduleApi.update(form)
  else await scheduleApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  refresh()
}
async function remove(id) {
  await scheduleApi.remove(id)
  ElMessage.success('删除成功')
  refresh()
}

async function refresh() {
  await Promise.all([load(), loadWeek()])
}

onMounted(refresh)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
.week-card { margin-bottom: 16px; padding: 20px 22px 4px; }
.section-title { font-size: 15px; font-weight: 600; color: var(--text-title); }
.week-item { line-height: 1.6; }
.week-meta { color: var(--text-secondary); font-size: 13px; margin-top: 4px; display: flex; gap: 16px; }
</style>
