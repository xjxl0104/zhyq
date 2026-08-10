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
            :src="`/api/file/download/${img.fileId}`"
            :preview-src-list="detail.images.map(i => `/api/file/download/${i.fileId}`)"
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
          <el-upload
            :action="uploadUrl"
            :headers="uploadHeaders"
            list-type="picture-card"
            :file-list="fileList"
            :limit="5"
            :on-success="onUploadSuccess"
            accept="image/png,image/jpeg"
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item label="附件">
          <el-upload
            :action="uploadUrl"
            :headers="uploadHeaders"
            :file-list="attachList"
            :limit="5"
            :on-success="onAttachSuccess"
            :on-exceed="() => ElMessage.warning('最多上传5个附件')"
          >
            <el-button type="primary" plain><el-icon><Plus /></el-icon>上传附件</el-button>
            <template #tip><div class="el-upload__tip">支持任意格式，单个≤10MB，最多5个</div></template>
          </el-upload>
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { suggestionApi } from '@/api/suggestion'
import { uploadUrl } from '@/api/file'
import SuggestionWall from './SuggestionWall.vue'

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
const uploadHeaders = { Authorization: `Bearer ${localStorage.getItem('token') || ''}` }
const submitForm = reactive({ type: 2, title: '', content: '' })
const submitRules = {
  type: [{ required: true, message: '请选择类型' }],
  title: [{ required: true, message: '请输入标题' }]
}

function onUploadSuccess(res) {
  if (res.code === 200 && res.data) uploadedFileIds.value.push(res.data.id)
}

function onAttachSuccess(res) {
  if (res.code === 200 && res.data) uploadedFileIds.value.push(res.data.id)
}

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
}

onMounted(load)
</script>
