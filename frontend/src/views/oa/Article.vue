<template>
  <div class="page-container">
    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="标题">
          <el-input v-model="query.title" placeholder="请输入标题" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.category" placeholder="全部" clearable style="width: 130px">
            <el-option label="新闻" value="新闻" />
            <el-option label="通知" value="通知" />
            <el-option label="知识库" value="知识库" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" :value="1" />
            <el-option label="已发布" :value="2" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增文章</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="分类" width="100">
          <template #default="{ row }">
            <el-tag :type="categoryTagType(row.category)">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="author" label="作者" width="110" />
        <el-table-column prop="views" label="阅读量" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 2 ? 'success' : 'info'">
              {{ row.status === 2 ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPreview(row)">预览</el-button>
            <el-popconfirm v-if="row.status === 1" title="确认发布该文章?" @confirm="publish(row.id)">
              <template #reference><el-button link type="success">发布</el-button></template>
            </el-popconfirm>
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
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="620px">
      <el-form :model="form" label-width="90px" ref="formRef" :rules="rules">
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width: 100%">
            <el-option label="新闻" value="新闻" />
            <el-option label="通知" value="通知" />
            <el-option label="知识库" value="知识库" />
          </el-select>
        </el-form-item>
        <el-form-item label="作者"><el-input v-model="form.author" /></el-form-item>
        <el-form-item label="正文"><el-input v-model="form.content" type="textarea" :rows="6" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">草稿</el-radio>
            <el-radio :value="2">已发布</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="附件">
          <FileUpload v-model="attachFiles" biz-type="article" :biz-id="form.id" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 预览抽屉 -->
    <el-drawer v-model="preview.visible" title="文章预览" size="480px">
      <h2 style="margin-top: 0">{{ preview.row?.title }}</h2>
      <div class="preview-meta">
        <span>作者:{{ preview.row?.author || '-' }}</span>
        <span>阅读量:{{ preview.row?.views ?? 0 }}</span>
      </div>
      <el-divider />
      <div class="preview-content">{{ preview.row?.content }}</div>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { articleApi } from '@/api/oa'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'

function categoryTagType(category) {
  if (category === '新闻') return 'primary'
  if (category === '通知') return 'warning'
  return 'info'
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, title: '', category: null, status: null })

async function load() {
  loading.value = true
  try {
    const res = await articleApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, title: '', category: null, status: null })
  load()
}

async function publish(id) {
  await articleApi.publish(id)
  ElMessage.success('发布成功')
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const defaultForm = () => ({ id: null, title: '', category: '新闻', author: '', content: '', status: 1 })
const form = reactive(defaultForm())
const attachFiles = ref([])
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }]
}

async function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑文章' : '新增文章'
  if (row) Object.assign(form, row)
  else Object.assign(form, defaultForm())
  attachFiles.value = []
  if (row) {
    try { attachFiles.value = await fileApi.list('article', row.id) } catch (e) { /* 忽略 */ }
  }
}
async function submit() {
  await formRef.value.validate()
  let articleId = form.id
  if (form.id) await articleApi.update(form)
  else articleId = await articleApi.add(form)
  const pendingIds = (attachFiles.value || []).filter(f => f && f.id && !f.bizId).map(f => f.id)
  if (articleId && pendingIds.length) {
    try { await fileApi.attach('article', articleId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await articleApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

// 预览
const preview = reactive({ visible: false, row: null })
async function openPreview(row) {
  preview.row = row
  preview.visible = true
  await articleApi.view(row.id)
  load()
}

onMounted(load)
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
.preview-meta { color: var(--text-secondary); font-size: 13px; display: flex; gap: 16px; }
.preview-content { white-space: pre-wrap; line-height: 1.8; color: #303133; }
</style>
