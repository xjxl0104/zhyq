<template>
  <view class="page-wrap pricing-page">
    <view class="card introduction">
      <view class="title">客户定价与分佣</view>
      <view class="pricing-note">由 P4 按客户／品牌设置每单金额。P1–P4 均无固定金额或比例，称号不决定收益。</view>
    </view>
    <view v-if="loading" class="card status-panel">正在加载定价资料…</view>
    <view v-else-if="loadError" class="card">
      <view class="error-text" role="alert">{{ loadError }}</view>
      <button class="btn secondary" @click="load">重新加载</button>
    </view>
    <view v-else-if="!canPrice" class="card">
      <view class="section-title">当前账号无定价权限</view>
      <view class="pricing-note">只有正常状态的 P4 可设置客户定价和分佣。其他称号的金额由负责该客户的 P4 配置。</view>
    </view>
    <view v-else-if="!customers.length" class="card">
      <view class="section-title">暂无可定价客户</view>
      <view class="pricing-note">您和邀请体系内成员推荐的客户会显示在这里。</view>
      <button class="btn secondary" @click="uni.navigateTo({ url: '/pages/referral/index' })">推荐客户</button>
    </view>
    <template v-else>
      <view class="card">
        <label class="field-label">客户／品牌</label>
        <picker :range="customerLabels" :value="selectedIndex" :disabled="saving || detailLoading" @change="changeCustomer">
          <view class="input picker-value"><text>{{ selectedCustomer?.name || '请选择需要定价的客户' }}</text><text class="picker-arrow">›</text></view>
        </picker>
        <view v-if="selectedCustomer" class="pricing-note">推荐人：{{ selectedCustomer.referrerName || '未记录' }}<text v-if="selectedCustomer.grade"> · 客户评级 {{ selectedCustomer.grade }}</text></view>
      </view>
      <view v-if="detailLoading" class="card status-panel">正在读取客户定价…</view>
      <view v-else-if="detailError" class="card">
        <view class="error-text" role="alert">{{ detailError }}</view>
        <button class="btn secondary" @click="loadCustomer(selectedCustomerId)">重新读取</button>
      </view>
      <template v-else-if="detail">
        <view class="card">
          <view class="row form-heading"><text class="section-title">每单金额</text><text class="tag" :class="detail.configured ? 'ok' : 'warn'">{{ detail.configured ? '已配置' : '待定价' }}</text></view>
          <view class="pricing-note">金额单位：元／单，最多 3 位小数（0.001 元 = 0.1 分）。没有分配金额时请明确填写 0。</view>
          <label class="field-label" for="total-price">总佣金单价</label>
          <view class="amount-field"><input id="total-price" class="input" v-model="form.totalPerOrder" type="digit" :disabled="!editable" placeholder="填写协商后的总额" @input="clearFeedback" /><text>元／单</text></view>
          <label class="field-label" for="owner-price">P4 本人单价 · {{ detail.ownerName || me.name || '本人' }}</label>
          <view class="amount-field"><input id="owner-price" class="input" v-model="form.ownerPerOrder" type="digit" :disabled="!editable" placeholder="填写本人金额，可为 0" @input="clearFeedback" /><text>元／单</text></view>
        </view>
        <view class="card">
          <view class="row form-heading"><text class="section-title">分配给其他人员</text><switch :checked="distribute" :disabled="!editable" color="#2b4fd6" @change="toggleDistribution" aria-label="分配给其他人员" /></view>
          <view class="pricing-note">可不分配；开启后最多选择 2 位具体受益人，并分别填写金额。</view>
          <template v-if="distribute">
            <view v-for="(row, index) in form.beneficiaries" :key="row.key" class="beneficiary-row">
              <view class="beneficiary-heading"><label class="field-label">受益人 {{ index + 1 }}</label><button class="text-button remove-button" :disabled="!editable" @click="removeBeneficiary(index)">移除</button></view>
              <picker :range="beneficiaryLabels" :value="beneficiaryIndex(row.promoterId)" :disabled="!editable || !candidates.length" @change="event => selectBeneficiary(index, event)">
                <view class="input picker-value"><text :class="{ placeholder: !row.promoterId }">{{ beneficiaryName(row) }}</text><text class="picker-arrow">›</text></view>
              </picker>
              <view class="amount-field"><input class="input" v-model="row.amountPerOrder" type="digit" :disabled="!editable" :placeholder="'受益人 ' + (index + 1) + ' 的单价'" :aria-label="'受益人 ' + (index + 1) + ' 的每单金额'" @input="clearFeedback" /><text>元／单</text></view>
            </view>
            <view v-if="!candidates.length" class="pricing-note">暂无可选的正常团队成员，可先关闭分配并保存本人金额。</view>
            <button v-if="form.beneficiaries.length < 2" class="btn secondary" :disabled="!editable || !candidates.length" @click="addBeneficiary">添加受益人</button>
          </template>
        </view>
        <view class="card summary-card">
          <view class="section-title">每单金额核对</view>
          <view class="row"><text>总佣金</text><text class="money">{{ formatPerOrder(form.totalPerOrder) }} 元</text></view>
          <view class="row"><text>P4 本人</text><text class="money">{{ formatPerOrder(form.ownerPerOrder) }} 元</text></view>
          <view class="row"><text>其他受益人合计</text><text class="money">{{ allocatedText }} 元</text></view>
          <view class="row"><text>公司保留</text><text class="money" :class="{ 'error-text': companyMills !== null && companyMills < 0 }">{{ companyText }} 元</text></view>
          <view class="pricing-note">公司保留 = 总佣金 − P4 本人金额 − 其他受益人金额。保存只影响之后产生的新计佣记录。</view>
          <view v-if="detail.updatedAt" class="update-time">上次保存 {{ detail.updatedAt.replace('T', ' ').slice(0, 16) }}</view>
          <view v-if="formError" class="error-text feedback" role="alert">{{ formError }}</view>
          <view v-if="savedMessage" class="success-text feedback" role="status">{{ savedMessage }}</view>
          <button class="btn" :disabled="!editable" :loading="saving" @click="save">{{ saving ? '正在保存' : '保存客户定价' }}</button>
          <view v-if="!detail.canEdit" class="pricing-note">当前客户定价不可编辑，请联系负责该客户的 P4。</view>
        </view>
      </template>
    </template>
  </view>
