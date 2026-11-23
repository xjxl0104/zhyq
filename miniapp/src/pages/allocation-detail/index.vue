<template>
  <view class="page-wrap pricing-page">
    <view class="card introduction"><view class="title">客户分佣</view><view class="pricing-note">总佣金先计提 10% 平台服务费，其余金额由 P4 分配。</view></view>
    <view v-if="loading" class="card status-panel">正在加载分佣资料…</view>
    <view v-else-if="loadError" class="card"><view class="error-text">{{ loadError }}</view><button class="btn secondary" @click="load">重新加载</button></view>
    <view v-else-if="!canPrice" class="card"><view class="section-title">分佣金额由负责客户的 P4 设置</view><button class="btn secondary" @click="navigateTo('/pages/allocation/index')">查看本人客户分佣</button></view>
    <template v-else-if="detail">
      <view class="card"><view class="section-title">{{ detail.customerName }}</view><view v-if="detail.contractNo" class="pricing-note">合同：{{ detail.contractNo }}</view><view class="pricing-note">负责伙伴：{{ detail.ownerName || me.name }}</view></view>
      <view class="card">
        <view class="row form-heading"><text class="section-title">每单金额</text><text class="tag" :class="detail.configured ? 'ok' : 'warn'">{{ detail.configured ? '已配置' : '待分佣' }}</text></view>
        <view class="pricing-note">个人单价最多 3 位小数（0.001 元）；没有分配金额时填写 0。</view>
        <view v-if="detail.requiresReallocation" class="error-text feedback">原方案超过扣除 10% 服务费后的可分配金额，请调整后保存。</view>
        <label class="field-label" for="total-price">总佣金单价</label>
        <view class="amount-field"><input id="total-price" class="input" v-model="form.totalPerOrder" type="digit" :disabled="!editable" placeholder="填写协商后的总额" @input="clearFeedback" /><text>元／单</text></view>
        <view class="fee-line row"><text>平台服务费（10%）</text><text class="money">{{ feeText }} 元／单</text></view>
        <label class="field-label" for="owner-price">本人单价 · {{ detail.ownerName || me.name || '本人' }}</label>
        <view class="amount-field"><input id="owner-price" class="input" v-model="form.ownerPerOrder" type="digit" :disabled="!editable" :placeholder="'最多可填 ' + maxForOwner + ' 元，可为 0'" @input="clearFeedback" /><text>元／单</text></view>
        <view class="remaining-note" :class="{ 'error-text': remainderUnits !== null && remainderUnits < 0 }">剩余可分配 {{ remainderText }} 元／单</view>
      </view>
      <view class="card">
        <view class="row form-heading"><text class="section-title">分配给其他人员</text><switch :checked="distribute" :disabled="!editable" color="#3857f5" @change="toggleDistribution" /></view>
        <view class="pricing-note">可不分配；最多选择 {{ maxBeneficiaries }} 位具体受益人。</view>
        <template v-if="distribute">
          <view v-for="(row, index) in form.beneficiaries" :key="row.key" class="beneficiary-row">
            <view class="beneficiary-heading"><label class="field-label">受益人 {{ index + 1 }}</label><button class="text-button remove-button" :disabled="!editable" @click="removeBeneficiary(index)">移除</button></view>
            <picker :range="beneficiaryLabels" :value="beneficiaryIndex(row.promoterId)" :disabled="!editable || !candidates.length" @change="event => selectBeneficiary(index, event)"><view class="input picker-value"><text>{{ beneficiaryName(row) }}</text><text>›</text></view></picker>
            <view class="amount-field"><input class="input" v-model="row.amountPerOrder" type="digit" :disabled="!editable" :placeholder="'最多可填 ' + maxForBeneficiary(index) + ' 元'" @input="clearFeedback" /><text>元／单</text></view>
            <view class="remaining-note" :class="{ 'error-text': remainderUnits !== null && remainderUnits < 0 }">剩余可分配 {{ remainderText }} 元／单</view>
          </view>
          <view v-if="!candidates.length" class="pricing-note">暂无可选的正常团队成员。</view>
          <button v-if="form.beneficiaries.length < maxBeneficiaries" class="btn secondary" :disabled="!editable || !candidates.length" @click="addBeneficiary">添加受益人</button>
        </template>
      </view>
      <view class="card summary-card">
        <view class="section-title">每单金额核对</view>
        <view class="row"><text>总佣金</text><text class="money">{{ formatPerOrder(form.totalPerOrder) }} 元</text></view>
        <view class="row"><text>平台服务费（10%）</text><text class="money">{{ feeText }} 元</text></view>
        <view class="row"><text>本人</text><text class="money">{{ formatPerOrder(form.ownerPerOrder) }} 元</text></view>
        <view class="row"><text>其他受益人合计</text><text class="money">{{ allocatedText }} 元</text></view>
        <view class="row"><text>剩余未分配</text><text class="money" :class="{ 'error-text': remainderUnits !== null && remainderUnits < 0 }">{{ remainderText }} 元</text></view>
        <view class="pricing-note">剩余未分配 = 总佣金 − 平台服务费 − 本人金额 − 其他受益人金额。服务费精确保留 4 位小数；不足 0.001 元的余额保留在未分配金额中。</view>
        <view v-if="detail.updatedAt" class="update-time">上次保存 {{ detail.updatedAt.replace('T', ' ').slice(0, 16) }}</view>
        <view v-if="formError" class="error-text feedback">{{ formError }}</view><view v-if="savedMessage" class="success-text feedback">{{ savedMessage }}</view>
        <button class="btn" :disabled="!editable" :loading="saving" @click="save">{{ saving ? '正在保存' : '保存客户分佣' }}</button>
        <view v-if="!detail.canEdit" class="pricing-note">当前分佣方案只可查看。</view>
      </view>
    </template>
  </view>
