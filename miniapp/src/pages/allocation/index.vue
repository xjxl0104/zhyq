<template>
  <view class="page-wrap allocation-list-page">
    <view class="card introduction">
      <view class="title">客户分佣</view>
      <view class="note">{{ canPrice ? '按已签约合同查看客户，选择一项设置每单分佣。平台服务费为总佣金的 10%。' : '查看已分配给您的合同、客户和每单金额。' }}</view>
    </view>
    <view v-if="loading" class="card state">正在加载客户分佣…</view>
    <view v-else-if="loadError" class="card"><view class="error-text">{{ loadError }}</view><button class="btn secondary" @click="load">重新加载</button></view>
    <view v-else-if="!visibleItems.length" class="card state">{{ canPrice ? '暂无可分佣的已签约合同或客户' : '暂无分配给您的客户佣金' }}</view>
    <view v-for="item in visibleItems" :key="item.key" class="card allocation-card">
      <view class="row"><text class="customer-name">{{ item.customerName }}</text><text v-if="canPrice" class="tag" :class="item.configured ? 'ok' : 'warn'">{{ item.configured ? '已配置' : '待分佣' }}</text></view>
      <view class="note">{{ item.contractNo ? '合同：' + item.contractNo : '已签约客户 · 客户分佣方案' }}</view>
      <view v-if="item.contractStatus" class="note">{{ statusLabel(item.contractStatus) }}</view>
      <view class="row own-amount"><text>本人每单分佣</text><text class="money">{{ item.amountPerOrder == null ? '待配置' : formatPerOrder(item.amountPerOrder) + ' 元／单' }}</text></view>
      <button v-if="canPrice && item.canEdit" class="btn secondary" @click="openItem(item)">进入分佣 ›</button>
      <view v-else-if="canPrice" class="note">该合同已结束，分佣记录仅供查看。</view>
    </view>
    <view v-if="!loading && !loadError && visibleItems.length" class="note count">共 {{ visibleItems.length }} 项</view>
  </view>
</template>
<script>
import { appShareMixin } from '@/utils/share'
export default { mixins: [appShareMixin] }
</script>
<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { meApi, pricingApi } from '@/api'
import { formatPerOrder } from '@/utils/pricing.mjs'
import { navigateTo } from '@/utils/nav.mjs'
const me = ref({})
const items = ref([])
const loading = ref(true)
const loadError = ref('')
const customerFilter = ref('')
let loaded = false
let requestSequence = 0
const canPrice = computed(() => me.value.positionCode === 'P4' && Number(me.value.status) === 1)
const visibleItems = computed(() => customerFilter.value ? items.value.filter(item => String(item.customerId) === customerFilter.value) : items.value)
onLoad(query => { customerFilter.value = String(query.customerId || ''); load() })
onShow(() => { if (loaded) load() })
async function load() {
  const sequence = ++requestSequence
  loading.value = true
  loadError.value = ''
  try {
    const current = await meApi.me()
    if (sequence !== requestSequence) return
    me.value = current
    if (Number(current.status) !== 1) throw new Error('当前伙伴账号不可用')
    const result = await pricingApi.allocations()
    if (sequence !== requestSequence) return
    items.value = Array.isArray(result) ? result : []
  } catch (error) {
    if (sequence !== requestSequence) return
    items.value = []
    loadError.value = error.message || '客户分佣加载失败，请重试'
  } finally {
    if (sequence === requestSequence) { loading.value = false; loaded = true }
  }
}
function statusLabel(status) { return ({ 4: '已生效', 5: '履约中', 6: '变更中', 7: '已到期', 8: '已终止' })[status] || '' }
function openItem(item) {
  if (!canPrice.value || !item.canEdit) return
  const query = item.contractId ? `contractId=${encodeURIComponent(item.contractId)}` : `customerId=${encodeURIComponent(item.customerId)}`
  navigateTo(`/pages/allocation-detail/index?${query}`)
}
</script>
<style scoped>
.allocation-list-page { padding-bottom: 40rpx; }
.introduction .title { font-size: 36rpx; }
.note { color: #56627d; font-size: 25rpx; line-height: 1.7; margin-top: 12rpx; }
.state { color: #56627d; text-align: center; }
.allocation-card .row { gap: 18rpx; }
.customer-name { min-width: 0; font-size: 31rpx; font-weight: 650; word-break: break-all; }
.allocation-card .tag { flex-shrink: 0; }
.own-amount { margin-top: 12rpx; }
.own-amount .money { text-align: right; }
.allocation-card .btn { margin-top: 12rpx; }
.error-text { color: #a93145; line-height: 1.7; }
.count { text-align: center; }
</style>