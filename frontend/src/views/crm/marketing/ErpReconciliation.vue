<template>
  <div class="table-card reconciliation" v-loading="loading">
    <div class="toolbar"><span class="title">ERP 周期对账</span></div>
    <el-alert type="info" :closable="false" title="按外部 ERP 导出核对有效出库单数" :description="scope" />
    <p class="hint">请从 ERP 导出相同客户范围、相同发货日期的有效出库单。先保留导出凭据，再录入单数；任何差异或待处理事件都会暂停本期结算。已确认记录保留历史，补单、撤单或货主映射变化后须重新确认。</p>
    <el-alert v-if="error" class="status-alert" type="error" :closable="false" :title="error" />
    <el-form label-width="138px" :disabled="saving" @submit.prevent>
      <el-form-item label="发货日期范围">
        <el-date-picker v-model="period" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" @change="periodChanged" />
        <el-button class="refresh" :disabled="!validPeriod" @click="load">计算 / 刷新</el-button>
      </el-form-item>
      <el-form-item label="ERP 有效出库单数">
        <el-input-number v-model="form.externalOrderCount" :min="0" :max="1000000000" :precision="0" :step="1" controls-position="right" placeholder="录入外部导出数" />
        <span class="hint field-hint">必须来自 ERP 导出，不能照抄系统数。</span>
      </el-form-item>
      <el-form-item label="外部数据来源" required>
        <el-input v-model="form.sourceName" maxlength="200" placeholder="例如：数智云仓 ERP 出库明细报表" />
      </el-form-item>
      <el-form-item label="凭据及筛选说明" required>
        <el-input v-model="form.evidence" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="填写导出文件名或凭据位置、导出时间、货主筛选范围及取消/退款处理口径，便于复核。" />
      </el-form-item>
      <el-form-item>
        <el-button v-if="canConfigure" type="primary" :disabled="!canSave" :loading="saving" @click="save">保存对账草稿</el-button>
        <span v-else class="hint">当前账号可以查看对账；提交及确认需要 ERP 配置权限。</span>
      </el-form-item>
    </el-form>

    <template v-if="preview">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="系统有效出库单">{{ preview.calculation.systemOrderCount }}</el-descriptions-item>
        <el-descriptions-item label="已取消 / 退款单">{{ preview.calculation.cancelledOrderCount }}</el-descriptions-item>
        <el-descriptions-item label="当前录入差额">{{ difference ?? '请填写外部单数' }}</el-descriptions-item>
        <el-descriptions-item label="待匹配事件">{{ preview.calculation.unmappedCount }}</el-descriptions-item>
        <el-descriptions-item label="重试 / 死信">{{ preview.calculation.retryCount }}</el-descriptions-item>
        <el-descriptions-item label="其他待处理">{{ preview.calculation.pendingCount }}</el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="preview.calculation.blockingCount" class="status-alert" type="error" :closable="false"
        title="存在阻塞，暂不能确认或结算" :description="preview.calculation.blockingReasons.join('；')" />
      <el-alert v-else-if="difference !== null && difference !== 0" class="status-alert" type="warning" :closable="false"
        title="外部单数与系统不一致，请排查漏单、撤单或客户范围；不能直接确认差异。" />

      <div class="toolbar history-title"><span class="title">对账版本记录</span><span class="hint">差额 = 外部单数 − 系统单数</span></div>
      <el-table :data="preview.history" border size="small" empty-text="尚未提交外部对账资料">
        <el-table-column label="版本" width="75"><template #default="{ row }">V{{ row.snapshot.revision }}</template></el-table-column>
        <el-table-column label="状态" min-width="155"><template #default="{ row }">
          <el-tag :type="row.stale ? 'warning' : row.snapshot.state === 'CONFIRMED' ? 'success' : 'info'">{{ stateLabel(row) }}</el-tag>
        </template></el-table-column>
        <el-table-column label="外部 / 系统" width="125"><template #default="{ row }">{{ row.snapshot.externalOrderCount ?? '—' }} / {{ row.snapshot.systemOrderCount ?? '—' }}</template></el-table-column>
        <el-table-column label="差额 / 阻塞" width="120"><template #default="{ row }">{{ row.snapshot.diffOrderCount ?? '—' }} / {{ row.snapshot.blockingCount }}</template></el-table-column>
        <el-table-column label="来源及凭据" min-width="250"><template #default="{ row }"><div>{{ row.snapshot.sourceName }}</div><div class="hint evidence">{{ row.snapshot.evidence }}</div></template></el-table-column>
        <el-table-column label="提交 / 确认" min-width="180"><template #default="{ row }"><div>{{ row.snapshot.createTime }}</div><div class="hint">{{ row.snapshot.confirmedBy }} {{ row.snapshot.confirmedAt }}</div></template></el-table-column>
        <el-table-column label="说明" min-width="160"><template #default="{ row }">{{ row.snapshot.reason || '—' }}</template></el-table-column>
        <el-table-column label="操作" fixed="right" width="100"><template #default="{ row }"><el-button v-if="canConfigure && row.canConfirm" type="primary" link :disabled="saving" @click="confirm(row.snapshot)">确认对账</el-button></template></el-table-column>
      </el-table>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { erpReconciliationApi } from '@/api/erp-reconciliation'
