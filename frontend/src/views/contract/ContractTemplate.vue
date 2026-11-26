<template>
  <div class="page-container">
    <!-- 分类页签 -->
    <div class="cat-bar">
      <button type="button" class="cat-pill" :class="{ active: activeCategory === 'all' }" @click="activeCategory = 'all'">
        <span>全部模板</span>
        <span class="pill-count">{{ list.length }}</span>
      </button>
      <button v-for="cat in CATEGORIES" :key="cat" type="button" class="cat-pill"
              :class="{ active: activeCategory === cat }" @click="activeCategory = cat">
        <span>{{ cat }}</span>
        <span class="pill-count">{{ categoryCounts[cat] || 0 }}</span>
      </button>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog(null)"><el-icon><Plus /></el-icon>新增合同模板</el-button>
        <el-input v-model="keyword" placeholder="搜索模板名称 / 版本号 / 适用说明" clearable style="width: 260px" />
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <span class="toolbar-tip">统一管理租赁合同、物业合同、简易合同、补充协议等标准范本，支持上传 Word/PDF 模板文件并随时一键下载。</span>
      </div>

      <el-table :data="viewRows" v-loading="loading" border stripe size="small" row-key="id">
        <el-table-column type="index" label="序号" width="55" align="center" />
        <el-table-column prop="name" label="模板名称" min-width="210" show-overflow-tooltip />
        <el-table-column label="模板分类" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="categoryTagType(row.category)" size="small">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="versionTag" label="版本号" width="90" align="center" />
        <el-table-column prop="applicableScope" label="适用说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="模板文件（点击下载）" min-width="260">
          <template #default="{ row }">
            <div v-if="row.files?.length" class="tpl-files">
              <div v-for="file in row.files" :key="file.id" class="tpl-file-item">
                <el-button link type="primary" :loading="downloadingId === file.id" @click="downloadFile(file)">
                  <el-icon><Download /></el-icon>
                  <span class="tpl-file-name" :title="file.originalName">{{ file.originalName }}</span>
                </el-button>
              </div>
            </div>
            <el-button v-else link type="info" @click="openFiles(row)">暂未上传文件，点击上传</el-button>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="160" />
        <el-table-column label="操作" width="185" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openFiles(row)">上传/管理文件</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该合同模板?" @confirm="remove(row)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑模板弹窗 -->
    <el-dialog v-model="dialog.visible" :title="form.id ? '编辑合同模板' : '新增合同模板'"
               width="660px" top="6vh" destroy-on-close :close-on-click-modal="false">
      <el-form :model="form" label-width="100px" size="small">
        <el-row :gutter="12">
          <el-col :span="24">
            <el-form-item label="模板名称" required>
              <el-input v-model="form.name" placeholder="如：DI PARK 标准厂房租赁合同（2026版）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模板分类" required>
              <el-select v-model="form.category" style="width: 100%">
                <el-option v-for="cat in CATEGORIES" :key="cat" :label="cat" :value="cat" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本号">
              <el-input v-model="form.versionTag" placeholder="如 V1.0 / 2026版" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="适用说明">
              <el-input v-model="form.applicableScope" placeholder="如：适用于园区标准厂房/云仓租赁签约" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sort" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="修订备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可记录本次模板修订要点" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="模板文件">
              <FileUpload v-model="formFiles" biz-type="contract_template" :biz-id="form.id || undefined"
                          accept=".doc,.docx,.pdf,.xls,.xlsx,.zip,.rar" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 模板文件快速管理弹窗 -->
    <el-dialog v-model="fileDialog.visible" :title="`模板文件 · ${fileDialog.row?.name || ''}`"
               width="580px" @closed="onFileDialogClosed">
      <el-alert type="info" :closable="false" show-icon>
        支持上传 Word（.doc/.docx）、PDF、Excel 等标准模板文件，可同时保留多份或新版文件。
      </el-alert>
      <div class="attach-uploader">
        <FileUpload v-model="dialogFiles" biz-type="contract_template" :biz-id="fileDialog.row?.id"
                    accept=".doc,.docx,.pdf,.xls,.xlsx,.zip,.rar" />
      </div>
      <div v-if="dialogFiles.length" class="attach-downloads">
        <div v-for="file in dialogFiles" :key="file.id" class="attach-download">
          <span class="attach-name" :title="file.originalName || file.name">{{ file.originalName || file.name }}</span>
          <el-button link type="primary" :loading="downloadingId === file.id" @click="downloadFile(file)">下载模板</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Plus } from '@element-plus/icons-vue'
import { contractTemplateApi } from '@/api/contract'
import { fileApi } from '@/api/file'
import { startFileDownload } from '@/utils/fileDownload'
import FileUpload from '@/components/FileUpload.vue'

