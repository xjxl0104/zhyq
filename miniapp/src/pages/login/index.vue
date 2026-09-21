<template>
  <view>
    <view class="card">
      <view style="font-size:36rpx;font-weight:600;margin-bottom:8rpx">园区伙伴</view>
      <view class="muted">推荐客户入驻园区 / 使用云仓服务,获得推荐奖励</view>
    </view>

    <view class="card" v-if="step === 'login'">
      <view class="muted" v-if="mockLogin">开发模式:输入任意标识作为微信身份(同一标识 = 同一用户)</view>
      <input v-if="mockLogin" class="input" v-model="jsCode" placeholder="如 dev-user-1" />
      <button class="btn" @click="wxLogin">微信登录</button>
    </view>

    <view class="card" v-else>
      <view class="muted">首次使用,请授权手机号并填写邀请码</view>
      <button v-if="!mockLogin" class="btn" open-type="getPhoneNumber" @getphonenumber="onPhone">授权手机号</button>
      <input v-if="mockLogin" class="input" v-model="form.phone" type="number" maxlength="11" placeholder="手机号" />
      <input class="input" v-model="form.name" placeholder="姓名(可选)" />
      <input class="input" v-model="form.inviteCode" maxlength="8" placeholder="邀请码(可选,7 天内可补填)" />
      <view class="row" @click="agreed = !agreed">
        <text class="muted">{{ agreed ? '☑' : '☐' }} 我已阅读并同意《园区伙伴协议》《隐私协议》</text>
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
