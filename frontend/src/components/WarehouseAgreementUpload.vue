<template>
  <el-dialog :model-value="visible" title="确认加盟协议并上线" width="560px" @update:model-value="emit('update:visible', $event)">
    <p>{{ warehouse?.name }} · {{ warehouse?.orderMode === 'manual' ? '人工导入订单模式' : 'ERP 接入模式' }}</p>
    <el-alert type="info" :closable="false" title="请核对已签署的协议。确认后云仓正式上线，人工导入模式不会被标记为 ERP 已联通。" />
    <div class="upload-area"><el-upload :show-file-list="false" accept=".pdf,.doc,.docx,.jpg,.jpeg,.png" :http-request="upload" :before-upload="beforeUpload" :disabled="busy"><el-button :loading="uploading">上传已签署协议</el-button></el-upload></div>
    <el-button v-if="file" link type="primary" @click="download">{{ file.name }}</el-button>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template #footer><el-button :disabled="busy" @click="emit('update:visible', false)">取消</el-button><el-button type="primary" :loading="busy" :disabled="uploading || !file" @click="confirm">确认协议并上线</el-button></template>
  </el-dialog>
</template>
<script setup>
import { ref, watch } from 'vue'
import { fileApi } from '@/api/file'
import { mktWarehouseApi } from '@/api/marketing'
import { startFileDownload } from '@/utils/fileDownload'
import { ElMessage } from 'element-plus'
const props = defineProps({ visible: Boolean, warehouse: Object }); const emit = defineEmits(['update:visible', 'saved'])
const file=ref(null),busy=ref(false),uploading=ref(false),error=ref('')
watch(()=>props.visible, async visible=>{if(!visible)return;file.value=null;error.value='';const match=/^file:([1-9]\d*)$/.exec(props.warehouse?.contractFile||'');if(match){try{const list=await fileApi.list('mkt_warehouse',props.warehouse.id);const f=list.find(x=>x.id===Number(match[1]));if(f)file.value={id:f.id,name:f.originalName}}catch(e){error.value='已上传协议读取失败，请重试或重新上传'}}})
function beforeUpload(f){if(f.size>20*1024*1024){ElMessage.error('协议文件不能超过20MB');return false}return true}
async function upload({file:raw,onSuccess,onError}){uploading.value=true;error.value='';try{const data=new FormData();data.append('file',raw);data.append('bizType','mkt_warehouse');data.append('bizId',props.warehouse.id);const r=await fileApi.upload(data);file.value={id:r.id,name:r.originalName};onSuccess(r)}catch(e){error.value=e.message||'协议上传失败';onError(e)}finally{uploading.value=false}}
async function confirm(){if(busy.value||!file.value)return;busy.value=true;error.value='';try{await mktWarehouseApi.signAgreement(props.warehouse.id,{contractFile:'file:'+file.value.id});ElMessage.success('云仓已上线');emit('saved');emit('update:visible',false)}catch(e){error.value=e.message||'上线失败，请核对云仓最新状态'}finally{busy.value=false}}
async function download(){try{await startFileDownload(file.value.id,file.value.name)}catch(e){error.value=e.message||'协议下载失败'}}
</script><style scoped>.upload-area{margin:20px 0}</style>
