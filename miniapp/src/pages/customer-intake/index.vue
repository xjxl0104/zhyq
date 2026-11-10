<template>
  <view class="page-wrap">
    <view v-if="submitted" class="card">
      <view class="title">需求已提交</view>
      <view class="muted message">感谢您的填写，园区将根据需求安排跟进。</view>
      <view class="muted">推荐伙伴：{{ info.partnerName || '园区伙伴' }}</view>
    </view>
    <template v-else>
      <view class="card">
        <view class="title">仓储发货需求</view>
        <view v-if="info.partnerName" class="muted message">{{ info.partnerName }} 邀请您填写需求，无需注册账号。</view>
        <view v-if="initialLoading" class="muted message">正在读取填写入口…</view>
        <view v-if="error" class="error">{{ error }}</view>
        <button v-if="error && referralToken" class="btn ghost" :loading="initialLoading" :disabled="initialLoading" @click="load">重新加载</button>
      </view>
      <view v-if="ready" class="card">
        <view class="muted">带 * 为必填，其他信息可按实际情况选填。</view>
        <view v-if="pendingBody" class="message">
          <view class="muted">{{ submitting ? '正在提交，请稍候…' : '尚未确认提交结果。请再次确认，系统将核对同一次提交，避免重复推荐。' }}</view>
          <button class="btn" :loading="submitting" :disabled="submitting" @click="submit(pendingBody)">确认提交结果</button>
        </view>
        <ReferralForm v-else :form="form" :warehouses="info.warehouses || []" :loading="submitting" :disabled="!agreed" submit-label="提交需求" @submit="submit">
          <checkbox-group @change="e => agreed = e.detail.value.includes('agreed')">
            <label class="consent"><checkbox value="agreed" :checked="agreed" color="#3857f5" /><text>我同意将以上信息提交给园区及推荐伙伴，用于需求对接和跟进。</text></label>
          </checkbox-group>
        </ReferralForm>
      </view>
    </template>
  </view>
</template>
<script setup>
import { ref, reactive } from 'vue'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import { publicReferralApi } from '@/api'
import { newReferralForm, newSubmissionId, parseReferralToken, referralShare } from '@/utils/referral-intake.mjs'
import ReferralForm from '@/components/ReferralForm.vue'
const form = reactive(newReferralForm()), info = ref({}), error = ref(''), referralToken = ref('')
const initialLoading = ref(false), ready = ref(false), agreed = ref(false), submitting = ref(false), submitted = ref(false)
const pendingBody = ref(null)
// Retained for retries when a response is lost; never create a second customer on retry.
const submissionId = newSubmissionId()
onLoad(async query => { referralToken.value = parseReferralToken(query); await load() })
onShareAppMessage(() => referralShare(referralToken.value))
async function load() {
  if (initialLoading.value) return
  error.value = ''; ready.value = false
  if (!referralToken.value) { error.value = '填写入口无效，请让伙伴重新发送专属二维码。'; return }
  initialLoading.value = true
  try { info.value = await publicReferralApi.info(referralToken.value); ready.value = true }
  catch (e) { error.value = e.message || '填写入口暂不可用，请重试或联系推荐伙伴' }
  finally { initialLoading.value = false }
}
async function submit(body) {
  if (!ready.value || !agreed.value || submitting.value || submitted.value) return
  submitting.value = true; error.value = ''
  if (!pendingBody.value) pendingBody.value = JSON.parse(JSON.stringify({ ...body, submissionId, agreed: true }))
  try {
    await publicReferralApi.submit(referralToken.value, pendingBody.value)
    submitted.value = true
    uni.pageScrollTo({ scrollTop: 0, duration: 200 })
  } catch (e) {
    if (e.code >= 400 && e.code < 500 && e.code !== 408) pendingBody.value = null
    error.value = e.message || '提交失败，请重试'; uni.showToast({ title: error.value, icon: 'none' })
  }
  finally { submitting.value = false }
}
</script>
<style scoped>
.message{margin:20rpx 0;line-height:1.7}.error{color:#a3293e;margin-top:20rpx;line-height:1.7}.consent{display:flex;align-items:flex-start;gap:12rpx;font-size:24rpx;color:var(--park-muted);line-height:1.7;margin:24rpx 0}.consent checkbox{transform:scale(.85);flex-shrink:0}
</style>
