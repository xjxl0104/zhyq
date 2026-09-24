<template>
  <view>
    <view class="card" style="display:flex;gap:16rpx;flex-wrap:wrap">
      <text v-for="(t, v) in FILTERS" :key="v" class="tag" :class="{ ok: status === v }" @click="status = v; load()">{{ t }}</text>
    </view>
    <view v-if="state.error" class="card"><view class="muted">{{state.error}}</view><button class="btn" :disabled="state.loading" @click="pager.retry()">重试</button></view><view class="card" v-if="state.loading && !list.length"><view class="muted">正在加载佣金…</view></view><view class="card" v-else-if="!list.length && state.loaded && !state.error"><view class="muted">暂无佣金。客户完成合同履约或产生有效出库订单后，佣金会按约定生成。</view></view>
    <view class="card" v-for="c in list" :key="c.id">
      <view class="row">
        <view>
          <view>{{ c.customerName || '客户' }} · {{ SRC[c.sourceType] || '' }}</view>
          <view class="muted">{{ c.grade ? c.grade + ' 级' : '' }} {{ c.sourceType === 1 ? c.poolFactor + ' 个月' : c.poolFactor + '%' }} × 级差 {{ c.diffPct }}%(份额 {{ c.sharePct }}%)</view>
          <view class="muted">{{ c.time?.slice(0, 16) }}<text v-if="c.status === 1 && c.unfreezeAt"> · 预计 {{ c.unfreezeAt.slice(0, 10) }} 解冻</text></view>
        </view>
        <view style="text-align:right">
          <view class="money" :style="{ color: c.sign === -1 ? '#c00' : '' }">{{ Number(c.amount).toFixed(2) }}</view>
          <text class="tag" :class="c.status === 1 ? 'warn' : c.status >= 2 && c.status <= 4 ? 'ok' : ''">{{ STATUS[c.status] }}</text>
        </view>
      </view>
    </view>
    <view v-if="list.length" class="card"><button v-if="list.length < state.total" class="btn ghost" :loading="state.loading" :disabled="state.loading" @click="pager.load(false)">加载更多收益</button><view v-else class="muted">共 {{ state.total }} 条，已全部显示</view></view>
  </view>
</template>

<script setup>
import { ref, reactive, computed, onUnmounted } from 'vue'
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { bizApi } from '@/api'
import { createPager } from '@/utils/pagination.mjs'
const FILTERS = { '': '全部', 1: '冻结', 2: '可结算', 3: '已结算', 4: '已提现' }
const STATUS = { 1: '冻结', 2: '可结算', 3: '已结算', 4: '已提现', 5: '作废' }
const SRC = { 1: '园区入驻', 2: '出库单', 3: '平台费', 4: '签约奖' }
const state=reactive({}), status=ref('')
const pager=createPager(bizApi.commissions,state)
const list=computed(()=>state.records)
function load(){return pager.load(true,{status:status.value||undefined})}
onShow(load)
onReachBottom(()=>pager.load(false))
onUnmounted(pager.invalidate)
</script>
