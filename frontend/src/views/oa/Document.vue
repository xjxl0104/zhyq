<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="标题">
          <el-input v-model="query.title" placeholder="请输入标题" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="收发文">
          <el-select v-model="query.docType" placeholder="全部" clearable style="width: 120px">
            <el-option label="收文" value="收文" />
            <el-option label="发文" value="发文" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="拟稿" :value="1" />
            <el-option label="核稿" :value="2" />
            <el-option label="签发" :value="3" />
            <el-option label="归档" :value="4" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增公文</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="docNo" label="文号" width="160" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="收发文" width="90">
          <template #default="{ row }">
            <el-tag :type="row.docType === '收文' ? 'warning' : 'primary'">{{ row.docType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fromUnit" label="来文单位" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="signBy" label="签发人" width="100" />
        <el-table-column prop="signTime" label="签发时间" width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openView(row)">查看</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm v-if="row.status === 1" title="确认提交核稿?" @confirm="doAction(row, 'review')">
              <template #reference><el-button link type="warning">核稿</el-button></template>
            </el-popconfirm>
            <el-popconfirm v-if="row.status === 2" title="确认签发?" @confirm="doAction(row, 'sign')">
              <template #reference><el-button link type="success">签发</el-button></template>
            </el-popconfirm>
            <el-popconfirm v-if="row.status === 3" title="确认归档?" @confirm="doAction(row, 'archive')">
              <template #reference><el-button link type="info">归档</el-button></template>
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
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="600px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="文号" prop="docNo"><el-input v-model="form.docNo" /></el-form-item>
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="收发文" prop="docType">
          <el-radio-group v-model="form.docType">
            <el-radio value="收文">收文</el-radio>
            <el-radio value="发文">发文</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="来文单位"><el-input v-model="form.fromUnit" placeholder="来文单位/拟稿部门" /></el-form-item>
        <el-form-item label="正文"><el-input v-model="form.content" type="textarea" :rows="6" /></el-form-item>
        <el-form-item label="附件">
          <FileUpload v-model="attachFiles" biz-type="oa_document" :biz-id="form.id" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 查看抽屉 -->
    <el-drawer v-model="viewer.visible" title="公文详情" size="480px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="文号">{{ viewer.row.docNo }}</el-descriptions-item>
        <el-descriptions-item label="标题">{{ viewer.row.title }}</el-descriptions-item>
        <el-descriptions-item label="收发文">{{ viewer.row.docType }}</el-descriptions-item>
        <el-descriptions-item label="来文单位">{{ viewer.row.fromUnit }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(viewer.row.status) }}</el-descriptions-item>
        <el-descriptions-item label="签发人">{{ viewer.row.signBy }}</el-descriptions-item>
        <el-descriptions-item label="签发时间">{{ viewer.row.signTime }}</el-descriptions-item>
      </el-descriptions>
      <div class="content-block">
        <div class="content-label">正文</div>
        <div class="content-body">{{ viewer.row.content }}</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { documentApi } from '@/api/oa'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'

// 状态:1拟稿 2核稿 3签发 4归档
const statusOptions = [
  { value: 1, label: '拟稿', type: 'info' },
  { value: 2, label: '核稿', type: 'warning' },
  { value: 3, label: '签发', type: 'success' },
  { value: 4, label: '归档', type: 'info' }
]
function statusText(v) {
  const s = statusOptions.find(o => o.value === v)
  return s ? s.label : '-'
}
function statusType(v) {
  const s = statusOptions.find(o => o.value === v)
  return s ? s.type : 'info'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, title: '', docType: null, status: null })

async function load() {
  loading.value = true
  try {
    const res = await documentApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, title: '', docType: null, status: null })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({ id: null, docNo: '', title: '', docType: '发文', fromUnit: '', content: '' })
const attachFiles = ref([])
const rules = {
  docNo: [{ required: true, message: '请输入文号', trigger: 'blur' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  docType: [{ required: true, message: '请选择收发文', trigger: 'change' }]
}

async function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑公文' : '新增公文'
  attachFiles.value = []
  if (row) {
    Object.assign(form, row)
    try { attachFiles.value = await fileApi.list('oa_document', row.id) } catch (e) { /* 忽略 */ }
  } else {
    Object.assign(form, { id: null, docNo: '', title: '', docType: '发文', fromUnit: '', content: '' })
  }
}
async function submit() {
  await formRef.value.validate()
  let newId = form.id
  if (form.id) await documentApi.update(form)
  else newId = await documentApi.add(form)
  const pendingIds = (attachFiles.value || []).filter(f => f && f.id && !f.bizId).map(f => f.id)
  if (newId && pendingIds.length) {
    try { await fileApi.attach('oa_document', newId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await documentApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

const actionMap = { review: documentApi.review, sign: documentApi.sign, archive: documentApi.archive }
const actionMsg = { review: '已提交核稿', sign: '已签发', archive: '已归档' }
async function doAction(row, action) {
  await actionMap[action](row.id)
  ElMessage.success(actionMsg[action])
  load()
}

const viewer = reactive({ visible: false, row: {} })
function openView(row) {
  viewer.row = row
  viewer.visible = true
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.content-block { margin-top: 16px; }
.content-label { color: var(--text-secondary); font-size: 13px; margin-bottom: 8px; }
.content-body { white-space: pre-wrap; line-height: 1.6; color: #303133; }
</style>
