<template>
  <el-dialog :model-value="modelValue" title="发票上传与开票状态" width="680px" destroy-on-close
             :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy"
             :before-close="beforeClose" @update:model-value="changeVisible">
    <el-descriptions :column="2" border size="small" class="notice-info">
      <el-descriptions-item label="单据编号">{{ notice?.noticeNo || '-' }}</el-descriptions-item>
      <el-descriptions-item v-if="mode === 'lease'" label="费用类别">
        <el-tag :type="notice?.feeCategory === 'RENT' ? 'primary' : 'success'" size="small">
          {{ notice?.feeCategory === 'RENT' ? '租金' : '物业管理费' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item v-else label="费用类别">
        <el-tag type="warning" size="small">水电 / 垃圾处理费</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="租户/公司" :span="2">{{ notice?.tenantName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="厂房/单元" :span="2">{{ notice?.unitName || '-' }}</el-descriptions-item>
      <el-descriptions-item v-if="mode === 'lease'" label="开票/收取单位" :span="2">{{ notice?.payeeName || '-' }}</el-descriptions-item>
    </el-descriptions>

    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="invoice-error" />

    <div v-loading="loading" class="invoice-files">
      <div class="files-heading">
        <strong>发票附件（PDF / OFD / 图片等）</strong>
        <el-upload multiple :show-file-list="false" :http-request="upload" :before-upload="beforeUpload"
                   :disabled="loading || loadFailed || saving || removingId !== null || !notice?.id">
          <el-button type="primary" plain :loading="pendingUploads > 0"
                     :disabled="loading || loadFailed || saving || removingId !== null">上传发票</el-button>
        </el-upload>
      </div>
      <p class="invoice-tip">开好发票后直接上传发票文件，系统会自动勾选「已开票」，点击下方「保存」即可生效（通知单整行将点亮七彩流光效果）。每份不超过 100MB。</p>
      <el-button v-if="loadFailed" @click="loadFiles()">重新加载发票</el-button>
      <ul v-else-if="files.length" class="file-list">
        <li v-for="file in files" :key="file.id">
          <span class="file-name" :title="file.originalName">{{ file.originalName }}</span>
          <span class="file-size">{{ fileSize(file.fileSize) }}</span>
          <el-button link type="primary" @click="download(file)">下载</el-button>
          <el-button link type="danger" :disabled="busy" @click="remove(file)">删除</el-button>
        </li>
      </ul>
      <p v-else-if="!loading" class="invoice-tip">暂未上传发票文件</p>
    </div>

    <el-form label-width="90px" class="invoice-state">
      <el-form-item label="开票状态">
        <el-radio-group v-model="invoiceStatus" :disabled="saving || loading || loadFailed">
          <el-radio :value="0">未开票</el-radio>
          <el-radio :value="1">已开票</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="busy" @click="changeVisible(false)">关闭</el-button>
      <el-button type="primary" :loading="saving" :disabled="busy || loadFailed || !notice?.id" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { feePeriodApi, leaseFeeApi } from '@/api/energy'
import { fileApi } from '@/api/file'
import { startFileDownload } from '@/utils/fileDownload'

const props = defineProps({
  modelValue: Boolean,
  notice: { type: Object, default: null },
  /** utility = 水电垃圾费通知单 | lease = 租金/物业管理费通知单 */
  mode: { type: String, default: 'utility' }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const files = ref([])
const invoiceStatus = ref(0)
const loading = ref(false)
const loadFailed = ref(false)
const pendingUploads = ref(0)
const removingId = ref(null)
const saving = ref(false)
const error = ref('')
const busy = computed(() => loading.value || saving.value || pendingUploads.value > 0 || removingId.value !== null)
const bizType = computed(() => (props.mode === 'lease' ? 'lease_fee_notice_invoice' : 'fee_notice_invoice'))
let generation = 0
const active = (value) => value === generation && props.modelValue

watch(() => [props.modelValue, props.notice?.id, props.mode], () => {
  generation++
  files.value = []
  invoiceStatus.value = Number(props.notice?.invoiceStatus) === 1 ? 1 : 0
  error.value = ''
  loading.value = loadFailed.value = saving.value = false
  pendingUploads.value = 0
  removingId.value = null
  if (props.modelValue && props.notice?.id) loadFiles()
}, { immediate: true })
onBeforeUnmount(() => { generation++ })

async function loadFiles() {
  const current = generation
  const id = props.notice?.id
  if (!active(current) || !id || loading.value) return
  loading.value = true
  loadFailed.value = false
  error.value = ''
  try {
    const result = await fileApi.list(bizType.value, id)
    if (active(current)) files.value = result || []
  } catch (cause) {
    if (active(current)) {
      loadFailed.value = true
      error.value = cause?.message || '发票附件加载失败，请重新加载'
    }
  } finally {
    if (active(current)) loading.value = false
  }
}

function beforeUpload(file) {
  if (!props.notice?.id || loading.value || loadFailed.value || saving.value || removingId.value !== null) return false
  if (file.size > 100 * 1024 * 1024) {
    ElMessage.error('文件不能超过 100MB')
    return false
  }
  return true
}

async function upload({ file }) {
  const current = generation
  const id = props.notice?.id
  if (!active(current) || !id || !beforeUpload(file)) throw new Error('当前无法上传发票')
  pendingUploads.value++
  error.value = ''
  try {
    const data = new FormData()
    data.append('file', file)
    data.append('bizType', bizType.value)
    data.append('bizId', id)
    const saved = await fileApi.upload(data, { timeout: 10 * 60 * 1000 })
    if (active(current)) {
      files.value = [...files.value.filter((item) => String(item.id) !== String(saved.id)), saved]
      // 上传发票后自动勾选已开票，点击保存即可一步到位
      if (invoiceStatus.value !== 1) invoiceStatus.value = 1
      ElMessage.success('发票已上传，点击「保存」即完成开票登记')
    }
    return saved
  } catch (cause) {
    if (active(current)) error.value = cause?.message || '发票上传失败，请重试'
    throw cause
  } finally {
    if (active(current)) pendingUploads.value--
  }
}

async function remove(file) {
  if (busy.value) return
  const current = generation
  removingId.value = file.id
  try {
    await ElMessageBox.confirm(`确定删除发票文件「${file.originalName}」？`, '删除发票', { type: 'warning' })
    if (!active(current)) return
    await fileApi.remove(file.id)
    if (active(current)) files.value = files.value.filter((item) => String(item.id) !== String(file.id))
  } catch (cause) {
    if (active(current) && cause !== 'cancel' && cause !== 'close') error.value = cause?.message || '发票删除失败，请重试'
  } finally {
    if (active(current)) removingId.value = null
  }
}

async function download(file) {
  try { await startFileDownload(file.id, file.originalName) }
  catch (cause) { ElMessage.error(cause?.message || '发票下载失败') }
}

async function save() {
  if (busy.value || loadFailed.value || !props.notice?.id) return
  const current = generation
  const id = props.notice.id
  saving.value = true
  error.value = ''
  try {
    const api = props.mode === 'lease' ? leaseFeeApi : feePeriodApi
    const saved = await api.updateNoticeInvoice(id, { invoiceStatus: invoiceStatus.value })
    if (!active(current)) return
    emit('saved', { id, invoiceStatus: Number(saved.invoiceStatus) === 1 ? 1 : 0 })
    ElMessage.success(Number(saved.invoiceStatus) === 1 ? '已保存，通知单已点亮开票状态' : '开票状态已保存')
    emit('update:modelValue', false)
  } catch (cause) {
    if (active(current)) error.value = cause?.message || '开票状态保存失败，请重试'
  } finally {
    if (active(current)) saving.value = false
  }
}

function changeVisible(visible) {
  if (!visible && busy.value) return
  emit('update:modelValue', visible)
}
function beforeClose(done) { if (!busy.value) done() }
function fileSize(bytes) {
  if (!bytes) return ''
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`
}
</script>

<style scoped>
.notice-info { margin-bottom: 16px; }
.invoice-error { margin-bottom: 12px; }
.invoice-files { min-height: 90px; }
.files-heading { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.invoice-tip { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.6; }
.file-list { list-style: none; padding: 0; margin: 10px 0; }
.file-list li { display: flex; align-items: center; gap: 8px; padding: 8px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.file-name { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.file-size { white-space: nowrap; font-size: 12px; color: var(--el-text-color-secondary); }
.invoice-state { margin-top: 18px; }
</style>
