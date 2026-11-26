<template>
  <div class="calc-shell">
    <div class="calc-container">
      <header class="calc-hero">
        <span class="calc-badge">广东主战区 · 园区严选云仓直连 · 0 中介加价</span>
        <h1 class="calc-title">30 秒云仓代发底价计算器</h1>
        <p class="calc-sub">基于园区认证云仓备案标准价卡实时测算 · 支持先拿 1～2 个爆款试发 7 天</p>
      </header>

      <section class="calc-card">
        <div class="sec-title">1. 选择发货主营类目</div>
        <div class="cat-grid">
          <button
            v-for="cat in CATEGORIES"
            :key="cat"
            type="button"
            class="cat-btn"
            :class="{ active: form.category === cat }"
            @click="selectCategory(cat)"
          >
            {{ cat }}
          </button>
        </div>

        <div class="sec-title" style="margin-top: 18px">
          2. 预估日均发货单量：<b class="brand-num">{{ form.dailyOrders }} 单/天</b>
        </div>
        <input
          v-model.number="form.dailyOrders"
          class="range-inp"
          type="range"
          min="100"
          max="5000"
          step="100"
          @change="refreshEstimate"
          data-testid="calc-daily-slider"
        />
        <div class="range-marks">
          <span>100 单/天</span>
          <span>1500 单/天</span>
          <span>5000+ 单/天</span>
        </div>

        <label class="field" style="margin-top: 14px">
          <span class="sec-title">3. 包裹平均重量（kg）</span>
          <input
            v-model="form.avgWeightKg"
            class="txt-inp"
            type="number"
            step="0.05"
            min="0.05"
            max="50"
            placeholder="例如：0.45"
            @change="refreshEstimate"
            data-testid="calc-weight-input"
          />
        </label>
      </section>

      <section class="result-card" data-testid="calc-quote-card">
        <div class="res-label">园区严选 {{ quote.matchedWarehouseCount || 3 }} 家匹配云仓 · 打包+快递一口价区间</div>
        <div class="res-price">
          ¥ {{ quote.lowPrice }} ~ ¥ {{ quote.highPrice }}
          <span class="res-unit">/ 单（含基础耗材）</span>
        </div>
        <div class="res-save">
          对比自租仓库雇 <b>{{ quote.packersEquivalent }}</b> 名打包工，预计每年净省约
          <span class="save-amt">¥ {{ formatNum(quote.estimatedYearlySaving) }}</span>，大促 24h 揽收率 ≥99.5%
        </div>
      </section>

      <section v-if="submitted" class="calc-card done-card" data-testid="calc-lead-done">
        <div class="done-title">🎉 专属分省报价单与 300 单免打包费试发券已登记</div>
        <p class="done-sub">
          园区供应链顾问将在工作时间 15 分钟内联系您发送广东分仓明细价表，请留意来电。
        </p>
      </section>

      <form v-else class="calc-card" @submit.prevent="submitLead" data-testid="calc-lead-form">
        <div class="sec-title">🎁 解锁分省详细价目表 + 领 300 单免打包费试发券</div>
        <input
          v-model="form.shopName"
          class="txt-inp"
          maxlength="100"
          placeholder="您的网店名或公司简称（必填，如：广州十三行某某女装）"
          data-testid="calc-shop-name"
        />
        <div class="grid-two">
          <input
            v-model="form.contact"
            class="txt-inp"
            maxlength="40"
            placeholder="您的称呼（选填，如：林总）"
            data-testid="calc-contact"
          />
          <input
            v-model="form.phone"
            class="txt-inp"
            type="tel"
            maxlength="11"
            inputmode="numeric"
            placeholder="接收报价单的 11 位手机号（必填）"
            data-testid="calc-phone"
          />
        </div>
        <p v-if="errMsg" class="err-msg">{{ errMsg }}</p>
        <button class="submit-btn" type="submit" :disabled="submitting" data-testid="calc-submit-btn">
          {{ submitting ? '正在生成专属报价单…' : '一键获取详细报价单与试发券' }}
        </button>
        <div class="safe-tip">🔒 园区官方直营保密，直通内部专属公海顾问，绝不向外部中介泄露号码</div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { mktPublicOpenApi } from '@/api/publicMarketing'

