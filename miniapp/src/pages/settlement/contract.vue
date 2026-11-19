<template>
  <view class="page-wrap contract-page">
    <view v-if="error" class="card" role="alert"><view class="muted">{{ error }}</view><button class="btn" :disabled="loading" @click="load">重新加载</button></view>
    <view v-if="loading && !contract" class="card muted">正在读取合同结算…</view>
    <template v-if="contract">
      <view class="card">
        <view class="title">{{ contract.customerName || '未命名客户' }}</view>
        <view class="caption">{{ contract.contractNo || '合同 #' + contract.contractId }} · {{ contract.monthCount }} 个月 · {{ contract.tickets }} 票</view>
        <view class="total-row">
          <view class="total-item"><view class="caption">佣金合计</view><view class="total-num">{{ money(contract.total) }}</view></view>
          <view class="total-item"><view class="caption">已结算</view><view class="total-num ok">{{ money(contract.settled) }}</view></view>
          <view class="total-item"><view class="caption">待结算</view><view class="total-num blue">{{ money(contract.settleable) }}</view></view>
          <view class="total-item"><view class="caption">冻结中</view><view class="total-num gold">{{ money(contract.frozen) }}</view></view>
        </view>
      </view>

      <view class="section-head"><text class="section-title">每月佣金</text><text class="caption">元</text></view>
      <view class="card">
        <scroll-view scroll-x class="chart-scroll" :show-scrollbar="false">
          <view class="chart">
            <view v-for="(m, i) in chartMonths" :key="m.month" class="col">
              <view class="col-amt">{{ money(m.total) }}</view>
              <view class="col-bar" :style="{ height: heights[i] + 'rpx' }">
                <view class="seg frozen" :style="{ flexGrow: Number(m.frozen) || 0 }" />
                <view class="seg settleable" :style="{ flexGrow: Number(m.settleable) || 0 }" />
                <view class="seg settled" :style="{ flexGrow: Number(m.settled) || 0 }" />
              </view>
              <view class="col-label">{{ String(m.month).slice(2) }}</view>
            </view>
          </view>
        </scroll-view>
        <view class="legend"><text><text class="dot settled" />已结算</text><text><text class="dot settleable" />待结算</text><text><text class="dot frozen" />冻结中</text></view>
      </view>

      <view class="section-head"><text class="section-title">每月结算明细</text></view>
      <view v-for="m in contract.months" :key="m.month" class="card month-card">
        <view class="month-head"><view class="month-name">{{ m.month }}</view><text :class="['tag', stateOf(m).tone]">{{ stateOf(m).text }}</text></view>
        <view class="month-grid">
          <view><view class="caption">发货票数</view><view>{{ m.tickets }}</view></view>
          <view><view class="caption">佣金</view><view>{{ money(m.total) }}</view></view>
          <view><view class="caption">已结算</view><view>{{ money(m.settled) }}</view></view>
          <view><view class="caption">待结算 / 冻结</view><view>{{ money(m.settleable) }} / {{ money(m.frozen) }}</view></view>
        </view>
      </view>
      <view class="caption foot-note">已结束月份的已解冻佣金每天 02:00 自动结算，结算后计入可提现余额。</view>
    </template>
    <view v-else-if="!loading && !error" class="card muted">没有找到这个合同的佣金记录。</view>
  </view>
</template>

<script>
import { appShareMixin } from '@/utils/share'
export default { mixins: [appShareMixin] }
</script>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { settlementApi } from '@/api'
import { money, barHeights } from '@/utils/settlement.mjs'

const contractId = ref(''), contract = ref(null), error = ref(''), loading = ref(false)
const chartMonths = computed(() => [...(contract.value?.months || [])].reverse())
const heights = computed(() => barHeights(chartMonths.value.map(m => m.total), 220))
function stateOf(m) {
  if (Number(m.frozen) > 0) return { text: '冻结中', tone: '' }
  if (Number(m.settleable) > 0) return { text: '待结算', tone: '' }
  return Number(m.settled) > 0 ? { text: '已结算', tone: 'ok' } : { text: '—', tone: '' }
}
async function load() {
  loading.value = true; error.value = ''
  try {
    const data = await settlementApi.overview()
    contract.value = (data.contracts || []).find(c => String(c.contractId) === String(contractId.value)) || null
    if (contract.value) uni.setNavigationBarTitle({ title: contract.value.customerName || '合同结算' })
  } catch (e) { error.value = e.message || '合同结算读取失败，请重试' }
  finally { loading.value = false }
}
onLoad(query => { contractId.value = query.id || ''; load() })
</script>

<style scoped>
.contract-page { padding-bottom: 40rpx; }
.total-row { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12rpx; margin-top: 24rpx; }
.total-num { margin-top: 6rpx; color: var(--park-text); font-size: 28rpx; font-weight: 650; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.total-num.ok { color: #1f9d76; } .total-num.blue { color: var(--park-blue); } .total-num.gold { color: #c98a14; }
.chart-scroll { width: 100%; white-space: nowrap; }
.chart { display: inline-flex; align-items: flex-end; gap: 22rpx; min-width: 100%; height: 320rpx; padding: 0 6rpx; border-bottom: 1rpx solid var(--park-line); }
.col { display: inline-flex; flex-direction: column; align-items: center; width: 76rpx; }
.col-amt { margin-bottom: 6rpx; color: var(--park-muted); font-size: 19rpx; }
.col-bar { display: flex; flex-direction: column; width: 40rpx; overflow: hidden; border-radius: 8rpx 8rpx 0 0; background: #eef1f8; }
.seg { flex-basis: 0; }
.settled { background: #39c49a; } .settleable { background: #637bff; } .frozen { background: #f5b84b; }
.col-label { margin: 10rpx 0; color: var(--park-muted); font-size: 21rpx; }
.legend { display: flex; gap: 28rpx; margin-top: 18rpx; color: var(--park-muted); font-size: 23rpx; }
.dot { display: inline-block; width: 16rpx; height: 16rpx; margin-right: 8rpx; border-radius: 4rpx; }
.month-head { display: flex; align-items: center; justify-content: space-between; }
.month-name { color: var(--park-text); font-size: 30rpx; font-weight: 650; }
.month-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16rpx; margin-top: 18rpx; font-size: 26rpx; overflow-wrap: anywhere; }
.foot-note { display: block; margin: 8rpx 30rpx; line-height: 1.6; }
</style>
