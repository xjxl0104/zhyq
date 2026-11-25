<template>
  <div class="connector-hub-panel" data-testid="connector-hub-panel">
    <!-- 顶部热插拔设计说明与批量派工条 -->
    <div class="hub-banner">
      <div class="hub-banner-text">
        💡 <b>7大域 18 通道热插拔架构</b>：全部免费与商业付费接口均已预埋就绪！<b>未申请/未付费的通道保持关闭即 ¥0 成本且绝不报错</b>；日后派同事办好哪项，填进 Key 打开开关即刻点亮对应业务能力。
      </div>
      <div class="hub-banner-actions">
        <el-button size="small" type="warning" @click="openBatchApplyModal" data-testid="btn-batch-colleague-sheet">
          📤 一键生成「同事分批申领任务单」
        </el-button>
        <el-button size="small" @click="loadOverview">刷新状态</el-button>
      </div>
    </div>

    <!-- 3 张 L1 -> L2 -> L3 同源下钻结论卡 -->
    <section class="hub-kpi-grid" aria-label="外部连接器点亮与省钱护栏分析">
      <article v-for="card in drillCards" :key="card.key" class="hub-kpi-card" :data-hub-kpi="card.key">
        <div class="kpi-head">
          <h3 class="kpi-title">{{ card.title }}</h3>
          <button type="button" class="drill-btn" @click="openDrill(card.key)">下钻 ›</button>
        </div>
        <div class="kpi-main">
          <span class="kpi-val">{{ card.primaryText }}</span>
          <el-tag :type="card.badgeType" size="small" effect="light">{{ card.badgeText }}</el-tag>
        </div>
        <div class="kpi-foot">{{ card.footText }}</div>
      </article>
    </section>

    <!-- 7 大域分类筛选胶囊 -->
    <div class="cat-filter-bar" role="tablist" aria-label="7大数据域筛选">
      <button
        v-for="cat in CATEGORIES"
        :key="cat.code"
        type="button"
        class="cat-chip"
        :class="{ active: activeCategory === cat.code }"
        :data-cat="cat.code"
        @click="activeCategory = cat.code"
      >
        {{ cat.label }} ({{ countByCat(cat.code) }})
      </button>
    </div>

    <!-- 18 张热插拔连接器卡片网格（桌面3列 / 手机1列自适应零溢出） -->
    <div v-loading="loading" class="conn-card-grid">
      <article
        v-for="conn in filteredConnectors"
        :key="conn.connectorCode"
        class="conn-card"
        :class="{ 'is-active': conn.enabled, 'is-fused': conn.healthStatus === 'QUOTA_FUSED' }"
        :data-conn="conn.connectorCode"
      >
        <div class="conn-top">
          <div>
            <div class="conn-name">{{ conn.displayName }}</div>
            <div class="conn-tags">
              <el-tag :type="conn.feeTier === 'FREE' ? 'success' : 'warning'" size="small" effect="plain">
                {{ conn.feeDesc }}
              </el-tag>
              <el-tag v-if="conn.gdLockOnly" type="primary" size="small" effect="plain">
                🔒 广东省钱锁保护
              </el-tag>
            </div>
          </div>
          <el-tag :type="statusTagType(conn)" size="small">
            {{ statusLabel(conn) }}
          </el-tag>
        </div>

        <p class="conn-desc">{{ conn.description }}</p>

        <!-- Webhook 通道直接显示可复制回调地址 -->
        <div v-if="conn.connectorCode.startsWith('AD_')" class="webhook-url-box">
          <code>{{ webhookUrlOf(conn.connectorCode) }}</code>
          <el-button link type="primary" size="small" @click="copyText(webhookUrlOf(conn.connectorCode), '已复制 Webhook 回调地址')">
            复制地址
          </el-button>
        </div>

        <!-- 月度调用配额与熔断进度条 -->
        <div class="quota-box">
          <div class="quota-row">
            <span>
              本月调用：<b>{{ conn.monthUsedCount || 0 }}</b> /
              {{ conn.monthlyQuotaLimit >= 999999 ? '不限免流' : `${conn.monthlyQuotaLimit} 次上限` }}
            </span>
            <span>{{ conn.configured ? '密钥已脱敏保存' : '未填Key' }}</span>
          </div>
          <div class="quota-track">
            <div
              class="quota-fill"
              :style="{
                width: `${quotaPct(conn)}%`,
                background: conn.healthStatus === 'QUOTA_FUSED' ? '#e11d48' : '#2563eb'
              }"
            />
          </div>
        </div>

        <div class="conn-actions">
          <el-button
            size="small"
            :type="conn.enabled ? 'default' : 'primary'"
            data-testid="btn-cfg-conn"
            @click="openCfgModal(conn)"
          >
            ⚙️ {{ conn.enabled ? '配置与限额' : '填Key点亮' }}
          </el-button>
          <el-button size="small" type="warning" plain @click="openSingleApplyModal(conn)">
            📋 同事申请指引
          </el-button>
          <el-button size="small" @click="handlePing(conn)">🩺 自检</el-button>
        </div>
      </article>
    </div>

    <!-- L1 -> L2 -> L3 三层下钻抽屉 -->
    <el-drawer
      v-model="drill.visible"
      :title="activeDrillCard ? `下钻溯源 · ${activeDrillCard.title}` : '下钻溯源'"
      size="min(540px, 95vw)"
      append-to-body
    >
      <div v-if="activeDrillCard" class="drill-body">
        <div class="drill-box">
          <div class="drill-head">
            <span class="layer-pill">L1 核心结论</span>
            <el-tag :type="activeDrillCard.badgeType" size="small">{{ activeDrillCard.badgeText }}</el-tag>
          </div>
          <p class="drill-q">{{ activeDrillCard.question }}</p>
          <div>当前结论：<b>{{ activeDrillCard.primaryText }}</b></div>
        </div>

        <div class="drill-box">
          <span class="layer-pill">L2 判定逻辑（同源搬运 · 零二次重算）</span>
          <p class="drill-q" style="margin-bottom: 0">{{ activeDrillCard.logicNote }}</p>
        </div>

        <div v-for="r in activeDrillCard.rows" :key="r.key" class="drill-box">
          <div class="drill-head">
            <b>{{ r.label }}</b>
            <el-tag size="small" type="primary" effect="plain">{{ r.value }}</el-tag>
          </div>
          <div class="sub-note" style="margin: 4px 0">{{ r.verdict }}</div>
          <div v-if="r.evidence" class="l3-box">
            <div><b>① 原始数值</b>：<code>{{ r.evidence.rawTable }}</code></div>
            <div><b>② 数据来源</b>：{{ r.evidence.sourceNote }}</div>
            <div v-if="r.evidence.formula"><b>③ 核算公式</b>：<code>{{ r.evidence.formula }}</code></div>
            <div><b>④ 透明标注</b>：{{ r.evidence.aiNote }}</div>
            <div v-if="r.evidence.actionLabel" style="margin-top: 4px">
              <el-button link type="primary" size="small" @click="runDrillAction(r.evidence)">
                {{ r.evidence.actionLabel }} ›
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 弹窗 1：单个连接器开关、月度限额与密钥配置 -->
    <el-dialog v-model="cfgModal.visible" :title="`⚙️ 配置连接器 · ${cfgModal.conn?.displayName || ''}`" width="min(560px, 95vw)">
      <el-form v-if="cfgModal.conn" label-position="top">
        <el-form-item>
          <el-switch
            v-model="cfgModal.enabled"
            active-text="启用该连接器通道"
            inactive-text="待机关闭（¥0 零调用）"
          />
        </el-form-item>

        <el-form-item v-if="['CORP', 'ECOM'].includes(cfgModal.conn.categoryCode)">
          <el-checkbox v-model="cfgModal.gdLockOnly">
            开启广东省定向省钱硬锁（非广东主体自动跳过，免扣该接口额度）
          </el-checkbox>
        </el-form-item>

        <el-form-item label="单月最大调用次数熔断上限（达到上限自动暂停外发防超支）">
          <el-input-number v-model="cfgModal.monthlyQuotaLimit" :min="10" :max="9999999" :step="100" style="width: 100%" />
        </el-form-item>

        <el-form-item
          v-for="field in (cfgModal.conn.fields || [])"
          :key="field"
          :label="`${FIELD_LABELS[field] || field} (${field})`"
        >
          <el-input
            v-model="cfgModal.credentials[field]"
            clearable
            :placeholder="`同事申领后粘贴 ${field}（含 **** 表示保持原加密密钥不变）`"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="cfgModal.busy" @click="cfgModal.visible = false">取消</el-button>
        <el-button type="primary" :loading="cfgModal.busy" data-testid="btn-save-conn" @click="submitSaveConn">
          保存配置
        </el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 2：派发给同事的接口申领任务单（单条 / 批量全集） -->
    <el-dialog v-model="applyModal.visible" :title="applyModal.title" width="min(620px, 95vw)">
      <p class="sub-note" style="margin-top: 0">
        您无需一次性全部购买，直接复制下方工单发微信/企微给同事按需分批办理，拿回字段填进对应卡片即可点亮：
      </p>
      <pre class="apply-sheet-pre">{{ applyModal.content }}</pre>
      <template #footer>
        <el-button @click="applyModal.visible = false">关闭</el-button>
        <el-button type="primary" @click="copyText(applyModal.content, '已复制申领任务单，可直接发给同事办理！')">
          一键复制发给同事
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { mktConnectorHubApi } from '@/api/marketing'
import { buildConnectorHubDrilldownCards } from './connectorHubDrilldown'

