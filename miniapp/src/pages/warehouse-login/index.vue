<template>
  <view class="page-wrap warehouse-login-page">
    <view class="warehouse-login-hero">
      <view class="brand-mark">W</view>
      <view class="login-title">云仓商家端</view>
      <view class="login-subtitle">加盟进度、ERP 接入、订单与结算，一站式掌握</view>
    </view>
    <view class="card login-card">
      <WechatAuthForm v-if="method === 'wechat'" warehouse @authenticated="done" @busy="busy = $event" />
      <PasswordAuthForm v-else warehouse @authenticated="done" />
      <button v-if="canUseWechat" class="method-link" :disabled="busy" @click="choose(method === 'wechat' ? 'password' : 'wechat')">{{ method === 'wechat' ? '使用账号密码登录' : '返回一键登录' }}</button>
    </view>
    <button class="identity-link" :disabled="busy" @click="chooseIdentity">切换身份</button>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { warehouseToken } from '@/utils/request'
import { warehouseMock as mock } from '@/utils/wechat'
import PasswordAuthForm from '@/components/PasswordAuthForm.vue'
import WechatAuthForm from '@/components/WechatAuthForm.vue'
const canUseWechat = ref(mock)
// #ifdef MP-WEIXIN
canUseWechat.value = true
// #endif
const method = ref(canUseWechat.value ? 'wechat' : 'password')
const busy = ref(false)

function choose(value) { if (!busy.value) method.value = value }
function done(t) { warehouseToken.set(t); uni.reLaunch({ url: '/pages/warehouse-dashboard/index' }) }
function chooseIdentity() { uni.reLaunch({ url: '/pages/entry/index' }) }
</script>

<style scoped>
.warehouse-login-page { padding: 24rpx 0 56rpx; }
.warehouse-login-hero { padding: 30rpx 42rpx 12rpx; }
.warehouse-login-hero .brand-mark { margin-bottom: 24rpx; background: #248596; }
.login-title { color: var(--park-ink); font-size: 48rpx; font-weight: 750; }
.login-subtitle { margin-top: 12rpx; color: #58617d; font-size: 28rpx; line-height: 1.6; }
.login-card { padding: 32rpx; }
.identity-link { padding: 16rpx; color: #58617d; background: transparent; font-size: 28rpx; line-height: 1.5; }
button:focus-visible { outline: 2px solid var(--park-blue); }
.method-link { margin-top: 24rpx; padding: 20rpx 0; background: transparent; color: #2e47cc; font-size: 28rpx; line-height: 1.5; }
</style>
