<template>
  <view>
    <!-- 1 客户信息 -->
    <view class="group first">
      <view class="group-title"><text class="bar" />客户信息</view>
      <input class="input" v-model="form.name" maxlength="100" placeholder="公司 / 品牌名称 *" />
      <view class="pair">
        <input class="input" v-model="form.contact" maxlength="50" placeholder="对接人（选填）" />
        <input class="input" v-model="form.requirements.contactPosition" maxlength="50" placeholder="对接人职位（选填）" />
      </view>
      <input class="input" v-model="form.phone" type="number" maxlength="11" placeholder="联系手机（选填）" />
    </view>

    <!-- 2 业务规模 -->
    <view class="group">
      <view class="group-title"><text class="bar" />业务规模 <text class="muted">选填</text></view>
      <view class="sub-title first-sub">销售品类 <text class="muted">可多选</text></view>
      <checkbox-group class="choices" @change="e => form.requirements.productCategories = e.detail.value">
        <label v-for="item in PRODUCT_CATEGORIES" :key="item" class="choice"><checkbox :value="item" :checked="form.requirements.productCategories.includes(item)" color="#3857f5" /><text>{{ item }}</text></label>
      </checkbox-group>
      <input v-if="form.requirements.productCategories.includes('其他')" class="input" v-model="form.requirements.productCategoryOther" maxlength="100" placeholder="请填写其他销售品类" />
      <view v-for="field in rangeFields" :key="field.key">
        <view class="sub-title">{{ field.label }} <text class="muted">单选</text></view>
        <radio-group class="choices" @change="e => form.requirements[field.key] = e.detail.value">
          <label v-for="option in field.options" :key="option.value" class="choice"><radio :value="option.value" :checked="(form.requirements[field.key] || '') === option.value" color="#3857f5" /><text>{{ option.label }}</text></label>
        </radio-group>
      </view>
      <view class="sub-title">产品重量 <text class="muted">可多选；填写占比时合计需为 100%</text></view>
      <checkbox-group @change="changeWeights">
        <view v-for="range in WEIGHT_RANGES" :key="range" class="weight-row">
          <label class="choice"><checkbox :value="range" :checked="selectedWeight(range)" color="#3857f5" /><text>{{ rangeLabel(range, 'kg') }}</text></label>
          <view v-if="selectedWeight(range)" class="percentage"><input type="digit" :value="weightPercentage(range)" maxlength="6" placeholder="占比选填" @input="e => setPercentage(range, e.detail.value)" /><text>%</text></view>
        </view>
      </checkbox-group>
    </view>

    <!-- 3 销售与发货 -->
    <view class="group">
      <view class="group-title"><text class="bar" />销售与发货</view>
      <view class="sub-title first-sub">销售平台 <text class="muted">可多选，选填</text></view>
      <checkbox-group class="choices" @change="e => form.requirements.salesPlatforms = e.detail.value">
        <label v-for="item in SALES_PLATFORMS" :key="item" class="choice"><checkbox :value="item" :checked="form.requirements.salesPlatforms.includes(item)" color="#3857f5" /><text>{{ item }}</text></label>
      </checkbox-group>
      <input v-if="form.requirements.salesPlatforms.includes('其他')" class="input" v-model="form.requirements.salesPlatformOther" maxlength="100" placeholder="请填写其他销售平台" />
      <view class="sub-title">指定快递承运商 <text class="muted">可多选，选填</text></view>
      <checkbox-group class="choices" @change="e => form.requirements.couriers = exclusiveNone(form.requirements.couriers, e.detail.value)">
        <label v-for="item in COURIERS" :key="item" class="choice"><checkbox :value="item" :checked="form.requirements.couriers.includes(item)" color="#3857f5" /><text>{{ item }}</text></label>
      </checkbox-group>
      <input v-if="form.requirements.couriers.includes('其他')" class="input" v-model="form.requirements.courierOther" maxlength="100" placeholder="请填写其他快递承运商" />
    </view>

    <!-- 4 现有仓配现状 -->
    <view class="group">
      <view class="group-title"><text class="bar" />现有仓配现状 <text class="muted">选填</text></view>
      <picker :range="warehouseModeOptions" :value="selectedIndex(warehouseModeOptions, form.requirements.warehouseMode)" range-key="label" @change="e => form.requirements.warehouseMode = warehouseModeOptions[e.detail.value].value">
        <view class="input select">
          <text class="sel-label">自建仓 / 外包云仓</text>
          <text :class="['sel-value', { empty: !form.requirements.warehouseMode }]">{{ form.requirements.warehouseMode || '请选择' }}</text>
        </view>
      </picker>
      <view class="sub-title">目前合作的快递公司 <text class="muted">可多选</text></view>
      <checkbox-group class="choices" @change="e => form.requirements.currentCouriers = exclusiveNone(form.requirements.currentCouriers, e.detail.value)">
        <label v-for="item in COURIERS" :key="item" class="choice"><checkbox :value="item" :checked="form.requirements.currentCouriers.includes(item)" color="#3857f5" /><text>{{ item }}</text></label>
      </checkbox-group>
      <input v-if="form.requirements.currentCouriers.includes('其他')" class="input" v-model="form.requirements.currentCourierOther" maxlength="100" placeholder="请填写目前合作的其他快递" />
      <view class="sub-title">目前最头疼的问题 <text class="muted">可多选</text></view>
      <checkbox-group class="choices" @change="e => form.requirements.painPoints = e.detail.value">
        <label v-for="item in PAIN_POINTS" :key="item" class="choice"><checkbox :value="item" :checked="form.requirements.painPoints.includes(item)" color="#3857f5" /><text>{{ item }}</text></label>
      </checkbox-group>
      <input v-if="form.requirements.painPoints.includes('其他')" class="input" v-model="form.requirements.painPointOther" maxlength="100" placeholder="请填写其他头疼的问题" />
    </view>

    <!-- 5 备注 + 提交 -->
    <view class="group">
      <view class="group-title"><text class="bar" />备注 <text class="muted">选填</text></view>
      <textarea class="input" v-model="form.remark" maxlength="1000" placeholder="还有什么需要补充的" style="height:160rpx" />
    </view>
    <slot />
    <button class="btn" :loading="loading" :disabled="loading || disabled" @click="submit">{{ submitLabel }}</button>
  </view>