const CATEGORIES = [
  { code: 'ALL', label: '全部 7 大域' },
  { code: 'CORP', label: '🏢 工商信用与关键人手机' },
  { code: 'ECOM', label: '🛍️ 全平台榜单与扫码验店' },
  { code: 'LBS', label: '🗺️ 高德LBS地推与真车程' },
  { code: 'AD', label: '📣 四大投流实时Webhook' },
  { code: 'LOGI', label: '📦 快递100防刷抽检' },
  { code: 'CALL', label: '📞 企微战报与隐私外呼' },
  { code: 'FIN', label: '🧾 6%增值税专票验真' }
]

const FIELD_LABELS = {
  appKey: '应用 AppKey',
  secretKey: '签名密钥 SecretKey',
  appSecret: '应用密钥 AppSecret',
  appId: '应用 AppId',
  token: '授权令牌 Token',
  appCode: '云市场 AppCode',
  apiKey: '接口密钥 ApiKey',
  endpoint: '接口请求地址 URL (https://...)',
  clientId: '开放平台 ClientId',
  clientSecret: '开放平台 ClientSecret',
  redirectUri: 'OAuth 授权回调地址',
  webServiceKey: '高德后端 Web服务 Key（核心必配）',
  webJsKey: '高德前端 Web端 JS Key',
  securityJsCode: '高德 JS 安全密钥 jscode',
  signatureSecret: '飞鱼 Webhook 验签密钥',
  tokenSecret: '腾讯线索通 Token 密钥',
  secret: '小红书聚光签名密钥',
  customer: '快递100 企业 Customer 编号',
  key: '快递100 授权 Key',
  sampleRatePct: '月度抽检比例 (%)，默认填 2',
  webhookUrl: '企微/飞书群机器人完整 Webhook URL',
  accessKeyId: '阿里云 AccessKeyId',
  accessKeySecret: '阿里云 AccessKeySecret',
  poolKey: 'AXB 隐私号码池 PoolKey',
  buyerTaxNo: '本园区收票主体纳税人识别号'
}

