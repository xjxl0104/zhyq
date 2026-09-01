<template>
  <el-dialog :model-value="modelValue" title="导入应收明细登记表" width="980px" @close="close">
    <el-steps :active="stage" align-center finish-status="success"><el-step title="选择文件" /><el-step title="预览与绑定" /><el-step title="补建主数据" /><el-step title="确认入库" /></el-steps>
    <div v-if="stage === 0" class="upload-stage">
      <GlassSurface variant="upload" class="import-surface">
        <el-upload drag :auto-upload="false" accept=".xlsx,.xls" :limit="1" :on-change="onFile"><div>拖拽或点击选择园区应收明细工作簿</div></el-upload>
      </GlassSurface>
      <el-alert title="系统读取 Excel 缓存值，不执行公式；完整账号仅加密保存。" type="info" :closable="false" />
    </div>
    <div v-else-if="stage === 2 && provision" class="provision-stage">
      <el-alert title="表格有、系统缺的租户与空间清单。确认建档后自动补建并回填绑定。" type="info" :closable="false" show-icon />
      <h4>待建租户</h4>
      <el-table :data="provision.tenants" border max-height="220">
        <el-table-column prop="rawName" label="原名" min-width="160" show-overflow-tooltip />
        <el-table-column label="最终名" min-width="180"><template #default="{ row }"><el-input v-model="row.finalName" :disabled="row.reuse" placeholder="最终名称" /></template></el-table-column>
        <el-table-column label="类型" width="130"><template #default="{ row }"><el-select v-model="row.tenantType" :disabled="row.reuse"><el-option label="企业" :value="1" /><el-option label="个人" :value="2" /></el-select></template></el-table-column>
        <el-table-column label="复用已有" width="110"><template #default="{ row }"><el-switch v-if="row.existingTenantId" v-model="row.reuse" /><span v-else>—</span></template></el-table-column>
      </el-table>
      <h4>待建空间</h4>
      <el-table :data="provision.spaces" border max-height="220">
        <el-table-column prop="rawFloor" label="楼层原串" min-width="150" show-overflow-tooltip />
        <el-table-column label="合成（项目/楼栋/楼层/房号）" min-width="260"><template #default="{ row }">{{ [row.projectName, row.buildingName, row.floorName, row.roomNo].filter(Boolean).join(' / ') }}</template></el-table-column>
        <el-table-column label="复用已有房间" width="130"><template #default="{ row }"><el-checkbox v-if="row.existingRoomId" v-model="row.reuse" /><span v-else>—</span></template></el-table-column>
      </el-table>
    </div>
    <div v-else-if="preview" class="preview-stage">
      <el-row :gutter="12" class="totals">
        <el-col v-for="item in totalCards" :key="item.label" :span="6"><el-statistic :title="item.label" :value="item.value" :precision="2" /></el-col>
      </el-row>
      <el-alert v-if="preview.invalidRows" :title="`${preview.invalidRows} 行仍需校验或绑定，全部处理后才能确认`" type="warning" show-icon />
      <el-table :data="preview.rows" border height="390">
        <el-table-column prop="sourceRow" label="Excel行" width="80" /><el-table-column prop="tenantName" label="租户" min-width="150" />
        <el-table-column prop="spaceName" label="空间" min-width="130" /><el-table-column prop="rentAccountMasked" label="租金账户" min-width="200" show-overflow-tooltip />
        <el-table-column label="主数据绑定" min-width="560">
          <template #default="{ row }"><div class="binding"><el-input v-model="row.tenantRefId" placeholder="租户ID" /><el-input v-model="row.spaceId" placeholder="空间ID（选填）" /><el-input v-model="row.roomId" placeholder="房间ID（选填）" /><el-input v-model="row.contractId" placeholder="合同ID" /><el-button type="primary" :disabled="!canBind(row)" @click="bind(row)">绑定</el-button></div></template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="状态/错误" min-width="180"><template #default="{ row }"><el-tag :type="row.status === 'VALID' ? 'success' : 'warning'">{{ row.status }}</el-tag> {{ row.errorMessage }}</template></el-table-column>
      </el-table>
    </div>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button v-if="stage === 0" type="primary" :loading="loading" :disabled="!file" @click="uploadPreview">解析预览</el-button>
      <template v-else-if="stage === 2">
        <el-button @click="stage = 1">返回预览</el-button>
        <el-button type="primary" :loading="loading" @click="doProvision">确认建档</el-button>
      </template>
      <template v-else>
        <el-button :loading="loading" @click="openProvision">补建主数据</el-button>
        <el-button type="primary" :loading="loading" :disabled="!canConfirmReceivableImport(preview)" @click="confirm">确认入库</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { receivableApi } from '@/api/receivable'
