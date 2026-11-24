<template>
  <view class="page-wrap">
    <view v-if="state === 'loading'" class="card muted">正在读取报价填写码…</view>
    <view v-else-if="state === 'error'" class="card"><view class="error">{{ error }}</view><button v-if="tokenValue" class="btn ghost" @click="load">重新读取</button></view>
    <view v-else-if="state === 'done'" class="card"><view class="title">报价需求已提交</view><view class="muted">园区将对比各云仓报价，运营人员会根据评估结果分配对接云仓。</view></view>
    <view v-else-if="pending" class="card"><view v-if="sending" class="muted">正在核对并提交报价需求…</view><template v-else><view class="error">{{ error }}</view><button class="btn" @click="send">再次确认提交结果</button></template></view>
    <view v-else>
      <view class="card"><view class="title">云仓报价需求</view><view class="muted">请按实际业务勾选。可跳过暂时无法确定的项目。</view></view>
      <view v-for="f in QUOTE_FIELDS" :key="f.key" class="card">
        <view class="title">{{ f.label }} <text v-if="f.type === 'single'" class="muted">单选</text><text v-else-if="f.type === 'multi' || f.type === 'services'" class="muted">多选</text></view>
        <view v-if="f.hint" class="muted">{{ f.hint }}</view>
        <checkbox-group v-if="f.type === 'multi'" class="choices" @change="changeMulti(f.key, $event.detail.value)">
          <label v-for="opt in f.options" :key="opt.value"><checkbox :value="opt.value" :checked="form[f.key].includes(opt.value)" color="#3857f5" />{{ opt.label }}</label>
        </checkbox-group>
        <radio-group v-else-if="f.type === 'single'" class="choices" @change="form[f.key] = $event.detail.value">
          <label v-for="opt in f.options" :key="opt.value"><radio :value="opt.value" :checked="form[f.key] === opt.value" color="#3857f5" />{{ opt.label }}</label>
        </radio-group>
        <button v-if="f.type === 'single' && form[f.key]" class="clear" @click="form[f.key] = ''">清除选择</button>
        <input v-if="f.type === 'decimal'" class="input" type="digit" :value="form[f.key]" @input="form[f.key] = $event.detail.value" placeholder="输入卡板位数量" />
        <input v-if="f.other && form[f.key].includes('其他')" class="input" :value="form[f.other]" @input="form[f.other] = $event.detail.value" maxlength="100" :placeholder="'请补充其他' + f.label" />
        <input v-if="f.key === 'weightRanges' && form.weightRanges.includes('>3')" class="input" v-model="form.heavyWeightKg" type="digit" placeholder="超过 3 kg 时通常几 kg（选填）" />
        <view v-if="f.type === 'services'">
          <view v-for="g in SERVICE_GROUPS" :key="g.key" class="service-group">
            <view class="subtitle">{{ g.label }}</view>
            <checkbox-group class="choices" @change="changeService(g, $event.detail.value)">
              <label v-for="v in g.values" :key="v"><checkbox :value="v" :checked="form.services.includes(v)" color="#3857f5" />{{ v }}</label>
            </checkbox-group>
            <input v-if="activeGroups.includes(g.key)" class="input" :value="form.serviceQuantities[g.key]" @input="form.serviceQuantities[g.key] = $event.detail.value" maxlength="200" :placeholder="'预计月用量（' + g.unit + '，选填）'" />
          </view>
        </view>
      </view>
      <view class="card"><view class="title">报价补充信息 <text class="muted">选填</text></view>
        <view v-for="f in SUPPLEMENT" :key="f.key" class="service-group"><view>{{ f.label }}{{ f.unit ? '（' + f.unit + '）' : '' }}</view>
          <input v-if="f.type === 'number'" class="input" type="number" :value="form[f.key]" @input="form[f.key] = $event.detail.value" />
          <input v-else-if="f.type === 'digit'" class="input" type="digit" :value="form[f.key]" @input="form[f.key] = $event.detail.value" />
          <input v-else class="input" type="text" :value="form[f.key]" @input="form[f.key] = $event.detail.value" maxlength="500" />
        </view>
      </view>
      <view class="card">
        <checkbox-group @change="agreed = $event.detail.value.length > 0"><label><checkbox value="yes" :checked="agreed" color="#3857f5" />同意将已填写的需求提交园区，用于云仓报价与跟进。</label></checkbox-group>
        <view v-if="error" class="error">{{ error }}</view><button class="btn" :loading="sending" :disabled="sending" @click="send">提交报价需求</button>
      </view>
    </view>
  </view>
</template>
<script>
import { appShareMixin } from '@/utils/share'
export default { mixins: [appShareMixin] }
</script>
<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { publicQuoteRequest } from '@/utils/request'
import { parseReferralToken, newSubmissionId, exclusiveNone } from '@/utils/referral-intake.mjs'
import { QUOTE_FIELDS, SERVICE_GROUPS, SUPPLEMENT, newQuoteForm, quotePayload } from '@/utils/quote-intake.mjs'
const form = reactive(newQuoteForm()), state = ref('loading'), error = ref(''), agreed = ref(false)
const tokenValue = ref(''), pending = ref(null), sending = ref(false)
const activeGroups = computed(() => SERVICE_GROUPS.filter(g => g.values.some(v => form.services.includes(v))).map(g => g.key))
onLoad(async query => { tokenValue.value = parseReferralToken(query); await load() })
async function load() {
  state.value = 'loading'; error.value = ''
  try {
    if (!tokenValue.value) throw new Error('报价填写码无效，请向推荐伙伴索取新的二维码')
    const res = await publicQuoteRequest('GET', tokenValue.value)
    state.value = res.submitted ? 'done' : 'ready'
  } catch (e) { state.value = 'error'; error.value = e.message || '读取失败，请重试' }
}
function changeMulti(key, selected) { form[key] = key === 'couriers' ? exclusiveNone(form[key], selected) : selected }
function changeService(g, selected) { form.services = [...form.services.filter(v => !g.values.includes(v)), ...selected] }
async function send() {
  if (sending.value || state.value !== 'ready') return
  error.value = ''
  if (!pending.value) {
    if (!agreed.value) { error.value = '请先确认报价资料提交授权'; return }
    try { pending.value = { submissionId: newSubmissionId(), agreed: true, requirements: quotePayload(form) } }
    catch (e) { error.value = e.message; return }
  }
  sending.value = true
  try { await publicQuoteRequest('POST', tokenValue.value, pending.value); state.value = 'done'; pending.value = null }
  catch (e) {
    if (Number(e.code) === 409) { pending.value = null; await load() }
    else if (Number(e.code) >= 400 && Number(e.code) < 500) { pending.value = null; error.value = e.message }
    else error.value = '提交结果暂未确认，请重试。系统将核对同一份资料。'
  } finally { sending.value = false }
}
</script>
<style scoped>
.title .muted{font-size:24rpx;font-weight:400}.choices{display:flex;flex-wrap:wrap;gap:16rpx 24rpx;margin-top:20rpx}.choices label{display:flex;align-items:center;min-height:56rpx}.choices checkbox,.choices radio{transform:scale(.82);transform-origin:left center}.service-group{margin:24rpx 0}.subtitle{font-weight:600}.clear{color:var(--park-blue);font-size:24rpx;background:none;margin:16rpx 0 0 auto;width:max-content}.clear::after{border:0}.error{color:#a3293e;margin:16rpx 0}
</style>
