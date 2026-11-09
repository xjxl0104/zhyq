<template>
  <view class="card">
    <view class="progress-heading"><view class="title">服务进度</view><text class="percent">{{ percent }}%</text></view>
    <view class="track"><view class="fill" :style="{ width: percent + '%' }" /></view>
    <view class="summary">{{ customer.publicProgress || '园区尚未更新服务进度，请稍后查看。' }}</view>
    <view v-if="customer.progressUpdatedAt" class="muted time">更新于 {{ formatTime(customer.progressUpdatedAt) }}</view>
    <view class="history-heading">进度记录</view>
    <view v-if="customer.progressHistoryHasMore" class="muted time">展示最近 100 条记录</view>
    <view v-if="!history.length" class="muted time">暂无进度记录</view>
    <view v-for="item in history" :key="item.id" class="history-item">
      <view class="history-meta"><text v-if="item.percentage != null" class="history-percent">{{ item.percentage }}%</text><text class="muted time">{{ formatTime(item.createdAt) }}</text></view>
      <view class="history-summary">{{ item.summary || '进度已更新' }}</view>
    </view>
  </view>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ customer: { type: Object, required: true } })
const percent = computed(() => Math.max(0, Math.min(100, Number(props.customer.progressPercentage) || 0)))
const history = computed(() => props.customer.progressHistory || [])
const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 16) : '时间待补充'
</script>
<style scoped>
.progress-heading,.history-meta{display:flex;align-items:center;justify-content:space-between;gap:20rpx}.percent{color:var(--park-blue);font-size:36rpx;font-weight:700}.track{height:14rpx;border-radius:12rpx;background:#e9edf8;margin:24rpx 0;overflow:hidden}.fill{height:100%;background:linear-gradient(90deg,var(--park-blue-2),var(--park-blue));border-radius:12rpx;transition:width .25s}.summary,.history-summary{white-space:pre-wrap;line-height:1.7;overflow-wrap:anywhere}.summary{margin:16rpx 0}.time{font-size:23rpx}.history-heading{margin-top:30rpx;font-weight:600}.history-item{margin-top:20rpx;padding:0 0 20rpx 18rpx;border-left:4rpx solid #dce4ff;border-bottom:1rpx solid var(--park-line)}.history-percent{color:var(--park-blue);font-weight:600}.history-summary{margin-top:10rpx;font-size:25rpx}
</style>
