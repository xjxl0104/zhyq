<template>
  <view>
    <view class="title">{{ registering ? '注册' + roleName : '账号密码登录' }}</view>
    <view class="auth-caption">{{ registering ? (warehouse ? '只需账号和密码，其他资料可登录后补充。' : '填写账号、密码和上级邀请码，其他资料可登录后补充。') : '使用为当前身份注册或设置的账号。' }}</view>
    <view class="field-label">登录账号</view>
    <input class="input" v-model="form.username" :maxlength="-1" placeholder="请输入账号" :disabled="busy" aria-label="登录账号" />
    <view class="field-label">{{ registering ? '设置密码' : '密码' }}</view>
    <view class="password-field">
      <input class="input" v-model="form.password" :password="!showPassword" :maxlength="-1" placeholder="请输入密码" :disabled="busy" aria-label="密码" @confirm="submit" />
      <button class="password-toggle" :disabled="busy" @click="showPassword = !showPassword">{{ showPassword ? '隐藏' : '显示' }}</button>
    </view>
    <template v-if="registering">
      <template v-if="!warehouse">
        <view class="field-label">上级邀请码（必填）</view>
        <input class="input" v-model="form.inviteCode" maxlength="8" placeholder="请输入邀请人提供的 8 位邀请码" :disabled="busy" aria-label="上级邀请码" aria-required="true" />
        <view class="auth-caption">注册后加入邀请人的团队。</view>
      </template>
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
const emit = defineEmits(['authenticated', 'busy'])
const registering = ref(false)
const busy = ref(false)
const error = ref('')
const showPassword = ref(false)
const form = reactive({ username: '', password: '', inviteCode: '', agreed: false })
const roleName = computed(() => props.warehouse ? '云仓商家' : '园区伙伴')
watch(() => props.inviteCode, value => { form.inviteCode = value }, { immediate: true })
function toggle() { registering.value = !registering.value; error.value = ''; form.password = ''; showPassword.value = false }
async function submit() {
  if (busy.value) return
  error.value = ''
  const username = form.username.trim()
  if (!username) { error.value = '请输入账号'; return }
  if (!form.password) { error.value = '请输入密码'; return }
  if (registering.value) {
    if (!form.agreed) { error.value = '请先阅读并同意协议'; return }
    if (!props.warehouse && !form.inviteCode.trim()) { error.value = '请填写上级邀请码'; return }
    if (!props.warehouse && !/^[A-Z2-9]{8}$/.test(form.inviteCode.trim().toUpperCase())) { error.value = '请输入有效的 8 位上级邀请码'; return }
  }
  busy.value = true
  emit('busy', true)
  try {
    const api = props.warehouse ? warehouseAuthApi : authApi
    const result = registering.value
      ? await api.passwordRegister({ username, password: form.password, agreed: form.agreed, ...(!props.warehouse ? { inviteCode: form.inviteCode.trim().toUpperCase() } : {}) })
      : await api.passwordLogin({ username, password: form.password })
    if (!result?.token) throw new Error('登录未完成，请重试')
    form.password = ''; showPassword.value = false
    emit('authenticated', result.token, registering.value)
  } catch (e) { error.value = e.message || '登录未完成，请稍后重试' }
  finally { busy.value = false; emit('busy', false) }
}
</script>

<style scoped>
.auth-caption { margin: 14rpx 0 24rpx; color: #58617d; font-size: 26rpx; line-height: 1.6; }
.field-label { margin-top: 24rpx; color: var(--park-text); font-size: 28rpx; font-weight: 500; }
.input { font-size: 32rpx; }
.password-field { position: relative; }
.password-field .input { padding-right: 112rpx; }
.password-toggle { position: absolute; right: 2rpx; top: 2rpx; bottom: 2rpx; min-width: 104rpx; margin: 0; padding: 0 20rpx; background: transparent; color: #2e47cc; font-size: 28rpx; display: flex; align-items: center; justify-content: center; }
.password-toggle:focus-visible { outline: 2px solid var(--park-blue); }
.auth-error { margin-top: 24rpx; color: #a3293e; font-size: 28rpx; line-height: 1.6; }
.auth-link { margin-top: 12rpx; padding: 14rpx 4rpx; color: #2e47cc; background: transparent; font-size: 28rpx; line-height: 1.5; }
.auth-link:focus-visible { outline: 2px solid var(--park-blue); }
.agreement { display: flex; align-items: flex-start; gap: 10rpx; margin-top: 20rpx; color: #58617d; font-size: 26rpx; line-height: 1.6; }
</style>
