<template>
  <div class="live-cam-wrap">
    <div class="cam-stage">
      <video v-show="!capturedPreview" ref="videoRef" autoplay muted playsinline class="cam-video" />
      <img v-if="capturedPreview" :src="capturedPreview" alt="现场水印照片预览" class="cam-preview" />

      <!-- 实时水印预览叠加层（拍摄瞬间会同步永久烧录进图像像素中） -->
      <div v-if="!capturedPreview && ready" class="live-wm-overlay">
        <div class="wm-title">{{ watermarkInfo.taskTitle }}</div>
        <div v-if="watermarkInfo.itemName">检查项：{{ watermarkInfo.itemName }}</div>
        <div>巡检人：{{ watermarkInfo.inspectorLine }}</div>
        <div>区域：{{ watermarkInfo.area || '-' }}</div>
        <div>时间：{{ liveClock }}</div>
      </div>

      <div v-if="!ready && !capturedPreview && !camError" class="cam-placeholder">
        正在唤起摄像头，请允许浏览器访问相机...
      </div>
    </div>

    <el-alert v-if="camError" type="warning" show-icon :closable="false" style="margin-top: 8px" :title="camError">
      <template #default>
        <div class="err-hint">
          <span>为防止传旧图作弊，系统仅允许现场即时拍照（不支持从相册选图）。</span>
          <el-button v-if="isTouchDevice" size="small" type="primary" style="margin-left: 8px" @click="triggerNativeCamera">
            调起手机原生相机拍摄
          </el-button>
          <el-button size="small" style="margin-left: 6px" @click="startCamera">重试摄像头</el-button>
        </div>
      </template>
    </el-alert>

    <!-- 仅在移动触控端 getUserMedia 不可用时兜底调起系统相机（强制 capture="environment"） -->
    <input
      ref="nativeCamInput"
      type="file"
      accept="image/*"
      capture="environment"
      hidden
      @change="onNativeCaptured"
    />

    <div class="cam-actions">
      <template v-if="!capturedPreview">
        <el-button v-if="ready" size="small" @click="flipCamera">切换前后镜头</el-button>
        <el-button v-if="isTouchDevice && !ready" size="small" type="primary" plain @click="triggerNativeCamera">
          <el-icon><Camera /></el-icon>手机相机即时拍
        </el-button>
        <el-button type="primary" :disabled="!ready" @click="snapFromVideo">
          <el-icon><Camera /></el-icon>立即拍照（自动加水印）
        </el-button>
      </template>
      <template v-else>
        <el-button @click="retake">重新拍摄</el-button>
        <el-button type="success" :loading="uploading" @click="confirmUpload">
          确认使用此水印照片
        </el-button>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import { fileApi } from '@/api/file'
import { routineInspectionApi } from '@/api/property'

const props = defineProps({
  inspectorName: { type: String, default: '' },
  inspectorPosition: { type: String, default: '' },
  inspectorPhone: { type: String, default: '' },
  area: { type: String, default: '' },
  taskTitle: { type: String, default: '' },
  itemName: { type: String, default: '' },
  resultLabel: { type: String, default: '正常' }
})
const emit = defineEmits(['uploaded'])

const videoRef = ref(null)
const nativeCamInput = ref(null)
const ready = ref(false)
const camError = ref('')
const uploading = ref(false)
const facingMode = ref('environment')
const capturedPreview = ref('')
const capturedBlob = ref(null)
const capturedAtText = ref('')
const capturedWatermarkText = ref('')

const isTouchDevice = computed(() => typeof window !== 'undefined' && !!window.matchMedia?.('(pointer: coarse)').matches)

