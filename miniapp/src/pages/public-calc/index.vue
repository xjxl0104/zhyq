<template>
  <view class="page-wrap calc-page">
    <view class="card calc-hero">
      <view class="calc-tag">智慧园区官方严选云仓 · 0 中介加价</view>
      <view class="calc-title">30 秒云仓代发底价计算器</view>
      <view class="calc-sub">基于园区认证云仓备案标准价卡实时测算 · 支持先拿 1～2 个爆款试发 7 天</view>
    </view>

    <view class="card">
      <view class="sec-title">1. 选择您的发货主营类目</view>
      <view class="cat-grid">
        <button
          v-for="cat in CATEGORIES"
          :key="cat"
          class="cat-btn"
          :class="{ active: form.category === cat }"
          @click="selectCategory(cat)"
        >
          {{ cat }}
        </button>
      </view>

      <view class="sec-title" style="margin-top: 28rpx">
        2. 预估日均发货单量：<text class="brand-num">{{ form.dailyOrders }} 单/天</text>
      </view>
      <slider
        :value="form.dailyOrders"
        min="100"
        max="5000"
        step="100"
        activeColor="#2563eb"
        @change="onDailyChange"
      />

      <view class="sec-title" style="margin-top: 20rpx">3. 包裹平均重量（kg）</view>
      <input
        class="calc-inp"
        type="digit"
        :value="form.avgWeightKg"
        placeholder="如 0.45"
        @input="onWeightInput"
      />
    </view>

    <view class="card result-card">
      <view class="res-label">园区严选 {{ quote.matchedWarehouseCount || 3 }} 家匹配云仓 · 打包+快递一口价区间</view>
      <view class="res-price">
        ¥ {{ quote.lowPrice }} ~ ¥ {{ quote.highPrice }}
        <text class="res-unit">/ 单（含基础耗材）</text>
      </view>
      <view class="res-save">
        📊 对比自租仓库雇 {{ quote.packersEquivalent }} 名打包工，预计每年为您净省约
        <text class="save-amt">¥ {{ formatNum(quote.estimatedYearlySaving) }}</text>，大促 24h 揽收率 ≥99.5%！
      </view>
    </view>

    <view v-if="submitted" class="card done-card">
      <view class="done-title">🎉 专属报价单与 300 单免打包费试发券已登记</view>
      <view class="done-sub">园区云仓供应链顾问将在工作时间 15 分钟内联系您发送分省明细价表，请留意来电。</view>
    </view>

    <view v-else class="card">
      <view class="sec-title">🎁 解锁分省详细价目表 + 领 300 单免打包费试发券</view>
      <input
        class="calc-inp"
        v-model="form.shopName"
        maxlength="100"
        placeholder="您的网店名或公司简称（必填，如：杭州某某女装店）"
      />
      <input
        class="calc-inp"
        v-model="form.contact"
        maxlength="40"
        placeholder="您的称呼（选填，如：林总）"
      />
      <input
        class="calc-inp"
        type="number"
        v-model="form.phone"
        maxlength="11"
        placeholder="接收报价单的 11 位手机号（必填）"
      />
      <view v-if="errMsg" class="err-txt">{{ errMsg }}</view>
      <button class="submit-btn" :disabled="submitting" @click="submitLead">
        {{ submitting ? '正在生成专属报价单…' : '一键获取详细报价单与试发券' }}
      </button>
      <view class="safe-tip">🔒 园区官方直营保密，绝不向外部中介泄露号码</view>
    </view>
  </view>
</template>

<script>
import { appShareMixin } from '@/utils/share'
export default { mixins: [appShareMixin] }
</script>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { publicCalcRequest } from '@/utils/request'

const CATEGORIES = ['女装服饰', '美妆个护', '休闲食品', '鞋靴箱包']
const form = reactive({
  projectId: 1,
  category: '女装服饰',
  dailyOrders: 800,
  avgWeightKg: '0.45',
  shopName: '',
  contact: '',
  phone: ''
})
const quote = reactive({
  lowPrice: '2.20',
  highPrice: '2.52',
  matchedWarehouseCount: 3,
  packersEquivalent: 3,
  estimatedYearlySaving: 68400
})
const submitting = ref(false)
const submitted = ref(false)
const errMsg = ref('')