const loading = ref(false)
const summary = ref({})
const connectors = ref([])
const activeCategory = ref('ALL')

const filteredConnectors = computed(() =>
  connectors.value.filter(c => activeCategory.value === 'ALL' || c.categoryCode === activeCategory.value)
)

function countByCat(code) {
  if (code === 'ALL') return connectors.value.length
  return connectors.value.filter(c => c.categoryCode === code).length
}

function statusLabel(conn) {
  if (!conn.enabled) return '○ 待开通(¥0)'
  if (conn.healthStatus === 'QUOTA_FUSED') return '⚡ 已达月限额熔断'
  if (conn.healthStatus === 'MISSING_KEY') return '待粘贴密钥'
  return '● 已点亮就绪'
}

function statusTagType(conn) {
  if (!conn.enabled) return 'info'
  if (conn.healthStatus === 'QUOTA_FUSED') return 'danger'
  if (conn.healthStatus === 'MISSING_KEY') return 'warning'
  return 'success'
}

function quotaPct(conn) {
  if (!conn.monthlyQuotaLimit || conn.monthlyQuotaLimit >= 999999) return conn.monthUsedCount > 0 ? 8 : 0
  return Math.min(100, Math.round(((conn.monthUsedCount || 0) / conn.monthlyQuotaLimit) * 100))
}

