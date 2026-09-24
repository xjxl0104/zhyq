<template>
  <view class="page-wrap">
    <view class="card security-card">
      <view class="title">{{ configured ? '修改账号密码' : '设置账号密码' }}</view>
      <view class="note">设置后可使用账号密码登录{{ warehouse ? '云仓商家端' : '园区伙伴端' }}，继续使用当前资料和业务记录。</view>
      <view v-if="loading" class="note">正在读取账号信息…</view>
      <template v-else-if="ready">
        <view class="field-label">登录账号</view>
        <input class="input" v-model="form.username" maxlength="32" placeholder="4–32 位字母、数字或下划线" :disabled="busy" aria-label="登录账号" />
        <template v-if="configured">
          <view class="field-label">当前密码</view>
          <input class="input" v-model="form.currentPassword" password maxlength="64" placeholder="请输入当前密码" :disabled="busy" aria-label="当前密码" />
        </template>
        <view class="field-label">新密码</view>
        <input class="input" v-model="form.password" password maxlength="64" placeholder="8–64 位，包含字母和数字" :disabled="busy" aria-label="新密码" />
        <view class="field-label">确认新密码</view>
        <input class="input" v-model="confirmation" password maxlength="64" placeholder="再次输入新密码" :disabled="busy" aria-label="确认新密码" />
      </template>
      <view v-if="error" class="error" role="alert">{{ error }}</view>
      <button v-if="ready" class="btn" :loading="busy" :disabled="busy || loading" @click="save">保存账号密码</button>
      <button v-else-if="!loading && !saved" class="btn" @click="load">重新读取</button>
      <view v-if="saved" class="success" role="status">账号已更新，请使用新账号密码重新登录。</view>
    </view>
  </view>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { authApi } from '@/api'
import { warehouseAuthApi } from '@/api/warehouse'
import { token, warehouseToken } from '@/utils/request'
const warehouse = ref(false); const configured = ref(false); const loading = ref(true); const ready = ref(false)
const busy = ref(false); const error = ref(''); const saved = ref(false); const confirmation = ref('')
const form = reactive({ username: '', password: '', currentPassword: '' })
const api = () => warehouse.value ? warehouseAuthApi : authApi
onLoad(q => { warehouse.value = q?.role === 'wh'; load() })
async function load() {
  loading.value = true; error.value = ''
  try {
    const status = await api().passwordStatus()
    configured.value = !!status.configured; form.username = status.username || ''; ready.value = true
  } catch (e) { error.value = e.message || '账号信息读取失败，请重试' }
  finally { loading.value = false }
}
async function save() {
  if (busy.value || !ready.value) return
  error.value = ''; saved.value = false
  if (!/^[A-Za-z0-9_]{4,32}$/.test(form.username.trim())) { error.value = '账号需为 4–32 位字母、数字或下划线'; return }
  if (form.password.length < 8 || form.password.length > 64 || !/[A-Za-z]/.test(form.password) || !/\d/.test(form.password)) { error.value = '密码需为 8–64 位，并包含字母和数字'; return }
  if (form.password !== confirmation.value) { error.value = '两次输入的密码不一致'; return }
  if (configured.value && !form.currentPassword) { error.value = '修改密码需要填写当前密码'; return }
  busy.value = true
  try {
    await api().passwordSetup({ ...form, username: form.username.trim() })
    configured.value = true; saved.value = true; ready.value = false; form.password = ''; form.currentPassword = ''; confirmation.value = ''
    ;(warehouse.value ? warehouseToken : token).clear()
    uni.showModal({ title: '账号已更新', content: '请使用新账号密码重新登录，原登录会话已失效。', showCancel: false,
      complete: () => uni.reLaunch({ url: warehouse.value ? '/pages/warehouse-login/index' : '/pages/login/index' }) })
  } catch (e) { error.value = e.message || '保存失败，请稍后重试' }
  finally { busy.value = false }
}
</script>
<style scoped>
.security-card { padding: 32rpx; }
.note { margin: 18rpx 0 28rpx; color: #58617d; font-size: 28rpx; line-height: 1.6; }
.field-label { margin-top: 24rpx; font-size: 28rpx; font-weight: 500; }
.input { font-size: 32rpx; }
.error, .success { margin-top: 24rpx; font-size: 28rpx; line-height: 1.6; }
.error { color: #a3293e; }
.success { color: #147a62; }
</style>
