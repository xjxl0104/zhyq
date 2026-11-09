<template>
  <view class="page-wrap">
    <view class="card">
      <view class="title">我的客户填写码</view>
      <view class="muted qr-note">发给客户，客户扫码即可自行填写需求，推荐归属自动记在您名下。</view>
      <view v-if="linkError" class="error">{{ linkError }}</view>
      <button v-if="!link.token" class="btn ghost" :loading="linkLoading" :disabled="linkLoading" @click="loadLink">重新读取专属二维码</button>
      <button v-if="link.token" class="btn ghost" :loading="codeLoading" :disabled="codeLoading" @click="makeCode">{{ codeImage ? '重新生成二维码' : '生成我的专属二维码' }}</button>
      <image v-if="codeImage" :src="codeImage" mode="widthFix" class="qr-image" @click="preview" />
      <button v-if="codeImage" class="btn ghost" @click="saveCode">保存二维码</button>
      <!-- #ifdef MP-WEIXIN -->
      <button v-if="link.token" class="btn" open-type="share">发给客户填写</button>
      <!-- #endif -->
    </view>
    <view class="card">
      <view class="muted">推荐后直接锁定 3 个自然月；签约成交后，推荐归属长期有效。</view>
      <ReferralForm :form="form" :warehouses="warehouses" :loading="loading" @submit="submit" />
    </view>
  </view>
</template>
<script setup>
import { ref, reactive } from 'vue'
import { onLoad, onUnload, onShareAppMessage } from '@dcloudio/uni-app'
import { bizApi } from '@/api'
import { downloadReferralCode } from '@/utils/request'
import { newReferralForm, referralShare } from '@/utils/referral-intake.mjs'
import ReferralForm from '@/components/ReferralForm.vue'
const warehouses = ref([]), loading = ref(false), form = reactive(newReferralForm())
const link = ref({}), linkError = ref(''), linkLoading = ref(false), codeLoading = ref(false), codeImage = ref('')
onLoad(async () => {
  await Promise.all([loadLink(), bizApi.warehouses().then(items => { warehouses.value = items }).catch(() => {})])
})
onShareAppMessage(() => referralShare(link.value.token))
onUnload(releaseImage)
function releaseImage() {
  // #ifdef H5
  if (codeImage.value?.startsWith('blob:')) URL.revokeObjectURL(codeImage.value)
  // #endif
}
async function loadLink() {
  if (linkLoading.value) return
  linkLoading.value = true; linkError.value = ''
  try { link.value = await bizApi.referralLink() } catch (e) { linkError.value = e.message || '专属二维码读取失败，请重试' } finally { linkLoading.value = false }
}
async function makeCode() {
  if (codeLoading.value || !link.value.token) return
  codeLoading.value = true; linkError.value = ''
  try {
    let env = 'release'
    // #ifdef MP-WEIXIN
    env = uni.getAccountInfoSync().miniProgram.envVersion || 'release'
    // #endif
    const image = await downloadReferralCode(env)
    releaseImage(); codeImage.value = image
  } catch (e) { linkError.value = e.message || '二维码生成失败，请重试' } finally { codeLoading.value = false }
}
const preview = () => uni.previewImage({ urls: [codeImage.value] })
async function saveCode() {
  // #ifdef H5
  const a = document.createElement('a'); a.href = codeImage.value; a.download = '客户填写专属二维码.png'; a.click()
  // #endif
  // #ifdef MP-WEIXIN
  try { await uni.saveImageToPhotosAlbum({ filePath: codeImage.value }); uni.showToast({ title: '已保存' }) }
  catch (e) { linkError.value = '保存未完成，请允许保存到相册，或点击二维码长按保存' }
  // #endif
}
async function submit(body) {
  if (loading.value) return
  loading.value = true
  try {
    const r = await bizApi.referral(body)
    uni.showModal({ title: '推荐成功', content: `客户已锁定${r.lockUntil ? '至 ' + r.lockUntil.slice(0, 10) : ' 3 个自然月'}，签约成交后长期有效。园区专员将跟进您的客户。`, showCancel: false,
      success: () => uni.switchTab({ url: '/pages/customers/index' }) })
  } catch (e) { uni.showToast({ title: e.message || '推荐提交失败，请重试', icon: 'none' }) }
  finally { loading.value = false }
}
</script>
<style scoped>
.qr-note{margin-top:16rpx;line-height:1.7}.qr-image{display:block;width:360rpx;margin:24rpx auto}.error{color:#a3293e;margin-top:16rpx;line-height:1.6}
</style>
