<template>
  <view v-if="section && row" class="card">
    <view class="title">标准报价明细</view>
    <picker mode="selector" :range="sections" range-key="label" :value="sectionIndex" @change="selectSection">
      <view class="choice">{{ section.label }} <text>切换附表 ›</text></view>
    </picker>
    <picker mode="selector" :range="section.rows" range-key="label" :value="rowIndex" @change="rowIndex=Number($event.detail.value)">
      <view class="choice">{{ row.label }} <text>选择项目 ›</text></view>
    </picker>
    <view v-if="section.key==='stage'" class="muted">填写一口价时优先采用该价，再扣除特殊减免。</view>
    <view v-for="col in section.columns" :key="col.key" class="cell">
      <text>{{ col.label }}</text>
      <textarea v-if="col.kind==='text'" class="note" :value="values[col.key]" maxlength="2000" auto-height
        placeholder="填写条款" @input="update(col.key,$event.detail.value)" />
      <input v-else class="number" type="digit" :value="values[col.key]" placeholder="待询价" @input="update(col.key,$event.detail.value)" />
    </view>
  </view>
</template>
<script setup>
import {computed,ref} from 'vue'
const props=defineProps({sections:{type:Array,required:true},cells:{type:Object,required:true}})
const emit=defineEmits(['change'])
const sectionIndex=ref(0),rowIndex=ref(0)
const section=computed(()=>props.sections?.[sectionIndex.value])
const row=computed(()=>section.value?.rows[rowIndex.value])
const values=computed(()=>(row.value && props.cells?.[row.value.id]) || {})
function selectSection(event){sectionIndex.value=Number(event.detail.value);rowIndex.value=0}
function update(key,value){emit('change',{id:row.value.id,key,value})}
</script>
<style scoped>
.choice{margin:18rpx 0;padding:18rpx;background:#f4f6ff;color:#27355b;border-radius:12rpx;display:flex;align-items:center;justify-content:space-between;gap:12rpx}
.choice text{flex-shrink:0;color:#495edb;font-size:24rpx}.cell{padding:16rpx 0;border-bottom:1rpx solid var(--park-line);font-size:27rpx;display:flex;align-items:center;justify-content:space-between;gap:12rpx;flex-wrap:wrap}
.number,.note{border-radius:8rpx;background:#f7f8fd;padding:12rpx}.number{width:135rpx;text-align:right}.note{width:100%;min-height:74rpx;box-sizing:border-box}
</style>
