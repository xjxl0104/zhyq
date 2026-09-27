<template>
  <view>
    <view class="title">一键登录</view>
    <view class="quick-note">首次登录自动创建账号，个人信息可稍后补充。</view>
    <template v-if="mock">
      <view class="quick-note">开发测试模式</view>
      <input class="input" v-model="testCode" :disabled="busy" placeholder="开发测试标识" aria-label="开发测试标识" />
      <input class="input" v-model="testPhone" type="number" maxlength="11" :disabled="busy" placeholder="手机号（仅测试手机号登录时填写）" aria-label="测试手机号" />
    </template>
    <checkbox-group @change="agreed = $event.detail.value.includes('agree')">
      <label class="agreement">
        <checkbox value="agree" :checked="agreed" :disabled="busy" color="#3857f5" />
        <text>我已阅读并同意{{ warehouse ? '云仓入驻' : '园区伙伴' }}协议与隐私协议</text>
      </label>
    </checkbox-group>
    <view v-if="error" class="quick-error" role="alert">{{ error }}</view>
    <button class="btn" :loading="busy && !usingPhone" :disabled="busy || !agreed" @click="login()">微信一键登录</button>
    <button v-if="mock" class="btn ghost" :loading="busy && usingPhone" :disabled="busy || !agreed" @click="loginWithTestPhone">手机号一键登录</button>
    <button v-else class="btn ghost" open-type="getPhoneNumber" :loading="busy && usingPhone" :disabled="busy || !agreed" @getphonenumber="onPhone">手机号一键登录</button>
    <view class="quick-note secondary-note">已有业务账号，请使用原登录方式，避免重复建号。</view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { authApi } from '@/api'
import { warehouseAuthApi } from '@/api/warehouse'
import { partnerMock, warehouseMock, getWechatLogin } from '@/utils/wechat'

const props = defineProps({ warehouse: Boolean, inviteCode: { type: String, default: '' } })
const emit = defineEmits(['authenticated', 'busy'])
const mock = computed(() => props.warehouse ? warehouseMock : partnerMock)
const agreed = ref(false), busy = ref(false), usingPhone = ref(false), error = ref('')
const testCode = ref(''), testPhone = ref('')

async function login(phoneAuthorization = {}) {
  if (busy.value) return
  error.value = ''
  if (!agreed.value) { error.value = '请先阅读并同意协议'; return }
  busy.value = true; usingPhone.value = !!(phoneAuthorization.phoneCode || phoneAuthorization.phone)
  emit('busy', true)
  try {
    const { code, appId } = await getWechatLogin(mock.value, testCode.value)
    const api = props.warehouse ? warehouseAuthApi : authApi
    const data = { jsCode: code, appId, agreed: true, ...phoneAuthorization }
    if (!props.warehouse && props.inviteCode) data.inviteCode = props.inviteCode
    const result = await api.quickLogin(data)
    if (!result?.token) throw new Error('登录未完成，请重新点击登录')
    emit('authenticated', result.token)
  } catch (e) { error.value = e.message || '登录未完成，请重试或使用账号密码登录' }
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
.quick-note { margin: 14rpx 0 28rpx; color: #58617d; font-size: 28rpx; line-height: 1.6; }
.secondary-note { margin: 26rpx 0 0; font-size: 26rpx; }
.agreement { display: flex; align-items: flex-start; gap: 10rpx; margin: 28rpx 0; color: #58617d; font-size: 26rpx; line-height: 1.6; }
.quick-error { margin: 22rpx 0; color: #a3293e; font-size: 28rpx; line-height: 1.6; }
button:focus-visible { outline: 2px solid var(--park-blue); outline-offset: 2px; }
</style>