import { buildReceivableBinding, canConfirmReceivableImport } from '../receivableModel'
import GlassSurface from '@/components/GlassSurface.vue'
const props = defineProps({ modelValue: Boolean }); const emit = defineEmits(['update:modelValue', 'confirmed'])
const stage = ref(0); const file = ref(null); const preview = ref(null); const provision = ref(null); const loading = ref(false)
const totalCards = computed(() => {
  const totals = preview.value?.totals || {}
  return [{ label: '月租金', value: totals.monthlyRent || 0 }, { label: '月物业费', value: totals.monthlyProperty || 0 }, { label: '月合计', value: totals.monthlyTotal || 0 }, { label: '保证金合计', value: Number(totals.rentDeposit || 0) + Number(totals.propertyDeposit || 0) }]
})
function onFile(uploadFile) { file.value = uploadFile.raw }
async function uploadPreview() { loading.value = true; try { preview.value = await receivableApi.preview(file.value); preview.value.totalsReconciled = true; stage.value = 1 } finally { loading.value = false } }
async function bind(row) {
  const hasStructuralError = row.status === 'INVALID'
  await receivableApi.bind(preview.value.batchId, row.rowId, buildReceivableBinding(row))
  if (!hasStructuralError) { row.status = 'VALID'; row.errorMessage = '' }
  preview.value.invalidRows = preview.value.rows.filter(item => item.status !== 'VALID').length
  ElMessage.success('绑定成功')
}
function positiveId(value) { return Number.isInteger(Number(value)) && Number(value) > 0 }
function canBind(row) { return positiveId(row.tenantRefId) && (positiveId(row.spaceId) || positiveId(row.roomId)) && positiveId(row.contractId) }
async function openProvision() {
  loading.value = true
  try {
    const data = await receivableApi.provisionPreview(preview.value.batchId)
    provision.value = {
      tenants: (data.tenants || []).map(t => ({ ...t, finalName: t.cleanName, tenantType: t.tenantType || 1, reuse: !!t.existingTenantId })),
      spaces: (data.spaces || []).map(s => ({ ...s, reuse: !!s.existingRoomId }))
    }
    stage.value = 2
  } finally { loading.value = false }
}
async function doProvision() {
  loading.value = true
  try {
    const body = {
      tenants: provision.value.tenants.map(t => ({ rawName: t.rawName, finalName: t.finalName, tenantType: t.tenantType, reuseTenantId: t.reuse ? t.existingTenantId : null })),
      spaces: provision.value.spaces.map(s => ({ rawFloor: s.rawFloor, reuseRoomId: s.reuse ? s.existingRoomId : null }))
    }
    await receivableApi.provision(preview.value.batchId, body)
    preview.value = await receivableApi.preview(file.value)
    preview.value.totalsReconciled = true
    provision.value = null
    stage.value = 1
    ElMessage.success('建档成功，已回填绑定')
  } finally { loading.value = false }
}
async function confirm() {
  loading.value = true
  try {
    const res = await receivableApi.confirm(preview.value.batchId)
    stage.value = 3
    // 确认后账单已自动生成(登记表是账单/收银台/逾期的源头,下游自动派生);
    // 兼容旧后端只返回条数的情况
    if (typeof res === 'object' && res?.bills) {
      const b = res.bills
      const failedNote = b.failed ? `,${b.failed} 条生成失败(每日自愈任务会重试)` : ''
      ElMessage.success(`成功导入 ${res.rows} 条,已自动生成账单 ${b.inserted} 张${failedNote}`)
    } else {
      ElMessage.success(`成功导入 ${res} 条`)
    }
    emit('confirmed')
    close()
  } finally {
    loading.value = false
  }
}
function close() { emit('update:modelValue', false); stage.value = 0; file.value = null; preview.value = null; provision.value = null }
</script>
<style scoped>.upload-stage,.preview-stage,.provision-stage{margin-top:24px}.upload-stage{display:grid;gap:16px}.provision-stage h4{margin:16px 0 8px}.totals{margin-bottom:16px}.binding{display:flex;gap:6px}.import-surface :deep(.el-upload),.import-surface :deep(.el-upload-dragger){width:100%}</style>
