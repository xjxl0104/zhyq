<template>
  <div class="attachments">
    <div v-for="f in modelValue" :key="f.id" class="file-row"><el-button link type="primary" @click="download(f)">{{ f.name || ('附件 ' + f.id) }}</el-button><el-button v-if="!readonly" link type="danger" :disabled="busy" @click="remove(f.id)">移除</el-button></div>
    <el-upload v-if="!readonly" :show-file-list="false" accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png" :http-request="upload" :before-upload="beforeUpload" :disabled="busy || !warehouseId || modelValue.length >= 10"><el-button :loading="busy" :disabled="!warehouseId || modelValue.length >= 10">上传附件</el-button></el-upload>
    <p v-if="!readonly" class="hint">每份不超过 20MB，最多 10 份。请上传真实签署文件。</p><span v-if="readonly && !modelValue.length">暂无附件</span>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
  </div>
</template>
<script setup>
import { ref } from 'vue'
import { fileApi } from '@/api/file'
import { startFileDownload } from '@/utils/fileDownload'
import { ElMessage } from 'element-plus'
const props = defineProps({ modelValue: { type: Array, default: () => [] }, warehouseId: Number, readonly: Boolean })
const emit = defineEmits(['update:modelValue', 'busy'])
const busy = ref(false), error = ref('')
function beforeUpload(f) { if(f.size > 20*1024*1024){ElMessage.error('附件不能超过 20MB');return false} if(!/\.(pdf|docx?|xlsx?|jpe?g|png)$/i.test(f.name)){ElMessage.error('请选择文档、表格或图片');return false} return true }
async function upload({file,onSuccess,onError}) { busy.value=true;emit('busy',true);error.value='';try{const body=new FormData();body.append('file',file);body.append('bizType','mkt_warehouse');body.append('bizId',props.warehouseId);const saved=await fileApi.upload(body);emit('update:modelValue',[...props.modelValue,{id:saved.id,name:saved.originalName,size:saved.fileSize,ext:saved.ext}]);onSuccess(saved)}catch(e){error.value=e.message||'附件上传失败';onError(e)}finally{busy.value=false;emit('busy',false)} }
function remove(id){emit('update:modelValue',props.modelValue.filter(f=>f.id!==id))}
async function download(f){try{await startFileDownload(f.id,f.name)}catch(e){error.value=e.message||'附件下载失败'}}
</script>
<style scoped>.file-row{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin-bottom:8px}.file-row :deep(.el-button){white-space:normal;text-align:left;overflow-wrap:anywhere}.hint{font-size:12px;color:var(--el-text-color-secondary);line-height:1.5}</style>