const formatLocalNow = (d = new Date()) => {
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const liveClock = ref(formatLocalNow())
let clockTimer = null
let stream = null
let runToken = 0

const watermarkInfo = computed(() => {
  const insParts = [props.inspectorName || '现场巡检员', props.inspectorPosition, props.inspectorPhone].filter(Boolean)
  return {
    taskTitle: props.taskTitle || '园区日常巡检',
    itemName: props.itemName || '',
    inspectorLine: insParts.join(' · '),
    area: props.area || '-'
  }
})

async function fetchAuthoritativeTime() {
  try {
    const res = await routineInspectionApi.serverTime()
    if (res?.now) return res.now
  } catch {}
  return formatLocalNow()
}

function stopCamera() {
  runToken++
  if (stream) {
    stream.getTracks().forEach((t) => t.stop())
    stream = null
  }
  ready.value = false
  if (videoRef.value) videoRef.value.srcObject = null
}

async function startCamera() {
  stopCamera()
  const myToken = ++runToken
  camError.value = ''
  try {
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error('当前浏览器不支持网页实时摄像头接口')
    }
    const s = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: facingMode.value }, width: { ideal: 1600 }, height: { ideal: 1200 } },
      audio: false
    })
    if (myToken !== runToken) {
      s.getTracks().forEach((t) => t.stop())
      return
    }
    stream = s
    await nextTick()
    if (!videoRef.value) return
    videoRef.value.srcObject = stream
    await videoRef.value.play()
    ready.value = true
  } catch (e) {
    if (myToken !== runToken) return
    camError.value = '无法打开实时摄像头：请检查浏览器相机权限（HTTPS 环境下可正常调用）。'
  }
}

function flipCamera() {
  facingMode.value = facingMode.value === 'environment' ? 'user' : 'environment'
  startCamera()
}

function triggerNativeCamera() {
  nativeCamInput.value?.click()
}

function drawWatermarkedCanvas(sourceEl, srcW, srcH, tsText) {
  const maxW = 1600
  const scale = srcW > maxW ? maxW / srcW : 1
  const w = Math.round(srcW * scale)
  const h = Math.round(srcH * scale)
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  ctx.drawImage(sourceEl, 0, 0, w, h)

  // 左下角烧录高可读防伪巡检水印（巡检人、姓名/职位、时间、区域、巡检任务内容）
  const lines = [
    `【${watermarkInfo.value.taskTitle}】`,
    `巡检内容：${watermarkInfo.value.itemName || '-'}`,
    `巡检区域：${watermarkInfo.value.area}`,
    `巡 检 人：${watermarkInfo.value.inspectorLine}`,
    `拍摄时间：${tsText}   结论：${props.resultLabel || '正常'}`
  ]
  const fontSize = Math.max(15, Math.round(w * 0.018))
  const lineH = Math.round(fontSize * 1.48)
  const padX = Math.round(fontSize * 0.85)
  const padY = Math.round(fontSize * 0.65)
  ctx.font = `600 ${fontSize}px "PingFang SC", "Microsoft YaHei", sans-serif`

  const maxLineW = Math.min(
    w - padX * 2,
    Math.max(...lines.map((l) => ctx.measureText(l).width)) + padX * 2
  )
  const boxH = lines.length * lineH + padY * 2
  const boxX = Math.round(w * 0.02)
  const boxY = Math.max(8, h - boxH - Math.round(h * 0.025))

  // 半透明深色底框 + 左侧主题色条
  ctx.fillStyle = 'rgba(15, 23, 42, 0.72)'
  ctx.fillRect(boxX, boxY, maxLineW, boxH)
  ctx.fillStyle = props.resultLabel === '发现异常' ? '#ef4444' : '#38bdf8'
  ctx.fillRect(boxX, boxY, Math.max(4, Math.round(fontSize * 0.25)), boxH)

  ctx.fillStyle = '#ffffff'
  ctx.textBaseline = 'top'
  lines.forEach((line, idx) => {
    const y = boxY + padY + idx * lineH
    ctx.fillText(line, boxX + padX, y, maxLineW - padX * 1.5)
  })

  return { canvas, watermarkText: lines.join(' | ') }
}