function formatNum(n) {
  return Number(n || 0).toLocaleString('zh-CN')
}

async function refreshEstimate() {
  try {
    const res = await publicCalcRequest('/estimate', {
      projectId: form.projectId,
      category: form.category,
      dailyOrders: Number(form.dailyOrders) || 500,
      avgWeightKg: Number(form.avgWeightKg) || 0.5
    })
    if (res) Object.assign(quote, res)
  } catch (_) { /* 离线或限流时保留前端平滑估算兜底 */ }
}

function selectCategory(cat) {
  form.category = cat
  refreshEstimate()
}
function onDailyChange(e) {
  form.dailyOrders = Number(e.detail.value) || 500
  refreshEstimate()
}
function onWeightInput(e) {
  form.avgWeightKg = e.detail.value
  refreshEstimate()
}

async function submitLead() {
  if (submitting.value) return
  errMsg.value = ''
  if (!form.shopName.trim()) { errMsg.value = '请填写您的网店名或公司简称'; return }
  if (!/^1[3-9]\d{9}$/.test(form.phone.trim())) { errMsg.value = '请填写有效的 11 位联系手机号'; return }
  submitting.value = true
  try {
    const res = await publicCalcRequest('/lead', {
      projectId: form.projectId,
      category: form.category,
      dailyOrders: Number(form.dailyOrders) || 500,
      avgWeightKg: Number(form.avgWeightKg) || 0.5,
      shopName: form.shopName.trim(),
      contact: form.contact.trim(),
      phone: form.phone.trim()
    })
    if (res) Object.assign(quote, res)
    submitted.value = true
  } catch (e) {
    errMsg.value = e?.message || '提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

onLoad((q) => {
  if (q?.projectId) form.projectId = Number(q.projectId) || 1
  refreshEstimate()
})
</script>

<style scoped>
.calc-page { padding-bottom: 48rpx; }
.calc-hero { background: linear-gradient(135deg, #0f172a, #1d4ed8); color: #fff; }
.calc-tag { display: inline-block; background: rgba(251, 191, 36, 0.22); color: #fde68a; font-size: 22rpx; padding: 4rpx 14rpx; border-radius: 999rpx; }
.calc-title { font-size: 40rpx; font-weight: 800; margin-top: 14rpx; }
.calc-sub { font-size: 24rpx; color: #dbeafe; margin-top: 8rpx; line-height: 1.5; }
.sec-title { font-size: 28rpx; font-weight: 700; color: #0f172a; margin-bottom: 14rpx; }
.brand-num { color: #2563eb; }
.cat-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16rpx; }
.cat-btn { background: #f8fafc; border: 2rpx solid #cbd5e1; border-radius: 14rpx; font-size: 26rpx; color: #334155; padding: 14rpx 0; }
.cat-btn.active { background: #eff6ff; border-color: #2563eb; color: #2563eb; font-weight: 700; }
.calc-inp { border: 2rpx solid #cbd5e1; border-radius: 12rpx; padding: 16rpx 20rpx; font-size: 27rpx; margin-bottom: 16rpx; background: #fff; }
.result-card { background: linear-gradient(135deg, #0f172a, #1e3a8a); color: #fff; }
.res-label { font-size: 24rpx; color: #cbd5e1; }
.res-price { font-size: 50rpx; font-weight: 800; color: #fbbf24; margin: 10rpx 0; }
.res-unit { font-size: 24rpx; color: #e2e8f0; font-weight: 400; }
.res-save { font-size: 24rpx; color: #93c5fd; line-height: 1.6; }
.save-amt { color: #4ade80; font-weight: 800; font-size: 28rpx; }
.submit-btn { background: #2563eb; color: #fff; border-radius: 14rpx; font-size: 30rpx; font-weight: 700; padding: 18rpx 0; margin-top: 8rpx; }
.safe-tip { text-align: center; font-size: 22rpx; color: #94a3b8; margin-top: 14rpx; }
.err-txt { color: #dc2626; font-size: 24rpx; margin-bottom: 10rpx; }
.done-card { background: #f0fdf4; border: 2rpx solid #86efac; }
.done-title { font-size: 30rpx; font-weight: 700; color: #166534; }
.done-sub { font-size: 25rpx; color: #15803d; margin-top: 10rpx; line-height: 1.5; }
</style>
