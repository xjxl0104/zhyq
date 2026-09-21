<template>
  <view>
    <view class="card" v-if="!list.length && loaded"><view class="muted">还没有推荐客户</view><button class="btn" @click="uni.navigateTo({ url: '/pages/referral/index' })">去推荐</button></view>
    <view class="card" v-for="c in list" :key="c.id" @click="uni.navigateTo({ url: '/pages/customer-detail/index?id=' + c.id })">
      <view class="row">
        <view>
          <view style="font-weight:600">{{ c.name }}</view>
          <view class="muted">{{ c.contact || '' }} {{ c.phone }}</view>
        </view>
        <view style="text-align:right">
          <view><text class="tag" :class="lockClass(c)">{{ lockText(c) }}</text></view>
          <view class="muted" style="margin-top:6rpx">{{ c.grade ? c.grade + ' 级' : '未评级' }} · {{ STATUS[c.status] || '' }}</view>
        </view>
      </view>
    </view>
    <button v-if="list.length" class="btn" @click="uni.navigateTo({ url: '/pages/referral/index' })">推荐新客户</button>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { bizApi } from '@/api'

const STATUS = { 1: '跟进中', 2: '已签约', 3: '已流失' }
const list = ref([])
const loaded = ref(false)
const lockText = (c) => !c.lockStatus ? '未锁定' : c.lockStatus === 1 ? `预锁 · 剩 ${c.lockDaysLeft} 天` : c.lockStatus === 2 ? `锁定 · 剩 ${c.lockDaysLeft} 天` : c.lockStatus === 3 ? '已成交' : '已释放'
const lockClass = (c) => c.lockStatus === 2 || c.lockStatus === 3 ? 'ok' : c.lockStatus === 1 ? 'warn' : ''

onShow(async () => { list.value = (await bizApi.customers({ pageNo: 1, pageSize: 100 })).records; loaded.value = true })
</script>
