<template>
  <view class="page-wrap team-add-page">
    <view class="card">
      <view class="title">新增团队成员</view>
      <view class="note">新成员会挂在您名下，称号比您低一级。</view>

      <view class="field-label">成员类型</view>
      <view class="type-row">
        <button class="type-btn" :class="{ active: loginable }" :disabled="busy" @click="loginable = true">登录成员</button>
        <button class="type-btn" :class="{ active: !loginable }" :disabled="busy" @click="loginable = false">不登录成员</button>
      </view>
      <view class="note">{{ loginable ? '必须填写手机号。对方用该手机号微信授权登录小程序后，会自动绑定为本档案。' : '不需要手机号，只作记录和分佣对象，不能登录小程序。' }}</view>

      <view class="field-label">姓名</view>
      <input class="input" v-model="name" maxlength="20" :disabled="busy" placeholder="成员姓名" aria-label="成员姓名" />

      <template v-if="loginable">
        <view class="field-label">手机号</view>
        <input class="input" v-model="phone" type="number" maxlength="11" :disabled="busy" placeholder="11 位手机号" aria-label="成员手机号" />
      </template>

      <view v-if="error" class="error-text" role="alert">{{ error }}</view>
      <button class="btn" :loading="busy" :disabled="busy" @click="submit">添加成员</button>
    </view>
  </view>
</template>

<script>
import { appShareMixin } from '@/utils/share'

export default { mixins: [appShareMixin] }
</script>

<script setup>
import { ref } from 'vue'
import { meApi } from '@/api'

const loginable = ref(true)
const name = ref('')
const phone = ref('')
const busy = ref(false)
const error = ref('')

async function submit() {
  if (busy.value) return
  error.value = ''
  const memberName = name.value.trim()
  if (!memberName) { error.value = '请填写成员姓名'; return }
  if (loginable.value && !/^1\d{10}$/.test(phone.value.trim())) { error.value = '登录成员请填写 11 位手机号'; return }
  busy.value = true
  try {
    await meApi.addTeamMember({ name: memberName, phone: loginable.value ? phone.value.trim() : '' })
    uni.showToast({ title: '已添加', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 600)
  } catch (failure) {
    error.value = failure.message || '添加失败，请重试'
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.title { font-size: 32rpx; font-weight: 650; }
.note { margin: 10rpx 0 20rpx; color: #56627d; font-size: 24rpx; line-height: 1.6; }
.field-label { margin: 24rpx 0 12rpx; font-size: 28rpx; font-weight: 600; }
.type-row { display: flex; gap: 16rpx; }
.type-btn { flex: 1; margin: 0; padding: 18rpx 0; border: 1rpx solid #c9d0e2; background: #fff; color: #44506b; font-size: 28rpx; border-radius: 16rpx; }
.type-btn.active { border-color: var(--park-blue); background: #eef1ff; color: var(--park-blue); font-weight: 600; }
.error-text { margin: 20rpx 0; color: #a93145; line-height: 1.6; }
</style>
