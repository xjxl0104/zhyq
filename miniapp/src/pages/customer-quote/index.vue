<template>
  <view class="page-wrap">
    <view v-if="error" class="card"><view class="error">{{ error }}</view><button class="btn ghost" :disabled="busy" @click="load">重新读取</button></view>
    <view v-if="loading" class="card muted">正在读取客户报价需求…</view>
    <view v-if="summary" class="card"><view class="title">{{ customerName }}</view>
      <view class="subtitle">{{ summary.stage === 'intent' ? '意向客户' : '线索客户' }}</view>
      <button v-if="summary.stage === 'lead' || (!summary.token && !summary.requirements)" class="btn" :loading="busy" :disabled="busy" @click="promote">{{ summary.stage === 'lead' ? '转为意向客户' : '领取本客户的填写码' }}</button>
      <view v-if="summary.stage === 'intent' && summary.token && !summary.submittedAt && !summary.requirements" class="share-box">
        <view class="muted">将专属填写码发给该客户，客户可直接填报报价需求。</view>
        <button class="btn ghost" :loading="busy" :disabled="busy" @click="makeCode">生成客户报价填写码</button>
        <image v-if="image" class="qr" :src="image" mode="widthFix" @click="preview" />
        <button v-if="image" class="btn ghost" @click="save">保存填写码</button>
        <!-- #ifdef MP-WEIXIN -->
        <button v-if="summary.token" class="btn" open-type="share">发给客户填写</button>
        <!-- #endif -->
      </view>
    </view>
    <view v-if="summary && (summary.submittedAt || summary.requirements)" class="card">
      <view class="title">已提交的报价需求</view><view v-if="summary.submittedAt" class="muted">{{ summary.submittedAt.replace('T',' ') }}</view>
      <view v-for="row in rows" :key="row.label" class="detail"><view class="muted">{{ row.label }}</view><view>{{ row.value }}</view></view>
    </view>
  </view>
</template>
<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow, onUnload, onShareAppMessage } from '@dcloudio/uni-app'
import { bizApi } from '@/api'
import { get, post, token, downloadQuoteCode } from '@/utils/request'
import { createAppShare } from '@/utils/share'
import { parseReferralToken } from '@/utils/referral-intake.mjs'
import { quoteRows } from '@/utils/quote-intake.mjs'
const id = ref(''), summary = ref(null), customerName = ref(''), loading = ref(false), busy = ref(false), image = ref(''), error = ref('')
let actor = '', sequence = 0
const rows = computed(() => quoteRows(summary.value?.requirements || {}))
const path = () => '/quote/customers/' + id.value
onLoad(q => { id.value = /^[1-9]\d*$/.test(q.id) ? q.id : '' })
onShow(load)
onUnload(() => { sequence++; releaseImage() })
onShareAppMessage(event => {
  const link = parseReferralToken({token:summary.value?.token})
  return event?.from === 'button' && link && actor === token.get() && !summary.value.submittedAt && !summary.value.requirements
    ? { ...createAppShare(), title:'邀请您填写云仓报价需求',path:'/pages/quote-intake/index?token=' + link }
    : createAppShare()
})
function releaseImage() {
  // #ifdef H5
  if (image.value.startsWith('blob:')) URL.revokeObjectURL(image.value)
  // #endif
  image.value = ''
}
async function load() {
  if (busy.value) return
  const attempt = ++sequence, session = token.get()
  if (actor !== session) { summary.value = null; customerName.value = ''; releaseImage() }
  actor = session; error.value = ''; loading.value = true
  try {
    if (!id.value) throw new Error('客户编号无效')
    const [state, customer] = await Promise.all([get(path()),bizApi.customer(id.value)])
    if (attempt === sequence) { summary.value = state; customerName.value = customer.name || '客户报价需求'; if (state.submittedAt || state.requirements) releaseImage() }
  } catch(e) { if (attempt === sequence) { summary.value = null; releaseImage(); error.value = e.message || '报价需求读取失败' } }
  finally { if (attempt === sequence) loading.value = false }
}
async function promote() {
  if (busy.value || loading.value || !summary.value || summary.value.token || summary.value.requirements) return
  busy.value = true; error.value = ''
  try { summary.value = await post(path() + '/intent') }
  catch(e) { error.value = e.message || '更新失败，请重试' }
  finally { busy.value = false }
}
async function makeCode() {
  if (busy.value || loading.value || !summary.value?.token) return
  busy.value = true; error.value = ''
  try {
    let env = 'release'
    // #ifdef MP-WEIXIN
    env = uni.getAccountInfoSync().miniProgram.envVersion || 'release'
    // #endif
    const next = await downloadQuoteCode(id.value,env)
    releaseImage(); image.value = next
  } catch(e) { error.value = e.message || '生成填写码失败，请重试' }
  finally { busy.value = false }
}
const preview = () => uni.previewImage({urls:[image.value]})
async function save() {
  // #ifdef H5
  const a = document.createElement('a'); a.href = image.value; a.download = '客户报价填写码.png'; a.click()
  // #endif
  // #ifdef MP-WEIXIN
  try { await uni.saveImageToPhotosAlbum({filePath:image.value}); uni.showToast({title:'已保存'}) }
  catch { error.value = '保存未完成，可点击二维码长按保存' }
  // #endif
}
</script>
<style scoped>
.qr{display:block;width:360rpx;margin:24rpx auto}.subtitle,.share-box,.detail{margin-top:20rpx}.detail{border-top:1rpx solid var(--park-line);padding-top:16rpx;word-break:break-word}.error{color:#a3293e}
</style>
