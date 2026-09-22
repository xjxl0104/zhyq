<template>
  <view class="page-wrap warehouse-login-page">
    <view class="warehouse-login-hero">
      <view class="brand-mark">W</view>
      <view class="eyebrow">DIPARK · WAREHOUSE</view>
      <view class="login-title">云仓商家端</view>
      <view class="login-subtitle">加盟进度、ERP 接入、订单与结算，一站式掌握</view>
    </view>
    <view class="card login-card">
      <view class="title">登录云仓工作台</view>
      <view class="muted login-note">{{ mock ? '开发模式已开启，可用测试标识快速体验' : '请使用微信登录并授权手机号' }}</view>
      <input v-if="mock" class="input" v-model="code" placeholder="开发模式微信标识" />
      <input class="input" v-model="warehouseId" type="number" placeholder="云仓编号（可选）" />
      <button class="btn" @click="login">微信登录</button>
    </view>
    <view v-if="needBind" class="card login-card">
      <view class="title">绑定云仓联系人</view>
      <view class="muted login-note">完成手机号绑定后即可进入云仓运营台</view>
      <input v-if="mock" class="input" v-model="phone" type="number" maxlength="11" placeholder="手机号" />
      <button v-else class="btn" open-type="getPhoneNumber" @getphonenumber="onPhone">授权手机号</button>
      <button class="btn" @click="bind">绑定并进入</button>
    </view>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { warehouseAuthApi } from '@/api/warehouse'
import { warehouseToken } from '@/utils/request'
const mock = import.meta.env.VITE_MP_MOCK_LOGIN !== 'false'
const code = ref(''); const warehouseId = ref(''); const phone = ref(''); const needBind = ref(false); const openid = ref(''); const encryptedData = ref(''); const iv = ref('')
async function login () { const c = mock ? code.value.trim() : (await uni.login({ provider: 'weixin' })).code; if (!c) return; const r = await warehouseAuthApi.wxLogin(c, warehouseId.value ? Number(warehouseId.value) : undefined); if (r.registered) return done(r.token); openid.value = r.openid; needBind.value = true }
function onPhone (e) { encryptedData.value = e.detail?.encryptedData || ''; iv.value = e.detail?.iv || '' }
async function bind () { const data = mock ? { openid: openid.value, phone: phone.value } : { openid: openid.value, encryptedData: encryptedData.value, iv: iv.value }; const r = await warehouseAuthApi.bindPhone(data); if (r.token) done(r.token) }
function done (t) { warehouseToken.set(t); uni.reLaunch({ url: '/pages/warehouse-dashboard/index' }) }
</script>
<style scoped>
.warehouse-login-page { padding-top: 34rpx; }
.warehouse-login-hero { padding: 34rpx 42rpx 30rpx; }
.warehouse-login-hero .brand-mark { margin-bottom: 34rpx; background: linear-gradient(145deg, #50c7d8, #299cb0); }
.login-title { margin-top: 12rpx; color: var(--park-ink); font-size: 52rpx; font-weight: 750; letter-spacing: -1rpx; }
.login-subtitle { margin-top: 12rpx; color: var(--park-muted); font-size: 26rpx; line-height: 1.6; }
.login-card { padding: 34rpx; }
.login-note { margin: 12rpx 0 24rpx; }
</style>
