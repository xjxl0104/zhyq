<template>
  <view class="page-wrap">
    <view class="card">
      <view class="wh-title">竞争保护白名单</view>
      <view class="wh-note">保护已有客户关系，避免重复营销。只需填写品牌或公司名称，不需要联系人、电话、订单或合作凭证。</view>
      <view class="privacy-note">名称加密保存，名单仅本云仓账号可见，不向其他云仓或营销伙伴展示。</view>
      <label class="wh-label" for="protection-name">品牌或公司名称</label>
      <input id="protection-name" v-model="name" class="wh-field" maxlength="100" :disabled="busy" placeholder="一次填写一个准确名称，最多 100 字" />
      <view class="wh-note">保存后立即用于新增报备校验，不改变已有报备或合同。系统忽略大小写、空格和全半角差异，按完整名称匹配；品牌别名、公司全称请分别添加。</view>
      <button class="btn" :disabled="busy || loading || !ready || !name.trim()" :loading="busy" @click="add">添加保护</button>
      <view v-if="error" class="wh-error" role="alert">{{ error }}</view>
    </view>
    <view class="card">
      <view class="wh-item-title">已保护 {{ items.length }} / 500 个名称</view>
      <view v-if="loading" class="wh-note">正在读取保护名单…</view>
      <button v-if="!ready && !loading" class="wh-secondary" @click="load">重新读取</button>
      <view v-if="ready && !items.length" class="wh-note">暂无保护名称，添加后即可生效。</view>
      <view v-for="item in items" :key="item.id" class="protection-row">
        <view class="protection-name">{{ item.name }}</view>
        <button size="mini" :disabled="busy || loading" @click="confirmRemove(item)">移除</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onHide, onUnload } from '@dcloudio/uni-app'
import { warehouseApi } from '@/api/warehouse'

const name = ref('')
const items = ref([])
const ready = ref(false)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
let revision = 0

async function load() {
  if (loading.value || busy.value) return
  const current = ++revision
  loading.value = true
  error.value = ''
  try {
    const result = await warehouseApi.protection()
    if (current !== revision) return
    items.value = result
    ready.value = true
  } catch (e) {
    if (current === revision) error.value = e.message || '读取失败，请重试'
  } finally { if (current === revision) loading.value = false }
}

async function add() {
  if (busy.value || loading.value || !ready.value || !name.value.trim()) return
  const current = revision
  busy.value = true
  error.value = ''
  let saved = false
  try {
    await warehouseApi.addProtection(name.value.trim())
    if (current !== revision) return
    name.value = ''
    saved = true
    uni.showToast({ title: '已添加保护', icon: 'success' })
  } catch (e) { if (current === revision) error.value = e.message || '保存失败，输入已保留，请重试' }
  finally { busy.value = false }
  if (saved) await load()
}

function confirmRemove(item) {
  const current = revision
  uni.showModal({ title: '移除保护？', content: '移除后，此名称可被新增报备。已有合同和客户归属不受影响。',
    success: async result => {
      if (!result.confirm || busy.value || current !== revision) return
      busy.value = true
      error.value = ''
      try {
        await warehouseApi.removeProtection(item.id)
        if (current !== revision) return
        items.value = items.value.filter(row => row.id !== item.id)
        uni.showToast({ title: '已移除保护', icon: 'success' })
      } catch (e) { if (current === revision) error.value = e.message || '移除失败，请重试' }
      finally { busy.value = false }
    }
  })
}

function clearPrivateData() {
  revision++
  items.value = []
  name.value = ''
  ready.value = false
  loading.value = false
  error.value = ''
}
onShow(() => { uni.hideShareMenu?.(); load() })
onHide(clearPrivateData)
onUnload(clearPrivateData)
</script>

<style scoped>
@import '../../styles/warehouse.css';
.privacy-note { margin: 24rpx 0; padding: 24rpx; background: #eef2fd; border-radius: 12rpx; line-height: 1.7; }
.protection-row { display:flex; align-items:center; justify-content:space-between; gap:24rpx; padding:24rpx 0; border-bottom:1rpx solid #e8eaef; }
.protection-name { flex:1; overflow-wrap:anywhere; }
</style>