const CATEGORIES = ['租赁合同', '物业合同', '简易合同', '补充协议', '退租协议', '其他模板']
const loading = ref(false)
const list = ref([])
const activeCategory = ref('all')
const keyword = ref('')
const statusFilter = ref(null)
const downloadingId = ref(null)

const categoryCounts = computed(() => {
  const map = {}
  for (const r of list.value) map[r.category] = (map[r.category] || 0) + 1
  return map
})

const viewRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return list.value.filter((r) => {
    if (activeCategory.value !== 'all' && r.category !== activeCategory.value) return false
    if (statusFilter.value != null && Number(r.status) !== Number(statusFilter.value)) return false
    if (!kw) return true
    return `${r.name || ''} ${r.category || ''} ${r.versionTag || ''} ${r.applicableScope || ''} ${r.remark || ''}`
      .toLowerCase().includes(kw)
  })
})

function categoryTagType(cat) {
  return {
    租赁合同: 'primary',
    物业合同: 'success',
    简易合同: 'warning',
    补充协议: 'danger',
    退租协议: 'info'
  }[cat] || ''
}

async function load() {
  loading.value = true
  try {
    list.value = await contractTemplateApi.list() || []
  } finally {
    loading.value = false
  }
}

const dialog = reactive({ visible: false, saving: false })
const form = reactive({})
const formFiles = ref([])

async function openDialog(row) {
  formFiles.value = []
  if (row) {
    Object.assign(form, { ...row })
    dialog.visible = true
    try { formFiles.value = await fileApi.list('contract_template', row.id) || [] } catch { /* ignore */ }
  } else {
    Object.assign(form, {
      id: null,
      name: '',
      category: activeCategory.value === 'all' ? '租赁合同' : activeCategory.value,
      versionTag: 'V1.0',
      applicableScope: '',
      status: 1,
      sort: 10,
      remark: ''
    })
    dialog.visible = true
  }
}

async function save() {
  if (!form.name?.trim()) return ElMessage.warning('请填写模板名称')
  if (!form.category) return ElMessage.warning('请选择模板分类')
  dialog.saving = true
  try {
    const saved = form.id
      ? await contractTemplateApi.update({ ...form })
      : await contractTemplateApi.add({ ...form })
    const savedId = saved?.id || form.id
    const pendingIds = (formFiles.value || []).filter((f) => f && f.id && !f.bizId).map((f) => f.id)
    if (savedId && pendingIds.length) {
      await fileApi.attachVerified('contract_template', savedId, pendingIds)
    }
    ElMessage.success('保存成功')
    dialog.visible = false
    await load()
  } finally {
    dialog.saving = false
  }
}

async function remove(row) {
  await contractTemplateApi.remove(row.id)
  ElMessage.success('已删除')
  await load()
}

const fileDialog = reactive({ visible: false, row: null })
const dialogFiles = ref([])

async function openFiles(row) {
  fileDialog.row = row
  fileDialog.visible = true
  dialogFiles.value = []
  try {
    dialogFiles.value = await fileApi.list('contract_template', row.id) || []
  } catch {
    ElMessage.error('文件加载失败')
  }
}
function onFileDialogClosed() {
  fileDialog.row = null
  dialogFiles.value = []
  load()
}
async function downloadFile(file) {
  downloadingId.value = file.id
  try {
    await startFileDownload(file.id, file.originalName || file.name || '合同模板')
  } catch {
    ElMessage.error('下载失败')
  } finally {
    downloadingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.cat-bar { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.cat-pill {
  display: inline-flex; align-items: center; gap: 6px; padding: 6px 13px;
  border-radius: 999px; border: 1px solid var(--el-border-color); background: var(--el-fill-color-blank);
  color: var(--el-text-color-regular); font-size: 13px; cursor: pointer; transition: all .15s;
}
.cat-pill:hover { border-color: var(--el-color-primary); color: var(--el-color-primary); }
.cat-pill.active { background: var(--el-color-primary); border-color: var(--el-color-primary); color: #fff; font-weight: 600; }
.pill-count { font-size: 11px; padding: 1px 6px; border-radius: 999px; background: rgba(0, 0, 0, .08); }
.cat-pill.active .pill-count { background: rgba(255, 255, 255, .24); }

.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.toolbar-tip { font-size: 12px; color: var(--el-text-color-secondary); }
.tpl-files { display: flex; flex-direction: column; gap: 4px; }
.tpl-file-item { display: flex; align-items: center; }
.tpl-file-name { max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-left: 4px; }
.attach-uploader { margin-top: 14px; }
.attach-downloads { margin-top: 12px; border-top: 1px solid var(--el-border-color-lighter); }
.attach-download { display: flex; align-items: center; gap: 10px; padding: 8px 0; }
.attach-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
