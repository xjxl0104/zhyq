<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadUrl, fileApi } from '@/api/file'
import { startFileDownload } from '@/utils/fileDownload'
import PhotoCapture from '@/components/PhotoCapture.vue'
import GlassSurface from '@/components/GlassSurface.vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  bizType: { type: String, default: '' },
  bizId: { type: [Number, String], default: null },
  accept: { type: String, default: '' },
  camera: Boolean,
  photosOnly: Boolean,
  disabled: Boolean
})
const emit = defineEmits(['update:modelValue', 'busy'])

const fileList = ref([])
const pending = ref(0)
const cameraUploading = ref(false)
let disposed = false
onBeforeUnmount(() => { disposed = true; emit('busy', false) })
watch(() => pending.value > 0 || cameraUploading.value, busy => emit('busy', busy))
async function uploadPhoto(file) {
  if (!beforeUpload(file)) return
  cameraUploading.value = true
  const data = new FormData()
  data.append('file', file)
  Object.entries(uploadData.value).forEach(([key, value]) => data.append(key, value))
  try {
    const uploaded = await fileApi.upload(data)
    onSuccess({ code: 0, data: uploaded })
    ElMessage.success('照片已上传')
  } catch { onError() }
  finally { cameraUploading.value = false }
}

// 把外部传入的附件记录映射成 el-upload 需要的结构
watch(() => props.modelValue, (val) => {
  fileList.value = (val || []).map(f => ({ name: f.originalName, url: f.url, id: f.id, raw: f }))
}, { immediate: true })

// computed:跟随 props 变化与当前 token(直传不走 axios 拦截器,需手动带头)
const headers = computed(() => ({ Authorization: `Bearer ${localStorage.getItem('zhyq_token') || ''}` }))
// bizId 为空时必须整个不发这个字段:FormData 只装字符串,null 会被转成字面量 "null",
// 后端 @RequestParam Long bizId 拿它转 Long 直接抛 NumberFormatException(整个上传 500)。
// 新建单据时本就还没有 ID —— 附件先传后回填(见各页面 persist 里的 fileApi.attach)。
const uploadData = computed(() => {
  const data = { bizType: props.bizType }
  if (props.bizId !== null && props.bizId !== undefined && props.bizId !== '') {
    data.bizId = props.bizId
  }
  return data
})

function onSuccess(res) {
  pending.value = Math.max(0, pending.value - 1)
  if (disposed) return
  // 后端 Result 结构 { code, message, data }
  if (res.code === 0 && res.data) {
    const next = [...props.modelValue, res.data]
    emit('update:modelValue', next)
  } else {
    ElMessage.error(res.message || '上传失败')
  }
}

function onError() {
  pending.value = Math.max(0, pending.value - 1)
  ElMessage.error('上传失败')
}

function beforeUpload(file) {
  if (props.disabled) return false
  if (props.photosOnly) {
    if (!/\.(jpe?g|png)$/i.test(file.name || '')) { ElMessage.error('处理照片仅支持 JPG、PNG 格式'); return false }
    if (file.size > 20 * 1024 * 1024) { ElMessage.error('处理照片不能超过20MB'); return false }
    if (props.modelValue.length + pending.value >= 20) { ElMessage.error('最多上传20张处理照片'); return false }
  }
  const is100M = file.size / 1024 / 1024 <= 100
  if (!is100M) ElMessage.error('文件不能超过 100MB')
  if (is100M) pending.value++
  return is100M
}

async function onRemove(uploadFile) {
  const id = uploadFile.id || (uploadFile.raw && uploadFile.raw.id)
  if (id) {
    // 营销合同和资金附件保留归档；提交前移除只是取消本次选择。
    if (!props.bizType.startsWith('mkt_')) {
      try { await fileApi.remove(id) } catch (e) { /* 忽略,前端仍移除 */ }
    }
    const next = props.modelValue.filter(f => f.id !== id)
    emit('update:modelValue', next)
  }
}

// 点击文件名 → 鉴权后交给浏览器下载管理器。
async function onPreview(uploadFile) {
  const id = uploadFile.id || (uploadFile.raw && uploadFile.raw.id)
    || (uploadFile.response && uploadFile.response.data && uploadFile.response.data.id)
  if (!id) return
  try {
    await startFileDownload(id, uploadFile.name)
    ElMessage.success('已交给浏览器下载，请在下载列表查看进度')
  } catch (e) {
    ElMessage.error('下载失败')
  }
}
</script>

<template>
  <GlassSurface variant="upload">
    <div v-if="camera" style="margin-bottom: 10px">
      <PhotoCapture :disabled="disabled || pending > 0 || cameraUploading" @capture="uploadPhoto" />
      <span v-if="cameraUploading" style="margin-left: 10px">照片上传中…</span>
    </div>
    <el-upload
      :action="uploadUrl"
      :headers="headers"
      :data="uploadData"
      :file-list="fileList"
      :accept="photosOnly ? 'image/jpeg,image/png' : accept"
      :disabled="disabled"
      :on-success="onSuccess"
      :on-error="onError"
      :before-upload="beforeUpload"
      :on-remove="onRemove"
      :on-preview="onPreview"
      multiple
    >
      <el-button type="primary" :disabled="disabled">{{ photosOnly ? '上传照片' : '选择文件' }}</el-button>
      <template #tip>
        <div class="el-upload__tip">
          <template v-if="photosOnly">支持 JPG、PNG；单张不超过20MB，最多20张。点击文件名查看。</template>
          <template v-else>支持各种格式(文档/表格/图片/图纸/压缩包/音视频等),可执行与脚本类文件除外;
          单个不超过 100MB;点击文件名下载</template>
        </div>
      </template>
    </el-upload>
  </GlassSurface>
</template>
