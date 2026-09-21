<template>
  <view v-if="a">
    <view class="card">
      <view class="muted">您可以在园区设定的区间内下调本团队各岗位份额,下调部分归您(级差)。只影响之后的成交。</view>
      <view v-if="!a.enabled" class="tag warn" style="margin-top:12rpx">园区已关闭此功能</view>
    </view>
    <view class="card" v-for="p in a.positions" :key="p.code">
      <view class="row"><text style="font-weight:600">{{ p.name }}</text><text class="money">{{ vals[p.code] }}%</text></view>
      <slider :min="p.minPct" :max="p.defaultPct" :value="vals[p.code]" show-value activeColor="#4f46e5" :disabled="!a.enabled" @change="e => vals[p.code] = e.detail.value" />
      <view class="muted">区间 {{ p.minPct }}% – {{ p.defaultPct }}%(园区默认 {{ p.defaultPct }}%)</view>
    </view>
    <button class="btn" :disabled="!a.enabled" @click="save">保存</button>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { meApi } from '@/api'
const a = ref(null); const vals = reactive({})
onLoad(async () => { a.value = await meApi.allocation(); a.value.positions.forEach(p => { vals[p.code] = p.currentPct }) })
async function save() { await meApi.setAllocation({ ...vals }); uni.showToast({ title: '已保存' }) }
</script>
