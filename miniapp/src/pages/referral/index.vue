<template>
  <view>
    <view class="card">
      <view class="muted">推荐后客户进入 7 天预锁,专员联系确认后转为 180 天有效锁定;期内成交归您。</view>
      <input class="input" v-model="form.name" placeholder="客户公司 / 店铺名称 *" />
      <input class="input" v-model="form.contact" placeholder="联系人" />
      <input class="input" v-model="form.phone" type="number" maxlength="11" placeholder="联系手机 *" />
      <picker :range="TYPES" range-key="label" @change="e => form.serviceType = TYPES[e.detail.value].value">
        <view class="input">需求类型:{{ TYPES.find(t => t.value === form.serviceType)?.label }}</view>
      </picker>
      <picker v-if="form.serviceType !== 4" :range="warehouses" range-key="name" @change="e => form.warehouseId = warehouses[e.detail.value].id">
        <view class="input">意向云仓:{{ warehouses.find(w => w.id === form.warehouseId)?.name || '不限' }}</view>
      </picker>
      <input class="input" v-model="form.demand" placeholder="需求(面积 / 日单量 / 品类)" />
      <textarea class="input" v-model="form.remark" placeholder="备注" style="height:120rpx" />
      <button class="btn" :loading="loading" :disabled="loading" @click="submit">提交推荐</button>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { bizApi } from '@/api'

const TYPES = [{ value: 2, label: '一件代发' }, { value: 1, label: '仓储' }, { value: 3, label: '仓配一体' }, { value: 4, label: '园区入驻(租仓/租办公)' }]
const warehouses = ref([{id:null,name:'不限'}])
const loading = ref(false)
const form = reactive({ name: '', contact: '', phone: '', serviceType: 2, warehouseId: null, demand: '', remark: '' })

onLoad(async () => { try { warehouses.value = [{id:null,name:'不限'}, ...await bizApi.warehouses()] } catch (e) { /* 可为空 */ } })

async function submit() {
  if (loading.value) return
  if (!form.name.trim()) return uni.showToast({ title: '请填客户名称', icon: 'none' })
  if (!/^1\d{10}$/.test(form.phone)) return uni.showToast({ title: '手机号格式不对', icon: 'none' })
  loading.value = true
  try {
    const r = await bizApi.referral({...form, warehouseId:form.serviceType === 4 ? null : form.warehouseId})
    uni.showModal({ title: '推荐成功', content: `已为您预锁至 ${r.prelockUntil?.slice(0, 10)},招商专员将尽快联系客户。`, showCancel: false,
      success: () => uni.switchTab({ url: '/pages/customers/index' }) })
  } finally { loading.value = false }
}
</script>
