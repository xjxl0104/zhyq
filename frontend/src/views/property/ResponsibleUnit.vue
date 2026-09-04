<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="单位名称/联系人/服务范围" clearable style="width: 220px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.unitType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="t in UNIT_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增单位</el-button>
        <el-button @click="importVisible = true"><el-icon><Upload /></el-icon>批量导入</el-button>
        <el-button link type="primary" @click="downloadTemplate">下载模板</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="70" />
        <el-table-column prop="name" label="单位名称" min-width="180" />
        <el-table-column prop="unitType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.unitType" size="small" :type="TYPE_TAG[row.unitType] || ''">{{ row.unitType }}</el-tag>
            <span v-else class="muted">未分类</span>
          </template>
        </el-table-column>
        <el-table-column prop="serviceScope" label="服务范围" min-width="140">
          <template #default="{ row }"><span v-if="row.serviceScope">{{ row.serviceScope }}</span><span v-else class="muted">—</span></template>
        </el-table-column>
        <el-table-column prop="contact" label="联系人" width="100">
          <template #default="{ row }"><span v-if="row.contact">{{ row.contact }}</span><span v-else class="muted">—</span></template>
        </el-table-column>
        <el-table-column prop="contactPhone" label="联系电话" width="140">
          <template #default="{ row }"><span v-if="row.contactPhone">{{ row.contactPhone }}</span><span v-else class="muted">—</span></template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="load"
        @current-change="load"
      />
    </div>

    <!-- 新增/编辑 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑责任单位' : '新增责任单位'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="单位名称" prop="name">
          <el-input v-model="form.name" maxlength="128" show-word-limit placeholder="如 华强电梯维保" />
        </el-form-item>
        <el-form-item label="单位类型" prop="unitType">
          <el-select v-model="form.unitType" placeholder="留空表示未分类" clearable style="width: 100%">
            <el-option v-for="t in UNIT_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务范围">
          <el-input v-model="form.serviceScope" placeholder="如 电梯/消防/空调" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contact" maxlength="64" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" maxlength="20" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入 -->
    <el-dialog v-model="importVisible" title="批量导入责任单位" width="620px" @close="resetImport">
      <el-alert type="info" :closable="false" show-icon class="mb12">
        <template #title>
          按<strong>单位名称</strong>去重：已存在则更新，不存在则新增。存在任何错误行时不会写入，请先修正。
        </template>
      </el-alert>

      <GlassSurface variant="upload" class="import-surface">
        <el-upload
          drag
          :auto-upload="false"
          :limit="1"
          :on-change="onFileChange"
          :on-exceed="onExceed"
          :file-list="fileList"
          accept=".xls,.xlsx"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip">
              仅支持 xls/xlsx。请先<el-button link type="primary" @click="downloadTemplate">下载模板</el-button>按表头填写
            </div>
          </template>
        </el-upload>
      </GlassSurface>

      <div v-if="previewed" class="preview-box">
        <div class="preview-head">
          <span>可导入 <strong class="success">{{ previewValid }}</strong> 条</span>
          <span v-if="previewErrors.length">，问题 <strong class="danger">{{ previewErrors.length }}</strong> 处</span>
        </div>
        <el-scrollbar v-if="previewErrors.length" max-height="180px">
          <div v-for="(e, i) in previewErrors" :key="i" class="err-line">{{ e }}</div>
        </el-scrollbar>
        <el-alert v-else type="success" :closable="false" show-icon title="校验通过，可以导入" />
      </div>

      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button :loading="checking" :disabled="!currentFile" @click="doPreview">校验</el-button>
        <el-button
          type="primary"
          :loading="importing"
          :disabled="!currentFile || (previewed && previewErrors.length > 0)"
          @click="doImport"
        >确认导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Upload, UploadFilled } from '@element-plus/icons-vue'
import { responsibleUnitApi } from '@/api/property'
import GlassSurface from '@/components/GlassSurface.vue'

const UNIT_TYPES = ['内部部门', '外部供应商', '物业', '施工方']
const TYPE_TAG = { 内部部门: 'primary', 外部供应商: 'warning', 物业: 'success', 施工方: 'info' }

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '', unitType: '' })

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, name: '', unitType: '', serviceScope: '', contact: '', contactPhone: '', enabled: 1, remark: '' })
const rules = {
  name: [{ required: true, message: '请输入单位名称', trigger: 'blur' }]
}

const importVisible = ref(false)
const fileList = ref([])
const currentFile = ref(null)
const checking = ref(false)
const importing = ref(false)
const previewed = ref(false)
const previewValid = ref(0)
const previewErrors = ref([])

async function load() {
  loading.value = true
  try {
    const d = await responsibleUnitApi.page({ ...query })
    list.value = d.records || []
    total.value = d.total || 0
  } catch (e) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function reset() {
  query.keyword = ''
  query.unitType = ''
  query.current = 1
  load()
}

function openDialog(row) {
  Object.assign(form, {
    id: null, name: '', unitType: '', serviceScope: '',
    contact: '', contactPhone: '', enabled: 1, remark: ''
  })
  if (row) Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await responsibleUnitApi.update(form.id, form)
    } else {
      await responsibleUnitApi.add(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」？已引用该单位的工单不会被改动。`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await responsibleUnitApi.remove(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  }
}

async function downloadTemplate() {
  try {
    const res = await responsibleUnitApi.template()
    const blob = new Blob([res.data], { type: 'application/octet-stream' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '责任单位导入模板.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('模板下载失败')
  }
}

// auto-upload=false,手动收集 raw,不走 action 以复用 axios 的 token 拦截器
function onFileChange(file) {
  currentFile.value = file.raw
  fileList.value = [file]
  previewed.value = false
  previewErrors.value = []
  previewValid.value = 0
}

function onExceed(files) {
  const f = files[0]
  currentFile.value = f
  fileList.value = [{ name: f.name, raw: f }]
  previewed.value = false
  previewErrors.value = []
}

function buildForm() {
  const fd = new FormData()
  fd.append('file', currentFile.value)
  return fd
}

async function doPreview() {
  checking.value = true
  try {
    const d = await responsibleUnitApi.preview(buildForm())
    previewValid.value = d.valid || 0
    previewErrors.value = d.errors || []
    previewed.value = true
    if (!previewErrors.value.length) ElMessage.success(`校验通过，可导入 ${previewValid.value} 条`)
  } catch (e) {
    ElMessage.error(e?.message || '校验失败')
  } finally {
    checking.value = false
  }
}

async function doImport() {
  importing.value = true
  try {
    const d = await responsibleUnitApi.import(buildForm())
    ElMessage.success(`导入完成：新增 ${d.created} 条，更新 ${d.updated} 条`)
    importVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e?.message || '导入失败')
  } finally {
    importing.value = false
  }
}

function resetImport() {
  fileList.value = []
  currentFile.value = null
  previewed.value = false
  previewErrors.value = []
  previewValid.value = 0
}

onMounted(load)
</script>

<style scoped>
.table-card {
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 16px;
}
.toolbar {
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
  align-items: center;
}
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
.muted {
  color: var(--el-text-color-placeholder);
}
.mb12 {
  margin-bottom: 12px;
}
.import-surface :deep(.el-upload),
.import-surface :deep(.el-upload-dragger) {
  width: 100%;
}
.preview-box {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}
.preview-head {
  margin-bottom: 8px;
  font-size: 13px;
}
.err-line {
  font-size: 12px;
  color: var(--el-color-danger);
  line-height: 1.9;
}
.success { color: var(--el-color-success); }
.danger  { color: var(--el-color-danger); }
</style>