</template>
<script setup>
import { SALES_PLATFORMS, PRODUCT_CATEGORIES, ITEMS_PER_PARCEL_RANGES, itemsPerParcelLabel, COURIERS, WEIGHT_RANGES, COUNT_RANGES, ORDER_RANGES, WAREHOUSE_MODES, PAIN_POINTS, rangeLabel, referralPayload, exclusiveNone } from '@/utils/referral-intake.mjs'
const props = defineProps({ form: { type: Object, required: true }, warehouses: { type: Array, default: () => [] }, loading: Boolean, disabled: Boolean, submitLabel: { type: String, default: '提交推荐' } })
const emit = defineEmits(['submit'])
const rangeFields = [
  { key: 'skuCount', label: 'SKU 数量', unit: '个', ranges: COUNT_RANGES },
  { key: 'dailyOrders', label: '日均单量', unit: '单', ranges: ORDER_RANGES },
  { key: 'itemsPerParcelRange', label: '平均包裹产品数量', ranges: ITEMS_PER_PARCEL_RANGES, labelFor: itemsPerParcelLabel }
].map(f => ({ ...f, options: [{ value: '', label: '暂不填写' }, ...f.ranges.map(value => ({ value, label: f.labelFor ? f.labelFor(value) : rangeLabel(value, f.unit) }))] }))
const warehouseModeOptions = [{ value: '', label: '暂不填写' }, ...WAREHOUSE_MODES.map(value => ({ value, label: value }))]
const selectedIndex = (options, value, key = 'value') => Math.max(0, options.findIndex(option => option[key] === value))
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
.group{margin-top:36rpx;padding-top:32rpx;border-top:1rpx solid var(--park-line)}.group.first{margin-top:24rpx;padding-top:0;border-top:0}
.group-title{display:flex;align-items:center;font-size:30rpx;font-weight:700;margin-bottom:8rpx}.group-title .muted{font-weight:400;font-size:22rpx;margin-left:12rpx}
.bar{width:8rpx;height:30rpx;margin-right:14rpx;border-radius:4rpx;background:var(--park-blue)}
.pair{display:flex;gap:16rpx}.pair .input{flex:1;min-width:0}
.select{display:flex;align-items:center;justify-content:space-between;gap:16rpx}.sel-label{flex-shrink:0}.sel-value{color:var(--park-text);text-align:right;font-weight:600}.sel-value.empty{color:var(--park-muted);font-weight:400}
.sub-title{font-size:26rpx;font-weight:600;margin:28rpx 0 12rpx}.sub-title .muted{font-weight:400;font-size:22rpx;margin-left:12rpx}.sub-title.first-sub{margin-top:12rpx}
.choices{display:flex;flex-wrap:wrap;gap:20rpx 28rpx}.choice{display:flex;align-items:center;gap:8rpx;min-height:60rpx}.choice checkbox,.choice radio{transform:scale(.82);transform-origin:left center;width:42rpx}
.weight-row{display:flex;align-items:center;justify-content:space-between;gap:12rpx;min-height:72rpx}.percentage{display:flex;align-items:center;gap:8rpx;width:180rpx}.percentage input{width:145rpx;padding:10rpx;border:1rpx solid var(--park-line);border-radius:10rpx;background:var(--park-bg);font-size:24rpx}
</style>
