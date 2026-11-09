<template>
  <view class="card">
    <view class="title">客户需求</view>
    <view v-if="demand" class="detail"><text class="muted">需求说明</text><text>{{ demand }}</text></view>
    <view v-for="row in rows" :key="row.label" class="detail"><text class="muted">{{ row.label }}</text><text>{{ row.value }}</text></view>
    <view v-if="remark" class="detail"><text class="muted">备注</text><text>{{ remark }}</text></view>
    <view v-if="!rows.length && !demand && !remark" class="muted empty">暂未补充详细需求</view>
  </view>
</template>
<script setup>
import { computed } from 'vue'
import { requirementRows } from '@/utils/referral-intake.mjs'
const props = defineProps({ requirements: { type: Object, default: () => ({}) }, demand: String, remark: String })
const rows = computed(() => requirementRows(props.requirements || {}))
</script>
<style scoped>.detail{display:flex;justify-content:space-between;gap:24rpx;margin-top:20rpx;line-height:1.7}.detail text:last-child{max-width:65%;text-align:right;white-space:pre-wrap;overflow-wrap:anywhere}.empty{margin-top:20rpx}</style>
