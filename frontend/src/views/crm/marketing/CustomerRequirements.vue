<template>
  <el-descriptions v-if="readonly" :column="1" border class="requirements-detail">
    <el-descriptions-item v-for="[label, text] in requirementsRows(modelValue)" :key="label" :label="label">{{ text }}</el-descriptions-item>
  </el-descriptions>
  <template v-else>
    <el-form-item label="对接人职位"><el-input :model-value="modelValue.contactPosition" @update:model-value="update('contactPosition', $event)" maxlength="50" placeholder="选填，如运营总监" /></el-form-item>
    <el-form-item label="销售平台"><el-checkbox-group :model-value="modelValue.salesPlatforms" @update:model-value="update('salesPlatforms', $event)"><el-checkbox v-for="v in platformOptions" :key="v" :label="v" /></el-checkbox-group><el-input v-if="modelValue.salesPlatforms.includes('其他')" :model-value="modelValue.salesPlatformOther" @update:model-value="update('salesPlatformOther', $event)" maxlength="100" placeholder="其他销售平台" /></el-form-item>
    <el-form-item label="产品重量">
      <div class="weight-options"><div v-for="range in weightRanges" :key="range" class="weight-row">
        <el-checkbox :model-value="isSelected(range)" @change="toggleWeight(range, $event)">{{ rangeLabel(range, 'kg') }}</el-checkbox>
        <el-input-number v-if="isSelected(range)" :model-value="weight(range).percentage" @update:model-value="setPercentage(range, $event)" :min="0" :max="100" :precision="2" :controls="false" placeholder="选填占比" size="small" /><span v-if="isSelected(range)">%</span>
      </div><div class="field-hint">可多选；占比可全部留空，填写时合计需为 100%。</div></div>
    </el-form-item>
    <el-form-item v-for="f in rangeFields" :key="f.key" :label="f.label"><el-select :model-value="modelValue[f.key]" @update:model-value="update(f.key, $event)" clearable placeholder="选填" style="width:100%"><el-option v-for="v in f.ranges" :key="v" :value="v" :label="rangeLabel(v, f.unit)" /></el-select></el-form-item>
    <el-form-item label="平均包裹产品数量"><el-input-number :model-value="modelValue.itemsPerParcel" @update:model-value="update('itemsPerParcel', $event ?? null)" :min="0.01" :max="9999" :precision="2" :controls="false" placeholder="选填，件" /></el-form-item>
    <el-form-item label="指定快递承运商"><el-checkbox-group :model-value="modelValue.couriers" @update:model-value="update('couriers', $event)"><el-checkbox v-for="v in courierOptions" :key="v" :label="v" /></el-checkbox-group><el-input v-if="modelValue.couriers.includes('其他')" :model-value="modelValue.courierOther" @update:model-value="update('courierOther', $event)" maxlength="100" placeholder="其他快递承运商" /></el-form-item>
    <el-divider content-position="left">现有仓配现状</el-divider>
    <el-form-item label="目前仓配方式"><el-select :model-value="modelValue.warehouseMode" @update:model-value="update('warehouseMode', $event || '')" clearable placeholder="自建仓还是外包云仓（选填）" style="width:100%"><el-option v-for="v in warehouseModeOptions" :key="v" :value="v" :label="v" /></el-select></el-form-item>
    <el-form-item label="目前合作快递"><el-checkbox-group :model-value="modelValue.currentCouriers || []" @update:model-value="update('currentCouriers', $event)"><el-checkbox v-for="v in courierOptions" :key="v" :label="v" /></el-checkbox-group><el-input v-if="(modelValue.currentCouriers || []).includes('其他')" :model-value="modelValue.currentCourierOther" @update:model-value="update('currentCourierOther', $event)" maxlength="100" placeholder="其他快递" /></el-form-item>
    <el-form-item label="最头疼的问题"><el-checkbox-group :model-value="modelValue.painPoints || []" @update:model-value="update('painPoints', $event)"><el-checkbox v-for="v in painPointOptions" :key="v" :label="v" /></el-checkbox-group><el-input v-if="(modelValue.painPoints || []).includes('其他')" :model-value="modelValue.painPointOther" @update:model-value="update('painPointOther', $event)" maxlength="100" placeholder="其他问题" /></el-form-item>
  </template>
</template>
<script setup>
import { platformOptions, courierOptions, warehouseModeOptions, painPointOptions, weightRanges, rangeFields, rangeLabel, requirementsRows } from './customerRequirements'
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
