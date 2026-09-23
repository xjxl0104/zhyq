<template>
  <view>
    <view class="title">{{ registering ? '注册' + roleName : '账号密码登录' }}</view>
    <view class="auth-caption">{{ registering ? '创建账号后即可进入工作台，继续完善资料。' : '使用为当前身份注册或设置的账号。' }}</view>
    <view class="field-label">登录账号</view>
    <input class="input" v-model="form.username" :maxlength="-1" placeholder="请输入账号" :disabled="busy" aria-label="登录账号" />
    <view class="field-label">{{ registering ? '设置密码' : '密码' }}</view>
    <input class="input" v-model="form.password" password :maxlength="-1" placeholder="请输入密码" :disabled="busy" aria-label="密码" @confirm="submit" />
    <template v-if="registering">
      <view class="field-label">确认密码</view>
      <input class="input" v-model="confirmation" password :maxlength="-1" placeholder="再次输入密码" :disabled="busy" aria-label="确认密码" />
      <view class="field-label">联系手机号</view>
      <input class="input" v-model="form.phone" type="number" maxlength="11" placeholder="用于业务联系" :disabled="busy" aria-label="联系手机号" />
      <view class="field-label">{{ warehouse ? '联系人姓名' : '姓名' }}</view>
      <input class="input" v-model="form.name" maxlength="32" placeholder="请输入姓名" :disabled="busy" aria-label="姓名" />
      <template v-if="warehouse">
        <view class="field-label">云仓名称</view>
        <input class="input" v-model="form.warehouseName" maxlength="100" placeholder="请输入云仓名称" :disabled="busy" aria-label="云仓名称" />
      </template>
      <template v-else>
        <view class="field-label">邀请码（选填）</view>
        <input class="input" v-model="form.inviteCode" maxlength="8" placeholder="可在注册后 7 天内补填" :disabled="busy" aria-label="邀请码" />
      </template>
      <view class="auth-caption">已有微信绑定资料，请使用微信登录后在“账号与密码”中设置，避免重复注册。</view>
      <checkbox-group @change="form.agreed = $event.detail.value.includes('agree')">
        <label class="agreement"><checkbox value="agree" :checked="form.agreed" :disabled="busy" color="#3857f5" /><text>我同意{{ warehouse ? '云仓入驻' : '园区伙伴' }}协议与隐私协议</text></label>
      </checkbox-group>
    </template>
    <view v-if="error" class="auth-error" role="alert">{{ error }}</view>
    <button class="btn" :loading="busy" :disabled="busy" @click="submit">{{ registering ? '注册并登录' : '登录' }}</button>
    <button class="auth-link" :disabled="busy" @click="toggle">{{ registering ? '已有账号，返回登录' : '还没有账号？注册账号' }}</button>
  </view>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { authApi } from '@/api'
import { warehouseAuthApi } from '@/api/warehouse'
const props = defineProps({ warehouse: Boolean, inviteCode: { type: String, default: '' } })
const emit = defineEmits(['authenticated'])
const registering = ref(false)
const busy = ref(false)
const error = ref('')
const confirmation = ref('')
const form = reactive({ username: '', password: '', phone: '', name: '', warehouseName: '', inviteCode: '', agreed: false })
const roleName = computed(() => props.warehouse ? '云仓商家' : '园区伙伴')
watch(() => props.inviteCode, value => { form.inviteCode = value }, { immediate: true })
function toggle() { registering.value = !registering.value; error.value = ''; form.password = ''; confirmation.value = '' }
async function submit() {
  if (busy.value) return
  error.value = ''
  const username = form.username.trim()
  if (!username) { error.value = '请输入账号'; return }
  if (!form.password) { error.value = '请输入密码'; return }
  if (registering.value) {
    if (form.password !== confirmation.value) { error.value = '两次输入的密码不一致'; return }
    if (!/^1\d{10}$/.test(form.phone.trim())) { error.value = '请输入正确的 11 位手机号'; return }
    if (!form.name.trim()) { error.value = '请输入姓名'; return }
    if (props.warehouse && !form.warehouseName.trim()) { error.value = '请输入云仓名称'; return }
    if (!form.agreed) { error.value = '请先阅读并同意协议'; return }
  }
  busy.value = true
  try {
    const api = props.warehouse ? warehouseAuthApi : authApi
    const result = registering.value
      ? await api.passwordRegister({ ...form, username, phone: form.phone.trim(), name: form.name.trim(), warehouseName: form.warehouseName.trim(), inviteCode: form.inviteCode.trim() })
      : await api.passwordLogin({ username, password: form.password })
    if (!result?.token) throw new Error('登录未完成，请重试')
    form.password = ''; confirmation.value = ''
    emit('authenticated', result.token, registering.value)
  } catch (e) { error.value = e.message || '登录未完成，请稍后重试' }
  finally { busy.value = false }
}
</script>

<style scoped>
.auth-caption { margin: 14rpx 0 24rpx; color: #58617d; font-size: 26rpx; line-height: 1.6; }
.field-label { margin-top: 24rpx; color: var(--park-text); font-size: 28rpx; font-weight: 500; }
.input { font-size: 32rpx; }
.auth-error { margin-top: 24rpx; color: #a3293e; font-size: 28rpx; line-height: 1.6; }
.auth-link { margin-top: 12rpx; padding: 14rpx 4rpx; color: #2e47cc; background: transparent; font-size: 28rpx; line-height: 1.5; }
.auth-link:focus-visible { outline: 2px solid var(--park-blue); }
.agreement { display: flex; align-items: flex-start; gap: 10rpx; margin-top: 20rpx; color: #58617d; font-size: 26rpx; line-height: 1.6; }
</style>
