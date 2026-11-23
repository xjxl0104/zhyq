<template>
  <view class="page-wrap">
    <view class="card">
      <view class="wh-row mode-header">
        <view class="wh-title">订单同步与 ERP 接入</view>
        <text class="env-badge" :class="envBadgeTone">{{ envStageText }}</text>
      </view>
      <view v-if="loading" class="wh-note">正在读取接入信息…</view>
      <template v-if="loaded">
        <view class="mode-grid">
          <view class="mode-track" :class="{ active: isYunshanMode }">
            <view class="wh-row"><text class="track-name">云山日票数同步</text><text v-if="isYunshanMode" class="track-pill">当前生效</text></view>
            <view class="wh-note track-desc">园区按日同步客户发货票数并自动核算按单佣金，无需云仓自研推送接口。</view>
          </view>
          <view class="mode-track" :class="{ active: !isYunshanMode }">
            <view class="wh-row"><text class="track-name">通用 ERP Webhook</text><text v-if="!isYunshanMode" class="track-pill">当前通道</text></view>
            <view class="wh-note track-desc">云仓推送订单出库事件（order.shipped）并每小时发送签名心跳，支持任意自研或三方 ERP。</view>
          </view>
        </view>

        <view v-if="isYunshanMode" class="guide-box info">
          <view class="guide-title">当前采用「云山日票数同步」模式</view>
          <view class="wh-note">系统已按日同步发货票数，日常履约无需配置下方 Webhook 凭证；如需切换为通用 ERP Webhook 实时推送，可先签发沙箱凭证自测后联系园区运营切换。</view>
        </view>

        <view class="status-summary">
          <view class="wh-row"><text class="wh-label inline-label">当前连接状态</text><view class="wh-status">{{ statusText }}</view></view>
          <view class="wh-note">接入环境：{{ envDetailText }}</view>
          <view class="wh-note">最近签名心跳：{{ erp.lastHeartbeatAt || '尚未收到有效签名请求' }}</view>
          <view class="wh-note">最近同步记录：{{ erp.lastSyncAt || '暂无同步记录' }}</view>
        </view>

        <view v-if="erp.configured && erp.status === 1 && erp.erpStatus === 3" class="guide-box danger" role="alert">
          <view class="guide-title">心跳离线提醒（状态码 3）</view>
          <view class="wh-note">园区超过约定周期未收到有效签名心跳。请检查云仓 ERP 定时任务是否每小时调用心跳接口（{{ heartbeatEndpointText }}），恢复调用后状态自动恢复正常。</view>
        </view>

        <template v-if="erp.configured">
          <view class="wh-label">应用编号（AppId）</view>
          <text class="credential" selectable>{{ erp.appId }}</text>
          <button class="wh-secondary" @click="copy(erp.appId)">复制应用编号</button>

          <view class="wh-label">接口地址</view>
          <view class="wh-note">{{ erp.env === 2 ? '正式上线（env=2）' : '沙箱联调（env=1）' }} · 订单事件：{{ erp.endpoint || '/api/open/v1/erp/order-event' }}</view>
          <view class="wh-note">签名心跳：{{ heartbeatEndpointText }}（无订单时也须每小时发送一次）</view>

          <template v-if="secret">
            <view class="wh-label">接入密钥（Secret）</view>
            <text class="credential" selectable>{{ secret }}</text>
            <view class="wh-note">密钥仅在本次签发后显示一次，请立即复制并妥善保存；离开本页后无法再次查看。</view>
            <button class="wh-secondary" @click="copy(secret)">复制接入密钥</button>
          </template>
          <view v-else class="wh-note">凭证已签发，密钥不会再次显示；如遗失密钥，请联系园区运营重置。</view>

          <view v-if="erp.status === 1 && erp.env !== 2" class="guide-box" :class="sandboxReady ? 'ok' : 'warn'">
            <view class="guide-title">{{ sandboxReady ? '已完成沙箱自测 · 请联系运营切换正式上线' : '沙箱联调指引（env=1 → 正式 env=2）' }}</view>
            <view class="wh-note">{{ goLiveGuideText }}</view>
            <button class="wh-secondary" :disabled="!!busy || loading" @click="runSelfTest">{{ busy === 'selftest' ? '正在校验格式…' : '发送示例报文自测格式' }}</button>
            <view v-if="selfTestMessage" class="wh-note">{{ selfTestMessage }}</view>
          </view>

          <view v-else-if="erp.status === 1 && erp.env === 2" class="guide-box ok">
            <view class="guide-title">正式上线运行中（env=2）</view>
            <view class="wh-note">当前推送的出库事件已接入正式计佣与对账链路。新增承接客户时，请联系园区运营在后台「单量与ERP对接 › 通用 ERP Webhook」补充对应客户的货主编码映射（customer_code）。</view>
          </view>
        </template>
        <template v-else>
          <view class="guide-box warn">
            <view class="guide-title">通用 ERP Webhook 三步上线流程</view>
            <view class="wh-note">1. 点击下方签发沙箱凭证（env=1）并保存密钥；2. 完成事件格式自测与签名心跳联调；3. 联系园区运营在后台「单量与ERP对接 › 通用 ERP Webhook」切换至正式环境（env=2）并完成货主编码映射。</view>
          </view>
          <button class="btn" :disabled="!!busy || loading" :loading="busy === 'issue'" @click="issue">签发沙箱凭证</button>
        </template>

        <view class="wh-actions">
          <button class="wh-secondary" :disabled="!!busy || loading" @click="ping">测试服务连接</button>
          <button class="wh-secondary" :disabled="!!busy || loading" @click="logs(true)">查看同步日志</button>
        </view>
        <view v-if="connectionMessage" class="wh-note">{{ connectionMessage }}</view>
      </template>
      <view v-if="error" class="wh-error" role="alert">{{ error }}</view>
      <button v-if="error && !loaded" class="wh-secondary" :disabled="loading" @click="load">重新读取</button>
    </view>
    <view v-if="logsLoaded" class="card">
      <view class="wh-title">同步日志</view>
      <view v-for="entry in logsRows" :key="entry.id" class="wh-item">
        <view class="wh-row"><text>{{ entry.event || '同步事件' }}</text><text>{{ entry.ok === 1 ? '成功' : '失败' }}</text></view>
        <view v-if="entry.error" class="wh-error">{{ entry.error }}</view>
      </view>
      <view v-if="!logsRows.length" class="wh-note">暂无同步日志</view>
      <button v-if="logsRows.length < logsTotal" class="wh-secondary" :disabled="!!busy" @click="logs(false)">加载更多日志</button>
    </view>
  </view>
