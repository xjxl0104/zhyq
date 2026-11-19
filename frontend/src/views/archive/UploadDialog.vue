<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { archiveApi, SECRET_OPTIONS } from '@/api/archive'
import { useProjectStore } from '@/stores/project'

const props = defineProps({
  modelValue: Boolean,
  categories: { type: Array, default: () => [] },
  docTypes: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({ get: () => props.modelValue, set: (v) => emit('update:modelValue', v) })
const project = useProjectStore()

// 每个待归档文件一行:state = uploading | analyzing | ready | saving | saved | failed
const rows = ref([])
const saving = ref(false)
let seq = 0

const cascaderProps = { value: 'code', label: 'name', children: 'children', emitPath: false }
const busy = computed(() => rows.value.some((r) => ['uploading', 'analyzing', 'saving'].includes(r.state)))
const readyCount = computed(() => rows.value.filter((r) => r.state === 'ready' || r.state === 'failed').length)

watch(visible, (v) => {
  if (v) rows.value = []
})

async function handleFile(options) {
  const file = options.file
  const row = reactive({
    key: ++seq, fileName: file.name, state: 'uploading', error: '', fileId: null, fileHash: '', summary: '',
    name: '', categoryCode: '', docType: '其他', secretLevel: 2, docDate: null, period: '', versionLabel: '',
    counterparty: '', confidence: 0, reasons: [], nameJunk: false, duplicate: null
  })
  rows.value.push(row)
  try {
    const sf = await archiveApi.upload(file)
    row.fileId = sf.id
    row.state = 'analyzing'
    const a = await archiveApi.analyze({ fileId: sf.id, lastModified: file.lastModified })
    Object.assign(row, {
      fileHash: a.fileHash, summary: a.summary, name: a.name, categoryCode: a.categoryCode, docType: a.docType,
      secretLevel: a.secretLevel, docDate: a.docDate, period: a.period || '', versionLabel: a.versionLabel || '',
      confidence: a.confidence, reasons: a.reasons || [], nameJunk: a.nameJunk, duplicate: a.duplicate, state: 'ready'
    })
  } catch (e) {
    row.state = 'failed'
    row.error = e?.message || '上传或分析失败'
    // 分析失败但文件已上传时,允许手动填写后归档
    if (row.fileId) {
      row.name = file.name.replace(/\.[^.]+$/, '')
      row.categoryCode = '99.01'
    }
  }
}

function confidenceTag(c) {
  if (c >= 70) return { type: 'success', text: `把握高 ${c}%` }
  if (c >= 40) return { type: 'warning', text: `请确认 ${c}%` }
  return { type: 'danger', text: '请手动选择' }
}

function stateText(r) {
  return { uploading: '上传中…', analyzing: '识别中…', saving: '保存中…', saved: '已归档', failed: r.error || '失败' }[r.state] || ''
}

function remove(row) {
  rows.value = rows.value.filter((r) => r.key !== row.key)
}

async function saveAll() {
  const todo = rows.value.filter((r) => (r.state === 'ready' || r.state === 'failed') && r.fileId)
  const invalid = todo.find((r) => !r.name?.trim() || !r.categoryCode)
  if (invalid) {
    ElMessage.warning(`「${invalid.fileName}」还没填名称或分类`)
    return
  }
  saving.value = true
  let ok = 0
  for (const r of todo) {
    r.state = 'saving'
    try {
      await archiveApi.add({
        projectId: project.currentProjectId, fileId: r.fileId, name: r.name.trim(), categoryCode: r.categoryCode,
        docType: r.docType, secretLevel: r.secretLevel, docDate: r.docDate || null, period: r.period || null,
        versionLabel: r.versionLabel || null, counterparty: r.counterparty || null, summary: r.summary, fileHash: r.fileHash
      })
      r.state = 'saved'
      ok++
    } catch (e) {
      r.state = 'failed'
      r.error = e?.message || '保存失败'
    }
  }
  saving.value = false
  if (ok) {
    ElMessage.success(`已归档 ${ok} 份资料`)
    emit('saved')
  }
}

function close() {
  visible.value = false
}
</script>

<template>
  <el-dialog v-model="visible" title="上传资料 · 自动识别名称与分类" width="1180px" top="4vh" :close-on-click-modal="false" @closed="rows = []">
    <el-alert type="info" :closable="false" show-icon class="tip"
      title="选择或拖入文件后,系统会读取文件名和内容(PDF、Word、Excel),自动给出规范化名称、分类、类型和密级。请逐行确认,不对的直接改,最后点「全部归档」。" />
    <el-upload drag multiple :show-file-list="false" :http-request="handleFile" :disabled="saving">
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">把文件拖到这里,或 <em>点击选择</em>(可多选,单个不超过 100MB)</div>
    </el-upload>

    <el-table v-if="rows.length" :data="rows" border size="small" class="rows" max-height="52vh" row-key="key">
      <el-table-column label="原文件名" width="190">
        <template #default="{ row }">
          <div class="orig" :title="row.fileName">{{ row.fileName }}</div>
          <el-tag v-if="row.state !== 'ready'" size="small" :type="row.state === 'failed' ? 'danger' : row.state === 'saved' ? 'success' : 'info'">{{ stateText(row) }}</el-tag>
          <el-tag v-else :type="confidenceTag(row.confidence).type" size="small">{{ confidenceTag(row.confidence).text }}</el-tag>
          <el-tag v-if="row.duplicate" type="warning" size="small" class="dup" :title="`与「${row.duplicate.name}」(${row.duplicate.archiveNo})内容完全相同`">已有相同文件</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="规范化名称" min-width="250">
        <template #default="{ row }">
          <el-input v-model="row.name" size="small" :disabled="row.state === 'saved' || row.state === 'saving'" maxlength="200" />
          <div v-if="row.reasons?.length" class="reasons">依据:{{ row.reasons.join(';') }}</div>
        </template>
      </el-table-column>
      <el-table-column label="分类" width="210">
        <template #default="{ row }">
          <el-cascader v-model="row.categoryCode" :options="categories" :props="cascaderProps" size="small" filterable
            :show-all-levels="false" placeholder="选择分类" :disabled="row.state === 'saved' || row.state === 'saving'" style="width: 100%" />
        </template>
      </el-table-column>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-select v-model="row.docType" size="small" :disabled="row.state === 'saved' || row.state === 'saving'">
            <el-option v-for="t in docTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="密级" width="95">
        <template #default="{ row }">
          <el-select v-model="row.secretLevel" size="small" :disabled="row.state === 'saved' || row.state === 'saving'">
            <el-option v-for="s in SECRET_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="文件日期" width="140">
        <template #default="{ row }">
          <el-date-picker v-model="row.docDate" type="date" value-format="YYYY-MM-DD" size="small" placeholder="可不填"
            style="width: 100%" :disabled="row.state === 'saved' || row.state === 'saving'" />
        </template>
      </el-table-column>
      <el-table-column label="对方单位" width="130">
        <template #default="{ row }">
          <el-input v-model="row.counterparty" size="small" placeholder="可不填" maxlength="100" :disabled="row.state === 'saved' || row.state === 'saving'" />
        </template>
      </el-table-column>
      <el-table-column label="" width="60" align="center">
        <template #default="{ row }">
          <el-button v-if="row.state !== 'saved' && row.state !== 'saving'" link type="danger" @click="remove(row)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <span class="foot-tip" v-if="rows.length">共 {{ rows.length }} 份,待归档 {{ readyCount }} 份</span>
      <el-button @click="close">关闭</el-button>
      <el-button type="primary" :loading="saving" :disabled="busy || !readyCount" @click="saveAll">全部归档</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.tip { margin-bottom: 12px; }
.rows { margin-top: 14px; }
.orig { word-break: break-all; line-height: 1.4; margin-bottom: 4px; font-size: 12px; }
.dup { margin-left: 4px; }
.reasons { margin-top: 4px; color: var(--text-secondary, #909399); font-size: 12px; line-height: 1.4; }
.foot-tip { margin-right: 14px; color: var(--text-secondary, #909399); font-size: 13px; }
</style>
