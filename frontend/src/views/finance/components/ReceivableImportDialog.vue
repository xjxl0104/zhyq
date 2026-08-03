<template>
  <el-dialog :model-value="modelValue" title="导入应收明细登记表" width="980px" @close="close">
    <el-steps :active="stage" align-center finish-status="success"><el-step title="选择文件" /><el-step title="预览与绑定" /><el-step title="确认入库" /></el-steps>
    <div v-if="stage === 0" class="upload-stage">
      <el-upload drag :auto-upload="false" accept=".xlsx,.xls" :limit="1" :on-change="onFile"><div>拖拽或点击选择园区应收明细工作簿</div></el-upload>
      <el-alert title="系统读取 Excel 缓存值，不执行公式；完整账号仅加密保存。" type="info" :closable="false" />
    </div>
    <div v-else-if="preview" class="preview-stage">
      <el-row :gutter="12" class="totals">
        <el-col v-for="item in totalCards" :key="item.label" :span="6"><el-statistic :title="item.label" :value="item.value" :precision="2" /></el-col>
      </el-row>
      <el-alert v-if="preview.invalidRows" :title="`${preview.invalidRows} 行仍需校验或绑定，全部处理后才能确认`" type="warning" show-icon />
      <el-table :data="preview.rows" border height="390">
        <el-table-column prop="sourceRow" label="Excel行" width="80" /><el-table-column prop="tenantName" label="租户" min-width="150" />
        <el-table-column prop="spaceName" label="空间" min-width="130" /><el-table-column prop="rentAccountMasked" label="租金账户" min-width="200" show-overflow-tooltip />
        <el-table-column label="主数据绑定" min-width="300">
          <template #default="{ row }"><div class="binding"><el-input v-model="row.tenantRefId" placeholder="租户ID" /><el-input v-model="row.spaceId" placeholder="空间ID" /><el-button type="primary" :disabled="!positiveId(row.tenantRefId) || !positiveId(row.spaceId)" @click="bind(row)">绑定</el-button></div></template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="状态/错误" min-width="180"><template #default="{ row }"><el-tag :type="row.status === 'VALID' ? 'success' : 'warning'">{{ row.status }}</el-tag> {{ row.errorMessage }}</template></el-table-column>
      </el-table>
    </div>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button v-if="stage === 0" type="primary" :loading="loading" :disabled="!file" @click="uploadPreview">解析预览</el-button>
      <el-button v-else type="primary" :loading="loading" :disabled="!canConfirmReceivableImport(preview)" @click="confirm">确认入库</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { receivableApi } from '@/api/receivable'
import { canConfirmReceivableImport } from '../receivableModel'
const props = defineProps({ modelValue: Boolean }); const emit = defineEmits(['update:modelValue', 'confirmed'])
const stage = ref(0); const file = ref(null); const preview = ref(null); const loading = ref(false)
const totalCards = computed(() => {
  const totals = preview.value?.totals || {}
  return [{ label: '月租金', value: totals.monthlyRent || 0 }, { label: '月物业费', value: totals.monthlyProperty || 0 }, { label: '月合计', value: totals.monthlyTotal || 0 }, { label: '保证金合计', value: Number(totals.rentDeposit || 0) + Number(totals.propertyDeposit || 0) }]
})
function onFile(uploadFile) { file.value = uploadFile.raw }
async function uploadPreview() { loading.value = true; try { preview.value = await receivableApi.preview(file.value); preview.value.totalsReconciled = true; stage.value = 1 } finally { loading.value = false } }
async function bind(row) {
  const hasStructuralError = row.status === 'INVALID'
  await receivableApi.bind(preview.value.batchId, row.rowId, { tenantRefId: Number(row.tenantRefId), spaceId: Number(row.spaceId) })
  if (!hasStructuralError) { row.status = 'VALID'; row.errorMessage = '' }
  preview.value.invalidRows = preview.value.rows.filter(item => item.status !== 'VALID').length
  ElMessage.success('绑定成功')
}
function positiveId(value) { return Number.isInteger(Number(value)) && Number(value) > 0 }
async function confirm() { loading.value = true; try { const count = await receivableApi.confirm(preview.value.batchId); stage.value = 2; ElMessage.success(`成功导入 ${count} 条`); emit('confirmed'); close() } finally { loading.value = false } }
function close() { emit('update:modelValue', false); stage.value = 0; file.value = null; preview.value = null }
</script>
<style scoped>.upload-stage,.preview-stage{margin-top:24px}.upload-stage{display:grid;gap:16px}.totals{margin-bottom:16px}.binding{display:flex;gap:6px}</style>
