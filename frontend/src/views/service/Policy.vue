<template>
  <div class="page-container">
    <!-- 政策匹配卡片 -->
    <el-card shadow="never" class="match-card">
      <template #header>政策匹配</template>
      <el-form :inline="true">
        <el-form-item label="租客">
          <el-select v-model="matchTenantId" placeholder="请选择租客" filterable style="width: 240px">
            <el-option v-for="t in tenantList" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!matchTenantId" @click="doMatch">匹配</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="matchList" v-loading="matchLoading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="110">
          <template #default="{ row }"><el-tag type="info">{{ row.ptype || '-' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="匹配原因" width="160">
          <template #default="{ row }">
            <el-tag :type="row.matchReason === '通用政策' ? 'info' : 'success'">{{ row.matchReason }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deadline" label="申报截止" width="120" />
      </el-table>
      <div v-if="matched && matchList.length === 0" class="empty-tip">暂无匹配的政策</div>
    </el-card>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="标题">
          <el-input v-model="query.title" placeholder="政策标题" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.ptype" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="t in ptypeOptions" :key="t" :label="t" :value="t" />
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
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增政策</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="source" label="发布单位" min-width="140" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }"><el-tag type="info">{{ row.ptype || '-' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="适用行业" width="120">
          <template #default="{ row }">{{ row.industry || '通用' }}</template>
        </el-table-column>
        <el-table-column prop="publishDate" label="发布日期" width="120" />
        <el-table-column prop="deadline" label="申报截止" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ statusMap[row.status] || '未知' }}</el-tag>
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
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="600px">
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
        <el-form-item label="标题" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="发布单位"><el-input v-model="form.source" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.ptype" style="width: 100%" filterable allow-create>
            <el-option v-for="t in ptypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用行业">
          <el-input v-model="form.industry" placeholder="留空表示通用政策" />
        </el-form-item>
        <el-form-item label="发布日期">
          <el-date-picker v-model="form.publishDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="申报截止">
          <el-date-picker v-model="form.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="(label, val) in statusMap" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item label="附件">
          <FileUpload v-model="attachFiles" biz-type="policy" :biz-id="form.id" />
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
import { policyApi } from '@/api/service'
import { fileApi } from '@/api/file'
import FileUpload from '@/components/FileUpload.vue'
import { tenantApi } from '@/api/tenant'

// 1有效 0已过期
const statusMap = { 1: '有效', 0: '已过期' }
const ptypeOptions = ['税收', '人才', '研发', '租金补贴']

const tenantList = ref([])
const matchTenantId = ref(null)
const matchList = ref([])
const matchLoading = ref(false)
const matched = ref(false)

async function loadTenants() {
  tenantList.value = await tenantApi.list()
}
async function doMatch() {
  if (!matchTenantId.value) return
  matchLoading.value = true
  try {
    matchList.value = await policyApi.match(matchTenantId.value)
    matched.value = true
  } finally {
    matchLoading.value = false
  }
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, title: '', ptype: null, status: null })

async function load() {
  loading.value = true
  try {
    const res = await policyApi.page(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function reset() {
  Object.assign(query, { pageNo: 1, title: '', ptype: null, status: null })
  load()
}

const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const defaultForm = () => ({ id: null, title: '', source: '', ptype: '', industry: '', publishDate: null, deadline: null, content: '', status: 1 })
const form = reactive(defaultForm())
const attachFiles = ref([])
const rules = {
  title: [{ required: true, message: '请输入政策标题', trigger: 'blur' }]
}

async function openDialog(row) {
  dialog.visible = true
  dialog.title = row ? '编辑政策' : '新增政策'
  attachFiles.value = []
  if (row) {
    Object.assign(form, row)
    try { attachFiles.value = await fileApi.list('policy', row.id) } catch (e) { /* 忽略 */ }
  } else {
    Object.assign(form, defaultForm())
  }
}
async function submitForm() {
  await formRef.value.validate()
  let policyId = form.id
  if (form.id) await policyApi.update(form)
  else policyId = await policyApi.add(form)
  const pendingIds = (attachFiles.value || []).filter(f => f && f.id && !f.bizId).map(f => f.id)
  if (policyId && pendingIds.length) {
    try { await fileApi.attach('policy', policyId, pendingIds) } catch (e) { /* 忽略,不阻断保存 */ }
  }
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}
async function remove(id) {
  await policyApi.remove(id)
  ElMessage.success('删除成功')
  load()
}

onMounted(() => {
  load()
  loadTenants()
})
</script>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
.toolbar { margin-bottom: 12px; }
.match-card { margin-bottom: 16px; }
.empty-tip { text-align: center; color: var(--el-text-color-secondary); padding: 12px 0; }
</style>
