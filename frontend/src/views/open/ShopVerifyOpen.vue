<template>
  <div class="open-shell">
    <div class="open-container">
      <header class="hero-card">
        <span class="hero-tag">智慧园区云仓供应链 · 商家单量登记回执</span>
        <h1 class="hero-title">10 秒店铺发货量自助登记</h1>
        <p class="hero-sub">用于匹配园区严选云仓阶梯底价与大促揽收保障，无需填写店铺后台密码</p>
      </header>

      <div class="honesty-banner" data-testid="self-report-notice">
        <strong>模式说明：</strong>当前为货主自报模式（未经官方开放平台自动核验），72 小时内有效且仅可提交一次；提交后由园区供应链顾问人工核对后为您匹配阶梯报价。
      </div>

      <div v-if="loading" class="card state-card">
        <div class="state-title">正在读取专属邀请信息…</div>
      </div>

      <div v-else-if="loadError" class="card state-card error-card" data-testid="verify-error-state">
        <div class="state-icon">!</div>
        <div class="state-title">邀请链接不可用</div>
        <p class="state-desc">{{ loadError }}</p>
      </div>

      <div v-else-if="submittedResult" class="card state-card done-card" data-testid="verify-done-state">
        <div class="state-icon ok">✓</div>
        <div class="state-title">已提交货主自报单量</div>
        <p class="state-desc">
          近 30 天发货总量 <b>{{ submittedResult.verified30dOrders }}</b> 单（折合日均约
          <b>{{ submittedResult.verifiedDailyAvg }}</b> 单/天）。园区顾问核对后将为您锁定对应阶梯报价。
        </p>
      </div>

      <div v-else-if="!session.submittable" class="card state-card warn-card" data-testid="verify-readonly-state">
        <div class="state-icon warn">i</div>
        <div class="state-title">
          {{ session.expired ? '该邀请链接已超过 72 小时有效期' : '该邀请链接已完成提交' }}
        </div>
        <p class="state-desc">
          平台：{{ platformLabel(session.platform) }} · 店铺：{{ session.shopName || '待补充' }} · 当前状态：{{ statusLabel(session.status) }}。如需重新填报请联系园区招商顾问重新生成邀请码。
        </p>
      </div>

      <form v-else class="card form-card" @submit.prevent="handleSubmit" data-testid="verify-form">
        <div class="session-meta">
          <div><span class="meta-k">电商平台：</span><b>{{ platformLabel(session.platform) }}</b></div>
          <div><span class="meta-k">关联店铺：</span><b>{{ session.shopName || '园区邀约商户' }}</b></div>
          <div v-if="session.expiresAt"><span class="meta-k">有效截止：</span><span>{{ formatTime(session.expiresAt) }}</span></div>
        </div>

        <label class="field">
          <span class="lb">近 30 天总发货单量（单）<i>*</i></span>
          <input
            v-model="form.orders30d"
            type="number"
            min="1"
            max="10000000"
            inputmode="numeric"
            placeholder="例如：24000（约日均 800 单）"
            data-testid="input-orders30d"
          />
          <span v-if="dailyAvgPreview > 0" class="field-hint">折合日均约 {{ dailyAvgPreview }} 单/天</span>
        </label>

        <div class="grid-two">
          <label class="field">
            <span class="lb">包裹平均重量（kg）</span>
            <input
              v-model="form.avgWeightKg"
              type="number"
              step="0.01"
              min="0.01"
              max="200"
              placeholder="例如：0.45"
              data-testid="input-weight"
            />
          </label>
          <label class="field">
            <span class="lb">近 30 天退货率（%）</span>
            <input
              v-model="form.returnRatePct"
              type="number"
              step="0.1"
              min="0"
              max="100"
              placeholder="例如：12.5"
              data-testid="input-return-rate"
            />
          </label>
        </div>

        <label class="field">
          <span class="lb">主营类目 / 发货备注（选填）</span>
          <textarea
            v-model="form.proofNote"
            rows="3"
            maxlength="200"
            placeholder="例如：女装针织衫为主，聚水潭 ERP 发货，期望下周安排爆款试发"
            data-testid="input-proof-note"
          />
        </label>

        <p v-if="submitError" class="err-msg">{{ submitError }}</p>
        <button class="primary-btn" type="submit" :disabled="submitting" data-testid="btn-submit-verify">
          {{ submitting ? '正在加密提交…' : '确认提交发货量数据' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { mktPublicOpenApi } from '@/api/publicMarketing'

const route = useRoute()
const loading = ref(true)
const loadError = ref('')
const submitting = ref(false)
const submitError = ref('')
const submittedResult = ref(null)
const session = reactive({
  platform: '',
  shopName: '',
  status: '',
  expiresAt: '',
  expired: false,
  submittable: false
})

const form = reactive({
  orders30d: '',
  avgWeightKg: '0.50',
  returnRatePct: '',
  proofNote: ''
})

const dailyAvgPreview = computed(() => {
  const n = Number(form.orders30d)
  return Number.isFinite(n) && n > 0 ? Math.max(1, Math.round(n / 30)) : 0
})

function platformLabel(code) {
  const map = {
    DOUDIAN: '抖音电商（抖店）',
    TAOBAO_1688: '淘宝 / 天猫 / 1688',
    PDD: '拼多多',
    XHS: '小红书电商',
    WECHAT_SHOP: '微信视频号小店'
  }
  return map[code] || code || '电商平台'
}

function statusLabel(st) {
  const map = {
    PENDING: '待填报',
    SELF_REPORTED: '货主已自报（待顾问核对）',
    ADOPTED: '园区已核对采纳',
    OAUTH_VERIFIED: '官方授权已核验'
  }
  return map[st] || st || '-'
}

function formatTime(v) {
  return String(v || '').replace('T', ' ').slice(0, 16)
}

async function loadSession() {
  const token = String(route.params.token || '').trim()
  if (!token) {
    loading.value = false
    loadError.value = '缺少验店邀请令牌参数'
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await mktPublicOpenApi.shopVerifyDetail(token)
    Object.assign(session, res || {})
  } catch (e) {
    loadError.value = e?.message || '验店邀请链接不存在或已失效'
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  if (submitting.value) return
  submitError.value = ''
  const orders = Number(form.orders30d)
  if (!Number.isFinite(orders) || orders <= 0) {
    submitError.value = '请填写大于 0 的近 30 天总发货单量'
    return
  }
  submitting.value = true
  try {
    const token = String(route.params.token || '').trim()
    const res = await mktPublicOpenApi.shopVerifySubmit(token, {
      orders30d: Math.round(orders),
      avgWeightKg: form.avgWeightKg !== '' ? Number(form.avgWeightKg) : null,
      returnRatePct: form.returnRatePct !== '' ? Number(form.returnRatePct) : null,
      proofNote: form.proofNote.trim() || '货主自报近30天单量'
    })
    submittedResult.value = res || { verified30dOrders: Math.round(orders), verifiedDailyAvg: dailyAvgPreview.value }
  } catch (e) {
    submitError.value = e?.message || '提交失败，请检查链接状态后重试'
  } finally {
    submitting.value = false
  }
}

onMounted(loadSession)
</script>

<style scoped>
.open-shell {
  min-height: 100vh;
  background: linear-gradient(180deg, #eff6ff 0%, #f8fafc 45%, #f1f5f9 100%);
  padding: 20px 14px 40px;
  box-sizing: border-box;
}
.open-container {
  max-width: 560px;
  margin: 0 auto;
}
.hero-card {
  background: linear-gradient(135deg, #0f172a 0%, #1d4ed8 100%);
  color: #fff;
  border-radius: 16px;
  padding: 22px 20px;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.16);
}
.hero-tag {
  display: inline-block;
  font-size: 12px;
  background: rgba(251, 191, 36, 0.22);
  color: #fde68a;
  padding: 3px 10px;
  border-radius: 999px;
}
.hero-title {
  margin: 10px 0 6px;
  font-size: 22px;
  font-weight: 800;
}
.hero-sub {
  margin: 0;
  font-size: 13px;
  color: #dbeafe;
  line-height: 1.5;
}
.honesty-banner {
  margin-top: 12px;
  background: #fffbeb;
  border: 1px solid #fcd34d;
  color: #92400e;
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 12.5px;
  line-height: 1.55;
}
.card {
  margin-top: 14px;
  background: #fff;
  border-radius: 16px;
  padding: 20px 18px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
}
.session-meta {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #334155;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.meta-k { color: #64748b; }
.field {
  display: flex;
  flex-direction: column;
  margin-bottom: 14px;
}
.lb {
  font-size: 13.5px;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 6px;
}
.lb i { color: #dc2626; font-style: normal; margin-left: 2px; }
.field input, .field textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 15px;
  color: #0f172a;
  background: #fff;
}
.field input:focus, .field textarea:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}
.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #2563eb;
  font-weight: 600;
}
.grid-two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
@media (max-width: 480px) {
  .grid-two { grid-template-columns: 1fr; gap: 0; }
}
.err-msg {
  margin: 0 0 10px;
  color: #dc2626;
  font-size: 13px;
}
.primary-btn {
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
.primary-btn:disabled { opacity: 0.65; cursor: not-allowed; }
.state-card { text-align: center; padding: 28px 18px; }
.state-icon {
  width: 44px; height: 44px; border-radius: 50%;
  margin: 0 auto 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px; font-weight: 800;
  background: #fee2e2; color: #dc2626;
}
.state-icon.ok { background: #dcfce7; color: #16a34a; }
.state-icon.warn { background: #fef3c7; color: #d97706; }
.state-title { font-size: 17px; font-weight: 700; color: #0f172a; }
.state-desc { margin: 8px 0 0; font-size: 13.5px; color: #475569; line-height: 1.6; }
</style>
