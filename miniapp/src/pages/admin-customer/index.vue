<template>
  <view class="page-wrap">
    <view v-if="loading" class="card muted">正在读取客户资料…</view>
    <view v-if="error" class="card"><view class="muted">{{ error }}</view><button class="btn secondary" @click="load">重新加载</button></view>
    <template v-if="data">
      <view class="card">
        <view class="title">{{ data.customer.name }}</view>
        <view class="row"><text class="muted">品牌名称</text><text>{{ data.customer.brandName || '未补充' }}</text></view>
        <view class="row"><text class="muted">对接人</text><text>{{ data.customer.contact || '未补充' }}</text></view>
        <view class="row"><text class="muted">联系电话</text><text>{{ data.customer.phone || '未补充' }}</text></view>
        <view class="row"><text class="muted">服务进度</text><text>{{ data.customer.progressPercentage == null ? '未更新' : data.customer.progressPercentage + '%' }}</text></view>
        <view v-if="data.customer.publicProgress" class="caption">{{ data.customer.publicProgress }}</view>
      </view>
      <view class="card"><view class="title">{{ warehouseId ? '本仓发货' : '发货与佣金' }}</view><view class="caption">{{ data.startDate }} 至 {{ data.endDate }}</view>
        <view class="row"><text>发货量</text><text class="money">{{ count(data.summary.shipmentVolume) }} 票</text></view>
        <view v-if="!warehouseId" class="row"><text>客户总佣金</text><text>{{ money(data.summary.commissionTotal) }} 元</text></view>
        <view v-if="!warehouseId" class="row"><text>伙伴所得佣金</text><text>{{ money(data.summary.partnerCommission) }} 元</text></view>
        <view v-if="!warehouseId" class="row"><text>待结算</text><text>{{ money(data.summary.pendingCommission) }} 元</text></view>
        <view v-if="!warehouseId" class="row"><text>已结算（含已提现）</text><text>{{ money(data.summary.settledCommission) }} 元</text></view>
      </view>
      <CustomerRequirements :requirements="data.customer.requirements" :demand="data.customer.demandArea" :remark="data.customer.intakeRemark" />
      <CustomerQuoteDemand v-if="data.customer.quoteIntake?.submittedAt" :intake="data.customer.quoteIntake" show-assessment />
      <view class="section-head"><text class="section-title">合同明细（{{ data.contracts.length }}）</text></view>
      <view v-if="!data.contracts.length" class="card muted">暂无合同</view>
      <view v-for="contract in data.contracts" :key="contract.id" class="card">
        <view class="title">{{ contract.contractNo }}</view><view class="caption">{{ CONTRACT_STATUS[contract.status] || '未知状态' }} · {{ contract.warehouseName }}</view>
        <view v-if="!warehouseId" class="row"><text>发货量</text><text>{{ count(contract.summary.shipmentVolume) }} 票</text></view>
        <view v-if="!warehouseId" class="row"><text>客户总佣金</text><text>{{ money(contract.summary.commissionTotal) }} 元</text></view>
        <view v-if="!warehouseId" class="row"><text>伙伴所得佣金</text><text>{{ money(contract.summary.partnerCommission) }} 元</text></view>
      </view>
    </template>
  </view>
</template>
<script>
import { appShareMixin } from '@/utils/share'
export default { mixins: [appShareMixin] }
</script>

<script setup>
import { ref, onUnmounted } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { adminOverviewApi } from '@/api'
import CustomerRequirements from '@/components/CustomerRequirements.vue'
import CustomerQuoteDemand from '@/components/CustomerQuoteDemand.vue'
import { overviewMoney as money, overviewCount as count, CONTRACT_STATUS } from '@/utils/admin-overview.mjs'
const data = ref(null), loading = ref(false), error = ref('')
const warehouseId = ref(null)
let id = null, dates = {}, sequence = 0
onLoad(options => { id = Number(options.id); warehouseId.value = options.warehouseId || null; dates = { startDate: options.startDate, endDate: options.endDate, ...(warehouseId.value ? { warehouseId: warehouseId.value } : {}) } })
async function load() {
  const current = ++sequence; data.value = null; error.value = ''; loading.value = true
  try {
    if (!Number.isSafeInteger(id) || id <= 0) throw new Error('客户参数无效，请返回总览重新进入')
    const result = await adminOverviewApi.customer(id, dates)
    if (current === sequence) data.value = result
  } catch (e) { if (current === sequence) error.value = e.message || '读取失败，请重试' }
  finally { if (current === sequence) loading.value = false }
}
onShow(load)
onUnmounted(() => { sequence++ })
</script>
<style scoped>
.title { font-size: 34rpx; font-weight: 700; overflow-wrap: anywhere; }
.row { gap: 24rpx; }
.row > text:last-child { text-align: right; overflow-wrap: anywhere; max-width: 65%; }
</style>
