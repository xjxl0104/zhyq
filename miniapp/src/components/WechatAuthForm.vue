<template>
  <view>
    <view class="title">一键登录</view>
    <view class="quick-note">{{ warehouse ? '首次登录自动注册，以后可继续使用微信或手机号登录。' : '首次注册需填写上级邀请码；已有账号可直接登录。' }}</view>
    <view v-if="!warehouse" class="invite-field">
      <view class="field-label">上级邀请码（首次注册必填）</view>
      <input class="input" v-model="invite" maxlength="8" :disabled="busy" placeholder="邀请人提供的 8 位邀请码" aria-label="上级邀请码" :aria-required="needsInvite" />
    </view>
    <template v-if="mock">
      <view class="quick-note">开发测试模式</view>
      <input class="input" v-model="testCode" :disabled="busy" placeholder="开发测试标识" aria-label="开发测试标识" />
      <input class="input" v-model="testPhone" type="number" maxlength="11" :disabled="busy" placeholder="手机号（仅测试手机号登录时填写）" aria-label="测试手机号" />
    </template>
    <checkbox-group @change="agreed = $event.detail.value.includes('agree')">
      <label class="agreement">
        <checkbox value="agree" :checked="agreed" :disabled="busy" color="#2b4fd6" />
        <text>我已阅读并同意{{ warehouse ? '云仓入驻' : '园区伙伴' }}协议与隐私协议</text>
      </label>
    </checkbox-group>
    <view v-if="error" class="quick-error" role="alert">{{ error }}</view>
    <button class="btn" :loading="busy && !usingPhone" :disabled="busy || !agreed" @click="login()">微信一键登录</button>
    <button v-if="mock" class="btn ghost" :loading="busy && usingPhone" :disabled="busy || !agreed" @click="loginWithTestPhone">手机号一键登录</button>
    <button v-else class="btn ghost" open-type="getPhoneNumber" :loading="busy && usingPhone" :disabled="busy || !agreed" @getphonenumber="onPhone">手机号一键登录</button>
    <view class="quick-note secondary-note">账号密码为备用登录方式。已有账号尚未关联微信时，请联系园区运营核验关联。</view>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { authApi } from '@/api'
import { warehouseAuthApi } from '@/api/warehouse'
import { partnerMock, warehouseMock, getWechatLogin } from '@/utils/wechat'

const props = defineProps({ warehouse: Boolean, inviteCode: { type: String, default: '' } })
const emit = defineEmits(['authenticated', 'busy'])
const mock = computed(() => props.warehouse ? warehouseMock : partnerMock)
const agreed = ref(false), busy = ref(false), usingPhone = ref(false), error = ref('')
const testCode = ref(''), testPhone = ref('')
const invite = ref(props.inviteCode), needsInvite = ref(false)
watch(() => props.inviteCode, value => { invite.value = value })

async function login(phoneAuthorization = {}) {
  if (busy.value) return
  error.value = ''
  if (!agreed.value) { error.value = '请先阅读并同意协议'; return }
  if (!props.warehouse && needsInvite.value && !invite.value.trim()) { error.value = '请填写上级邀请码'; return }
  if (!props.warehouse && invite.value.trim() && !/^[A-Z2-9]{8}$/.test(invite.value.trim().toUpperCase())) { error.value = '请输入有效的 8 位上级邀请码'; return }
  busy.value = true; usingPhone.value = !!(phoneAuthorization.phoneCode || phoneAuthorization.phone)
  emit('busy', true)
  try {
    const { code, appId } = await getWechatLogin(mock.value, testCode.value)
    const api = props.warehouse ? warehouseAuthApi : authApi
    const data = { jsCode: code, appId, agreed: true, ...phoneAuthorization }
    if (!props.warehouse && invite.value.trim()) data.inviteCode = invite.value.trim().toUpperCase()
    const result = await api.quickLogin(data)
    if (!result?.token) throw new Error('登录未完成，请重新点击登录')
    emit('authenticated', result.token)
  } catch (e) {
    error.value = e.message || '登录未完成，请重试或使用账号密码登录'
    if (!props.warehouse && /邀请码/.test(error.value)) needsInvite.value = true
  }
  finally { busy.value = false; emit('busy', false) }
}

function onPhone(event) {
  if (busy.value) return
  const detail = event?.detail || {}
  if (!agreed.value) { error.value = '请先阅读并同意协议'; return }
  if (detail.errMsg && !detail.errMsg.endsWith(':ok')) {
    error.value = '未完成手机号授权，可重试或直接使用微信一键登录'
    return
  }
  if (!detail.code) {
    error.value = '未取得手机号授权，请重试或直接使用微信一键登录'
    return
  }
  return login({ phoneCode: detail.code })
}

function loginWithTestPhone() {
  if (!mock.value) { error.value = '请通过微信授权手机号登录'; return }
  if (!/^1\d{10}$/.test(testPhone.value.trim())) { error.value = '请输入正确的测试手机号'; return }
  return login({ phone: testPhone.value.trim() })
}
</script>

<style scoped>
.quick-note { margin: 14rpx 0 28rpx; color: #6b7386; font-size: 28rpx; line-height: 1.6; }
.secondary-note { margin: 26rpx 0 0; font-size: 26rpx; }
.agreement { display: flex; align-items: flex-start; gap: 10rpx; margin: 28rpx 0; color: #6b7386; font-size: 26rpx; line-height: 1.6; }
.quick-error { margin: 22rpx 0; color: #c0392b; font-size: 28rpx; line-height: 1.6; }
.field-label { margin-top: 24rpx; color: var(--park-text); font-size: 28rpx; font-weight: 500; }
button:focus-visible { outline: 2px solid var(--park-blue); outline-offset: 2px; }
</style>
