<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { archiveApi, SECRET_OPTIONS, STATUS_OPTIONS, secretOf, statusOf, fmtSize, categoryLabelMap } from '@/api/archive'
import { startFileDownload } from '@/utils/fileDownload'
import { useProjectStore } from '@/stores/project'
import UploadDialog from './UploadDialog.vue'

const project = useProjectStore()

const categories = ref([])
const docTypes = ref([])
const counts = ref({})
const labelMap = computed(() => categoryLabelMap(categories.value))

const loading = ref(false)
const list = ref([])
const total = ref(0)
const currentCode = ref('')
const query = reactive({ pageNo: 1, pageSize: 20, keyword: '', docType: null, secretLevel: null, status: null, year: null })
const years = Array.from({ length: 8 }, (_, i) => new Date().getFullYear() - i)

const treeData = computed(() =>
  categories.value.map((top) => ({
    code: top.code,
    label: top.name,
    count: (top.children || []).reduce((n, c) => n + (counts.value[c.code] || 0), 0),
    children: (top.children || []).map((c) => ({ code: c.code, label: c.name, count: counts.value[c.code] || 0 }))
  }))
)
const totalCount = computed(() => Object.values(counts.value).reduce((a, b) => a + b, 0))

async function loadMeta() {
  const meta = await archiveApi.meta()
  categories.value = meta.categories || []
  docTypes.value = meta.docTypes || []
}
async function loadStats() {
  counts.value = (await archiveApi.stats()) || {}
}
async function load() {
  loading.value = true
  try {
    const res = await archiveApi.page({ ...query, categoryCode: currentCode.value || undefined })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.pageNo = 1
  load()
}
function reset() {
  Object.assign(query, { pageNo: 1, keyword: '', docType: null, secretLevel: null, status: null, year: null })
  load()
}
function pickCategory(node) {
  currentCode.value = node?.code || ''
  search()
}
function refreshAll() {
  loadStats()
  load()
}

// ---------- 编辑 ----------
const uploadVisible = ref(false)
const dialog = reactive({ visible: false })
const form = reactive({})
const formRef = ref()
const cascaderProps = { value: 'code', label: 'name', children: 'children', emitPath: false }
const rules = {
  name: [{ required: true, message: '请填写名称', trigger: 'blur' }],
  categoryCode: [{ required: true, message: '请选择分类', trigger: 'change' }]
}
function openEdit(row) {
  Object.assign(form, { remark: '', counterparty: '', period: '', versionLabel: '', docDate: null, expireDate: null, ...row })
  dialog.visible = true
}
async function submit() {
  await formRef.value.validate()
  await archiveApi.update({ ...form })
  ElMessage.success('已保存')
  dialog.visible = false
  refreshAll()
}
async function download(row) {
  try {
    await startFileDownload(row.fileId, `${row.name}.${row.fileExt || ''}`.replace(/\.$/, ''))
    ElMessage.success('已交给浏览器下载,请在下载列表查看')
  } catch (e) {
    ElMessage.error(e?.message || '下载失败')
  }
}
async function remove(row) {
  await archiveApi.remove(row.id)
  ElMessage.success('已删除')
  refreshAll()
}

watch(() => project.currentProjectId, () => { loadStats(); search() })
onMounted(async () => {
  await Promise.all([loadMeta(), loadStats()])
  load()
})
</script>

<template>
  <div class="page-container arc-page">
    <aside class="tree-card">
      <div class="tree-head">资料分类</div>
      <div class="tree-all" :class="{ on: !currentCode }" @click="pickCategory(null)">
        全部资料 <span class="cnt">{{ totalCount }}</span>
      </div>
      <el-tree :data="treeData" node-key="code" :expand-on-click-node="false" highlight-current :current-node-key="currentCode"
        :props="{ label: 'label', children: 'children' }" @node-click="pickCategory">
        <template #default="{ data }">
          <span class="node"><span class="code">{{ data.code }}</span>{{ data.label }}
            <span v-if="data.count" class="cnt">{{ data.count }}</span></span>
        </template>
      </el-tree>
    </aside>

    <section class="main">
      <div class="search-bar">
        <el-form :inline="true" :model="query" @submit.prevent="search">
          <el-form-item label="关键词">
            <el-input v-model="query.keyword" placeholder="名称 / 档号 / 对方单位 / 内容" clearable style="width: 240px" @keyup.enter="search" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="query.docType" clearable placeholder="全部" style="width: 110px">
              <el-option v-for="t in docTypes" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item label="密级">
            <el-select v-model="query.secretLevel" clearable placeholder="全部" style="width: 90px">
              <el-option v-for="s in SECRET_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" clearable placeholder="全部" style="width: 90px">
              <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="年份">
            <el-select v-model="query.year" clearable placeholder="全部" style="width: 90px">
              <el-option v-for="y in years" :key="y" :label="y" :value="y" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="table-card">
        <div class="toolbar">
          <el-button type="primary" @click="uploadVisible = true"><el-icon><Upload /></el-icon>上传资料</el-button>
          <span class="hint">{{ currentCode ? (labelMap[currentCode] || currentCode) : '全部资料' }} · 共 {{ total }} 份</span>
        </div>
        <el-table :data="list" v-loading="loading" border stripe>
          <el-table-column prop="archiveNo" label="档号" width="150" />
          <el-table-column prop="name" label="名称" min-width="260" show-overflow-tooltip />
          <el-table-column label="分类" width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ labelMap[row.categoryCode] || row.categoryCode }}</template>
          </el-table-column>
          <el-table-column prop="docType" label="类型" width="90" />
          <el-table-column label="密级" width="75">
            <template #default="{ row }"><el-tag :type="secretOf(row.secretLevel).type" size="small">{{ secretOf(row.secretLevel).label }}</el-tag></template>
          </el-table-column>
          <el-table-column label="期间 / 日期" width="130">
            <template #default="{ row }">{{ row.period || row.docDate || '-' }}</template>
          </el-table-column>
          <el-table-column prop="counterparty" label="对方单位" width="130" show-overflow-tooltip />
          <el-table-column label="状态" width="70">
            <template #default="{ row }"><el-tag :type="statusOf(row.status).type" size="small">{{ statusOf(row.status).label }}</el-tag></template>
          </el-table-column>
          <el-table-column label="文件" width="110">
            <template #default="{ row }">{{ (row.fileExt || '').toUpperCase() }} {{ fmtSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="download(row)">下载</el-button>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-popconfirm title="删除后文件也会一并删除,确认?" @confirm="remove(row)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" class="pager" background
          layout="total, sizes, prev, pager, next" :total="total" :page-sizes="[20, 50, 100]" @change="load" />
      </div>
    </section>

    <UploadDialog v-model="uploadVisible" :categories="categories" :doc-types="docTypes" @saved="refreshAll" />

    <el-dialog v-model="dialog.visible" title="编辑资料" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="档号"><el-input :model-value="form.archiveNo" disabled /></el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="200" /></el-form-item>
        <el-form-item label="分类" prop="categoryCode">
          <el-cascader v-model="form.categoryCode" :options="categories" :props="cascaderProps" filterable :show-all-levels="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.docType" style="width: 160px"><el-option v-for="t in docTypes" :key="t" :label="t" :value="t" /></el-select>
          <span class="gap" />
          <el-select v-model="form.secretLevel" style="width: 110px"><el-option v-for="s in SECRET_OPTIONS" :key="s.value" :label="s.label" :value="s.value" /></el-select>
          <span class="gap" />
          <el-select v-model="form.status" style="width: 110px"><el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" /></el-select>
        </el-form-item>
        <el-form-item label="文件日期"><el-date-picker v-model="form.docDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
          <span class="gap" />到期日期
          <el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" style="width: 200px; margin-left: 8px" /></el-form-item>
        <el-form-item label="期间 / 版本">
          <el-input v-model="form.period" placeholder="如 2026年10月" maxlength="32" style="width: 200px" />
          <span class="gap" />
          <el-input v-model="form.versionLabel" placeholder="如 盖章版" maxlength="32" style="width: 200px" />
        </el-form-item>
        <el-form-item label="对方单位"><el-input v-model="form.counterparty" maxlength="100" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
        <el-form-item label="原文件名"><span class="muted">{{ form.originalName }}</span></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.arc-page { display: flex; gap: 16px; align-items: flex-start; }
.tree-card { width: 268px; flex: none; background: var(--el-bg-color, #fff); border: 1px solid var(--el-border-color-lighter, #ebeef5); border-radius: 8px; padding: 12px 8px; max-height: calc(100vh - 150px); overflow: auto; }
.tree-head { font-weight: 600; padding: 0 8px 8px; }
.tree-all { padding: 6px 8px; margin-bottom: 4px; border-radius: 4px; cursor: pointer; display: flex; justify-content: space-between; }
.tree-all:hover, .tree-all.on { background: var(--el-color-primary-light-9, #ecf5ff); color: var(--el-color-primary, #409eff); }
.node { font-size: 13px; display: flex; align-items: center; gap: 6px; }
.node .code { color: var(--el-text-color-secondary, #909399); font-size: 12px; font-variant-numeric: tabular-nums; }
.cnt { margin-left: auto; font-size: 12px; color: var(--el-text-color-secondary, #909399); }
.main { flex: 1; min-width: 0; }
.hint { margin-left: 14px; color: var(--el-text-color-secondary, #909399); font-size: 13px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.gap { display: inline-block; width: 8px; }
.muted { color: var(--el-text-color-secondary, #909399); word-break: break-all; }
</style>
