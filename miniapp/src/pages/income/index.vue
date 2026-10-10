<template>
  <view class="page-wrap income-page">
    <view class="card">
      <view class="income-heading">
        <view class="title">收益明细</view>
        <button class="income-refresh" :disabled="state.loading" @click="load">{{ state.loading ? '正在刷新…' : '刷新' }}</button>
      </view>
      <view class="income-filters">
        <button v-for="filter in FILTERS" :key="filter.value" class="tag income-filter" :class="{ ok: status === filter.value }" :aria-label="filter.label + '收益'" @click="changeStatus(filter.value)">{{ filter.label }}</button>
      </view>
    </view>
    <view v-if="state.error" class="card" role="alert">
      <view class="title">{{ list.length ? '更多收益暂未加载' : '收益暂未加载' }}</view>
      <view class="income-message income-error">{{ state.error }}</view>
      <button class="btn" :disabled="state.loading" :loading="state.loading" @click="pager.retry()">{{ list.length ? '重试加载更多' : '重新加载收益' }}</button>
    </view>
    <view v-if="state.loading && !list.length" class="card" role="status"><view class="income-message">正在加载收益，请稍候…</view></view>
    <view v-else-if="!list.length && state.loaded && !state.error" class="card" role="status">
      <view class="title">{{ status ? '暂无' + selectedLabel + '收益' : '暂未产生佣金' }}</view>
      <view class="income-message">{{ status ? '当前筛选下没有记录，可查看全部收益。' : '客户完成合同履约或产生有效出库订单后，佣金会按约定生成。已有业务但未显示收益，可联系园区运营核对。' }}</view>
      <button v-if="status" class="btn secondary" @click="changeStatus('')">查看全部收益</button>
      <button v-else class="btn secondary" @click="load">刷新收益</button>
    </view>
    <view class="card" v-for="c in list" :key="c.id">
      <view class="row income-row">
        <view class="income-details">
          <view class="income-name">{{ c.customerName || '客户' }} · {{ SRC[c.sourceType] || '佣金' }}</view>
          <view v-if="c.pricingId || c.amountPerOrder != null" class="income-message">自定义单价 {{ formatPerOrder(c.amountPerOrder) }} 元／单 × {{ c.orderCount ?? '—' }} 单</view>
          <view v-else class="income-message">历史规则：{{ c.grade ? c.grade + ' 级' : '' }} {{ c.poolFactor ?? '—' }}{{ c.sourceType === 1 ? ' 个月' : '%' }} × 级差 {{ c.diffPct ?? '—' }}%（份额 {{ c.sharePct ?? '—' }}%）</view>
          <view class="income-message">{{ incomeTime(c.time) }}<text v-if="c.status === 1 && c.unfreezeAt"> · 预计 {{ incomeTime(c.unfreezeAt, true) }} 解冻</text></view>
        </view>
        <view class="income-value">
          <view class="money" :class="{ 'income-negative': c.sign === -1 }">{{ incomeAmount(c.amount) }} 元</view>
          <text class="tag" :class="c.status === 1 ? 'warn' : c.status >= 2 && c.status <= 4 ? 'ok' : ''">{{ STATUS[c.status] || '状态待确认' }}</text>
        </view>
      </view>
    </view>
    <view v-if="list.length && !state.error" class="card"><button v-if="list.length < state.total" class="btn ghost" :loading="state.loading" :disabled="state.loading" @click="pager.load(false)">加载更多收益</button><view v-else class="income-message">共 {{ state.total }} 条，已全部显示</view></view>
  </view>
</template>

<script>
import { appShareMixin } from '@/utils/share'

export default { mixins: [appShareMixin] }
</script>

<script setup>
import { ref, reactive, computed, onUnmounted } from 'vue'
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { bizApi } from '@/api'
import { createPager } from '@/utils/pagination.mjs'
import { formatPerOrder } from '@/utils/pricing.mjs'
import { incomeFilters, incomePage, incomeAmount, incomeTime } from '@/utils/income.mjs'
const FILTERS = [{ value: '', label: '全部' }, { value: '1', label: '冻结' }, { value: '2', label: '可结算' }, { value: '3', label: '已结算' }, { value: '4', label: '已提现' }]
const STATUS = { 1: '冻结', 2: '可结算', 3: '已结算', 4: '已提现', 5: '作废' }
const SRC = { 1: '园区入驻', 2: '出库单', 3: '平台费', 4: '签约奖' }
const state=reactive({}), status=ref('')
const pager=createPager(async params=>incomePage(await bizApi.commissions(params)),state)
const list=computed(()=>state.records)
const selectedLabel=computed(()=>FILTERS.find(filter=>filter.value===status.value)?.label || '')
function load(){return pager.load(true,incomeFilters(status.value))}
function changeStatus(value){if(status.value===value)return;status.value=value;return load()}
onShow(load)
onReachBottom(()=>pager.load(false))
onUnmounted(pager.invalidate)
</script>

<style scoped>
.income-heading{display:flex;align-items:center;justify-content:space-between;gap:20rpx}
.income-refresh{margin:0;padding:8rpx 12rpx;color:#2b4fd6;background:transparent;font-size:28rpx;line-height:52rpx}
.income-filters{display:flex;flex-wrap:wrap;gap:12rpx;margin-top:20rpx}
.income-filter{margin:0;min-height:76rpx;padding:0 20rpx;font-size:26rpx;line-height:76rpx}
.income-message{margin-top:12rpx;color:#6b7386;font-size:26rpx;line-height:1.65;overflow-wrap:anywhere}
.income-error,.income-negative{color:#c0392b}
.income-row{align-items:flex-start;flex-wrap:wrap}
.income-details{flex:1 1 320rpx;min-width:0}
.income-name{overflow-wrap:anywhere}
.income-value{max-width:100%;margin-left:auto;text-align:right;overflow-wrap:anywhere}
.income-value .tag{margin-top:12rpx}
.income-refresh:focus-visible,.income-filter:focus-visible{outline:2px solid #2b4fd6;outline-offset:2px}
</style>
