<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(label, val) in statusMap" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="施工单位">
          <el-input v-model="query.contractor" placeholder="请输入施工单位" clearable style="width: 180px" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增装修申请</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="tenantRefId" label="申请租客ID" width="100" />
        <el-table-column prop="roomId" label="房间ID" width="90" />
        <el-table-column prop="contractor" label="施工单位" min-width="140" />
        <el-table-column prop="contact" label="联系人" width="100" />
        <el-table-column prop="startDate" label="开始日期" width="120" />
        <el-table-column prop="endDate" label="结束日期" width="120" />
        <el-table-column prop="scope" label="装修范围" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusMap[row.status] || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" link type="success" @click="approve(row.id)">通过</el-button>
            <el-button v-if="row.status === 1" link type="danger" @click="reject(row.id)">驳回</el-button>
            <el-button v-if="row.status === 2" link type="success" @click="finish(row.id)">完工</el-button>
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
        <el-form-item label="申请租客" prop="tenantRefId">
          <el-select v-model="form.tenantRefId" placeholder="选择租客" filterable style="width: 100%">
            <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="房间ID"><el-input-number v-model="form.roomId" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="施工单位" prop="contractor"><el-input v-model="form.contractor" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="装修范围"><el-input v-model="form.scope" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="附件">
          <FileUpload v-model="attachFiles" biz-type="decoration" :biz-id="form.id" />
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
import { decorationApi } from '@/api/service'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'
import { tenantApi } from '@/api/tenant'

// 1待审批 2施工中 3已完工 4已驳回
const statusMap = { 1: '待审批', 2: '施工中', 3: '已完工', 4: '已驳回' }

function statusTagType(status) {
  const map = { 1: 'warning', 2: 'primary', 3: 'success', 4: 'danger' }
  return map[status] || 'info'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, status: null, contractor: '' })

const tenants = ref([])
async function loadTenants() {
  try {
    const res = await tenantApi.list()
    tenants.value = res || []
  } catch (e) { /* 下拉数据失败不阻塞列表 */ }
}

async function load() {
  loading.value = true
  try {
    const res = await decorationApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, status: null, contractor: '' })
  load()
}

async function approve(id) {
  await decorationApi.approve(id)
  ElMessage.success('已通过')
  load()
}
async function reject(id) {
  await decorationApi.reject(id)
  ElMessage.success('已驳回')
  load()
}
async function finish(id) {
  await decorationApi.finish(id)
  ElMessage.success('已完工')
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const defaultForm = () => ({ id: null, tenantRefId: null, roomId: null, contractor: '', contact: '', phone: '', startDate: null, endDate: null, scope: '' })
const form = reactive(defaultForm())
const attachFiles = ref([])
const rules = {
  tenantRefId: [{ required: true, message: '请选择申请租客', trigger: 'change' }],
  contractor: [{ required: true, message: '请输入施工单位', trigger: 'blur' }]
}

async function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑装修申请' : '新增装修申请'
  attachFiles.value = []
  if (row) {
    Object.assign(form, row)
    try { attachFiles.value = await fileApi.list('decoration', row.id) } catch (e) { /* 忽略 */ }
  } else {
    Object.assign(form, defaultForm())
  }
}
async function submit() {
  await formRef.value.validate()
  let decorationId = form.id
  if (form.id) await decorationApi.update(form)
  else decorationId = await decorationApi.add(form)
  const pendingIds = (attachFiles.value || []).filter(f => f && f.id && !f.bizId).map(f => f.id)
  if (decorationId && pendingIds.length) {
    try { await fileApi.attach('decoration', decorationId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await decorationApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(async () => {
  await loadTenants()
  load()
})
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
</style>