</template>
<script>
import { appShareMixin } from '@/utils/share'

export default { mixins: [appShareMixin] }
</script>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { warehouseApi } from '@/api/warehouse'

const erp = reactive({})
const dashboard = reactive({})
const secret = ref('')
const loaded = ref(false), loading = ref(false), busy = ref(''), error = ref(''), connectionMessage = ref('')
const selfTested = ref(false), selfTestMessage = ref('')
const logsRows = ref([]), logsLoaded = ref(false), logsTotal = ref(0), logsPage = ref(1)

const orderMode = computed(() => erp.orderMode || dashboard.orderMode || '')
const isYunshanMode = computed(() => ['yunshan', 'manual'].includes(orderMode.value))
const heartbeatEndpointText = computed(() => erp.heartbeatEndpoint || '/api/open/v1/erp/heartbeat')
const statusText = computed(() => !erp.configured ? '尚未配置接入凭证' : erp.status !== 1 ? '接入凭证已停用'
  : ({ 0: '已配置，等待签名连接', 1: '沙箱连接正常', 2: '正式连接正常', 3: '签名心跳超时' }[erp.erpStatus] || '等待签名连接'))
const envStageText = computed(() => {
  if (!erp.configured) return isYunshanMode.value ? '云山同步' : '未接入'
  if (erp.status !== 1) return '凭证已停用'
  if (erp.erpStatus === 3) return '心跳离线'
  return erp.env === 2 ? '正式上线' : '沙箱联调'
})
const envBadgeTone = computed(() => {
  if (!erp.configured) return isYunshanMode.value ? 'ok' : 'muted'
  if (erp.status !== 1 || erp.erpStatus === 3) return 'danger'
  return erp.env === 2 ? 'ok' : 'warn'
})
const envDetailText = computed(() => {
  if (!erp.configured) return '未接入（尚未签发通用 ERP Webhook 凭证）'
  if (erp.status !== 1) return '凭证已停用，请联系园区运营核实'
  if (erp.erpStatus === 3) return `${erp.env === 2 ? '正式上线（env=2）' : '沙箱联调（env=1）'} · 心跳离线（状态码 3）`
  return erp.env === 2 ? '正式上线（env=2） · 推送订单直接进入正式计佣' : '沙箱联调（env=1） · 仅校验格式与签名，不产生正式订单或佣金'
})
const sandboxReady = computed(() => Boolean(erp.configured && erp.status === 1 && erp.env !== 2 && (erp.erpStatus === 1 || erp.lastHeartbeatAt || selfTested.value)))
const goLiveGuideText = computed(() => sandboxReady.value
  ? '沙箱联调已就绪！请联系园区运营在后台「单量与ERP对接 › 通用 ERP Webhook」一键切换至正式环境（env=2），并完成「货主编码映射」（将 ERP 货主编码 customer_code 绑定至园区客户与合同），切换后即可正式上线计佣。'
  : '沙箱环境（env=1）仅校验事件格式与签名，不产生正式订单或佣金。完成沙箱自测与签名心跳联调后，请联系园区运营在后台「单量与ERP对接 › 通用 ERP Webhook」一键切换至正式环境（env=2）并完成货主编码映射。')