function webhookUrlOf(code) {
  const ch = code.replace('AD_', '').toLowerCase()
  const origin = typeof window !== 'undefined' ? window.location.origin : ''
  return `${origin}/api/open/v1/ad-leads/${ch}`
}

const drillCards = computed(() => buildConnectorHubDrilldownCards(summary.value))
const drill = reactive({ visible: false, key: 'hub' })
const activeDrillCard = computed(() => drillCards.value.find(c => c.key === drill.key) || null)

function openDrill(key) {
  drill.key = key
  drill.visible = true
}

function runDrillAction(ev) {
  drill.visible = false
  if (ev.openBatchSheet) { openBatchApplyModal(); return }
  if (ev.filterCat) activeCategory.value = ev.filterCat
}

async function loadOverview() {
  loading.value = true
  try {
    const res = await mktConnectorHubApi.overview()
    summary.value = res?.summary || {}
    connectors.value = res?.connectors || []
  } catch (_) {
    /* request.js 已弹错 */
  } finally {
    loading.value = false
  }
}

const cfgModal = reactive({
  visible: false, conn: null, enabled: false, gdLockOnly: true,
  monthlyQuotaLimit: 1000, credentials: {}, busy: false
})

function openCfgModal(conn) {
  cfgModal.conn = conn
  cfgModal.enabled = !!conn.enabled
  cfgModal.gdLockOnly = !!conn.gdLockOnly
  cfgModal.monthlyQuotaLimit = conn.monthlyQuotaLimit || 1000
  cfgModal.credentials = { ...(conn.credentials || {}) }
  cfgModal.busy = false
  cfgModal.visible = true
}

async function submitSaveConn() {
  if (!cfgModal.conn || cfgModal.busy) return
  cfgModal.busy = true
  try {
    await mktConnectorHubApi.save(cfgModal.conn.connectorCode, {
      enabled: cfgModal.enabled,
      gdLockOnly: cfgModal.gdLockOnly,
      monthlyQuotaLimit: cfgModal.monthlyQuotaLimit,
      credentials: cfgModal.credentials
    })
    ElMessage.success(`「${cfgModal.conn.displayName}」配置已保存`)
    cfgModal.visible = false
    await loadOverview()
  } catch (_) {
    /* request.js 已统一处理报错 */
  } finally {
    cfgModal.busy = false
  }
}

async function handlePing(conn) {
  try {
    const res = await mktConnectorHubApi.ping(conn.connectorCode)
    if (res?.ok) ElMessage.success(res.message || '连接器自检通过')
    else ElMessage.warning(res?.message || '当前通道待开通')
    await loadOverview()
  } catch (_) {
    /* request.js 已统一处理报错 */
  }
}

const applyModal = reactive({ visible: false, title: '', content: '' })

function openSingleApplyModal(conn) {
  applyModal.title = `📋 同事申领任务单 · ${conn.displayName}`
  applyModal.content = [
    `【智慧园区外部数据接口申领任务单】`,
    `1. 接口通道：${conn.displayName} (${conn.connectorCode})`,
    `2. 资费参考：${conn.feeDesc}`,
    `3. 办理官网：${conn.applyUrl}`,
    `4. 办理步骤：${conn.applyGuide}`,
    `5. 办妥后需交回字段：${(conn.fields || []).join('、')}`,
    `6. 回填位置：智慧园区管理后台 › 全民营销 › 规则与字典 › 外部数据连接器中心`
  ].join('\n')
  applyModal.visible = true
}

