<template>
  <el-dialog :model-value="modelValue" title="租金与物业管理费 · 合同基础台账" width="1220px" top="4vh" destroy-on-close
             @update:model-value="$emit('update:modelValue', $event)">
    <div class="toolbar">
      <el-button type="primary" @click="openEdit(null)"><el-icon><Plus /></el-icon>新增合同</el-button>
      <span class="tip">修改面积、月固定总价、基础单价、递增比例/周期或免租规则后，所有草稿月份的租金与物业费会自动按新参数重算；已确认出账的历史月份读冻结快照不受影响。</span>
    </div>

    <el-table :data="contracts" v-loading="loading" border size="small" max-height="480">
      <el-table-column prop="seqNo" label="序号" width="55" align="center" />
      <el-table-column prop="tenantName" label="租户名称" min-width="185" show-overflow-tooltip />
      <el-table-column prop="unitName" label="楼层/区域" min-width="180" show-overflow-tooltip />
      <el-table-column label="计租面积(㎡)" width="100" align="right">
        <template #default="{ row }">{{ num(row.leaseArea) }}</template>
      </el-table-column>
      <el-table-column label="起止日期" width="180" align="center">
        <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
      </el-table-column>
      <el-table-column label="月固定总价" width="130" align="right">
        <template #default="{ row }">
          <span v-if="Number(row.fixedMonthlyRent) > 0" class="fixed-rent">¥{{ money(row.fixedMonthlyRent) }}/月</span>
          <span v-else class="muted">按单价</span>
        </template>
      </el-table-column>
      <el-table-column label="基础租金单价" width="105" align="right">
        <template #default="{ row }">{{ num(row.rentUnitPrice, 4) }}</template>
      </el-table-column>
      <el-table-column label="基础物业单价" width="105" align="right">
        <template #default="{ row }">{{ num(row.propertyUnitPrice, 4) }}</template>
      </el-table-column>
      <el-table-column label="递增规则" width="110" align="center">
        <template #default="{ row }">每{{ row.escalationMonths || 36 }}月 +{{ Math.round(Number(row.escalationRate || 0) * 100) }}%</template>
      </el-table-column>
      <el-table-column prop="collectionTiming" label="约定收款时点" min-width="140" show-overflow-tooltip />
      <el-table-column label="状态" width="70" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '在用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="108" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑计费合同' : '新增计费合同'" width="820px" append-to-body destroy-on-close>
      <el-form :model="form" label-width="145px" size="small">
        <el-row :gutter="14">
          <el-col :span="12"><el-form-item label="租户名称" required><el-input v-model="form.tenantName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="楼层/区域" required><el-input v-model="form.unitName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="合同序号"><el-input-number v-model="form.seqNo" :min="1" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="计租面积(㎡)" required><el-input-number v-model="form.leaseArea" :min="0" :precision="2" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="起租日" required><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="到期日" required><el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>

          <el-col :span="12">
            <el-form-item label="月租金单价(元/㎡)" :required="!hasFixedRent">
              <el-input-number v-model="form.rentUnitPrice" :min="0" :precision="6" style="width: 100%"
                               :placeholder="hasFixedRent ? '已按月固定总价计费' : '元/㎡/月'" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="月物业单价(元/㎡)" required>
              <el-input-number v-model="form.propertyUnitPrice" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="月固定总价(元/月)">
              <div class="fixed-rent-row">
                <el-input-number v-model="form.fixedMonthlyRent" :min="0" :precision="2" :controls="false"
                                 placeholder="选填，如 102800（填了则按月固定总价起始并递增）" style="width: 280px" />
                <span class="unit-label">元 / 月</span>
                <el-button v-if="hasFixedRent" link type="info" @click="form.fixedMonthlyRent = null">清空改按单价</el-button>
              </div>
              <div class="field-hint">
                选填。若租户按月固定总价签约，填写后起始租金按此总价计算，后续每期递增也直接按该总价递增{{ impliedUnitText }}；留空或填 0 则按「计租面积 × 月租金单价」计算。
              </div>
            </el-form-item>
          </el-col>

          <el-col :span="12"><el-form-item label="递增比例(0.1=10%)"><el-input-number v-model="form.escalationRate" :min="0" :max="1" :step="0.01" :precision="4" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="递增周期(月)"><el-input-number v-model="form.escalationMonths" :min="1" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="租金保证金(元)"><el-input-number v-model="form.rentDeposit" :min="0" :precision="2" :controls="false" placeholder="选填" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="物业保证金(元)"><el-input-number v-model="form.propertyDeposit" :min="0" :precision="2" :controls="false" placeholder="选填" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="保证金缴纳约定"><el-input v-model="form.depositTerms" placeholder="如 签约3日内一次性缴纳 / 分期计划" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="协议编号"><el-input v-model="form.agreementNo" placeholder="租金协议号 / 物业协议号，用「 / 」分隔" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="约定收款时点"><el-input v-model="form.collectionTiming" placeholder="如 当月30日前收取下个月租金" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">在用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="免租/优惠规则JSON">
              <el-input v-model="form.rulesJson" type="textarea" :rows="4" placeholder='{"periods":[{"from":"2026-06","to":"2026-07","rentFactor":0,"propertyFactor":0,"remark":"免租期"}]}' />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { leaseFeeApi } from '@/api/energy'