</template>
<script>
import { appShareMixin } from '@/utils/share'
export default { mixins: [appShareMixin] }
</script>
<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { meApi, pricingApi } from '@/api'
import { navigateTo } from '@/utils/nav.mjs'
import { amountToMills, formatPerOrder, millsToAmount, validatePricing, platformFee, pricingRemainder, unitsToAmount } from '@/utils/pricing.mjs'
const me = ref({})
const candidates = ref([])
const detail = ref(null)
const loading = ref(true)
const saving = ref(false)
const loadError = ref('')
const formError = ref('')
const savedMessage = ref('')
const distribute = ref(false)
const form = reactive({ totalPerOrder: '', ownerPerOrder: '', beneficiaries: [] })
let customerId = ''
let contractId = ''
let rowKey = 0
let requestSequence = 0
const canPrice = computed(() => me.value.positionCode === 'P4' && Number(me.value.status) === 1)
const editable = computed(() => canPrice.value && detail.value?.canEdit && !saving.value)
const maxBeneficiaries = computed(() => contractId ? 8 : 2)
const beneficiaryLabels = computed(() => ['请选择具体受益人', ...candidates.value.map(member => `${member.name || '未命名伙伴'} · ${member.positionCode}${member.phoneTail ? ' · 尾号' + member.phoneTail : ''}（${member.id}）`)])
const activeBeneficiaries = computed(() => distribute.value ? form.beneficiaries : [])
const allocatedMills = computed(() => {
  let sum = 0
  for (const row of activeBeneficiaries.value) { const n = amountToMills(row.amountPerOrder); if (n === null) return null; sum += n }
  return Number.isSafeInteger(sum) ? sum : null
})
const allocatedText = computed(() => allocatedMills.value === null ? '—' : millsToAmount(allocatedMills.value))
const feeText = computed(() => platformFee(form.totalPerOrder))
const remainderUnits = computed(() => pricingRemainder(form.totalPerOrder, form.ownerPerOrder, activeBeneficiaries.value))
const remainderText = computed(() => unitsToAmount(remainderUnits.value))
const maxForOwner = computed(() => maxInput(pricingRemainder(form.totalPerOrder, '0', activeBeneficiaries.value)))
function maxInput(units) { return units === null ? '—' : millsToAmount(Math.max(0, Math.floor(units / 10))) }
function maxForBeneficiary(index) { return maxInput(pricingRemainder(form.totalPerOrder, form.ownerPerOrder, activeBeneficiaries.value.filter((_, i) => i !== index))) }
onLoad(query => { customerId = String(query.customerId || ''); contractId = String(query.contractId || ''); load() })
onUnload(() => { requestSequence += 1 })
async function load() {
  const sequence = ++requestSequence
  loading.value = true
  loadError.value = ''
  detail.value = null
  try {
    const current = await meApi.me()
    if (sequence !== requestSequence) return
    me.value = current
    if (!canPrice.value) return
    if (!/^[1-9]\d*$/.test(contractId || customerId)) throw new Error('请从客户分佣列表选择合同或客户')
    const [value, members] = await Promise.all([contractId ? pricingApi.contract(contractId) : pricingApi.customer(customerId), pricingApi.beneficiaries()])
    if (sequence !== requestSequence) return
    candidates.value = members
    applyDetail(value)
  } catch (error) { if (sequence === requestSequence) loadError.value = error.message || '分佣资料读取失败，请重试' }
  finally { if (sequence === requestSequence) loading.value = false }
}
function applyDetail(value) {
  detail.value = value
  form.totalPerOrder = value.totalPerOrder == null ? '' : formatPerOrder(value.totalPerOrder)
  form.ownerPerOrder = value.ownerPerOrder == null ? '' : formatPerOrder(value.ownerPerOrder)
  form.beneficiaries = (value.beneficiaries || []).map(row => ({ ...row, key: ++rowKey, amountPerOrder: formatPerOrder(row.amountPerOrder) }))
  distribute.value = form.beneficiaries.length > 0
}
function clearFeedback() { formError.value = ''; savedMessage.value = '' }
function addBeneficiary() { if (!editable.value || form.beneficiaries.length >= maxBeneficiaries.value) return; form.beneficiaries.push({ key: ++rowKey, promoterId: '', amountPerOrder: '' }); clearFeedback() }
function removeBeneficiary(index) { if (!editable.value) return; form.beneficiaries.splice(index, 1); if (!form.beneficiaries.length) distribute.value = false; clearFeedback() }
function toggleDistribution(event) { if (!editable.value) return; distribute.value = event.detail.value; if (distribute.value && !form.beneficiaries.length) addBeneficiary(); clearFeedback() }
function beneficiaryIndex(id) { return candidates.value.findIndex(member => String(member.id) === String(id)) + 1 }
function beneficiaryName(row) { const member = candidates.value.find(item => String(item.id) === String(row.promoterId)); return member ? `${member.name || '未命名伙伴'} · ${member.positionCode}${member.phoneTail ? ' · 尾号' + member.phoneTail : ''}` : row.promoterName ? `${row.promoterName} · 请确认成员状态` : '请选择具体受益人' }
function selectBeneficiary(index, event) { if (!editable.value) return; const member = candidates.value[Number(event.detail.value) - 1]; Object.assign(form.beneficiaries[index], { promoterId: member?.id || '', promoterName: member?.name || '', positionCode: member?.positionCode || '' }); clearFeedback() }
async function save() {
  if (!editable.value) return
  clearFeedback()
  formError.value = validatePricing(form.totalPerOrder, form.ownerPerOrder, activeBeneficiaries.value, maxBeneficiaries.value)
  if (formError.value) return
  saving.value = true
  const sequence = requestSequence
  try {
    const payload = { totalPerOrder: formatPerOrder(form.totalPerOrder), ownerPerOrder: formatPerOrder(form.ownerPerOrder), beneficiaries: activeBeneficiaries.value.map(row => ({ promoterId: row.promoterId, amountPerOrder: formatPerOrder(row.amountPerOrder) })) }
    const result = contractId ? await pricingApi.saveContract(contractId, payload) : await pricingApi.save(customerId, payload)
    if (sequence !== requestSequence) return
    applyDetail(result)
    savedMessage.value = '客户分佣已保存。'
    uni.showToast({ title: '分佣已保存', icon: 'success' })
  } catch (error) { if (sequence === requestSequence) formError.value = error.message || '保存失败，已保留填写内容，请重试' }
  finally { saving.value = false }
}
</script>
<style scoped>
.pricing-page { padding-bottom: 40rpx; }
.introduction .title { font-size: 36rpx; }
.pricing-note { margin-top: 12rpx; color: #56627d; font-size: 25rpx; line-height: 1.7; }
.status-panel { color: #56627d; text-align: center; }
.field-label { display: block; margin-top: 28rpx; font-size: 29rpx; font-weight: 600; }
.amount-field { position: relative; }
.amount-field .input { padding-right: 124rpx; font-variant-numeric: tabular-nums; }
.amount-field > text { position: absolute; top: 0; right: 22rpx; color: #56627d; font-size: 24rpx; line-height: 88rpx; pointer-events: none; }
.picker-value { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; line-height: 1.5; padding-top: 20rpx; padding-bottom: 20rpx; }
.form-heading { min-height: 0; padding-top: 0; padding-bottom: 14rpx; }
.beneficiary-row { margin-top: 24rpx; padding-top: 24rpx; border-top: 1rpx solid var(--park-line); }
.beneficiary-heading { display: flex; justify-content: space-between; align-items: center; }
.beneficiary-heading .field-label { margin: 0; }
.text-button { margin: 0; padding: 8rpx 0; background: transparent; font-size: 24rpx; line-height: 48rpx; }
.remove-button,.error-text { color: #a93145; }
.error-text { font-size: 25rpx; line-height: 1.6; }
.success-text { color: #116b56; font-size: 25rpx; }
.feedback,.fee-line { margin-top: 24rpx; }
.fee-line { color: #3152a4; gap: 16rpx; }
.remaining-note { margin-top: 12rpx; color: #3152a4; font-size: 24rpx; }
.remaining-note.error-text { color: #a93145; }
.update-time { margin-top: 12rpx; color: #56627d; font-size: 22rpx; }
.summary-card .row { gap: 16rpx; }
.summary-card .money { text-align: right; word-break: break-all; }
</style>