<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthImage } from '@/utils/authImage'
import { startFileDownload } from '@/utils/fileDownload'

const props = defineProps({ files: { type: Array, default: () => [] } })
const { srcFor, resolveAll, revokeAll } = useAuthImage()
const ready = ref(false)
let disposed = false
const isPhoto = file => /^(image\/(jpeg|png|gif|webp))$/i.test(file.contentType || '')
  || /\.(jpe?g|png|gif|webp)$/i.test(file.originalName || '')
const photos = computed(() => props.files.filter(isPhoto))
const others = computed(() => props.files.filter(file => !isPhoto(file)))
const previewUrls = computed(() => photos.value.map(file => srcFor(file.id)).filter(Boolean))
const previewIndex = file => previewUrls.value.indexOf(srcFor(file.id))
async function download(file) {
  try { await startFileDownload(file.id, file.originalName) }
  catch { ElMessage.error('下载失败，请重试') }
}
watch(photos, async files => {
  ready.value = false
  await resolveAll(files.map(file => file.id))
  if (disposed) { revokeAll(); return }
  ready.value = true
}, { immediate: true })
onBeforeUnmount(() => { disposed = true; revokeAll() })
</script>

<template>
  <div class="photo-gallery">
    <div v-if="photos.length" class="photo-gallery__grid">
      <div v-for="file in photos" :key="file.id" class="photo-gallery__item">
        <el-image v-if="srcFor(file.id)" class="photo-gallery__thumb" :src="srcFor(file.id)"
                  :alt="file.originalName || '现场照片'" fit="cover"
                  :preview-src-list="previewUrls" :initial-index="previewIndex(file)" preview-teleported />
        <div v-else class="photo-gallery__placeholder">{{ ready ? '无法预览' : '加载中…' }}</div>
        <div class="photo-gallery__caption" :title="file.originalName">{{ file.originalName || '现场照片' }}</div>
        <el-button link type="primary" size="small" @click="download(file)">下载原图</el-button>
      </div>
    </div>
    <div v-if="others.length" class="photo-gallery__other">
      <el-button v-for="file in others" :key="file.id" link type="primary"
                 @click="download(file)">{{ file.originalName || '附件' }}（下载）</el-button>
    </div>
  </div>
</template>

<style scoped>
.photo-gallery__grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(104px, 1fr)); gap: 12px; margin: 8px 0; }
.photo-gallery__item { min-width: 0; }
.photo-gallery__thumb, .photo-gallery__placeholder { display: flex; width: 100%; height: 104px; border-radius: 6px; border: 1px solid var(--border, #dcdfe6); }
.photo-gallery__thumb { cursor: zoom-in; }
.photo-gallery__placeholder { align-items: center; justify-content: center; color: var(--text-secondary, #909399); font-size: 12px; }
.photo-gallery__caption { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; margin-top: 4px; }
.photo-gallery__other { display: flex; flex-wrap: wrap; gap: 4px 12px; }
</style>
