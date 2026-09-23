<template>
  <view>
    <view class="card" style="display:flex;gap:16rpx;flex-wrap:wrap">
      <text v-for="(t, v) in FILTERS" :key="v" class="tag" :class="{ ok: status === v }" @click="status = v; load()">{{ t }}</text>
    </view>
    <view v-if="error" class="card"><view class="muted">{{error}}</view><button class="btn" @click="load()">重试</button></view><view class="card" v-if="loading && !list.length"><view class="muted">正在加载佣金…</view></view><view class="card" v-else-if="!list.length && !error"><view class="muted">暂无佣金。客户完成合同履约或产生有效出库订单后，佣金会按约定生成。</view></view>
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
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { bizApi } from '@/api'
const FILTERS = { '': '全部', 1: '冻结', 2: '可结算', 3: '已结算', 4: '已提现' }
const STATUS = { 1: '冻结', 2: '可结算', 3: '已结算', 4: '已提现', 5: '作废' }
const SRC = { 1: '园区入驻', 2: '出库单', 3: '平台费', 4: '签约奖' }
const loading=ref(false),error=ref('')
const list = ref([]); const status = ref('')
async function load() { loading.value=true;error.value='';try{list.value=(await bizApi.commissions({pageNo:1,pageSize:100,status:status.value||undefined})).records||[]}catch(e){error.value=e.message||'佣金加载失败，请重试'}finally{loading.value=false} }
onShow(load)
</script>
