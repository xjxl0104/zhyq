<template>
  <view class="page-wrap team-add-page">
    <view class="card">
      <view class="title">新增团队成员</view>
      <view class="note">新成员会挂在您名下。{{ isP4 ? '您可以指定他的称号。' : '称号比您低一级。' }}</view>

      <view class="field-label">姓名</view>
      <input class="input" v-model="name" maxlength="20" :disabled="busy" placeholder="成员姓名" aria-label="成员姓名" />

      <template v-if="isP4">
        <view class="field-label">称号</view>
        <picker :range="positionLabels" :value="positionIndex" :disabled="busy" @change="event => positionIndex = Number(event.detail.value)">
          <view class="input picker-value"><text>{{ positionLabels[positionIndex] }}</text><text class="picker-arrow">›</text></view>
        </picker>
      </template>

      <view class="field-label">手机号（选填）</view>
      <input class="input" v-model="phone" type="number" maxlength="11" :disabled="busy" placeholder="11 位手机号" aria-label="成员手机号" />
      <view class="note">{{ phone.trim() ? '对方用这个手机号微信授权登录小程序后，会自动绑定为本档案。' : '不填手机号时，该成员暂时不可登录，之后可以在“我的团队”里补录手机号。' }}</view>

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
const error = ref('')

onShow(async () => { try { me.value = await meApi.me() } catch (failure) { error.value = failure.message || '读取账号信息失败' } })

async function submit() {
  if (busy.value) return
  error.value = ''
  const memberName = name.value.trim()
  const mobile = phone.value.trim()
  if (!memberName) { error.value = '请填写成员姓名'; return }
  if (mobile && !/^1\d{10}$/.test(mobile)) { error.value = '请输入 11 位手机号，或留空'; return }
  busy.value = true
  try {
    await meApi.addTeamMember({ name: memberName, phone: mobile, positionCode: isP4.value ? POSITIONS[positionIndex.value][0] : undefined })
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
