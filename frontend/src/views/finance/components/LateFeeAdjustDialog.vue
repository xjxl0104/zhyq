<template>
  <el-dialog :model-value="modelValue" title="调整滞纳金" width="480px"
             @update:model-value="$emit('update:modelValue', $event)">
    <el-form v-if="bill" label-width="96px">
      <el-form-item label="账单号"><span>{{ bill.code }}</span></el-form-item>
      <el-form-item label="费用类型"><span>{{ bill.feeType }}</span></el-form-item>
      <el-form-item label="本金">
        <span>¥{{ money(bill.amount) }}<span class="muted">（已收 ¥{{ money(bill.paidAmount) }}）</span></span>
      </el-form-item>
      <el-form-item label="系统算出">
        <span class="strike-hint">¥{{ money(bill.lateFee) }}</span>
        <el-tag v-if="bill.lateFeeManual === 1" type="warning" size="small" class="tag">已人工调整</el-tag>
      </el-form-item>
      <el-form-item label="调整为">
        <el-input-number v-model="form.lateFee" :precision="2" :min="0" :step="100"
                         controls-position="right" style="width: 100%" />
      </el-form-item>
      <el-form-item label="调整原因">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" show-word-limit
                  placeholder="如：与租户协商减免、系统重复计算等" />
      </el-form-item>
      <div class="tip">
        调整后该账单的滞纳金<strong>锁定</strong>，不再被每日自动重算覆盖；欠款 = 本金 + 滞纳金，
        减免后如已收满会自动转为「已结清」。点「恢复自动计算」可撤销锁定、按系统口径重算。
      </div>
    </el-form>
    <template #footer>
      <el-button v-if="bill?.lateFeeManual === 1" :loading="saving" @click="restore">恢复自动计算</el-button>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { billApi } from '@/api/finance'

const props = defineProps({ modelValue: Boolean, bill: Object })
const emit = defineEmits(['update:modelValue', 'saved'])
const saving = ref(false)
const form = reactive({ lateFee: 0, remark: '' })

function money(v) { return v == null ? '0.00' : Number(v).toFixed(2) }

// 每次打开都用当前账单的值重置,避免上一张单的输入串到下一张
watch(() => [props.modelValue, props.bill], ([open, bill]) => {
  if (!open || !bill) return
  form.lateFee = Number(bill.lateFee || 0)
  form.remark = bill.lateFeeRemark || ''
}, { immediate: true })

async function save() {
  saving.value = true
  try {
    await billApi.adjustLateFee(props.bill.id, { lateFee: form.lateFee, remark: form.remark })
    ElMessage.success(`滞纳金已调整为 ¥${money(form.lateFee)}，不再被自动重算覆盖`)
    emit('update:modelValue', false)
    emit('saved')
  } finally { saving.value = false }
}

async function restore() {
  saving.value = true
  try {
    await billApi.adjustLateFee(props.bill.id, { restoreAuto: true })
    ElMessage.success('已恢复自动计算并重算')
    emit('update:modelValue', false)
    emit('saved')
  } finally { saving.value = false }
}
</script>

<style scoped>
.muted { margin-left: 8px; color: var(--el-text-color-secondary); }
.strike-hint { color: var(--el-text-color-secondary); }
.tag { margin-left: 8px; }
.tip { margin: 4px 12px 0 96px; font-size: 12px; line-height: 1.6; color: var(--el-text-color-secondary); }
</style>
