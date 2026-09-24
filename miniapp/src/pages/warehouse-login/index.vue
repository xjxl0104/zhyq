<template>
  <view class="page-wrap warehouse-login-page">
    <view class="warehouse-login-hero">
      <view class="brand-mark">W</view>
      <view class="login-title">云仓商家端</view>
      <view class="login-subtitle">加盟进度、ERP 接入、订单与结算，一站式掌握</view>
    </view>
    <view class="card login-card">
      <view class="login-methods">
        <button :class="{ active: method === 'password' }" :disabled="busy" @click="choose('password')">账号密码</button>
        <button :class="{ active: method === 'wechat' }" :disabled="busy" @click="choose('wechat')">微信登录</button>
      </view>
      <PasswordAuthForm v-if="method === 'password'" warehouse @authenticated="done" />
      <view v-else>
        <view class="title">{{ needBind ? '绑定云仓联系人' : '微信快捷登录' }}</view>
        <template v-if="!needBind">
          <view class="login-note">{{ mock ? '开发测试模式，请输入测试标识' : '使用已绑定云仓的微信登录；首次绑定需要授权手机号。' }}</view>
          <input v-if="mock" class="input" v-model="code" placeholder="开发模式微信标识" aria-label="开发标识" />
          <input class="input" v-model="warehouseId" type="number" placeholder="云仓编号（选填）" aria-label="云仓编号" />
          <button class="btn" :loading="busy" :disabled="busy" @click="login">微信登录</button>
        </template>
        <template v-else>
          <view class="login-note">授权的手机号需与已登记云仓的联系电话一致。新商家可通过账号密码页注册。</view>
          <input v-if="mock" class="input" v-model="phone" type="number" maxlength="11" placeholder="手机号" aria-label="手机号" />
          <button v-if="mock" class="btn" :loading="busy" :disabled="busy" @click="bind()">绑定并进入</button>
          <button v-else class="btn" open-type="getPhoneNumber" :loading="busy" :disabled="busy" @getphonenumber="onPhone">授权手机号并进入</button>
          <button class="btn ghost" :disabled="busy" @click="restart">重新获取微信身份</button>
        </template>
        <view v-if="error" class="login-error" role="alert">{{ error }}</view>
      </view>
    </view>
    <button class="identity-link" :disabled="busy" @click="chooseIdentity">切换身份</button>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { warehouseAuthApi } from '@/api/warehouse'
import { warehouseToken } from '@/utils/request'
import { warehouseMock as mock, getWechatLogin, readPhoneAuthorization } from '@/utils/wechat'
import PasswordAuthForm from '@/components/PasswordAuthForm.vue'
const method = ref('password')
const code = ref(''); const warehouseId = ref(''); const phone = ref(''); const needBind = ref(false); const openid = ref(''); const loginTicket = ref('')
const busy = ref(false); const error = ref('')
function choose(value) { method.value = value; error.value = '' }
function restart() { needBind.value = false; openid.value = ''; loginTicket.value = ''; error.value = '' }
async function login() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    if (warehouseId.value && !/^[1-9]\d*$/.test(warehouseId.value)) throw new Error('云仓编号应为正整数，也可留空')
    const wx = await getWechatLogin(mock, code.value)
    const r = await warehouseAuthApi.wxLogin(wx.code, warehouseId.value ? Number(warehouseId.value) : undefined, wx.appId)
    if (r.registered && r.token) return done(r.token)
    openid.value = r.openid; loginTicket.value = r.loginTicket || ''; needBind.value = true
  } catch (e) { error.value = e.message || '微信登录未完成，请重试或使用账号密码登录' }
  finally { busy.value = false }
}
function onPhone(e) { try { bind(readPhoneAuthorization(e)) } catch (err) { error.value = err.message } }
async function bind(phoneAuth = {}) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    if (mock && !/^1\d{10}$/.test(phone.value)) throw new Error('请输入正确的 11 位手机号')
    const data = { openid: openid.value, loginTicket: loginTicket.value, ...phoneAuth }
    if (mock) data.phone = phone.value
    const r = await warehouseAuthApi.bindPhone(data)
    if (!r.token) throw new Error('未找到手机号对应的云仓，请联系园区确认登记资料，或返回账号密码页注册新商家')
    done(r.token)
  } catch (e) {
    if (!mock) { needBind.value = false; openid.value = ''; loginTicket.value = '' }
    error.value = (e.message || '绑定未完成') + (mock ? '' : '；请重新微信登录后授权')
  }
  finally { busy.value = false }
}
function done(t, newlyRegistered = false) { warehouseToken.set(t); uni.reLaunch({ url: newlyRegistered ? '/pages/warehouse-apply/index' : '/pages/warehouse-dashboard/index' }) }
function chooseIdentity() { uni.reLaunch({ url: '/pages/entry/index' }) }
</script>
<style scoped>
.warehouse-login-page { padding: 24rpx 0 56rpx; }
.warehouse-login-hero { padding: 30rpx 42rpx 12rpx; }
.warehouse-login-hero .brand-mark { margin-bottom: 24rpx; background: #248596; }
.login-title { color: var(--park-ink); font-size: 48rpx; font-weight: 750; }
.login-subtitle { margin-top: 12rpx; color: #58617d; font-size: 28rpx; line-height: 1.6; }
.login-card { padding: 32rpx; }
.login-methods { display: flex; gap: 12rpx; margin-bottom: 32rpx; padding-bottom: 24rpx; border-bottom: 1rpx solid var(--park-line); }
.login-methods button { flex: 1; padding: 16rpx 8rpx; border-radius: 12rpx; background: #f4f6fb; color: #58617d; font-size: 28rpx; line-height: 1.5; }
.login-methods button.active { color: #2e47cc; background: #eef1ff; font-weight: 600; }
.login-note { margin: 20rpx 0; color: #58617d; font-size: 28rpx; line-height: 1.6; }
.login-error { margin: 22rpx 0; color: #a3293e; font-size: 28rpx; line-height: 1.6; }
.identity-link { padding: 16rpx; color: #58617d; background: transparent; font-size: 28rpx; line-height: 1.5; }
.input { font-size: 32rpx; }
button:focus-visible { outline: 2px solid var(--park-blue); }
</style>
