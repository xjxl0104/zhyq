<template>
  <view class="wh-home">
    <view class="card hero-card wh-hero">
      <view class="wh-role">{{ profile ? (profile.isOwner ? '主账号 · 本仓全部业务' : '业务员 · 本人客户') : '云仓工作台' }}</view>
      <view class="hero-title">{{ profile?.warehouseName || stats.warehouseName || '云仓工作台' }}</view>
      <view class="wh-state">{{ JOIN[stats.joinStatus] || '正在读取状态' }}<text v-if="stats.orderMode"> · {{ modeLabel }}</text></view>
    </view>
    <view v-if="profile?.isOwner && !profile.profileComplete && profile.profileEditable" class="card">
      <view class="section-title">完善云仓信息</view>
      <button class="btn secondary" @click="$emit('open','warehouse-profile')">补充云仓与联系人资料</button>
    </view>
    <view v-if="error" class="card"><view class="wh-error">{{ error }}</view><button class="btn secondary" :disabled="loading" @click="$emit('refresh')">重新读取</button></view>
    <view class="section-head"><text class="section-title">运营概况</text><text v-if="loading" class="caption">更新中</text></view>
    <view class="metrics">
      <view v-for="item in metrics" :key="item.key" class="metric">
        <text class="metric-label">{{ item.label }}</text><view class="metric-value">{{ stats[item.key] ?? '—' }}<text class="unit">{{ item.unit }}</text></view>
      </view>
    </view>
    <view v-for="section in groups" :key="section.title">
      <view class="section-head"><text class="section-title">{{ section.title }}</text></view>
      <view class="entry-grid"><button v-for="item in section.items" :key="item.page" class="quick-card pressable" @click="$emit('open',item.page)">
        <view class="quick-icon">{{ item.icon }}</view><view class="entry-copy"><view class="quick-title">{{ item.title }}</view><view class="quick-desc">{{ item.note }}</view></view>
      </button></view>
    </view>
  </view>
</template>
<script setup>
import { computed } from 'vue'
import { JOIN } from '@/utils/warehouse-ui'
import { dashboardSections, warehouseDashboardEntries } from '@/utils/warehouse-dashboard.mjs'
const props = defineProps({ profile: Object, stats: { type: Object, required: true }, loading: Boolean, error: String })
defineEmits(['open','refresh'])
const modeLabel = computed(() => ({ manual: '云山发货量同步', yunshan: '云山发货量同步', erp: '外部 ERP 接入', pending: '待配置订单接入' }[props.stats.orderMode] || '订单接入待确认'))
const metrics = [
  { key: 'pendingCustomers', label: '待确认客户', unit: '家' }, { key: 'customerCount', label: '已承接客户', unit: '家' },
  { key: 'todayOrders', label: '今日发货', unit: '票' }, { key: 'monthOrders', label: '本月发货', unit: '票' }
]
const groups = computed(() => {
  const entries = warehouseDashboardEntries(props.profile, props.stats)
  return dashboardSections.map((title,i) => ({ title, items: entries.filter(item => item.section === i) })).filter(group => group.items.length)
})
</script>
<style scoped>
.wh-home { color:#17203e; }
.wh-hero { padding:34rpx; }
.wh-role,.wh-state { color:#e0e8ff; font-size:25rpx; }
.wh-role { margin-bottom:10rpx; }.wh-state { margin-top:18rpx; }
.metrics,.entry-grid { margin:0 24rpx; display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:14rpx; }
.metric { border:1rpx solid #e3e8f4; background:#fff; border-radius:20rpx; padding:20rpx 24rpx; }
.metric-label { color:#52617c;font-size:25rpx; }.metric-value { font-size:44rpx; font-weight:700; overflow-wrap:anywhere; }.unit { font-size:22rpx;color:#52617c;margin-left:8rpx;font-weight:400; }
.entry-grid button { width:100%; margin:0;line-height:1.4;text-align:left; padding:18rpx 14rpx; min-height:112rpx; gap:12rpx; }
.entry-grid button::after { border:0; }.entry-copy { min-width:0; }.wh-error { color:#a3293e; margin-bottom:12rpx; }
</style>
