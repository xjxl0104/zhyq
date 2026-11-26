<template>
  <div class="bills">
    <div class="bills-head">
      <strong>当月供应商账单（留存单据）</strong>
      <span>支持点击或直接拖拽文件到下方对应框内上传，上传后永久保存在本月下；仅上传者本人或管理员可删除。</span>
    </div>
    <div class="bill-grid">
      <div
        v-for="k in kinds"
        :key="k.key"
        class="bill-card"
        :class="{ 'is-dragging': draggingKey === k.key }"
        @dragenter.prevent="onDragEnter(k.key)"
        @dragover.prevent="onDragEnter(k.key)"
        @dragleave.prevent="onDragLeave($event, k.key)"
        @drop.prevent="onDrop($event, k)"
      >
        <div class="bill-title">
          <span>{{ k.label }}</span>
          <el-upload :show-file-list="false" :http-request="(opt) => upload(k, opt)" :disabled="!periodId"
                     accept=".pdf,.jpg,.jpeg,.png,.xlsx,.xls,.doc,.docx" multiple>
            <el-button size="small" type="primary" plain :loading="uploading === k.key">
              <el-icon><Upload /></el-icon>上传
            </el-button>
          </el-upload>
        </div>
        <ul v-if="(files[k.key] || []).length" class="file-list">
          <li v-for="f in files[k.key]" :key="f.id">
            <el-icon><Document /></el-icon>
            <span class="name" :title="f.originalName">{{ f.originalName }}</span>
            <span class="size">{{ size(f.fileSize) }}</span>
            <el-button link type="primary" size="small" @click="download(f)">下载</el-button>
            <el-button link type="danger" size="small" @click="remove(k, f)">删除</el-button>
          </li>
        </ul>
        <div v-else class="empty">
          {{ draggingKey === k.key ? '松开鼠标即可上传' : `还没有上传${k.label}（可将文件拖拽到此处）` }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, Upload } from '@element-plus/icons-vue'
import { fileApi } from '@/api/file'
import { downloadBlob } from '../feePeriod'

// kinds: [{ key: 'electric' | 'water' | 'garbage' | 'rent' | 'property', label }]
const props = defineProps({ periodId: { type: Number, default: null }, kinds: { type: Array, required: true } })
const emit = defineEmits(['changed'])

const files = reactive({})
const uploading = ref('')
const draggingKey = ref('')
const bizType = (k) => `energy_fee_${k.key}`

async function load() {
  if (!props.periodId) return
  await Promise.all(props.kinds.map(async (k) => {
    files[k.key] = await fileApi.list(bizType(k), props.periodId) || []
  }))
}

async function uploadFile(k, file) {
  if (!props.periodId || !file) return
  uploading.value = k.key
  try {
    const body = new FormData()
    body.append('file', file)
    body.append('bizType', bizType(k))
    body.append('bizId', props.periodId)
    await fileApi.upload(body)
    ElMessage.success(`「${file.name}」已上传到${k.label}`)
  } finally {
    uploading.value = ''
  }
}

async function upload(k, opt) {
  await uploadFile(k, opt.file)
  await load()
  emit('changed')
}

function onDragEnter(key) {
  if (!props.periodId) return
  draggingKey.value = key
}

function onDragLeave(e, key) {
  if (draggingKey.value !== key) return
  const rect = e.currentTarget.getBoundingClientRect()
  if (e.clientX <= rect.left || e.clientX >= rect.right || e.clientY <= rect.top || e.clientY >= rect.bottom) {
    draggingKey.value = ''
  }
}

async function onDrop(e, k) {
  draggingKey.value = ''
  if (!props.periodId) return
  const dropped = Array.from(e.dataTransfer?.files || [])
  if (!dropped.length) return
  for (const f of dropped) {
    await uploadFile(k, f)
  }
  await load()
  emit('changed')
}

async function download(f) {
  const res = await fileApi.download(f.id)
  downloadBlob(res, f.originalName || '账单')
}

async function remove(k, f) {
  await ElMessageBox.confirm(`确定删除「${f.originalName}」？删除后无法恢复。`, '删除单据', { type: 'warning' })
  await fileApi.remove(f.id)
  ElMessage.success('已删除')
  await load()
  emit('changed')
}

function size(n) {
  if (!n) return ''
  return n > 1048576 ? `${(n / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(n / 1024))} KB`
}

watch(() => props.periodId, load, { immediate: true })
</script>

<style scoped>
.bills { margin-bottom: 12px; padding: 12px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-fill-color-blank); }
.bills-head { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; margin-bottom: 10px; }
.bills-head span { font-size: 12px; color: var(--el-text-color-secondary); }
.bill-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 10px; }
.bill-card { border: 1px dashed var(--el-border-color); border-radius: 8px; padding: 8px 10px; transition: border-color 0.2s, background-color 0.2s; }
.bill-card.is-dragging { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.bill-title { display: flex; align-items: center; justify-content: space-between; font-weight: 600; margin-bottom: 6px; }
.file-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
.file-list li { display: flex; align-items: center; gap: 6px; font-size: 13px; }
.file-list .name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-list .size { color: var(--el-text-color-secondary); font-size: 12px; }
.empty { font-size: 12px; color: var(--el-text-color-placeholder); padding: 4px 0; }
</style>
