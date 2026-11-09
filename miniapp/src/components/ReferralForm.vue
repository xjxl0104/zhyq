<template>
  <view>
    <input class="input" v-model="form.name" maxlength="100" placeholder="客户公司 / 店铺名称 *" />
    <input class="input" v-model="form.brandName" maxlength="100" placeholder="品牌名称（选填，请填写准确名称）" />
    <input class="input" v-model="form.contact" maxlength="50" placeholder="联系人（选填）" />
    <input class="input" v-model="form.phone" type="number" maxlength="11" placeholder="联系手机（选填）" />
    <picker :range="SERVICE_TYPES" range-key="label" @change="e => form.serviceType = SERVICE_TYPES[e.detail.value].value">
      <view class="input">需求类型：{{ SERVICE_TYPES.find(t => t.value === form.serviceType)?.label }}</view>
    </picker>
    <picker v-if="form.serviceType !== 4" :range="warehouseOptions" range-key="name" @change="e => form.warehouseId = warehouseOptions[e.detail.value].id">
      <view class="input">意向云仓：{{ warehouseOptions.find(w => w.id === form.warehouseId)?.name || '不限' }}</view>
    </picker>
    <input class="input" v-model="form.demand" maxlength="200" placeholder="需求（面积 / 日单量 / 品类，选填）" />
    <view class="section">
      <view class="field-title">销售平台 <text class="muted">可多选，选填</text></view>
      <checkbox-group class="choices" @change="e => form.requirements.salesPlatforms = e.detail.value">
        <label v-for="item in SALES_PLATFORMS" :key="item" class="choice"><checkbox :value="item" :checked="form.requirements.salesPlatforms.includes(item)" color="#3857f5" /><text>{{ item }}</text></label>
      </checkbox-group>
      <input v-if="form.requirements.salesPlatforms.includes('其他')" class="input" v-model="form.requirements.salesPlatformOther" maxlength="100" placeholder="请填写其他销售平台" />
    </view>
    <view class="section">
      <view class="field-title">产品重量 <text class="muted">可多选，选填</text></view>
      <view class="muted hint">可补充各重量段的订单占比；填写时合计需为 100%。</view>
      <checkbox-group @change="changeWeights">
        <view v-for="range in WEIGHT_RANGES" :key="range" class="weight-row">
          <label class="choice"><checkbox :value="range" :checked="selectedWeight(range)" color="#3857f5" /><text>{{ rangeLabel(range, 'kg') }}</text></label>
          <view v-if="selectedWeight(range)" class="percentage"><input type="digit" :value="weightPercentage(range)" maxlength="6" placeholder="占比选填" @input="e => setPercentage(range, e.detail.value)" /><text>%</text></view>
        </view>
      </checkbox-group>
    </view>
    <picker v-for="field in rangeFields" :key="field.key" :range="field.options" range-key="label" @change="e => form.requirements[field.key] = field.options[e.detail.value].value">
      <view class="input">{{ field.label }}：{{ form.requirements[field.key] ? rangeLabel(form.requirements[field.key], field.unit) : '请选择（选填）' }}</view>
    </picker>
    <textarea class="input" v-model="form.requirements.packaging" maxlength="500" placeholder="打包方式（选填，如纸箱、气泡袋等）" style="height:140rpx" />
    <view class="section">
      <view class="field-title">指定快递承运商 <text class="muted">可多选，选填</text></view>
      <checkbox-group class="choices" @change="e => form.requirements.couriers = e.detail.value">
        <label v-for="item in COURIERS" :key="item" class="choice"><checkbox :value="item" :checked="form.requirements.couriers.includes(item)" color="#3857f5" /><text>{{ item }}</text></label>
      </checkbox-group>
      <input v-if="form.requirements.couriers.includes('其他')" class="input" v-model="form.requirements.courierOther" maxlength="100" placeholder="请填写其他快递承运商" />
    </view>
    <picker :range="invoiceOptions" range-key="label" @change="e => form.requirements.invoiceRequired = invoiceOptions[e.detail.value].value">
      <view class="input">是否开发票：{{ invoiceOptions.find(x => x.value === form.requirements.invoiceRequired)?.label }}</view>
    </picker>
    <textarea class="input" v-model="form.remark" maxlength="1000" placeholder="备注（选填）" style="height:160rpx" />
    <slot />
    <button class="btn" :loading="loading" :disabled="loading || disabled" @click="submit">{{ submitLabel }}</button>
  </view>
</template>
<script setup>
import { computed } from 'vue'
import { SALES_PLATFORMS, COURIERS, WEIGHT_RANGES, AREA_RANGES, COUNT_RANGES, ORDER_RANGES, SERVICE_TYPES, rangeLabel, referralPayload } from '@/utils/referral-intake.mjs'
const props = defineProps({ form: { type: Object, required: true }, warehouses: { type: Array, default: () => [] }, loading: Boolean, disabled: Boolean, submitLabel: { type: String, default: '提交推荐' } })
const emit = defineEmits(['submit'])
const warehouseOptions = computed(() => [{ id: null, name: '不限' }, ...props.warehouses])
const rangeFields = [
  { key: 'storageArea', label: '仓储面积', unit: '㎡', ranges: AREA_RANGES },
  { key: 'productCount', label: '销售产品种类数', unit: '种', ranges: COUNT_RANGES },
  { key: 'skuCount', label: 'SKU 数量', unit: '个', ranges: COUNT_RANGES },
  { key: 'dailyOrders', label: '日均单量', unit: '单', ranges: ORDER_RANGES }
].map(f => ({ ...f, options: [{ value: '', label: '暂不填写' }, ...f.ranges.map(value => ({ value, label: rangeLabel(value, f.unit) }))] }))
const invoiceOptions = [{ value: null, label: '请选择（选填）' }, { value: true, label: '是' }, { value: false, label: '否' }]
const selectedWeight = range => props.form.requirements.productWeights.some(x => x.range === range)
const weightPercentage = range => props.form.requirements.productWeights.find(x => x.range === range)?.percentage ?? ''
function changeWeights(e) {
  props.form.requirements.productWeights = WEIGHT_RANGES.filter(range => e.detail.value.includes(range)).map(range => ({ range, percentage: weightPercentage(range) }))
}
function setPercentage(range, value) { const item = props.form.requirements.productWeights.find(x => x.range === range); if (item) item.percentage = value }
function submit() {
  if (props.loading || props.disabled) return
  try { emit('submit', referralPayload(props.form)) } catch (e) { uni.showToast({ title: e.message, icon: 'none' }) }
}
</script>
<style scoped>
.section{margin:24rpx 0;padding:20rpx 0;border-bottom:1rpx solid var(--park-line)}.field-title{font-weight:600;margin-bottom:16rpx}.field-title .muted{font-weight:400;font-size:22rpx;margin-left:12rpx}.choices{display:flex;flex-wrap:wrap;gap:20rpx 28rpx}.choice{display:flex;align-items:center;gap:8rpx;min-height:60rpx}.choice checkbox{transform:scale(.82);transform-origin:left center;width:42rpx}.hint{font-size:23rpx;margin-bottom:12rpx;line-height:1.6}.weight-row{display:flex;align-items:center;justify-content:space-between;gap:12rpx;min-height:72rpx}.percentage{display:flex;align-items:center;gap:8rpx;width:180rpx}.percentage input{width:145rpx;padding:10rpx;border:1rpx solid var(--park-line);border-radius:10rpx;background:var(--park-bg);font-size:24rpx}
</style>
