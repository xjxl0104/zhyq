<template>
  <el-descriptions v-if="readonly" :column="1" border class="requirements-detail">
    <el-descriptions-item v-for="[label, text] in requirementsRows(modelValue)" :key="label" :label="label">{{ text }}</el-descriptions-item>
  </el-descriptions>
  <template v-else>
    <el-form-item label="销售平台"><el-checkbox-group :model-value="modelValue.salesPlatforms" @update:model-value="update('salesPlatforms', $event)"><el-checkbox v-for="v in platformOptions" :key="v" :label="v" /></el-checkbox-group><el-input v-if="modelValue.salesPlatforms.includes('其他')" :model-value="modelValue.salesPlatformOther" @update:model-value="update('salesPlatformOther', $event)" maxlength="100" placeholder="其他销售平台" /></el-form-item>
    <el-form-item label="产品重量">
      <div class="weight-options"><div v-for="range in weightRanges" :key="range" class="weight-row">
        <el-checkbox :model-value="isSelected(range)" @change="toggleWeight(range, $event)">{{ rangeLabel(range, 'kg') }}</el-checkbox>
        <el-input-number v-if="isSelected(range)" :model-value="weight(range).percentage" @update:model-value="setPercentage(range, $event)" :min="0" :max="100" :precision="2" :controls="false" placeholder="选填占比" size="small" /><span v-if="isSelected(range)">%</span>
      </div><div class="field-hint">可多选；占比可全部留空，填写时合计需为 100%。</div></div>
    </el-form-item>
    <el-form-item v-for="f in rangeFields" :key="f.key" :label="f.label"><el-select :model-value="modelValue[f.key]" @update:model-value="update(f.key, $event)" clearable placeholder="选填" style="width:100%"><el-option v-for="v in f.ranges" :key="v" :value="v" :label="rangeLabel(v, f.unit)" /></el-select></el-form-item>
    <el-form-item label="打包方式"><el-input :model-value="modelValue.packaging" @update:model-value="update('packaging', $event)" type="textarea" maxlength="500" placeholder="选填，如纸箱、气泡袋、定制包装等" /></el-form-item>
    <el-form-item label="指定快递承运商"><el-checkbox-group :model-value="modelValue.couriers" @update:model-value="update('couriers', $event)"><el-checkbox v-for="v in courierOptions" :key="v" :label="v" /></el-checkbox-group><el-input v-if="modelValue.couriers.includes('其他')" :model-value="modelValue.courierOther" @update:model-value="update('courierOther', $event)" maxlength="100" placeholder="其他快递承运商" /></el-form-item>
    <el-form-item label="是否开发票"><el-select :model-value="modelValue.invoiceRequired" @update:model-value="update('invoiceRequired', $event === '' ? null : $event)" clearable placeholder="选填"><el-option label="是" :value="true" /><el-option label="否" :value="false" /></el-select></el-form-item>
  </template>
</template>
<script setup>
import { platformOptions, courierOptions, weightRanges, rangeFields, rangeLabel, requirementsRows } from './customerRequirements'
const props = defineProps({ modelValue: { type: Object, required: true }, readonly: Boolean })
const emit = defineEmits(['update:modelValue'])
const update = (key, value) => emit('update:modelValue', { ...props.modelValue, [key]: value })
const weight = range => props.modelValue.productWeights.find(w => w.range === range)
const isSelected = range => !!weight(range)
function toggleWeight(range, checked) {
  update('productWeights', checked ? [...props.modelValue.productWeights, { range, percentage: null }] : props.modelValue.productWeights.filter(w => w.range !== range))
}
function setPercentage(range, percentage) {
  update('productWeights', props.modelValue.productWeights.map(w => w.range === range ? { range, percentage: percentage ?? null } : w))
}
</script>
<style scoped>
.weight-options { width:100%; }.weight-row { display:flex; align-items:center; gap:8px; }.weight-row .el-checkbox { min-width:180px; }.weight-row .el-input-number { width:110px; }.field-hint { color:var(--el-text-color-secondary); font-size:12px; }.requirements-detail { overflow-wrap:anywhere; }
</style>
