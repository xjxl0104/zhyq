<template>
  <view class="page-wrap login-page">
    <view class="login-hero">
      <view class="brand-mark">P</view>
      <view class="eyebrow">DIPARK · SMART PARK</view>
      <view class="login-title">园区伙伴</view>
      <view class="login-subtitle">连接客户与园区服务，让每一次推荐更有价值</view>
    </view>

    <view class="card login-card" v-if="step === 'login'">
      <view class="title">欢迎回来</view>
      <view class="muted login-note" v-if="mockLogin">开发模式已开启，输入任意标识即可体验完整流程</view>
      <input v-if="mockLogin" class="input" v-model="jsCode" placeholder="开发标识，例如 dev-user-1" />
      <button class="btn" @click="wxLogin">微信登录</button>
      <view class="form-note">登录后可推荐客户、查看收益、管理团队，并实时掌握客户跟进状态。</view>
    </view>

    <view class="card login-card" v-else>
      <view class="title">完成伙伴资料</view>
      <view class="muted login-note">首次使用需要授权手机号，方便园区专员与您联系</view>
      <button v-if="!mockLogin" class="btn" open-type="getPhoneNumber" @getphonenumber="onPhone">授权手机号</button>
      <input v-if="mockLogin" class="input" v-model="form.phone" type="number" maxlength="11" placeholder="手机号" />
      <input class="input" v-model="form.name" placeholder="姓名（可选）" />
      <input class="input" v-model="form.inviteCode" maxlength="8" placeholder="邀请码（可选，7 天内可补填）" />
      <view class="agreement pressable" @click="agreed = !agreed">
        <view class="check-box" :class="{ checked: agreed }">✓</view>
        <text>我已阅读并同意《园区伙伴协议》《隐私协议》</text>
      </view>
      <button class="btn" :disabled="!agreed" @click="bindPhone">注册并登录</button>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { authApi, meApi } from '@/api'
import { token } from '@/utils/request'

const step = ref('login')
const jsCode = ref('')
const openid = ref('')
const agreed = ref(false)
const form = reactive({ phone: '', name: '', inviteCode: '' })
const phoneAuth = reactive({ encryptedData: '', iv: '' })
const mockLogin = import.meta.env.VITE_MP_MOCK_LOGIN !== 'false'

onLoad((q) => { if (q && q.invite) form.inviteCode = q.invite })

async function wxLogin() {
  let code = jsCode.value.trim()
  if (!mockLogin) code = (await uni.login({ provider: 'weixin' })).code
  if (!code) return uni.showToast({ title: '微信登录失败', icon: 'none' })
  const r = await authApi.wxLogin(code)
  if (r.registered) return done(r.token)
  openid.value = r.openid
  step.value = 'bind'
}
async function bindPhone() {
  if (mockLogin && !/^1\d{10}$/.test(form.phone)) return uni.showToast({ title: '手机号格式不对', icon: 'none' })
  if (!mockLogin && (!phoneAuth.encryptedData || !phoneAuth.iv)) return uni.showToast({ title: '请先授权手机号', icon: 'none' })
  const payload = mockLogin ? { openid: openid.value, ...form } : { openid: openid.value, ...phoneAuth, name: form.name, inviteCode: form.inviteCode }
  const r = await authApi.bindPhone(payload)
  token.set(r.token)
  await meApi.agree('v1')
  done(r.token)
}
function onPhone(e) {
  if (e.detail && e.detail.errMsg && e.detail.errMsg.indexOf('ok') < 0) return
  phoneAuth.encryptedData = e.detail.encryptedData || ''
  phoneAuth.iv = e.detail.iv || ''
}
function done(t) {
  token.set(t)
  uni.switchTab({ url: '/pages/home/index' })
}
</script>

<style scoped>
.login-page { padding: 32rpx 0 56rpx; }
.login-hero { padding: 38rpx 42rpx 30rpx; }
.login-hero .brand-mark { margin-bottom: 34rpx; }
.login-title { margin-top: 12rpx; color: var(--park-ink); font-size: 52rpx; font-weight: 750; letter-spacing: -1rpx; }
.login-subtitle { max-width: 560rpx; margin-top: 12rpx; color: var(--park-muted); font-size: 26rpx; line-height: 1.6; }
.login-card { padding: 34rpx; }
.login-note { margin: 12rpx 0 24rpx; }
.agreement { display: flex; align-items: flex-start; gap: 14rpx; margin-top: 24rpx; color: var(--park-muted); font-size: 23rpx; line-height: 1.5; }
.check-box { display: flex; align-items: center; justify-content: center; width: 34rpx; height: 34rpx; flex: 0 0 34rpx; color: transparent; border: 2rpx solid #cbd2e2; border-radius: 10rpx; font-size: 22rpx; }
.check-box.checked { color: #fff; border-color: var(--park-blue); background: var(--park-blue); }
</style>
