<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { fileApi } from '@/api/file'

const props = defineProps({ fileId: [Number, String], point: Object, editable: Boolean })
const emit = defineEmits(['update:point'])
const imageUrl = ref('')
const loading = ref(false)
const error = ref('')
const zoom = ref(1)
let generation = 0
const markerStyle = computed(() => props.point ? { left: `${props.point.x * 100}%`, top: `${props.point.y * 100}%` } : {})
function release() {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  imageUrl.value = ''
}
watch(() => props.fileId, async id => {
  const run = ++generation
  release()
  error.value = ''
  zoom.value = 1
  loading.value = !!id
  if (!id) return
  try {
    const res = await fileApi.download(id)
    if (run !== generation) return
    if (!['image/png', 'image/jpeg'].includes(res.data.type)) throw new Error('不是可显示的平面图')
    imageUrl.value = URL.createObjectURL(res.data)
  } catch {
    if (run === generation) error.value = '平面图加载失败，请检查访问权限或重新打开'
  } finally { if (run === generation) loading.value = false }
}, { immediate: true })
onBeforeUnmount(() => { generation++; release() })
function selectPoint(event) {
  if (!props.editable || !imageUrl.value || error.value) return
  const rect = event.currentTarget.getBoundingClientRect()
  if (!rect.width || !rect.height) return
  emit('update:point', {
    x: Number(Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)).toFixed(8)),
    y: Number(Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height)).toFixed(8))
  })
}
function movePoint(event) {
  if (!props.editable || !imageUrl.value || error.value) return
  const delta = { ArrowLeft: [-0.01, 0], ArrowRight: [0.01, 0], ArrowUp: [0, -0.01], ArrowDown: [0, 0.01], Enter: [0, 0], ' ': [0, 0] }[event.key]
  if (!delta) return
  event.preventDefault()
  const current = props.point || { x: 0.5, y: 0.5 }
  emit('update:point', { x: Number(Math.max(0, Math.min(1, current.x + delta[0])).toFixed(8)), y: Number(Math.max(0, Math.min(1, current.y + delta[1])).toFixed(8)) })
}
</script>

<template>
  <div class="floor-plan-viewer" v-loading="loading">
    <div v-if="imageUrl && !error" class="plan-tools">
      <span>{{ editable ? '点击图纸标记维修位置；可放大后定位' : (point ? '红点为报修位置' : '楼层平面图') }}</span>
      <div>
        <el-button size="small" :disabled="zoom <= 1" aria-label="缩小平面图" @click="zoom = Math.max(1, zoom - 0.5)">−</el-button>
        <el-button size="small" :disabled="zoom >= 4" aria-label="放大平面图" @click="zoom = Math.min(4, zoom + 0.5)">＋</el-button>
        <el-button v-if="editable && point" size="small" @click="emit('update:point', null)">清除点位</el-button>
      </div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-else-if="imageUrl" class="plan-scroll">
      <div class="plan-canvas" :class="{ editable }" :style="{ width: `${zoom * 100}%` }"
           :role="editable ? 'button' : 'img'" :tabindex="editable ? 0 : undefined"
           :aria-label="editable ? '平面图维修定位，点击或使用方向键调整位置' : '维修位置平面图'"
           @click="selectPoint" @keydown="movePoint">
        <img :src="imageUrl" alt="楼层建筑平面图" draggable="false" @error="error = '图片无法显示，请重新上传 JPG 或 PNG 平面图'" />
        <span v-if="point" class="plan-marker" :style="markerStyle" aria-label="报修点位" />
      </div>
    </div>
    <el-empty v-else-if="!loading" description="该楼层尚未上传平面图，可先填写位置说明" :image-size="64" />
  </div>
</template>

<style scoped>
.floor-plan-viewer { width: 100%; min-height: 80px; }
.plan-tools { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 10px; font-size: 13px; color: var(--text-secondary); }
.plan-scroll { max-height: 60vh; overflow: auto; border: 1px solid var(--el-border-color); border-radius: 8px; background: #f5f7fa; }
.plan-canvas { position: relative; line-height: 0; }
.plan-canvas img { display: block; width: 100%; height: auto; pointer-events: none; user-select: none; }
.plan-canvas.editable { cursor: crosshair; }
.plan-canvas:focus-visible { outline: 3px solid var(--el-color-primary); outline-offset: -3px; }
.plan-marker { position: absolute; width: 18px; height: 18px; border: 3px solid #fff; border-radius: 50%; box-sizing: border-box; background: #e43131; box-shadow: 0 0 0 2px #e43131, 0 2px 8px #0006; transform: translate(-50%, -50%); pointer-events: none; }
</style>
