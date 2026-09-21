<template>
  <view v-if="c">
    <view class="card">
      <view style="font-size:34rpx;font-weight:600">{{ c.name }}</view>
      <view class="row"><text class="muted">联系人</text><text>{{ c.contact || '-' }} {{ c.phone }}</text></view>
      <view class="row"><text class="muted">评级</text><text>{{ c.grade || '未评级' }}</text></view>
      <view class="row"><text class="muted">状态</text><text>{{ STATUS[c.status] || '-' }}</text></view>
      <view class="row"><text class="muted">锁定</text><text>{{ lockText }}</text></view>
      <view class="row"><text class="muted">推荐时间</text><text>{{ c.createTime?.slice(0, 10) }}</text></view>
    </view>
    <button v-if="c.canExtend" class="btn" @click="extend">申请延期 +90 天</button>
    <view class="muted" style="margin:0 40rpx">延期需近 60 天有到访或方案记录,由运营审核。锁定到期前 15 天会提醒您。</view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { bizApi } from '@/api'

const STATUS = { 1: '跟进中', 2: '已签约', 3: '已流失' }
const c = ref(null)
let id
onLoad(async (q) => { id = q.id; c.value = await bizApi.customer(id) })
const lockText = computed(() => { const x = c.value; return !x.lockStatus ? '未锁定' : x.lockStatus === 1 ? `预锁,剩 ${x.lockDaysLeft} 天` : x.lockStatus === 2 ? `有效锁定,剩 ${x.lockDaysLeft} 天` : x.lockStatus === 3 ? '已成交' : '已释放' })
async function extend() {
  await bizApi.extend(id, '小程序申请延期')
  uni.showToast({ title: '已延期' })
  c.value = await bizApi.customer(id)
}
</script>
