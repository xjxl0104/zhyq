<template>
  <view>
    <view v-for="f in modelValue" :key="f.id" class="wh-item">
      <view class="wh-row"><button class="file-name" @click="open(f)">{{ f.name || f.originalName || '查看附件' }}</button><button v-if="!readonly" size="mini" :disabled="busy" @click="remove(f.id)">移除</button></view>
    </view>
    <view v-if="!modelValue.length" class="wh-note">尚未上传资料</view>
    <button v-if="!readonly" class="wh-secondary" :disabled="busy || modelValue.length >= max" :loading="busy" @click="choose">{{ busy ? '正在上传…' : '选择并上传文件' }}</button>
    <view v-if="!readonly" class="wh-note">支持 PDF、Word、Excel 和 JPG/PNG，每份不超过 20MB，最多 {{ max }} 份。</view>
    <view v-if="error" class="wh-error" role="alert">{{ error }}</view>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { warehouseApi } from '@/api/warehouse'
const props = defineProps({ modelValue: { type: Array, default: () => [] }, readonly: Boolean, max: { type: Number, default: 10 } })
const emit = defineEmits(['update:modelValue', 'busy'])
const busy = ref(false); const error = ref('')
async function choose() {
  if (busy.value) return
  error.value = ''; busy.value = true; emit('busy', true)
  try {
    let result
    // #ifdef MP-WEIXIN
    result = await uni.chooseMessageFile({ count: 1, type: 'file', extension: ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'jpg', 'jpeg', 'png'] })
    // #endif
    // #ifndef MP-WEIXIN
    result = await uni.chooseFile({ count: 1, extension: ['.pdf', '.doc', '.docx', '.xls', '.xlsx', '.jpg', '.jpeg', '.png'] })
    // #endif
    const selected = result.tempFiles?.[0]; const path = selected?.path || result.tempFilePaths?.[0]
    if (!path) return
    const stored = await warehouseApi.uploadFile(path, selected?.name || '附件')
    emit('update:modelValue', [...props.modelValue, stored])
  } catch (e) { if (!/cancel/i.test(e.errMsg || e.message || '')) error.value = e.message || '上传失败，请重新选择文件' }
  finally { busy.value = false; emit('busy', false) }
}
function remove(id) { emit('update:modelValue', props.modelValue.filter(f => f.id !== id)) }
async function open(file) { error.value = ''; try { await warehouseApi.openFile(file.id, file.name || file.originalName) } catch(e) { error.value = e.message || '文件打开失败，请重试' } }
</script>
<style scoped>
@import '../styles/warehouse.css';
.file-name { flex: 1; min-width: 0; background: transparent; color: #2e47cc; text-align: left; font-size: 28rpx; line-height: 1.5; overflow-wrap: anywhere; padding: 12rpx 0; }
</style>