import { money, num } from '../feePeriod'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue', 'changed'])

const loading = ref(false)
const saving = ref(false)
const contracts = ref([])
const editVisible = ref(false)
const form = reactive({})

const hasFixedRent = computed(() => Number(form.fixedMonthlyRent) > 0)
const impliedUnitText = computed(() => {
  if (!hasFixedRent.value || !(Number(form.leaseArea) > 0)) return ''
  const unit = Number(form.fixedMonthlyRent) / Number(form.leaseArea)
  return `（折算约 ${unit.toFixed(4)} 元/㎡/月）`
})

async function load() {
  loading.value = true
  try {
    contracts.value = await leaseFeeApi.contracts(true) || []
  } finally {
    loading.value = false
  }
}

watch(() => props.modelValue, (v) => { if (v) load() })

function openEdit(row) {
  Object.assign(form, row ? {
    ...row,
    fixedMonthlyRent: row.fixedMonthlyRent == null ? null : Number(row.fixedMonthlyRent),
    rentDeposit: row.rentDeposit == null ? null : Number(row.rentDeposit),
    propertyDeposit: row.propertyDeposit == null ? null : Number(row.propertyDeposit)
  } : {
    id: null, seqNo: contracts.value.length + 1, tenantName: '', unitName: '', agreementNo: '',
    leaseArea: 0, startDate: '', endDate: '', rentUnitPrice: 0, fixedMonthlyRent: null, propertyUnitPrice: 2,
    escalationRate: 0.1, escalationMonths: 36, rentDeposit: null, propertyDeposit: null, depositTerms: '',
    collectionTiming: '', rulesJson: '', status: 1
  })
  editVisible.value = true
}

async function save() {
  if (!form.tenantName?.trim() || !form.unitName?.trim()) return ElMessage.warning('请填写租户名称和楼层/区域')
  if (!form.startDate || !form.endDate) return ElMessage.warning('请填写起租日和到期日')
  if (form.rulesJson?.trim()) {
    try { JSON.parse(form.rulesJson) } catch { return ElMessage.warning('免租/优惠规则 JSON 格式不正确') }
  }
  const payload = {
    ...form,
    fixedMonthlyRent: Number(form.fixedMonthlyRent) > 0 ? Number(form.fixedMonthlyRent) : null
  }
  saving.value = true
  try {
    await leaseFeeApi.saveContract(payload)
    ElMessage.success('合同已保存，草稿月份已按新规则重算')
    editVisible.value = false
    await load()
    emit('changed')
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除合同「${row.tenantName} - ${row.unitName}」？`, '删除合同', { type: 'warning' })
  await leaseFeeApi.deleteContract(row.id)
  ElMessage.success('已删除')
  await load()
  emit('changed')
}
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.tip { font-size: 12px; color: var(--el-text-color-secondary); }
.fixed-rent { font-weight: 600; color: var(--el-color-primary); font-variant-numeric: tabular-nums; }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
.fixed-rent-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.unit-label { font-size: 12px; color: var(--el-text-color-secondary); }
.field-hint { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.5; margin-top: 4px; }
</style>