function openBatchApplyModal() {
  const pending = connectors.value.filter(c => !c.enabled || !c.configured)
  const target = pending.length ? pending : connectors.value
  applyModal.title = `📤 外部数据接口分批申领总清单（共 ${target.length} 项）`
  applyModal.content = target.map((c, idx) =>
    `${idx + 1}. 【${c.displayName}】(${c.feeDesc})\n   办理入口：${c.applyUrl}\n   操作说明：${c.applyGuide}\n   交回字段：${(c.fields || []).join(' / ')}`
  ).join('\n\n')
  applyModal.visible = true
}

async function copyText(text, okMsg) {
  try {
    if (navigator?.clipboard?.writeText) await navigator.clipboard.writeText(text)
    ElMessage.success(okMsg || '已复制到剪贴板')
  } catch (_) {
    ElMessage.info('请直接选中框内文本复制')
  }
}

onMounted(loadOverview)
</script>

<style scoped>
.hub-banner {
  background: linear-gradient(90deg, #eff6ff, #f0fdf4);
  border: 1px solid #bfdbfe;
  border-radius: 10px;
  padding: 11px 15px;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 13px;
  color: #1e3a8a;
}
.hub-banner-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.hub-kpi-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.hub-kpi-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 13px 15px;
}
.kpi-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.kpi-title { margin: 0; font-size: 13px; font-weight: 600; color: #475569; }
.drill-btn {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
  border-radius: 6px;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}
.kpi-main { display: flex; align-items: baseline; gap: 8px; margin: 7px 0 4px; flex-wrap: wrap; }
.kpi-val { font-size: 22px; font-weight: 800; color: #0f172a; }
.kpi-foot, .sub-note { font-size: 12px; color: #64748b; line-height: 1.5; }

.cat-filter-bar { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
.cat-chip {
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #334155;
  border-radius: 999px;
  padding: 5px 12px;
  font-size: 12px;
  cursor: pointer;
}
.cat-chip.active {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
  font-weight: 600;
}

.conn-card-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.conn-card {
  background: #fff;
  border: 1px dashed #cbd5e1;
  border-radius: 11px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.conn-card.is-active {
  border-style: solid;
  border-color: #86efac;
  background: linear-gradient(180deg, #f0fdf4 0%, #ffffff 36%);
}
.conn-card.is-fused {
  border-style: solid;
  border-color: #fda4af;
}
.conn-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; }
.conn-name { font-weight: 700; font-size: 13.5px; color: #0f172a; }
.conn-tags { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 4px; }
.conn-desc { margin: 0; font-size: 12px; color: #475569; line-height: 1.5; }
.webhook-url-box {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  padding: 4px 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  word-break: break-all;
}
.quota-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 7px;
  padding: 7px 9px;
  font-size: 11.5px;
  color: #334155;
}
.quota-row { display: flex; justify-content: space-between; gap: 6px; flex-wrap: wrap; }
.quota-track { height: 5px; background: #e2e8f0; border-radius: 99px; overflow: hidden; margin-top: 5px; }
.quota-fill { height: 100%; border-radius: 99px; }
.conn-actions { margin-top: auto; display: flex; flex-wrap: wrap; gap: 6px; padding-top: 4px; }

.drill-body { display: flex; flex-direction: column; gap: 10px; }
.drill-box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; }
.drill-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.layer-pill { font-size: 11px; font-weight: 700; background: #dbeafe; color: #1d4ed8; padding: 2px 8px; border-radius: 999px; }
.drill-q { margin: 6px 0; font-size: 13px; color: #334155; }
.l3-box { border-top: 1px dashed #cbd5e1; margin-top: 6px; padding-top: 6px; font-size: 12px; display: flex; flex-direction: column; gap: 4px; word-break: break-all; }
.apply-sheet-pre {
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 12px;
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  font-size: 12.5px;
  line-height: 1.6;
}

@media (max-width: 1100px) {
  .hub-kpi-grid { grid-template-columns: 1fr; }
  .conn-card-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 760px) {
  .conn-card-grid { grid-template-columns: 1fr; }
}
</style>
