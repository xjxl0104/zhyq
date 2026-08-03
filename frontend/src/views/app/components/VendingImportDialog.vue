<template>
  <el-dialog :model-value="modelValue" title="导入自动售货机数据" width="960px" @close="close">
    <el-steps :active="stage" align-center finish-status="success">
      <el-step title="选择类型与文件" /><el-step title="校验并排除错误" /><el-step title="确认入库" />
    </el-steps>

    <div v-if="stage === 0" class="stage-panel">
      <el-form label-width="100px">
        <el-form-item label="数据类型">
          <el-select v-model="type" style="width:240px">
            <el-option v-for="item in vendingTypes" :key="item.name" :label="`${item.label}数据`" :value="item.name" />
          </el-select>
          <el-button class="template-button" @click="downloadTemplate">下载标准模板</el-button>
        </el-form-item>
        <el-form-item label="标准文件">
          <el-upload drag :auto-upload="false" accept=".xlsx,.xls" :limit="1" :on-change="onFile">
            <div>拖拽或点击选择已按标准模板整理的文件</div>
          </el-upload>
        </el-form-item>
      </el-form>
      <el-alert title="本版本不解析厂商原生导出文件；请勿上传账号、密码、Cookie 或页面抓取内容。" type="warning" :closable="false" show-icon />
    </div>

    <div v-else-if="preview" class="stage-panel">
      <div class="summary-row">
        <el-tag>总行数 {{ preview.totalRows }}</el-tag>
        <el-tag type="success">有效 {{ preview.validRows }}</el-tag>
        <el-tag type="danger">错误 {{ preview.invalidRows }}</el-tag>
        <el-tag type="info">已排除 {{ preview.excludedRows }}</el-tag>
      </div>
      <el-alert v-if="preview.invalidRows" title="错误行不会写入业务表。可返回修改源文件，或选择错误行并记录原因后排除。" type="warning" :closable="false" />
      <div v-if="preview.invalidRows" class="exclude-bar">
        <el-input v-model="excludeReason" placeholder="填写排除原因" />
        <el-button type="warning" :disabled="!selectedInvalidIds.length || !excludeReason.trim()" @click="excludeRows">排除所选错误行</el-button>
      </div>
      <el-table :data="preview.rows" border height="390" @selection-change="onSelection">
        <el-table-column type="selection" width="46" :selectable="row => row.status === 'INVALID'" />
        <el-table-column prop="rowNo" label="Excel行" width="80" />
        <el-table-column label="数据" min-width="420" show-overflow-tooltip>
          <template #default="{ row }">{{ rowSummary(row.values) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误/排除原因" min-width="220" show-overflow-tooltip />
      </el-table>
    </div>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button v-if="stage === 0" type="primary" :loading="loading" :disabled="!file" @click="uploadPreview">解析预览</el-button>
      <el-button v-else type="primary" :loading="loading" :disabled="!canConfirmVendingImport(preview)" @click="confirm">确认入库</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { vendingApi } from '@/api/vending'
import { canConfirmVendingImport, vendingTypes } from '../vendingModel'

const props = defineProps({ modelValue: Boolean, initialType: { type: String, default: 'MACHINE' } })
const emit = defineEmits(['update:modelValue', 'confirmed'])
const stage = ref(0)
const type = ref(props.initialType)
const file = ref(null)
const preview = ref(null)
const loading = ref(false)
const selectedInvalidIds = ref([])
const excludeReason = ref('')

watch(() => props.modelValue, (visible) => { if (visible) type.value = props.initialType })

function onFile(uploadFile) { file.value = uploadFile.raw }
function onSelection(rows) { selectedInvalidIds.value = rows.filter(row => row.status === 'INVALID').map(row => row.rowId) }
function rowSummary(values) { return Object.entries(values || {}).map(([key, value]) => `${key}: ${value || '—'}`).join('；') }
function statusType(status) { return status === 'VALID' ? 'success' : status === 'INVALID' ? 'danger' : 'info' }

async function downloadTemplate() {
  const response = await vendingApi.template(type.value)
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = `自动售货机-${vendingTypes.find(item => item.name === type.value)?.label || ''}数据模板.xlsx`
  link.click()
  URL.revokeObjectURL(url)
}

async function uploadPreview() {
  loading.value = true
  try {
    preview.value = await vendingApi.preview(type.value, file.value)
    stage.value = 1
  } finally { loading.value = false }
}

async function excludeRows() {
  preview.value = await vendingApi.exclude(preview.value.batchId, {
    rowIds: selectedInvalidIds.value,
    reason: excludeReason.value.trim()
  })
  selectedInvalidIds.value = []
  excludeReason.value = ''
  ElMessage.success('错误行已保留审计记录并排除')
}

async function confirm() {
  loading.value = true
  try {
    const count = await vendingApi.confirm(preview.value.batchId)
    stage.value = 2
    ElMessage.success(`成功导入 ${count} 条`)
    emit('confirmed', { batchId: preview.value.batchId, count })
    close()
  } finally { loading.value = false }
}

function close() {
  emit('update:modelValue', false)
  stage.value = 0
  file.value = null
  preview.value = null
  selectedInvalidIds.value = []
  excludeReason.value = ''
}
</script>

<style scoped>
.stage-panel { margin-top:24px; }
.template-button { margin-left:12px; }
.summary-row { display:flex; gap:10px; margin-bottom:14px; }
.exclude-bar { display:flex; gap:10px; margin:14px 0; }
</style>
