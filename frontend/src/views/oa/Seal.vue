<template>
  <div class="seal-page">
    <div class="page-head">
      <div>
        <div class="page-title">电子印章管理</div>
        <div class="page-sub">集中管理园区电子印章（透明底 PNG），可在「缴费汇总」通知单模板及「装修及动火」审批表中直接调用与拖拽盖章</div>
      </div>
      <div class="head-actions">
        <el-button type="primary" @click="openAdd">+ 上传电子印章</el-button>
      </div>
    </div>

    <div v-loading="loading" class="seal-grid">
      <div v-for="s in list" :key="s.id" class="seal-card" :class="{ disabled: s.status !== 1 }">
        <div class="seal-preview-box">
          <img v-if="s.imageData" :src="s.imageData" :alt="s.name" class="seal-img" />
          <el-tag v-if="s.isDefault === 1" size="small" type="danger" effect="dark" class="badge-default">默认印章</el-tag>
          <el-tag size="small" :type="s.status === 1 ? 'success' : 'info'" class="badge-status">
            {{ s.status === 1 ? '启用中' : '已停用' }}
          </el-tag>
        </div>
        <div class="seal-info">
          <div class="seal-name" :title="s.name">{{ s.name }}</div>
          <div class="seal-meta">
            <span>{{ s.category || '电子印章' }}</span>
            <span>·</span>
            <span>标准宽 {{ s.defaultWidthMm || 42 }}mm</span>
          </div>
          <div v-if="s.orgName" class="seal-org" :title="s.orgName">{{ s.orgName }}</div>
          <div v-if="s.remark" class="seal-remark">{{ s.remark }}</div>
        </div>
        <div class="seal-actions">
          <el-button v-if="s.isDefault !== 1 && s.status === 1" link type="warning" size="small" @click="setDefault(s)">设为默认</el-button>
          <el-button link type="primary" size="small" @click="openEdit(s)">编辑</el-button>
          <el-button link :type="s.status === 1 ? 'info' : 'success'" size="small" @click="toggleStatus(s)">
            {{ s.status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-popconfirm title="确认删除该电子印章？已引用该印章的模板将自动取消盖章。" @confirm="remove(s)">
            <template #reference>
              <el-button link type="danger" size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </div>
      </div>

      <div class="seal-card add-card" @click="openAdd">
        <div class="add-plus">+</div>
        <div class="add-label">新增电子印章</div>
        <div class="add-hint">支持 PNG / JPG，可自动去白底转透明</div>
      </div>
    </div>

    <el-dialog v-model="dlgVisible" :title="form.id ? '编辑电子印章' : '上传电子印章'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="98px">
        <el-form-item label="印章名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：DIPARK数智云仓产业园物业管理服务部章" />
        </el-form-item>
        <el-form-item label="印章分类">
          <el-select v-model="form.category" allow-create filterable default-first-option style="width: 100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属单位">
          <el-input v-model="form.orgName" placeholder="例如：DIPARK数智云仓产业园物业管理服务部" />
        </el-form-item>
        <el-form-item label="默认印章宽度">
          <el-input-number v-model="form.defaultWidthMm" :min="20" :max="80" :step="2" />
          <span class="unit-tip">mm（打印标准一般为 38~48mm，可在排版界面自由缩放）</span>
        </el-form-item>
        <el-form-item label="印章图片" prop="imageData">
          <div class="upload-area">
            <div class="upload-controls">
              <input ref="fileInput" type="file" accept="image/png,image/jpeg,image/webp" class="hidden-file" @change="onFilePick" />
              <el-button size="small" type="primary" plain @click="fileInput?.click()">选择印章图片</el-button>
              <el-checkbox v-model="autoRemoveWhite" size="small" @change="reprocessRawImage">智能去除白色背景</el-checkbox>
            </div>
            <div v-if="form.imageData" class="upload-preview">
              <img :src="form.imageData" alt="印章预览" />
            </div>
            <div class="upload-tip">推荐上传透明底 PNG 图片；若为白底扫描件，勾选「智能去除白色背景」即可自动扣成透明底。</div>
          </div>
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注说明">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选：用途说明（如物业费通知单、装修动火审批）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存印章</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { sealApi } from '@/api/oa'

const categories = ['物业专用章', '财务专用章', '公章', '合同专用章', '安环审批章', '其他印章']
const loading = ref(false)
const list = ref([])
const dlgVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const fileInput = ref()
const autoRemoveWhite = ref(false)
const rawFileDataUrl = ref('')

const emptyForm = () => ({
  id: null,
  name: '',
  category: '物业专用章',
  orgName: '',
  imageData: '',
  defaultWidthMm: 44,
  isDefault: 0,
  status: 1,
  remark: ''
})
const form = ref(emptyForm())
const rules = {
  name: [{ required: true, message: '请输入印章名称', trigger: 'blur' }],
  imageData: [{ required: true, message: '请上传印章图片', trigger: 'change' }]
}

async function load() {
  loading.value = true
  try {
    list.value = (await sealApi.list()) || []
  } finally {
    loading.value = false
  }
}

function openAdd() {
  form.value = emptyForm()
  rawFileDataUrl.value = ''
  autoRemoveWhite.value = false
  dlgVisible.value = true
}

function openEdit(row) {
  form.value = { ...row }
  rawFileDataUrl.value = row.imageData || ''
  autoRemoveWhite.value = false
  dlgVisible.value = true
}

function onFilePick(e) {
  const file = e.target.files?.[0]
  if (!file) return
  e.target.value = ''
  if (file.size > 3 * 1024 * 1024) {
    ElMessage.warning('印章图片请控制在 3MB 以内')
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    rawFileDataUrl.value = String(reader.result || '')
    // JPG 图片默认开启智能去白底，PNG 保持原透明通道
    autoRemoveWhite.value = file.type === 'image/jpeg'
    processImage(rawFileDataUrl.value, autoRemoveWhite.value)
  }
  reader.readAsDataURL(file)
}

function reprocessRawImage() {
  if (!rawFileDataUrl.value) return
  processImage(rawFileDataUrl.value, autoRemoveWhite.value)
}

function processImage(srcDataUrl, stripWhite) {
  const img = new Image()
  img.onload = () => {
    const maxDim = 640
    const ratio = Math.min(1, maxDim / Math.max(img.width, img.height))
    const w = Math.max(1, Math.round(img.width * ratio))
    const h = Math.max(1, Math.round(img.height * ratio))
    const canvas = document.createElement('canvas')
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')
    ctx.clearRect(0, 0, w, h)
    ctx.drawImage(img, 0, 0, w, h)
    if (stripWhite) {
      const imgData = ctx.getImageData(0, 0, w, h)
      const d = imgData.data
      for (let i = 0; i < d.length; i += 4) {
        const r = d[i], g = d[i + 1], b = d[i + 2], a = d[i + 3]
        if (a === 0) continue
        const brightness = (r + g + b) / 3
        const maxDiff = Math.max(Math.abs(r - g), Math.abs(g - b), Math.abs(r - b))
        if (brightness > 235 && maxDiff < 24) {
          d[i + 3] = 0
        } else if (brightness > 210 && maxDiff < 32) {
          d[i + 3] = Math.round(a * (235 - brightness) / 25)
        }
      }
      ctx.putImageData(imgData, 0, 0)
    }
    form.value.imageData = canvas.toDataURL('image/png')
  }
  img.src = srcDataUrl
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.value.id) {
      await sealApi.update(form.value)
    } else {
      await sealApi.add(form.value)
    }
    ElMessage.success('电子印章已保存')
    dlgVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function setDefault(row) {
  await sealApi.setDefault(row.id)
  ElMessage.success(`已将「${row.name}」设为默认印章`)
  await load()
}

async function toggleStatus(row) {
  const nextStatus = row.status === 1 ? 0 : 1
  await sealApi.update({ ...row, status: nextStatus })
  ElMessage.success(nextStatus === 1 ? '已启用该印章' : '已停用该印章')
  await load()
}

async function remove(row) {
  await sealApi.remove(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.seal-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px;
  border: 1px solid #eceef5;
}
.page-title {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
}
.page-sub {
  font-size: 13px;
  color: #64748b;
  margin-top: 4px;
}
.seal-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(270px, 1fr));
  gap: 16px;
}
.seal-card {
  background: #fff;
  border: 1px solid #e6e9f2;
  border-radius: 14px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}
.seal-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.07);
}
.seal-card.disabled {
  opacity: 0.65;
}
.seal-preview-box {
  position: relative;
  height: 176px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-image:
    linear-gradient(45deg, #f3f5f9 25%, transparent 25%),
    linear-gradient(-45deg, #f3f5f9 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #f3f5f9 75%),
    linear-gradient(-45deg, transparent 75%, #f3f5f9 75%);
  background-size: 16px 16px;
  background-position: 0 0, 0 8px, 8px -8px, -8px 0;
  border-bottom: 1px solid #edf0f7;
  padding: 14px;
}
.seal-img {
  max-width: 82%;
  max-height: 138px;
  object-fit: contain;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.08));
}
.badge-default {
  position: absolute;
  top: 10px;
  left: 10px;
}
.badge-status {
  position: absolute;
  top: 10px;
  right: 10px;
}
.seal-info {
  padding: 12px 14px 10px;
  flex: 1;
}
.seal-name {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.seal-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #6366f1;
  margin-top: 4px;
}
.seal-org {
  font-size: 12px;
  color: #475569;
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.seal-remark {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.seal-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  padding: 8px 12px 10px;
  border-top: 1px solid #f1f5f9;
  background: #fafbfe;
}
.add-card {
  min-height: 290px;
  align-items: center;
  justify-content: center;
  border: 2px dashed #cbd5e1;
  background: #f8fafc;
  cursor: pointer;
  color: #64748b;
  text-align: center;
  padding: 20px;
}
.add-card:hover {
  border-color: #6366f1;
  color: #4f46e5;
  background: #f5f3ff;
}
.add-plus {
  font-size: 38px;
  line-height: 1;
  font-weight: 300;
}
.add-label {
  font-size: 15px;
  font-weight: 600;
  margin-top: 8px;
}
.add-hint {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}
.unit-tip {
  margin-left: 8px;
  font-size: 12px;
  color: #64748b;
}
.upload-area {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.upload-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}
.hidden-file {
  display: none;
}
.upload-preview {
  height: 148px;
  border: 1px dashed #cbd5e1;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-image:
    linear-gradient(45deg, #f3f5f9 25%, transparent 25%),
    linear-gradient(-45deg, #f3f5f9 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #f3f5f9 75%),
    linear-gradient(-45deg, transparent 75%, #f3f5f9 75%);
  background-size: 14px 14px;
  background-position: 0 0, 0 7px, 7px -7px, -7px 0;
  padding: 10px;
}
.upload-preview img {
  max-height: 124px;
  max-width: 80%;
  object-fit: contain;
}
.upload-tip {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.4;
}
</style>
