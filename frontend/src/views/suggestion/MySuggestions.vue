<template>
  <div class="page-container">
    <div class="table-card">
      <div class="toolbar">
        <h3 style="margin:0">我的建议</h3>
        <el-button type="primary" @click="showSubmit = true"><el-icon><Plus /></el-icon>新建意见</el-button>
      </div>
      <SuggestionWall v-loading="loading" :items="list" :type-map="typeMap" :status-map="statusMap"
                      @open="row => viewDetail(row.id)" />
      <el-pagination class="pager" background layout="total, prev, pager, next"
                     :total="total" v-model:current-page="page" :page-size="20" @change="load" />
    </div>

    <el-dialog v-model="detailVisible" title="建议详情" width="600px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="标题" :span="2">{{ detail.suggestion.title }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ typeMap[detail.suggestion.type] }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusMap[detail.suggestion.status] }}</el-descriptions-item>
          <el-descriptions-item label="详细说明" :span="2">{{ detail.suggestion.content || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.images.length" style="margin-top:12px">
          <p style="font-weight:500;margin-bottom:8px">截图</p>
          <el-image v-for="img in detail.images" :key="img.id"
            :src="srcFor(img.fileId)"
            :preview-src-list="detail.images.map(i => srcFor(i.fileId)).filter(Boolean)"
            style="width:80px;height:80px;margin-right:8px" fit="cover" />
        </div>
        <div v-if="detail.logs.length" style="margin-top:16px">
          <p style="font-weight:500;margin-bottom:8px">处理记录</p>
          <el-timeline>
            <el-timeline-item v-for="log in detail.logs" :key="log.id" :timestamp="log.createdAt" placement="top">
              <span>{{ log.operatorName }} - {{ log.action }}</span>
              <span v-if="log.remark"> | {{ log.remark }}</span>
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>
    </el-dialog>

    <!-- 新建意见弹窗 -->
    <el-dialog v-model="showSubmit" title="新建意见" width="540px" :close-on-click-modal="false">
      <el-form :model="submitForm" :rules="submitRules" ref="submitFormRef" label-width="80px">
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="submitForm.type">
            <el-radio :value="1">Bug</el-radio>
            <el-radio :value="2">建议</el-radio>
            <el-radio :value="3">其他</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="submitForm.title" maxlength="50" show-word-limit placeholder="简要描述" />
        </el-form-item>
        <el-form-item label="详细说明">
          <el-input v-model="submitForm.content" type="textarea" :rows="4" placeholder="可选" />
        </el-form-item>
        <el-form-item label="截图">
          <GlassSurface variant="upload" class="suggestion-upload-surface">
            <el-upload
              :http-request="doImageUpload"
              list-type="picture-card"
              :file-list="fileList"
              :limit="3"
              :before-upload="beforeImageUpload"
              :on-exceed="() => ElMessage.warning('最多上传3张截图')"
              accept="image/png,image/jpeg"
            >
              <el-icon><Plus /></el-icon>
            </el-upload>
          </GlassSurface>
        </el-form-item>
        <el-form-item label="附件">
          <GlassSurface variant="upload" class="suggestion-upload-surface">
            <el-upload
              :http-request="doAttachUpload"
              :file-list="attachList"
              :limit="2"
              :before-upload="beforeAttachUpload"
              :on-exceed="() => ElMessage.warning('最多上传2个附件')"
            >
              <el-button type="primary" plain><el-icon><Plus /></el-icon>上传附件</el-button>
              <template #tip><div class="el-upload__tip">支持 pdf/doc/docx/xls/xlsx/txt/zip 等，单个≤20MB；截图与附件合计最多5个</div></template>
            </el-upload>
          </GlassSurface>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSubmit = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="doSubmit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { suggestionApi } from '@/api/suggestion'
import { fileApi } from '@/api/file'
import { useAuthImage } from '@/utils/authImage'
import SuggestionWall from './SuggestionWall.vue'
import GlassSurface from '@/components/GlassSurface.vue'

// 截图预览必须走鉴权下载换 blob：直接使用文件地址不带 token，会返回 401。
const { srcFor, resolveAll, revokeAll } = useAuthImage()

const typeMap = { 1: 'Bug', 2: '建议', 3: '其他' }
const statusMap = { 1: '待处理', 2: '已确认', 3: '处理中', 4: '已解决', 5: '已采纳', 6: '已关闭' }
const statusStyle = { 1: 'info', 2: 'warning', 3: '', 4: 'success', 5: 'success', 6: 'info' }

const list = ref([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const detailVisible = ref(false)
const detail = ref(null)

const showSubmit = ref(false)
const submitting = ref(false)
const submitFormRef = ref(null)
const fileList = ref([])
const attachList = ref([])
const uploadedFileIds = ref([])
const submitForm = reactive({ type: 2, title: '', content: '' })
const submitRules = {
  type: [{ required: true, message: '请选择类型' }],
  title: [{ required: true, message: '请输入标题' }]
}

// 后端 FileStorageService 的扩展名白名单与大小上限,前端先挡一道,避免传完才报错
const IMAGE_EXT = ['png', 'jpg', 'jpeg']
const ATTACH_EXT = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'csv', 'zip', 'rar', '7z']
const MAX_SIZE = 20 * 1024 * 1024

function checkFile(file, exts) {
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  if (!exts.includes(ext)) {
    ElMessage.error(`不支持的格式 .${ext}，仅支持 ${exts.join('/')}`)
    return false
  }
  if (file.size > MAX_SIZE) {
    ElMessage.error('单个文件不能超过 20MB')
    return false
  }
  // 后端校验截图与附件合计最多 5 个,这里同步拦一道
  if (uploadedFileIds.value.length >= 5) {
    ElMessage.error('截图与附件合计最多 5 个')
    return false
  }
  return true
}

const beforeImageUpload = (file) => checkFile(file, IMAGE_EXT)
const beforeAttachUpload = (file) => checkFile(file, ATTACH_EXT)

// 用 :http-request 走 axios 实例,复用 request.js 的 token 拦截器。
// 不能用 el-upload 的 action: 那是浏览器原生直传,绕过拦截器拿不到 token
async function uploadViaApi(option) {
  const fd = new FormData()
  fd.append('file', option.file)
  try {
    const res = await fileApi.upload(fd)
    if (res?.id) {
      uploadedFileIds.value.push(res.id)
      option.onSuccess(res)
    } else {
      throw new Error('上传响应缺少文件 id')
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '上传失败')
    option.onError(e)
  }
}

const doImageUpload = uploadViaApi
const doAttachUpload = uploadViaApi

async function doSubmit() {
  await submitFormRef.value.validate()
  submitting.value = true
  try {
    await suggestionApi.submit({ ...submitForm, sourceUrl: window.location.href, fileIds: uploadedFileIds.value })
    ElMessage.success('提交成功')
    showSubmit.value = false
    submitForm.type = 2; submitForm.title = ''; submitForm.content = ''
    fileList.value = []; attachList.value = []; uploadedFileIds.value = []
    load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '提交失败')
  } finally { submitting.value = false }
}

async function load() {
  loading.value = true
  try {
    const res = await suggestionApi.mine({ page: page.value, size: 20 })
    list.value = res.records || []
    total.value = res.total || 0
  } finally { loading.value = false }
}

async function viewDetail(id) {
  const res = await suggestionApi.mineDetail(id)
  detail.value = res
  detailVisible.value = true
  await resolveAll((res.images || []).map(i => i.fileId))
}

onMounted(load)
onUnmounted(revokeAll)
</script>

<style scoped>
.suggestion-upload-surface { width: 100%; }
</style>
