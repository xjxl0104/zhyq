<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadUrl, fileApi } from '@/api/file'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  bizType: { type: String, default: '' },
  bizId: { type: [Number, String], default: null },
  accept: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const fileList = ref([])

// 把外部传入的附件记录映射成 el-upload 需要的结构
watch(() => props.modelValue, (val) => {
  fileList.value = (val || []).map(f => ({ name: f.originalName, url: f.url, id: f.id, raw: f }))
}, { immediate: true })

// computed:跟随 props 变化与当前 token(直传不走 axios 拦截器,需手动带头)
const headers = computed(() => ({ Authorization: `Bearer ${localStorage.getItem('zhyq_token') || ''}` }))
const uploadData = computed(() => ({ bizType: props.bizType, bizId: props.bizId }))

function onSuccess(res) {
  // 后端 Result 结构 { code, message, data }
  if (res.code === 0 && res.data) {
    const next = [...props.modelValue, res.data]
    emit('update:modelValue', next)
  } else {
    ElMessage.error(res.message || '上传失败')
  }
}

function onError() {
  ElMessage.error('上传失败')
}

function beforeUpload(file) {
  const is20M = file.size / 1024 / 1024 <= 20
  if (!is20M) ElMessage.error('文件不能超过 20MB')
  return is20M
}

async function onRemove(uploadFile) {
  const id = uploadFile.id || (uploadFile.raw && uploadFile.raw.id)
  if (id) {
    try { await fileApi.remove(id) } catch (e) { /* 忽略,前端仍移除 */ }
    const next = props.modelValue.filter(f => f.id !== id)
    emit('update:modelValue', next)
  }
}

// 点击文件名 → 鉴权下载(/uploads 静态匿名访问已关闭)
async function onPreview(uploadFile) {
  const id = uploadFile.id || (uploadFile.raw && uploadFile.raw.id)
    || (uploadFile.response && uploadFile.response.data && uploadFile.response.data.id)
  if (!id) return
  try {
    const res = await fileApi.download(id)
    const blobUrl = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = uploadFile.name || 'file'
    a.click()
    URL.revokeObjectURL(blobUrl)
  } catch (e) {
    ElMessage.error('下载失败')
  }
}
</script>

<template>
  <el-upload
    :action="uploadUrl"
    :headers="headers"
    :data="uploadData"
    :file-list="fileList"
    :accept="accept"
    :on-success="onSuccess"
    :on-error="onError"
    :before-upload="beforeUpload"
    :on-remove="onRemove"
    :on-preview="onPreview"
    multiple
  >
    <el-button type="primary">选择文件</el-button>
    <template #tip>
      <div class="el-upload__tip">支持 jpg/png/pdf/doc/xls/dwg 等,单个不超过 20MB;点击文件名下载</div>
    </template>
  </el-upload>
</template>
