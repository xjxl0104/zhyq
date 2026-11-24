<template>
  <el-dialog :model-value="modelValue" title="缴费资料与状态" width="680px" destroy-on-close
             :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy"
             :before-close="beforeClose" @update:model-value="changeVisible">
    <el-descriptions :column="1" border class="notice-info">
      <el-descriptions-item label="通知单">{{ notice?.noticeNo || '-' }}</el-descriptions-item>
      <el-descriptions-item label="费用类别">
        <el-tag :type="notice?.feeCategory === 'RENT' ? 'primary' : 'success'" size="small">
          {{ notice?.feeCategory === 'RENT' ? '租金' : '物业管理费' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="收取单位">{{ notice?.payeeName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="租户/公司">{{ notice?.tenantName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="楼层/区域">{{ notice?.unitName || '-' }}</el-descriptions-item>
    </el-descriptions>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="payment-error" />
    <div v-loading="loading" class="payment-files">
      <div class="files-heading">
        <strong>缴费资料（汇款回单 / 凭证）</strong>
        <el-upload multiple :show-file-list="false" :http-request="upload" :before-upload="beforeUpload"
                   :disabled="loading || loadFailed || saving || removingId !== null || !notice?.id">
          <el-button type="primary" plain :loading="pendingUploads > 0" :disabled="loading || loadFailed || saving || removingId !== null">上传资料</el-button>
        </el-upload>
      </div>
      <p class="payment-tip">支持图片、PDF、Office、压缩包等，每份不超过 100MB。资料上传后即保存，可多次补充。</p>
      <el-button v-if="loadFailed" @click="loadFiles()">重新加载资料</el-button>
      <ul v-else-if="files.length" class="file-list">
        <li v-for="file in files" :key="file.id">
          <span class="file-name" :title="file.originalName">{{ file.originalName }}</span>
          <span class="file-size">{{ fileSize(file.fileSize) }}</span>
          <el-button link type="primary" @click="download(file)">下载</el-button>
          <el-button link type="danger" :disabled="busy" @click="remove(file)">删除</el-button>
        </li>
      </ul>
      <p v-else-if="!loading" class="payment-tip">暂无缴费资料</p>
    </div>
    <el-form label-width="90px" class="payment-state">
      <el-form-item label="缴费状态">
        <el-radio-group v-model="paymentStatus" :disabled="saving || loading || loadFailed">
          <el-radio :value="0">未缴费</el-radio>
          <el-radio :value="1">已缴费</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="busy" @click="changeVisible(false)">关闭</el-button>
      <el-button type="primary" :loading="saving" :disabled="busy || loadFailed || !notice?.id" @click="save">保存状态</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { leaseFeeApi } from '@/api/energy'
import { fileApi } from '@/api/file'
import { startFileDownload } from '@/utils/fileDownload'

const props = defineProps({ modelValue: Boolean, notice: { type: Object, default: null } })
const emit = defineEmits(['update:modelValue', 'saved'])
const files = ref([])
const paymentStatus = ref(0)
const loading = ref(false)
const loadFailed = ref(false)
const pendingUploads = ref(0)
const removingId = ref(null)
const saving = ref(false)
const error = ref('')
const busy = computed(() => loading.value || saving.value || pendingUploads.value > 0 || removingId.value !== null)
const BIZ_TYPE = 'lease_fee_notice_payment'
let generation = 0
const active = (value) => value === generation && props.modelValue

watch(() => [props.modelValue, props.notice?.id], () => {
  generation++
  files.value = []
  paymentStatus.value = Number(props.notice?.paymentStatus) === 1 ? 1 : 0
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
    const result = await fileApi.list(BIZ_TYPE, id)
    if (active(current)) files.value = result || []
  } catch (cause) {
    if (active(current)) {
      loadFailed.value = true
      error.value = cause?.message || '缴费资料加载失败，请重新加载'
    }
  } finally {
    if (active(current)) loading.value = false
  }
}
function beforeUpload(file) {
  if (!props.notice?.id || loading.value || loadFailed.value || saving.value || removingId.value !== null) return false
  if (file.size > 100 * 1024 * 1024) { ElMessage.error('文件不能超过 100MB'); return false }
  return true
}
async function upload({ file }) {
  const current = generation
  const id = props.notice?.id
  if (!active(current) || !id || !beforeUpload(file)) throw new Error('当前无法上传资料')
  pendingUploads.value++
  error.value = ''
  try {
    const data = new FormData()
    data.append('file', file)
    data.append('bizType', BIZ_TYPE)
    data.append('bizId', id)
    const saved = await fileApi.upload(data, { timeout: 10 * 60 * 1000 })
    if (active(current)) {
      files.value = [...files.value.filter(item => String(item.id) !== String(saved.id)), saved]
      ElMessage.success('缴费资料已上传')
    }
  } finally {
    if (active(current)) pendingUploads.value--
  }
}
async function download(file) { await startFileDownload(file.id, file.originalName) }
async function remove(file) {
  const current = generation
  await ElMessageBox.confirm(`确定删除「${file.originalName}」？删除后无法恢复。`, '删除缴费资料', { type: 'warning' })
  removingId.value = file.id
  try {
    await fileApi.remove(file.id)
    if (active(current)) {
      files.value = files.value.filter(item => String(item.id) !== String(file.id))
      ElMessage.success('已删除')
    }
  } finally {
    if (active(current)) removingId.value = null
  }
}
async function save() {
  const current = generation
  const id = props.notice?.id
  if (!active(current) || !id) return
  saving.value = true
  try {
    const updated = await leaseFeeApi.updateNoticePayment(id, { paymentStatus: paymentStatus.value })
    if (active(current)) {
      emit('saved', { id, paymentStatus: updated?.paymentStatus ?? paymentStatus.value })
      ElMessage.success('缴费状态已保存')
      changeVisible(false)
    }
  } finally {
    if (active(current)) saving.value = false
  }
}
function fileSize(size) {
  if (!size) return ''
  return size > 1048576 ? `${(size / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(size / 1024))} KB`
}
function beforeClose(done) { if (!busy.value) done() }
function changeVisible(v) { if (!busy.value) emit('update:modelValue', v) }
</script>

<style scoped>
.notice-info { margin-bottom: 12px; }
.payment-error { margin-bottom: 12px; }
.payment-files { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 12px; margin-bottom: 12px; }
.files-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.payment-tip { margin: 4px 0; font-size: 12px; color: var(--el-text-color-secondary); }
.file-list { list-style: none; padding: 0; margin: 8px 0 0; display: flex; flex-direction: column; gap: 6px; }
.file-list li { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.file-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-size { font-size: 12px; color: var(--el-text-color-secondary); }
.payment-state { margin-top: 6px; }
</style>
