<template>
  <view>
    <!-- 1 客户信息 -->
    <view class="group first">
      <view class="group-title"><text class="bar" />客户信息</view>
      <input class="input" v-model="form.name" maxlength="100" placeholder="公司名称 *" />
      <input class="input" v-model="form.brandName" maxlength="100" placeholder="品牌名称（选填）" />
      <input class="input" v-model="form.contact" maxlength="50" placeholder="对接人（选填）" />
      <input class="input" v-model="form.phone" type="number" maxlength="11" placeholder="联系手机（选填）" />
    </view>

    <!-- 现有仓配现状 -->
    <view class="group">
      <view class="group-title"><text class="bar" />现有仓配现状 <text class="muted">选填</text></view>
      <view class="sub-title first-sub">目前仓配方式 <text class="muted">单选</text><text v-if="form.requirements.warehouseMode" class="clear-choice" @click="clearRange('warehouseMode')">清除</text></view>
      <radio-group class="choices" @change="e => form.requirements.warehouseMode = e.detail.value">
        <label v-for="mode in WAREHOUSE_MODES" :key="mode" class="choice"><radio :value="mode" :checked="form.requirements.warehouseMode === mode" color="#3857f5" /><text>{{ mode }}</text></label>
      </radio-group>
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

    <slot />
    <button class="btn" :loading="loading" :disabled="loading || disabled" @click="submit">{{ submitLabel }}</button>
  </view>
</template>
<script setup>
import { WAREHOUSE_MODES, PAIN_POINTS, COURIERS, referralPayload, exclusiveNone } from '@/utils/referral-intake.mjs'
const props = defineProps({ form: { type: Object, required: true }, loading: Boolean, disabled: Boolean, submitLabel: { type: String, default: '提交推荐' } })
const emit = defineEmits(['submit'])
const clearRange = key => { props.form.requirements[key] = '' }
function submit() {
  if (props.loading || props.disabled) return
  try { emit('submit', referralPayload(props.form)) } catch (e) { uni.showToast({ title: e.message, icon: 'none' }) }
}
</script>
<style scoped>
.group{margin-top:36rpx;padding-top:32rpx;border-top:1rpx solid var(--park-line)}.group.first{margin-top:24rpx;padding-top:0;border-top:0}
.group-title{display:flex;align-items:center;font-size:36rpx;font-weight:700;margin-bottom:12rpx}.group-title .muted{font-weight:400;font-size:24rpx;margin-left:12rpx}
.bar{width:8rpx;height:30rpx;margin-right:14rpx;border-radius:4rpx;background:var(--park-blue)}
.sub-title{font-size:31rpx;font-weight:600;margin:30rpx 0 16rpx}.sub-title .muted{font-weight:400;font-size:24rpx;margin-left:12rpx}.sub-title.first-sub{margin-top:12rpx}
.clear-choice{float:right;padding:0 12rpx;color:var(--park-blue);font-size:22rpx;font-weight:400;line-height:40rpx}
.choices{display:flex;flex-wrap:wrap;gap:20rpx 28rpx}.choice{display:flex;align-items:center;gap:8rpx;min-height:60rpx}.choice checkbox,.choice radio{transform:scale(.82);transform-origin:left center;width:42rpx}
</style>
