<script setup>
import { ref, watch } from 'vue'
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

const headers = { Authorization: `Bearer ${localStorage.getItem('zhyq_token') || ''}` }
const uploadData = { bizType: props.bizType, bizId: props.bizId }

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
    multiple
  >
    <el-button type="primary">选择文件</el-button>
    <template #tip>
      <div class="el-upload__tip">支持 jpg/png/pdf/doc/xls/dwg 等,单个不超过 20MB</div>
    </template>
  </el-upload>
</template>