</template>

<script>
import { appShareMixin } from '@/utils/share'

export default { mixins: [appShareMixin] }
</script>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { meApi, pricingApi } from '@/api'
import { amountToMills, formatPerOrder, millsToAmount, validatePricing } from '@/utils/pricing.mjs'

const me = ref({})
const customers = ref([])
const candidates = ref([])
const selectedCustomerId = ref('')
const detail = ref(null)
const loading = ref(true)
const detailLoading = ref(false)
const saving = ref(false)
const loadError = ref('')
const detailError = ref('')
const formError = ref('')
const savedMessage = ref('')
const distribute = ref(false)
const form = reactive({ totalPerOrder: '', ownerPerOrder: '', beneficiaries: [] })
let requestedCustomerId = ''
let rowKey = 0
let savedSnapshot = ''

const canPrice = computed(() => me.value.positionCode === 'P4' && Number(me.value.status) === 1)
const editable = computed(() => canPrice.value && detail.value?.canEdit && !saving.value)
const selectedIndex = computed(() => Math.max(0, customers.value.findIndex(customer => String(customer.id) === String(selectedCustomerId.value))))
const selectedCustomer = computed(() => customers.value.find(customer => String(customer.id) === String(selectedCustomerId.value)))
const customerLabels = computed(() => customers.value.map(customer => `${customer.name} · ${customer.configured ? '已配置' : '待定价'}（${customer.id}）`))
const beneficiaryLabels = computed(() => ['请选择具体受益人', ...candidates.value.map(member => `${member.name || '未命名伙伴'} · ${member.positionCode}（${member.id}）`)])
const activeBeneficiaries = computed(() => distribute.value ? form.beneficiaries : [])
const allocatedMills = computed(() => {
  let sum = 0
  for (const beneficiary of activeBeneficiaries.value) {
    const amount = amountToMills(beneficiary.amountPerOrder)
    if (amount === null) return null
    sum += amount
  }
  return Number.isSafeInteger(sum) ? sum : null
})
const companyMills = computed(() => {
  const total = amountToMills(form.totalPerOrder)
  const owner = amountToMills(form.ownerPerOrder)
  return total === null || owner === null || allocatedMills.value === null ? null : total - owner - allocatedMills.value
})
const allocatedText = computed(() => allocatedMills.value === null ? '—' : millsToAmount(allocatedMills.value))
const companyText = computed(() => companyMills.value === null ? '—' : millsToAmount(companyMills.value))

