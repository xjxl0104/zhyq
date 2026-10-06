<template>
  <view class="page-wrap">
    <view class="card">
      <view class="wh-title">ERP 自助接入</view>
      <view v-if="loading" class="wh-note">正在读取接入信息…</view>
      <template v-if="loaded">
        <view class="wh-status">{{ statusText }}</view>
        <view class="wh-note">最近同步：{{ erp.lastSyncAt || '暂无同步记录' }}</view>
        <template v-if="erp.configured">
          <view class="wh-label">应用编号</view><text class="credential" selectable>{{ erp.appId }}</text>
          <view class="wh-note">{{ erp.env === 2 ? '正式环境' : '沙箱环境' }} · {{ erp.endpoint }}</view>
          <button class="wh-secondary" @click="copy(erp.appId)">复制应用编号</button>
          <template v-if="secret">
            <view class="wh-label">接入密钥</view><text class="credential" selectable>{{ secret }}</text>
            <view class="wh-note">密钥仅在本次签发后显示，请复制并妥善保存；离开本页后将无法再次查看。</view>
            <button class="wh-secondary" @click="copy(secret)">复制接入密钥</button>
          </template>
          <view v-else class="wh-note">凭证已签发，密钥不会再次显示；如未保存，请联系园区运营处理。</view>
        </template>
        <button v-else class="btn" :disabled="busy || loading" :loading="busy === 'issue'" @click="issue">签发沙箱凭证</button>
        <button class="wh-secondary" :disabled="busy || loading" @click="ping">测试服务连接</button>
        <view v-if="connectionMessage" class="wh-note">{{ connectionMessage }}</view>
        <button class="wh-secondary" :disabled="busy || loading" @click="logs(true)">查看同步日志</button>
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
      <button v-if="logsRows.length < logsTotal" class="wh-secondary" :disabled="busy" @click="logs(false)">加载更多日志</button>
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
const secret = ref('')
const loaded = ref(false), loading = ref(false), busy = ref(''), error = ref(''), connectionMessage = ref('')
const logsRows = ref([]), logsLoaded = ref(false), logsTotal = ref(0), logsPage = ref(1)
const statusText = computed(() => !erp.configured ? '尚未配置接入凭证' : erp.status === 1 ? '接入凭证已启用' : '接入凭证已停用')
function applyErp(data) {
  // The API reveals a secret only once. Keep that value in this page's memory
  // when onShow refreshes the public credential metadata after copying.
  if (erp.appId && erp.appId !== data.appId) secret.value = ''
  Object.assign(erp, data)
  if (data.secret) secret.value = data.secret
  loaded.value = true
}
async function load() {
  if (loading.value || busy.value) return
  loading.value = true; error.value = ''
  try { applyErp(await warehouseApi.erp()) }
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
async function ping() {
  if (busy.value || loading.value) return
  busy.value = 'ping'; error.value = ''; connectionMessage.value = ''
  try {
    const result = await warehouseApi.ping()
    if (!result.ok) throw new Error('服务连接失败，请重试')
    const state = { 0: '未接入', 1: '沙箱联调', 2: '正式联通', 3: '连接中断' }[result.erpStatus] || '待确认'
    connectionMessage.value = '园区服务可访问；ERP 状态：' + state
    erp.lastSyncAt = result.lastSyncAt
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
.credential { display: block; margin: 16rpx 0; overflow-wrap: anywhere; font-size: 26rpx; line-height: 1.6; }
</style>