const route = useRoute()
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
    const res = await mktPublicOpenApi.calcEstimate({
      projectId: form.projectId,
      category: form.category,
      dailyOrders: Number(form.dailyOrders) || 500,
      avgWeightKg: Number(form.avgWeightKg) || 0.5
    })
    if (res) Object.assign(quote, res)
  } catch (_) {
    // 限流或离线时保留本地平滑估算区间兜底
  }
}

function selectCategory(cat) {
  form.category = cat
  refreshEstimate()
}

async function submitLead() {
  if (submitting.value) return
  errMsg.value = ''
  if (!form.shopName.trim()) {
    errMsg.value = '请填写您的网店名或公司简称'
    return
  }
  if (!/^1[3-9]\d{9}$/.test(form.phone.trim())) {
    errMsg.value = '请填写有效的 11 位联系手机号'
    return
  }
  submitting.value = true
  try {
    const res = await mktPublicOpenApi.calcSubmitLead({
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

onMounted(() => {
  if (route.query?.projectId) {
    form.projectId = Number(route.query.projectId) || 1
  }
  refreshEstimate()
})
</script>

<style scoped>
.calc-shell {
  min-height: 100vh;
  background: linear-gradient(180deg, #eff6ff 0%, #f8fafc 50%, #f1f5f9 100%);
  padding: 20px 14px 44px;
  box-sizing: border-box;
}
.calc-container {
  max-width: 600px;
  margin: 0 auto;
}
.calc-hero {
  background: linear-gradient(135deg, #0f172a 0%, #1d4ed8 100%);
  color: #fff;
  border-radius: 16px;
  padding: 22px 20px;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.16);
}
.calc-badge {
  display: inline-block;
  background: rgba(251, 191, 36, 0.22);
  color: #fde68a;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 999px;
}
.calc-title {
  margin: 10px 0 6px;
  font-size: 24px;
  font-weight: 800;
}
.calc-sub {
  margin: 0;
  font-size: 13px;
  color: #dbeafe;
  line-height: 1.5;
}
.calc-card {
  margin-top: 14px;
  background: #fff;
  border-radius: 16px;
  padding: 20px 18px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
}
.sec-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 10px;
  display: block;
}
.brand-num { color: #2563eb; }
.cat-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}
.cat-btn {
  background: #f8fafc;
  border: 1.5px solid #cbd5e1;
  border-radius: 10px;
  padding: 10px 8px;
  font-size: 14px;
  color: #334155;
  cursor: pointer;
}
.cat-btn.active {
  background: #eff6ff;
  border-color: #2563eb;
  color: #2563eb;
  font-weight: 700;
}
.range-inp { width: 100%; accent-color: #2563eb; cursor: pointer; }
.range-marks {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}
.field { display: block; }
.txt-inp {
  width: 100%;
  box-sizing: border-box;
  border: 1.5px solid #cbd5e1;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 14.5px;
  margin-bottom: 12px;
  background: #fff;
}
.txt-inp:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}
.grid-two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
@media (max-width: 480px) {
  .grid-two { grid-template-columns: 1fr; gap: 0; }
}
.result-card {
  margin-top: 14px;
  background: linear-gradient(135deg, #0f172a 0%, #1e3a8a 100%);
  color: #fff;
  border-radius: 16px;
  padding: 20px 18px;
}
.res-label { font-size: 13px; color: #cbd5e1; }
.res-price {
  font-size: 28px;
  font-weight: 800;
  color: #fbbf24;
  margin: 8px 0;
}
.res-unit { font-size: 13px; color: #e2e8f0; font-weight: 400; }
.res-save { font-size: 13px; color: #93c5fd; line-height: 1.6; }
.save-amt { color: #4ade80; font-weight: 800; font-size: 15px; }
.submit-btn {
  width: 100%;
  border: none;
  border-radius: 12px;
  background: #2563eb;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  padding: 13px 16px;
  cursor: pointer;
}
.submit-btn:disabled { opacity: 0.65; cursor: not-allowed; }
.safe-tip {
  text-align: center;
  font-size: 12px;
  color: #64748b;
  margin-top: 10px;
}
.err-msg { color: #dc2626; font-size: 13px; margin: 0 0 10px; }
.done-card { background: #f0fdf4; border: 1.5px solid #86efac; }
.done-title { font-size: 16px; font-weight: 700; color: #166534; }
.done-sub { margin: 8px 0 0; font-size: 13.5px; color: #15803d; line-height: 1.6; }
</style>