onLoad(query => { requestedCustomerId = query.customerId || ''; load() })
function snapshot() {
  return JSON.stringify({ total: form.totalPerOrder, owner: form.ownerPerOrder, distribute: distribute.value,
    beneficiaries: activeBeneficiaries.value.map(({ promoterId, amountPerOrder }) => ({ promoterId, amountPerOrder })) })
}
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    me.value = await meApi.me()
    if (!canPrice.value) return
    ;[customers.value, candidates.value] = await Promise.all([pricingApi.customers(), pricingApi.beneficiaries()])
    if (requestedCustomerId) {
      const found = customers.value.find(customer => String(customer.id) === String(requestedCustomerId))
      if (found) await loadCustomer(found.id)
      else loadError.value = '该客户不在您的定价范围内，请重新加载并选择客户'
      requestedCustomerId = ''
    } else if (customers.value.length) {
      await loadCustomer(customers.value[0].id)
    }
  } catch (error) {
    loadError.value = error.message || '定价资料加载失败，请重试'
  } finally {
    loading.value = false
  }
}
async function changeCustomer(event) {
  const customer = customers.value[Number(event.detail.value)]
  if (!customer || String(customer.id) === String(selectedCustomerId.value)) return
  if (detail.value && snapshot() !== savedSnapshot) {
    const confirmed = await new Promise(resolve => uni.showModal({ title: '切换客户', content: '当前填写的金额尚未保存，切换后将放弃这些修改。', confirmText: '继续切换', cancelText: '继续填写', success: result => resolve(result.confirm), fail: () => resolve(false) }))
    if (!confirmed) return
  }
  await loadCustomer(customer.id)
}
async function loadCustomer(customerId) {
  selectedCustomerId.value = customerId
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  clearFeedback()
  try { applyDetail(await pricingApi.customer(customerId)) }
  catch (error) { detailError.value = error.message || '客户定价读取失败，请重试' }
  finally { detailLoading.value = false }
}
function applyDetail(value) {
  detail.value = value
  form.totalPerOrder = value.totalPerOrder == null ? '' : formatPerOrder(value.totalPerOrder)
  form.ownerPerOrder = value.ownerPerOrder == null ? '' : formatPerOrder(value.ownerPerOrder)
  form.beneficiaries = (value.beneficiaries || []).map(row => ({ ...row, key: ++rowKey, amountPerOrder: formatPerOrder(row.amountPerOrder) }))
  distribute.value = form.beneficiaries.length > 0
  savedSnapshot = snapshot()
}
function clearFeedback() { formError.value = ''; savedMessage.value = '' }
function addBeneficiary() {
  if (!editable.value || form.beneficiaries.length >= 2) return
  form.beneficiaries.push({ key: ++rowKey, promoterId: '', promoterName: '', amountPerOrder: '' })
  clearFeedback()
}
function removeBeneficiary(index) {
  form.beneficiaries.splice(index, 1)
  if (!form.beneficiaries.length) distribute.value = false
  clearFeedback()
}
function toggleDistribution(event) {
  distribute.value = event.detail.value
  if (distribute.value && !form.beneficiaries.length) addBeneficiary()
  clearFeedback()
}
function beneficiaryIndex(id) { return candidates.value.findIndex(member => String(member.id) === String(id)) + 1 }
function beneficiaryName(row) {
  const member = candidates.value.find(candidate => String(candidate.id) === String(row.promoterId))
  return member ? `${member.name || '未命名伙伴'} · ${member.positionCode}` : row.promoterName ? `${row.promoterName} · 请确认成员状态` : '请选择具体受益人'
}
function selectBeneficiary(index, event) {
  const member = candidates.value[Number(event.detail.value) - 1]
  Object.assign(form.beneficiaries[index], { promoterId: member?.id || '', promoterName: member?.name || '', positionCode: member?.positionCode || '' })
  clearFeedback()
}
async function save() {
  if (!editable.value) return
  clearFeedback()
  formError.value = validatePricing(form.totalPerOrder, form.ownerPerOrder, activeBeneficiaries.value)
  if (formError.value) return
  saving.value = true
  try {
    const result = await pricingApi.save(selectedCustomerId.value, {
      totalPerOrder: formatPerOrder(form.totalPerOrder), ownerPerOrder: formatPerOrder(form.ownerPerOrder),
      beneficiaries: activeBeneficiaries.value.map(row => ({ promoterId: row.promoterId, amountPerOrder: formatPerOrder(row.amountPerOrder) }))
    })
    applyDetail(result)
    if (selectedCustomer.value) Object.assign(selectedCustomer.value, { configured: true, totalPerOrder: result.totalPerOrder })
    savedMessage.value = '客户定价已保存，新计佣记录将使用本次设置。'
    uni.showToast({ title: '定价已保存', icon: 'success' })
  } catch (error) {
    formError.value = error.message || '保存失败，已保留填写内容，请重试'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.pricing-page { padding-bottom: 40rpx; }
.pricing-page .card { border: 0; box-shadow: 0 8rpx 24rpx rgba(29, 42, 87, .055); }
.pricing-page .card::after { display: none; }
.introduction .title { font-size: 36rpx; }
.pricing-note { margin-top: 12rpx; color: #56627d; font-size: 24rpx; line-height: 1.7; }
.status-panel { color: #56627d; text-align: center; }
.field-label { display: block; margin-top: 28rpx; color: var(--park-text); font-size: 26rpx; font-weight: 600; }
.field-label:first-child { margin-top: 0; }
.amount-field { position: relative; }
.amount-field .input { padding-right: 124rpx; font-variant-numeric: tabular-nums; }
.amount-field > text { position: absolute; top: 0; right: 22rpx; color: #56627d; font-size: 24rpx; line-height: 88rpx; pointer-events: none; }
.picker-value { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; line-height: 1.5; padding-top: 20rpx; padding-bottom: 20rpx; }
.picker-value > text:first-child { min-width: 0; word-break: break-all; }
.picker-arrow { color: #56627d; font-size: 34rpx; flex-shrink: 0; }
.placeholder { color: #56627d; }
.form-heading { min-height: 0; padding-top: 0; padding-bottom: 14rpx; }
.beneficiary-row { margin-top: 24rpx; padding-top: 24rpx; border-top: 1rpx solid var(--park-line); }
.beneficiary-heading { display: flex; justify-content: space-between; align-items: center; }
.beneficiary-heading .field-label { margin: 0; }
.text-button { min-width: 88rpx; margin: 0; padding: 8rpx 0; color: var(--park-blue); background: transparent; font-size: 24rpx; line-height: 48rpx; }
.text-button[disabled] { color: #66718b; opacity: .65; }
.remove-button { color: #a93145; }
.error-text { color: #a93145; font-size: 25rpx; line-height: 1.6; }
.success-text { color: #116b56; font-size: 25rpx; line-height: 1.6; }
.feedback { margin-top: 24rpx; }
.update-time { margin-top: 12rpx; color: #56627d; font-size: 22rpx; }
.summary-card .row { gap: 16rpx; }
.summary-card .money { text-align: right; word-break: break-all; }
.pricing-page button:focus-visible { outline: 3rpx solid var(--park-blue); outline-offset: 4rpx; }
</style>
