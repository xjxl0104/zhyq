<template>
  <view class="page-wrap login-page">
    <view class="login-hero">
      <view class="brand-mark">P</view>
      <view class="login-title">全民营销</view>
      <view class="login-subtitle">连接客户与园区服务，让每一次推荐更有价值</view>
    </view>
    <view class="card login-card">
      <WechatAuthForm v-if="method === 'wechat'" :invite-code="inviteCode" @authenticated="done" @busy="busy = $event" />
      <PasswordAuthForm v-else :invite-code="inviteCode" @authenticated="done" @busy="busy = $event" />
      <button v-if="canUseWechat" class="method-link" :disabled="busy" @click="choose(method === 'wechat' ? 'password' : 'wechat')">{{ method === 'wechat' ? '账号密码登录（备用）' : '返回一键登录' }}</button>
    </view>
    <button class="demo-link" :disabled="busy" @click="goDemo">无需登录，进入测试体验</button>
    <button class="identity-link" :disabled="busy" @click="chooseIdentity">切换身份</button>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { token } from '@/utils/request'
import { partnerMock as mock } from '@/utils/wechat'
import PasswordAuthForm from '@/components/PasswordAuthForm.vue'
import WechatAuthForm from '@/components/WechatAuthForm.vue'
const canUseWechat = ref(mock)
// #ifdef MP-WEIXIN
canUseWechat.value = true
// #endif
const method = ref(canUseWechat.value ? 'wechat' : 'password')
const busy = ref(false)
const inviteCode = ref('')
onLoad(q => {
  try {
    const invite = decodeURIComponent(q?.invite || q?.scene || '')
    if (/^[A-Za-z0-9]{8}$/.test(invite)) inviteCode.value = invite
  } catch (_) { /* An invalid invitation must not prevent login. */ }
})
function choose(value) { if (!busy.value) method.value = value }
function done(t) { token.set(t); uni.switchTab({ url: '/pages/home/index' }) }
function chooseIdentity() { if (!busy.value) uni.reLaunch({ url: '/pages/entry/index?select=1' }) }
function goDemo() { if (!busy.value) uni.navigateTo({ url: '/pages/demo/index' }) }
</script>

<style scoped>
.login-page { padding: 24rpx 0 56rpx; }
.login-hero { padding: 30rpx 42rpx 12rpx; }
.login-hero .brand-mark { margin-bottom: 24rpx; }
.login-title { color: var(--park-ink); font-size: 48rpx; font-weight: 750; }
.login-subtitle { max-width: 560rpx; margin-top: 12rpx; color: #58617d; font-size: 28rpx; line-height: 1.6; }
.login-card { padding: 32rpx; }
.identity-link { padding: 16rpx; color: #58617d; background: transparent; font-size: 28rpx; line-height: 1.5; }
.demo-link { margin-top: 24rpx; padding: 18rpx; color: var(--park-blue); background: transparent; font-size: 26rpx; line-height: 1.5; }
button:focus-visible { outline: 2px solid var(--park-blue); }
.method-link { margin-top: 24rpx; padding: 20rpx 0; background: transparent; color: #2e47cc; font-size: 28rpx; line-height: 1.5; }
</style>
