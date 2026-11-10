<template>
  <view class="page-wrap team-add-page">
    <view class="card">
      <view class="title">新增团队成员</view>
      <view class="note">新成员会挂在您名下。{{ !profileReady ? (profileLoading ? '正在核对账号和称号权限。' : '请先读取账号信息后再添加。') : isP4 ? '您可以指定 P1–P3 称号，P4 由后台授予。' : '称号比您低一级。' }}</view>

      <view class="field-label">姓名</view>
      <input class="input" v-model="name" maxlength="20" :disabled="formDisabled" placeholder="成员姓名" aria-label="成员姓名" />

      <template v-if="isP4">
        <view class="field-label">称号</view>
        <picker :range="positionLabels" :value="positionIndex" :disabled="formDisabled" @change="event => positionIndex = Number(event.detail.value)">
          <view class="input picker-value"><text>{{ positionLabels[positionIndex] }}</text><text class="picker-arrow">›</text></view>
        </picker>
      </template>

      <view class="field-label">手机号（选填）</view>
      <input class="input" v-model="phone" type="number" maxlength="11" :disabled="formDisabled" placeholder="11 位手机号" aria-label="成员手机号" />
      <view class="note">{{ phone.trim() ? '对方用这个手机号微信授权登录小程序后，会自动绑定为本档案。' : '不填手机号时，该成员暂时不可登录，之后可以在“我的团队”里补录手机号。' }}</view>

      <view v-if="error" class="error-text" role="alert">{{ error }}</view>
      <button v-if="!profileReady && !profileLoading" class="btn secondary" :disabled="busy || submitted" @click="loadProfile">重新读取账号信息</button>
      <button class="btn" :loading="busy || profileLoading" :disabled="formDisabled" @click="submit">{{ submitted ? '已添加，正在返回…' : '添加成员' }}</button>
    </view>
  </view>
</template>

<script>
import { appShareMixin } from '@/utils/share'

export default { mixins: [appShareMixin] }
</script>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { meApi } from '@/api'

const POSITIONS = [['P1', '园区伙伴'], ['P2', '银牌合伙人'], ['P3', '金牌合伙人']]
const positionLabels = POSITIONS.map(([code, label]) => `${code} · ${label}`)
const me = ref({})
const isP4 = computed(() => me.value.positionCode === 'P4' && Number(me.value.status) === 1)
const name = ref('')
const phone = ref('')
const positionIndex = ref(0)
const busy = ref(false)
const submitted = ref(false), profileReady = ref(false), profileLoading = ref(false)
const error = ref('')
const formDisabled = computed(() => !profileReady.value || profileLoading.value || busy.value || submitted.value)

onShow(loadProfile)
async function loadProfile() {
  if (profileLoading.value || busy.value || submitted.value) return
  profileLoading.value = true; profileReady.value = false; error.value = ''
  try {
    me.value = await meApi.me()
    if (Number(me.value.status) !== 1) { error.value = '当前账号状态不能新增成员，请联系园区运营'; return }
    profileReady.value = true
  } catch (failure) {
    error.value = failure.message || '读取账号信息失败，请重试'
  } finally {
    profileLoading.value = false
  }
}

async function submit() {
  if (formDisabled.value) return
  error.value = ''
  const memberName = name.value.trim()
  const mobile = phone.value.trim()
  if (!memberName) { error.value = '请填写成员姓名'; return }
  if (mobile && !/^1\d{10}$/.test(mobile)) { error.value = '请输入 11 位手机号，或留空'; return }
  if (isP4.value && !POSITIONS[positionIndex.value]) { error.value = '请选择 P1–P3 称号'; return }
  busy.value = true
  try {
    await meApi.addTeamMember({ name: memberName, phone: mobile, positionCode: isP4.value ? POSITIONS[positionIndex.value][0] : undefined })
    submitted.value = true
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
.picker-value { display: flex; justify-content: space-between; align-items: center; }
.error-text { margin: 20rpx 0; color: #a93145; line-height: 1.6; }
</style>