import { hasPermission } from '@/utils/permission'

const props = defineProps({ warehouseId: { type: [Number, String], required: true } })
const canConfigure = computed(() => hasPermission('crm:marketing:erp:config'))
const period = ref([])
const preview = ref(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const emptyForm = () => ({ externalOrderCount: undefined, sourceName: '', evidence: '' })
const form = ref(emptyForm())
let sequence = 0
const scope = computed(() => preview.value?.scope || '本云仓、所选发货日期内、系统登记客户的有效出库单数（唯一单号）；包含手工补单和云仓直签，排除退款、取消和沙箱。不是件数、包裹数或含自有客户的全仓发货量。')
const validPeriod = computed(() => period.value?.length === 2)
const canSave = computed(() => validPeriod.value && Number.isInteger(form.value.externalOrderCount) && form.value.sourceName.trim() && form.value.evidence.trim())
const difference = computed(() => preview.value && Number.isInteger(form.value.externalOrderCount) ? form.value.externalOrderCount - preview.value.calculation.systemOrderCount : null)
const params = () => ({ periodStart: period.value[0], periodEnd: period.value[1] })

function stateLabel(row) {
  if (row.snapshot.state === 'LEGACY') return '旧版记录，需重新对账'
  if (row.stale) return '数据已变，需重核'
  return row.snapshot.state === 'CONFIRMED' ? '已确认' : '待确认草稿'
}
function reset() { sequence++; preview.value = null; form.value = emptyForm(); loading.value = false; error.value = '' }
async function load() {
  if (!validPeriod.value || !props.warehouseId) return
  const ticket = ++sequence
  loading.value = true
  error.value = ''
  try {
    const result = await erpReconciliationApi.preview(props.warehouseId, params())
    if (ticket !== sequence) return
    preview.value = result
  } catch (failure) {
    if (ticket === sequence) { preview.value = null; error.value = failure?.message || '对账计算失败，请重试' }
  } finally { if (ticket === sequence) loading.value = false }
}
async function periodChanged() { reset(); await load() }
async function save() {
  if (!canSave.value || saving.value) return
  saving.value = true
  error.value = ''
  const warehouseId = props.warehouseId
  const dates = params()
  try {
    await erpReconciliationApi.submit(warehouseId, { ...dates, ...form.value })
    ElMessage.success('已保存新对账草稿；确认后才可用于结算')
    if (props.warehouseId === warehouseId && dates.periodStart === period.value?.[0] && dates.periodEnd === period.value?.[1]) await load()
  } catch (failure) {
    if (props.warehouseId === warehouseId) error.value = failure?.message || '保存对账草稿失败，请重试'
  } finally { saving.value = false }
}
async function confirm(snapshot) {
  if (saving.value) return
  saving.value = true
  error.value = ''
  const warehouseId = props.warehouseId
  try {
    const { value } = await ElMessageBox.prompt('请说明已核对的 ERP 凭据和客户范围。确认后保存固定版本，后续数据变化须重新核对。', '确认周期对账', {
      inputType: 'textarea', inputValidator: value => !!value?.trim() && value.length <= 500 || '请填写确认说明，最多500字',
      confirmButtonText: '确认对账', cancelButtonText: '取消'
    })
    await erpReconciliationApi.confirm(warehouseId, snapshot.id, value.trim())
    ElMessage.success('对账已确认')
    if (props.warehouseId === warehouseId) await load()
  } catch (failure) { if (failure !== 'cancel' && failure !== 'close' && props.warehouseId === warehouseId) error.value = failure?.message || '对账确认失败，请刷新重试' }
  finally { saving.value = false }
}
watch(() => props.warehouseId, () => { reset(); if (validPeriod.value) load() })
</script>

<style scoped>
.reconciliation { margin-top: 16px; }
.toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.title { font-weight: 600; }
.hint { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.6; }
.field-hint, .refresh { margin-left: 12px; }
.status-alert, .history-title { margin-top: 18px; }
.evidence { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
