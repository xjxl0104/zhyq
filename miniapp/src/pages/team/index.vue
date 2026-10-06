<template>
  <view class="page-wrap team-page">
    <view v-if="error" class="card"><view class="error-text" role="alert">{{ error }}</view><button class="btn secondary" :disabled="loading || !!savingMemberId" @click="load">重新加载</button></view>
    <view v-if="loading && !loaded" class="card muted">正在读取团队成员…</view>
    <view v-if="loaded" class="card">
      <view class="row team-heading"><text class="title">{{ canManage ? '邀请体系成员' : '直属成员' }} {{ list.length }} 人</text>
        <button v-if="canManage" class="text-button" @click="uni.navigateTo({ url: '/pages/allocation/index' })">客户定价 ›</button></view>
      <view v-if="canManage" class="team-note">显示邀请体系内的正常成员。您可以设置 P1–P4 称号，金额仍按客户单独配置。P4 拥有定价权。</view>
      <view v-if="!list.length" class="team-note">还没有可显示的成员。朋友通过您的邀请码注册后会加入团队。</view>
      <view class="row member-row" v-for="member in list" :key="member.id">
        <view class="member-info"><view class="member-name">{{ member.name || '未命名伙伴' }}</view><view class="team-note">{{ member.positionCode }} · {{ member.positionName || positionName(member.positionCode) }}<text v-if="member.joinTime"> · {{ member.joinTime.slice(0, 10) }}</text></view></view>
        <picker v-if="canManage && String(member.id) !== String(me.id)" :range="positionOptions" :value="positionIndex(member.positionCode)" :disabled="loading || !!savingMemberId || !positions.length" @change="event => changePosition(member, event)">
          <view class="position-action" :class="{ 'is-disabled': loading || !!savingMemberId }">{{ String(savingMemberId) === String(member.id) ? '保存中…' : '设置称号 ›' }}</view>
        </picker>
        <text v-else class="tag" :class="member.status === 1 ? 'ok' : 'warn'">{{ member.status === 1 ? '正常' : '冻结' }}</text>
      </view>
      <view v-if="savedMessage" class="success-text" role="status">{{ savedMessage }}</view>
    </view>
    <button class="btn" @click="uni.switchTab({ url: '/pages/me/index' })">查看我的邀请码</button>
  </view>
</template>

<script>
import { appShareMixin } from '@/utils/share'

export default { mixins: [appShareMixin] }
</script>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { meApi, pricingApi } from '@/api'
const POS = { P1: '园区伙伴', P2: '银牌合伙人', P3: '金牌合伙人', P4: '钻石合伙人' }
const CODES = ['P1', 'P2', 'P3', 'P4']
const positions = ref([])
const positionOptions = computed(() => positions.value.map(({ code, name }) => `${code} · ${name}${code === 'P4' ? '（拥有定价权）' : ''}`))
const list = ref([]), me = ref({}), loading = ref(false), loaded = ref(false)
const savingMemberId = ref(''), error = ref(''), savedMessage = ref('')
const canManage = computed(() => me.value.positionCode === 'P4' && Number(me.value.status) === 1)
const positionName = code => positions.value.find(position => position.code === code)?.name || POS[code] || code
const positionIndex = code => Math.max(0, positions.value.findIndex(position => position.code === code))

onShow(load)
async function load() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    me.value = await meApi.me()
    if (canManage.value) {
      const [members, options] = await Promise.all([pricingApi.team(), pricingApi.positions()])
      list.value = members
      positions.value = options.filter(position => CODES.includes(position.code))
    } else {
      list.value = await meApi.team()
      positions.value = []
    }
    loaded.value = true
  } catch (failure) {
    error.value = failure.message || '团队读取失败，请重新加载'
  } finally {
    loading.value = false
  }
}
async function changePosition(member, event) {
  if (!canManage.value || loading.value || savingMemberId.value || String(member.id) === String(me.value.id)) return
  const code = positions.value[Number(event.detail.value)]?.code
  if (!code || code === member.positionCode) return
  const authorityNotice = code === 'P4' ? '设为 P4 后，对方将拥有其客户的定价权，其负责客户的原定价需由对方重新设置。' : member.positionCode === 'P4' ? '调整后，对方将失去 P4 定价权，其原负责客户需由新的负责 P4 重新设置定价。' : '称号调整不会自动套用任何固定佣金金额。'
  const confirmed = await new Promise(resolve => uni.showModal({
    title: '确认设置成员称号', content: `将 ${member.name || '该成员'} 设置为 ${code} · ${positionName(code)}？${authorityNotice}`,
    confirmText: '确认设置', cancelText: '取消', success: result => resolve(result.confirm), fail: () => resolve(false)
  }))
  if (!confirmed || !canManage.value || savingMemberId.value) return
  savingMemberId.value = member.id
  error.value = ''
  savedMessage.value = ''
  try {
    await pricingApi.setPosition(member.id, code)
    member.positionCode = code
    member.positionName = positionName(code)
    await load()
    savedMessage.value = `${member.name || '成员'}的称号已设置为 ${code}`
    uni.showToast({ title: '称号已更新', icon: 'success' })
  } catch (failure) {
    error.value = failure.message || '称号保存失败，请重试'
  } finally {
    savingMemberId.value = ''
  }
}
</script>

<style scoped>
.team-heading { flex-wrap: wrap; gap: 12rpx; padding-top: 0; }
.team-heading .title { font-size: 30rpx; }
.team-note { margin-top: 10rpx; color: #56627d; font-size: 24rpx; line-height: 1.6; }
.member-row { align-items: flex-start; gap: 18rpx; }
.member-info { min-width: 0; flex: 1; }
.member-name { font-size: 28rpx; font-weight: 600; word-break: break-all; }
.text-button { margin: 0; padding: 8rpx 0; background: transparent; color: var(--park-blue); font-size: 24rpx; line-height: 48rpx; }
.position-action { padding: 12rpx 0 12rpx 12rpx; color: var(--park-blue); font-size: 24rpx; line-height: 1.5; white-space: nowrap; }
.is-disabled { color: #66718b; opacity: .65; }
.error-text { color: #a93145; line-height: 1.6; }
.success-text { margin-top: 20rpx; color: #116b56; font-size: 25rpx; line-height: 1.6; }
</style>