function applyErp(data) {
  // The API reveals a secret only once. Keep that value in this page's memory
  // when onShow refreshes the public credential metadata after copying.
  if (erp.appId && erp.appId !== data.appId) secret.value = ''
  Object.keys(erp).forEach(key => { delete erp[key] })
  Object.assign(erp, data)
  if (data.secret) secret.value = data.secret
  loaded.value = true
}
async function load() {
  if (loading.value || busy.value) return
  loading.value = true; error.value = ''
  try {
    const tasks = [warehouseApi.erp()]
    if (typeof warehouseApi.dashboard === 'function') tasks.push(warehouseApi.dashboard())
    const results = await Promise.allSettled(tasks)
    if (results[0].status === 'rejected') throw results[0].reason
    applyErp(results[0].value)
    if (results[1]?.status === 'fulfilled' && results[1].value) Object.assign(dashboard, results[1].value)
  }
  catch (e) { error.value = e.message || '接入信息读取失败，请重试' }
  finally { loading.value = false }
}
async function issue() {
  if (busy.value || loading.value || !loaded.value || erp.configured) return
  busy.value = 'issue'; error.value = ''
  try { applyErp(await warehouseApi.issueSandbox()); uni.showToast({ title: secret.value ? '凭证已签发，请保存密钥' : '凭证已存在', icon: 'none' }) }
  catch (e) { error.value = e.message || '签发失败，请重试' }
  finally { busy.value = '' }
}
async function runSelfTest() {
  if (busy.value || loading.value || !erp.configured || typeof warehouseApi.testOrders !== 'function') return
  busy.value = 'selftest'; error.value = ''; selfTestMessage.value = ''
  try {
    const result = await warehouseApi.testOrders([{
      event_id: 'sandbox-self-test-1',
      event: 'order.shipped',
      order_no: 'SANDBOX-SO-001',
      warehouse_code: erp.appId || 'WH-SANDBOX',
      customer_code: 'CUST-DEMO-01',
      occurred_at: '2026-01-01T10:00:00+08:00',
      qty: 1,
      packages: 1,
      logistics: { tracking_no: 'SF0000000001' },
    }])
    selfTested.value = Number(result?.accepted || 0) > 0
    selfTestMessage.value = selfTested.value
      ? `示例事件格式校验通过（通过 ${result.accepted} 条）。下一步请发送真实签名心跳并联系园区运营在后台「单量与ERP对接 › 通用 ERP Webhook」切换正式环境与配置货主编码映射。`
      : (result?.message || '示例事件校验未通过，请检查事件字段')
  } catch (e) { error.value = e.message || '沙箱格式自测失败，请重试' }
  finally { busy.value = '' }
}
async function ping() {
  if (busy.value || loading.value) return
  busy.value = 'ping'; error.value = ''; connectionMessage.value = ''
  try {
    const result = await warehouseApi.ping()
    if (!result.ok) throw new Error('服务连接失败，请重试')
    const state = { 0: '未接入', 1: '沙箱联调', 2: '正式联通', 3: '连接中断' }[result.erpStatus] || '待确认'
    connectionMessage.value = '园区服务可访问，此检查不验证 ERP 连接；最近签名状态：' + state
    erp.lastSyncAt = result.lastSyncAt
    erp.lastHeartbeatAt = result.lastHeartbeatAt
    erp.erpStatus = result.erpStatus
  } catch (e) { error.value = e.message || '服务连接失败，请重试' }
  finally { busy.value = '' }
}
async function logs(reset = true) {
  if (busy.value || loading.value || (!reset && logsRows.value.length >= logsTotal.value)) return
  busy.value = 'logs'; error.value = ''
  const next = reset ? 1 : logsPage.value + 1
  try {
    const result = await warehouseApi.syncLogs({ pageNo: next, pageSize: 20 })
    logsRows.value = reset ? result.records || [] : logsRows.value.concat(result.records || [])
    logsTotal.value = Number(result.total || 0); logsPage.value = next; logsLoaded.value = true
  } catch (e) { error.value = e.message || '同步日志读取失败，请重试' }
  finally { busy.value = '' }
}
function copy(value) {
  if (!value) return
  uni.setClipboardData({ data: value, fail: () => { error.value = '复制失败，请长按文本复制' } })
}
onShow(load)
</script>
<style scoped>
@import '../../styles/warehouse.css';
.mode-header { margin-bottom: 12rpx; }
.inline-label { margin-top: 0; }
.env-badge { display: inline-block; padding: 6rpx 16rpx; border-radius: 999rpx; font-size: 24rpx; font-weight: 600; }
.env-badge.muted { color: #58617d; background: #edf0f7; }
.env-badge.warn { color: #9a6406; background: #fff4db; }
.env-badge.ok { color: #12785f; background: #e8f8f2; }
.env-badge.danger { color: #a3293e; background: #fdecef; }
.mode-grid { display: flex; flex-direction: column; gap: 16rpx; margin: 18rpx 0; }
.mode-track { padding: 20rpx 22rpx; border: 1rpx solid #dfe5f2; border-radius: 16rpx; background: #f8f9fc; }
.mode-track.active { border-color: #3857f5; background: #f2f5ff; }
.track-name { color: #17203e; font-size: 29rpx; font-weight: 700; }
.track-pill { padding: 4rpx 12rpx; color: #2e47cc; border-radius: 999rpx; background: #dfe6ff; font-size: 22rpx; font-weight: 600; }
.track-desc { margin: 8rpx 0 0; font-size: 25rpx; }
.status-summary { margin-top: 18rpx; padding-top: 18rpx; border-top: 1rpx solid #e6eaf3; }
.guide-box { margin: 20rpx 0; padding: 22rpx; border-radius: 16rpx; border-left: 6rpx solid #3857f5; background: #f4f6ff; }
.guide-box.warn { border-left-color: #d98b14; background: #fff9eb; }
.guide-box.ok { border-left-color: #14876e; background: #edf9f4; }
.guide-box.danger { border-left-color: #c5364d; background: #fef0f2; }
.guide-title { color: #17203e; font-size: 28rpx; font-weight: 700; }
.credential { display: block; margin: 16rpx 0; overflow-wrap: anywhere; font-size: 26rpx; line-height: 1.6; }
</style>
