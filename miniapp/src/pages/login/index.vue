<template>
  <view class="page-wrap login-page">
    <view class="login-hero">
      <view class="brand-mark">P</view>
      <view class="login-title">园区伙伴</view>
      <view class="login-subtitle">连接客户与园区服务，让每一次推荐更有价值</view>
    </view>
    <view class="card login-card">
      <view class="login-methods">
        <button :class="{ active: method === 'password' }" :disabled="busy" @click="choose('password')">账号密码</button>
        <button :class="{ active: method === 'wechat' }" :disabled="busy" @click="choose('wechat')">微信登录</button>
      </view>
      <PasswordAuthForm v-if="method === 'password'" :invite-code="form.inviteCode" @authenticated="done" />
      <view v-else-if="step === 'login'">
        <view class="title">微信快捷登录</view>
        <view class="login-note" v-if="mockLogin">开发测试模式，请输入测试标识</view>
        <input v-if="mockLogin" class="input" v-model="jsCode" placeholder="开发标识，例如 dev-user-1" aria-label="开发标识" />
        <view v-if="error" class="login-error" role="alert">{{ error }}</view>
        <button class="btn" :loading="busy" :disabled="busy" @click="wxLogin">微信登录</button>
        <view class="login-note">首次使用需要授权手机号，完成伙伴资料。</view>
      </view>
      <view v-else>
        <view class="title">完成伙伴资料</view>
        <view class="login-note">填写资料并同意协议后，授权手机号即可完成注册。</view>
        <input class="input" v-model="form.name" maxlength="32" placeholder="姓名（选填）" aria-label="姓名" />
        <input class="input" v-model="form.inviteCode" maxlength="8" placeholder="邀请码（选填，7 天内可补填）" aria-label="邀请码" />
        <input v-if="mockLogin" class="input" v-model="form.phone" type="number" maxlength="11" placeholder="手机号" aria-label="手机号" />
        <checkbox-group @change="agreed = $event.detail.value.includes('agree')">
          <label class="agreement"><checkbox value="agree" :checked="agreed" color="#3857f5" /><text>我已阅读并同意《园区伙伴协议》《隐私协议》</text></label>
        </checkbox-group>
        <view v-if="error" class="login-error" role="alert">{{ error }}</view>
        <button v-if="mockLogin" class="btn" :disabled="!agreed || busy" :loading="busy" @click="bindPhone()">注册并登录</button>
        <button v-else class="btn" open-type="getPhoneNumber" :disabled="!agreed || busy" :loading="busy" @getphonenumber="onPhone">授权手机号并登录</button>
        <button class="btn ghost" :disabled="busy" @click="restart">重新获取微信身份</button>
      </view>
    </view>
    <button class="identity-link" :disabled="busy" @click="chooseIdentity">切换身份</button>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { authApi, meApi } from '@/api'
import { token } from '@/utils/request'
import { partnerMock as mockLogin, getWechatLogin, readPhoneAuthorization } from '@/utils/wechat'
import PasswordAuthForm from '@/components/PasswordAuthForm.vue'
const method = ref('password')
const step = ref('login')
const jsCode = ref('')
const openid = ref('')
const loginTicket = ref('')
const busy = ref(false)
const error = ref('')
const agreed = ref(false)
const form = reactive({ phone: '', name: '', inviteCode: '' })
onLoad(q => { const invite = q?.invite || q?.scene; if (invite && /^[A-Za-z0-9]{8}$/.test(decodeURIComponent(invite))) form.inviteCode = decodeURIComponent(invite) })
function choose(value) { method.value = value; error.value = '' }
function restart() { step.value = 'login'; openid.value = ''; loginTicket.value = ''; error.value = '' }
async function wxLogin() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    const { code, appId } = await getWechatLogin(mockLogin, jsCode.value)
    const r = await authApi.wxLogin(code, appId)
    if (r.registered && r.token) return done(r.token)
    openid.value = r.openid; loginTicket.value = r.loginTicket || ''; step.value = 'bind'
  } catch (e) { error.value = e.message || '微信登录未完成，请重试或使用账号密码登录' }
  finally { busy.value = false }
}
async function bindPhone(phoneAuth = {}) {
  if (busy.value) return
  if (!agreed.value) { error.value = '请先阅读并同意协议'; return }
  if (mockLogin && !/^1\d{10}$/.test(form.phone)) { error.value = '请输入正确的 11 位手机号'; return }
  busy.value = true; error.value = ''
  try {
    const payload = { openid: openid.value, loginTicket: loginTicket.value, name: form.name, inviteCode: form.inviteCode, ...phoneAuth }
    if (mockLogin) payload.phone = form.phone
    const r = await authApi.bindPhone(payload)
    if (!r.token) throw new Error('注册未完成，请重新获取微信身份后重试')
    token.set(r.token)
    await meApi.agree('v1')
    done(r.token)
  } catch (e) {
    if (!mockLogin) { step.value = 'login'; openid.value = ''; loginTicket.value = '' }
    error.value = (e.message || '绑定未完成') + (mockLogin ? '' : '；请重新微信登录后授权')
  }
  finally { busy.value = false }
}
function onPhone(e) {
  try { bindPhone(readPhoneAuthorization(e)) } catch (err) { error.value = err.message }
}
function done(t) { token.set(t); uni.switchTab({ url: '/pages/home/index' }) }
function chooseIdentity() { uni.reLaunch({ url: '/pages/entry/index' }) }
</script>

<style scoped>
.login-page { padding: 24rpx 0 56rpx; }
.login-hero { padding: 30rpx 42rpx 12rpx; }
.login-hero .brand-mark { margin-bottom: 24rpx; }
.login-title { color: var(--park-ink); font-size: 48rpx; font-weight: 750; }
.login-subtitle { max-width: 560rpx; margin-top: 12rpx; color: #58617d; font-size: 28rpx; line-height: 1.6; }
.login-card { padding: 32rpx; }
.login-methods { display: flex; gap: 12rpx; margin-bottom: 32rpx; padding-bottom: 24rpx; border-bottom: 1rpx solid var(--park-line); }
.login-methods button { flex: 1; padding: 16rpx 8rpx; border-radius: 12rpx; background: #f4f6fb; color: #58617d; font-size: 28rpx; line-height: 1.5; }
.login-methods button.active { color: #2e47cc; background: #eef1ff; font-weight: 600; }
.login-note { margin: 20rpx 0; color: #58617d; font-size: 28rpx; line-height: 1.6; }
.login-error { margin: 22rpx 0; color: #a3293e; font-size: 28rpx; line-height: 1.6; }
.agreement { display: flex; align-items: flex-start; gap: 10rpx; margin-top: 24rpx; color: #58617d; font-size: 26rpx; line-height: 1.6; }
.identity-link { padding: 16rpx; color: #58617d; background: transparent; font-size: 28rpx; line-height: 1.5; }
.input { font-size: 32rpx; }
button:focus-visible { outline: 2px solid var(--park-blue); }
</style>
