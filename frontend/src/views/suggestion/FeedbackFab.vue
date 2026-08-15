<template>
  <!-- 全局悬浮按钮 -->
  <el-button class="feedback-fab" type="primary" circle @click="visible = true">
    <el-icon :size="20"><ChatDotRound /></el-icon>
  </el-button>

  <!-- 提交弹窗 -->
  <el-dialog v-model="visible" title="提交建议/Bug" width="540px" :close-on-click-modal="false">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
      <el-form-item label="类型" prop="type">
        <el-radio-group v-model="form.type">
          <el-radio :value="1">Bug</el-radio>
          <el-radio :value="2">建议</el-radio>
          <el-radio :value="3">其他</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" maxlength="50" show-word-limit placeholder="简要描述问题或想法" />
      </el-form-item>
      <el-form-item label="详细说明">
        <el-input v-model="form.content" type="textarea" :rows="4" placeholder="可选，补充说明" />
      </el-form-item>
      <el-form-item label="截图">
        <el-upload
          :http-request="doUpload"
          list-type="picture-card"
          :file-list="fileList"
          :limit="5"
          :before-upload="beforeUpload"
          :on-success="onUploadSuccess"
          :on-exceed="() => ElMessage.warning('最多上传5张')"
          accept="image/png,image/jpeg"
        >
          <el-icon><Plus /></el-icon>
          <template #tip><div class="el-upload__tip">支持 jpg/png，单张≤5MB，最多5张。可 Ctrl+V 粘贴截图</div></template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Plus } from '@element-plus/icons-vue'
import { suggestionApi } from '@/api/suggestion'
import { fileApi } from '@/api/file'

const visible = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const fileList = ref([])
const uploadedFileIds = ref([])

const form = reactive({ type: 1, title: '', content: '' })
const rules = {
  type: [{ required: true, message: '请选择类型' }],
  title: [{ required: true, message: '请输入标题' }]
}

function beforeUpload(file) {
  const isImage = ['image/jpeg', 'image/png'].includes(file.type)
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isImage) { ElMessage.error('只能上传 jpg/png 图片'); return false }
  if (!isLt5M) { ElMessage.error('图片大小不能超过 5MB'); return false }
  return true
}

// 走 axios 实例复用 token 拦截器: el-upload 的 action 是浏览器原生直传,
// 绕过拦截器拿不到 token, 会被 Spring Security 401 挡在 controller 之外
async function doUpload(option) {
  try {
    const fd = new FormData()
    fd.append('file', option.file)
    const res = await fileApi.upload(fd)   // 拦截器已拆包, res 即 SysFile
    if (!res?.id) throw new Error('上传响应缺少文件 id')
    uploadedFileIds.value.push(res.id)
    option.onSuccess(res)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '上传失败')
    option.onError(e)
  }
}

async function submit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    await suggestionApi.submit({
      ...form,
      sourceUrl: window.location.href,
      fileIds: uploadedFileIds.value
    })
    ElMessage.success('提交成功，感谢您的反馈')
    visible.value = false
    resetForm()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.type = 1
  form.title = ''
  form.content = ''
  fileList.value = []
  uploadedFileIds.value = []
}

function handlePaste(e) {
  if (!visible.value) return
  const items = e.clipboardData?.items
  if (!items) return
  for (const item of items) {
    if (item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file && uploadedFileIds.value.length < 5) {
        const formData = new FormData()
        formData.append('file', file, `screenshot_${Date.now()}.png`)
        // 同样走 fileApi 而非裸 fetch, 复用拦截器的 token 与错误处理
        fileApi.upload(formData).then(res => {
          if (!res?.id) return
          uploadedFileIds.value.push(res.id)
          fileList.value.push({ name: res.originalName, url: `/api/file/download/${res.id}` })
        }).catch(err => {
          ElMessage.error(err?.response?.data?.message || '截图上传失败')
        })
      }
    }
  }
}

onMounted(() => document.addEventListener('paste', handlePaste))
onUnmounted(() => document.removeEventListener('paste', handlePaste))
</script>

<style scoped>
.feedback-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 2000;
  width: 48px;
  height: 48px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
</style>
