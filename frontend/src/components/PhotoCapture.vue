<script setup>
import { nextTick, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'

const props = defineProps({ disabled: Boolean })
const emit = defineEmits(['capture'])
const mobileInput = ref()
const video = ref()
const visible = ref(false)
const ready = ref(false)
const error = ref('')
let stream = null
let generation = 0
function stop() {
  generation++
  stream?.getTracks().forEach(track => track.stop())
  stream = null
  ready.value = false
  if (video.value) video.value.srcObject = null
}
function close() { visible.value = false; stop() }
async function start() {
  if (props.disabled) return
  if (window.matchMedia?.('(pointer: coarse)').matches) {
    mobileInput.value?.click()
    return
  }
  visible.value = true
  ready.value = false
  error.value = ''
}
async function connect() {
  const run = ++generation
  try {
    if (!navigator.mediaDevices?.getUserMedia) throw new Error('unavailable')
    const acquired = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false })
    if (run !== generation || !visible.value) { acquired.getTracks().forEach(track => track.stop()); return }
    stream = acquired
    await nextTick()
    video.value.srcObject = stream
    await video.value.play()
    ready.value = true
  } catch {
    if (run === generation) { stop(); error.value = '无法打开相机，请允许摄像头访问，或使用下方拍照/选图入口。' }
  }
}
function takePhoto() {
  if (!ready.value || !video.value?.videoWidth) return
  const canvas = document.createElement('canvas')
  canvas.width = video.value.videoWidth
  canvas.height = video.value.videoHeight
  canvas.getContext('2d').drawImage(video.value, 0, 0)
  const run = generation
  canvas.toBlob(blob => {
    if (!blob || run !== generation) return
    emit('capture', new File([blob], `报修照片-${Date.now()}.jpg`, { type: 'image/jpeg' }))
    close()
  }, 'image/jpeg', 0.9)
}
function selectPhoto(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) { ElMessage.error('请选择照片'); return }
  emit('capture', file)
  close()
}
onBeforeUnmount(stop)
</script>

<template>
  <el-button :icon="Camera" :disabled="disabled" @click="start">直接拍照</el-button>
  <input ref="mobileInput" type="file" accept="image/*" capture="environment" hidden @change="selectPhoto" />
  <el-dialog v-model="visible" title="拍摄报修现场" width="640px" append-to-body destroy-on-close @opened="connect" @close="stop">
    <video ref="video" autoplay muted playsinline class="camera-video" />
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button @click="mobileInput?.click()">拍照 / 选择照片</el-button>
      <el-button type="primary" :disabled="!ready || disabled" @click="takePhoto">拍照并上传</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.camera-video { display: block; width: 100%; max-height: 55vh; background: #161a24; border-radius: 8px; margin-bottom: 12px; }
</style>