async function snapFromVideo() {
  if (!ready.value || !videoRef.value?.videoWidth) return
  const tsText = await fetchAuthoritativeTime()
  const { canvas, watermarkText } = drawWatermarkedCanvas(
    videoRef.value, videoRef.value.videoWidth, videoRef.value.videoHeight, tsText
  )
  canvas.toBlob((blob) => {
    if (!blob) return ElMessage.error('拍照生成失败，请重试')
    capturedBlob.value = blob
    capturedAtText.value = tsText
    capturedWatermarkText.value = watermarkText
    if (capturedPreview.value) URL.revokeObjectURL(capturedPreview.value)
    capturedPreview.value = URL.createObjectURL(blob)
    stopCamera()
  }, 'image/jpeg', 0.88)
}

async function onNativeCaptured(ev) {
  const file = ev.target.files?.[0]
  ev.target.value = ''
  if (!file || !file.type.startsWith('image/')) return
  const tsText = await fetchAuthoritativeTime()
  const imgUrl = URL.createObjectURL(file)
  const img = new Image()
  img.onload = () => {
    try {
      const { canvas, watermarkText } = drawWatermarkedCanvas(img, img.naturalWidth, img.naturalHeight, tsText)
      canvas.toBlob((blob) => {
        URL.revokeObjectURL(imgUrl)
        if (!blob) return ElMessage.error('水印照片处理失败')
        capturedBlob.value = blob
        capturedAtText.value = tsText
        capturedWatermarkText.value = watermarkText
        if (capturedPreview.value) URL.revokeObjectURL(capturedPreview.value)
        capturedPreview.value = URL.createObjectURL(blob)
        stopCamera()
      }, 'image/jpeg', 0.88)
    } catch {
      URL.revokeObjectURL(imgUrl)
      ElMessage.error('水印处理失败，请重拍')
    }
  }
  img.onerror = () => {
    URL.revokeObjectURL(imgUrl)
    ElMessage.error('读取拍摄照片失败')
  }
  img.src = imgUrl
}

function retake() {
  if (capturedPreview.value) URL.revokeObjectURL(capturedPreview.value)
  capturedPreview.value = ''
  capturedBlob.value = null
  startCamera()
}

async function confirmUpload() {
  if (!capturedBlob.value) return
  uploading.value = true
  try {
    const fname = `巡检现场-${Date.now()}.jpg`
    const file = new File([capturedBlob.value], fname, { type: 'image/jpeg' })
    const fd = new FormData()
    fd.append('file', file)
    fd.append('bizType', 'pm_routine_photo')
    const saved = await fileApi.upload(fd)
    const fileId = saved?.id || saved?.fileId
    if (!fileId) throw new Error('上传成功但未返回文件 ID')
    emit('uploaded', {
      fileId,
      url: `/api/file/download/${fileId}`,
      capturedAt: capturedAtText.value,
      watermark: capturedWatermarkText.value
    })
    ElMessage.success('现场水印照片已上传')
    if (capturedPreview.value) URL.revokeObjectURL(capturedPreview.value)
    capturedPreview.value = ''
    capturedBlob.value = null
  } catch (e) {
    ElMessage.error(e?.message || '照片上传失败，请重试')
  } finally {
    uploading.value = false
  }
}

onMounted(() => {
  clockTimer = setInterval(() => { liveClock.value = formatLocalNow() }, 1000)
  startCamera()
})

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
  if (capturedPreview.value) URL.revokeObjectURL(capturedPreview.value)
  stopCamera()
})
</script>

<style scoped>
.live-cam-wrap { width: 100%; }
.cam-stage {
  position: relative;
  width: 100%;
  min-height: 280px;
  max-height: 56vh;
  background: #0f172a;
  border-radius: 10px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.cam-video, .cam-preview {
  display: block;
  width: 100%;
  max-height: 56vh;
  object-fit: contain;
}
.cam-placeholder {
  color: #cbd5e1;
  font-size: 13px;
  padding: 40px 16px;
}
.live-wm-overlay {
  position: absolute;
  left: 12px;
  bottom: 12px;
  max-width: calc(100% - 24px);
  padding: 8px 12px;
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.72);
  border-left: 3px solid #38bdf8;
  color: #fff;
  font-size: 12px;
  line-height: 1.5;
  pointer-events: none;
}
.wm-title { font-weight: 700; margin-bottom: 2px; }
.err-hint { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; font-size: 12px; }
.cam-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
