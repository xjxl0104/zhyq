<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="表计编号">
          <el-input v-model="query.code" placeholder="请输入编号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="能源类型">
          <el-select v-model="query.energyType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="t in energyTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="在用" :value="1" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增表计</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="code" label="表计编号" min-width="140" />
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column label="能源类型" width="110">
          <template #default="{ row }">
            <el-tag :type="energyTagType(row.energyType)">{{ row.energyType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ratio" label="倍率" width="90" />
        <el-table-column prop="lastReading" label="上次读数" width="120" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '在用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openReadings(row)">抄表记录</el-button>
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

    <!-- 表计表单弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="520px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="表计编号" prop="code">
          <el-input v-model="form.code" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="能源类型" prop="energyType">
          <el-select v-model="form.energyType" placeholder="请选择" style="width: 100%">
            <el-option v-for="t in energyTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="倍率"><el-input-number v-model="form.ratio" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="上次读数"><el-input-number v-model="form.lastReading" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">在用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 抄表记录抽屉 -->
    <el-drawer v-model="readingDrawer.visible" :title="readingDrawer.title" size="60%">
      <div class="toolbar">
        <el-button type="primary" @click="openReadingDialog()"><el-icon><Plus /></el-icon>新增抄表</el-button>
      </div>
      <el-table :data="readingList" v-loading="readingLoading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="prevReading" label="上次读数" min-width="110" />
        <el-table-column prop="currReading" label="本次读数" min-width="110" />
        <el-table-column prop="usageAmount" label="用量" min-width="100" />
        <el-table-column prop="readSource" label="抄表方式" min-width="110" />
        <el-table-column prop="readTime" label="抄表时间" width="170" />
        <el-table-column prop="fee" label="费用" width="100" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="确认删除?" @confirm="removeReading(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next"
                     :total="readingTotal" v-model:current-page="readingQuery.pageNo"
                     v-model:page-size="readingQuery.pageSize" @change="loadReadings" />
    </el-drawer>

    <!-- 抄表表单弹窗 -->
    <el-dialog v-model="readingDialog.visible" title="新增抄表" width="480px">
      <el-form :model="readingForm" label-width="90px" ref="readingFormRef">
        <el-form-item label="上次读数">
          <el-input-number v-model="readingForm.prevReading" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="本次读数">
          <el-input-number v-model="readingForm.currReading" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="抄表方式">
          <el-select v-model="readingForm.readSource" style="width: 100%">
            <el-option v-for="s in readSources" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="抄表时间">
          <el-date-picker v-model="readingForm.readTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="readingDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitReading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { meterApi, readingApi } from '@/api/energy'

const energyTypes = ['电', '水', '燃气', '热力']
const readSources = ['自动采集', '人工', '估算', '补录', '换表']

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, code: '', energyType: null, status: null })

function energyTagType(type) {
  const map = { '电': 'warning', '水': 'primary', '燃气': 'danger', '热力': 'success' }
  return map[type] || 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await meterApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, code: '', energyType: null, status: null })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, code: '', name: '', energyType: '电', ratio: 1, lastReading: 0, status: 1 })
const rules = {
  code: [{ required: true, message: '请输入表计编号', trigger: 'blur' }],
  energyType: [{ required: true, message: '请选择能源类型', trigger: 'change' }]
}

function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑表计' : '新增表计'
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: null, code: '', name: '', energyType: '电', ratio: 1, lastReading: 0, status: 1 })
}
async function submit() {
  await formRef.value.validate()
  if (form.id) await meterApi.update(form)
  else await meterApi.add(form)
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await meterApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

// 抄表记录抽屉
const readingDrawer = reactive({ visible: false, title: '', meter: null })
const readingLoading = ref(false)
const readingList = ref([])
const readingTotal = ref(0)
const readingQuery = reactive({ pageNo: 1, pageSize: 10, meterId: null })

function openReadings(row) {
  readingDrawer.visible = true
  readingDrawer.title = `抄表记录 - ${row.code}`
  readingDrawer.meter = row
  readingQuery.pageNo = 1
  readingQuery.meterId = row.id
  loadReadings()
}
async function loadReadings() {
  readingLoading.value = true
  try {
    const res = await readingApi.page(readingQuery)
    readingList.value = res.records
    readingTotal.value = res.total
  } finally {
    readingLoading.value = false
  }
}
async function removeReading(id) {
  await readingApi.remove(id)
  ElMessage.success('删除成功')
  loadReadings()
}

const readingFormRef = ref()
const readingDialog = reactive({ visible: false })
const readingForm = reactive({ meterId: null, prevReading: 0, currReading: 0, readSource: '人工', readTime: null })

function openReadingDialog() {
  readingDialog.visible = true
  Object.assign(readingForm, {
    meterId: readingDrawer.meter?.id,
    prevReading: readingDrawer.meter?.lastReading || 0,
    currReading: 0,
    readSource: '人工',
    readTime: null
  })
}
async function submitReading() {
  await readingApi.add(readingForm)
  ElMessage.success('保存成功')
  readingDialog.visible = false
  loadReadings()
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
</style>
