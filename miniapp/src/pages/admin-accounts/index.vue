<template>
  <view class="page-wrap admin-page">
    <view v-if="checking" class="card muted">正在核验管理权限…</view>
    <view v-else-if="!authorized" class="card">
      <view class="title">无法进入账号管理</view>
      <view class="message">{{ accessError || '此页面仅对超级管理员开放。' }}</view>
      <button class="btn ghost" @click="authorize">重新核验</button>
    </view>
    <template v-else>
      <view class="card controls">
        <view class="title">账号管理</view>
        <view class="message">管理伙伴与云仓商家，变更会记录操作人和原因。</view>
        <view class="tabs">
          <button :class="['tab', { active: type === 'mp' }]" :disabled="busy" @click="switchType('mp')">伙伴</button>
          <button :class="['tab', { active: type === 'wh' }]" :disabled="busy" @click="switchType('wh')">云仓商家</button>
        </view>
        <view class="search-row">
          <input class="input search" v-model="keyword" maxlength="100" placeholder="姓名、账号、手机号或编码" aria-label="搜索账号" confirm-type="search" @confirm="search" />
          <button class="action" :disabled="loading || busy" @click="search">搜索</button>
        </view>
        <button class="btn" :disabled="busy" @click="beginCreate">新增{{ type === 'mp' ? '伙伴' : '云仓商家' }}</button>
      </view>

      <view v-if="mode" id="admin-form" class="card editor">
        <view class="title">{{ editorTitle }}</view>
        <view v-if="target" class="message">{{ target.name }} · 编号 {{ target.id }}</view>
        <template v-if="mode === 'create'">
          <view class="field-label">{{ type === 'mp' ? '姓名' : '云仓名称' }} *</view>
          <input class="input" v-model="form.name" :maxlength="type === 'mp' ? 32 : 100" :disabled="busy" aria-label="姓名或云仓名称" />
          <view class="field-label">联系电话（选填）</view>
          <input class="input" v-model="form.phone" maxlength="11" type="number" :disabled="busy" aria-label="联系电话" />
          <view class="field-label">登录账号 *</view>
          <input class="input" v-model="form.username" maxlength="512" :disabled="busy" aria-label="登录账号" />
          <view class="field-label">初始密码 *</view>
          <input class="input" v-model="form.password" password maxlength="4096" :disabled="busy" aria-label="初始密码" />
          <template v-if="type === 'mp'">
            <view class="field-label">上级邀请码（选填）</view>
            <input class="input code" v-model="form.parentInviteCode" maxlength="8" :disabled="busy" placeholder="不填则无上级" aria-label="上级邀请码" />
          </template>
          <view v-else class="message">商家创建后进入资质审核，完善资料并审核后才能开展业务。</view>
        </template>
        <template v-else-if="mode === 'invite'">
          <view class="message">原邀请码：{{ target.inviteCode }}。修改后旧分享链接及旧小程序码失效，已绑定的上下级关系保留。</view>
          <view class="field-label">新的本人邀请码 *</view>
          <input class="input code" v-model="form.inviteCode" maxlength="8" :disabled="busy" placeholder="8 位字母或 2–9 数字" aria-label="新的本人邀请码" />
        </template>
        <view v-else-if="mode === 'delete'" class="message">删除后无法登录，账号将从列表移除。有团队、客户、订单或资金等业务记录时不可删除，请改用停用登录。</view>
        <view v-else class="message">{{ nextDisabled ? '停用后微信和密码登录均不可用，当前登录也会失效。历史业务数据保留。' : '恢复后用户需重新登录；原有业务审核状态保留。' }}</view>
        <view class="field-label">操作原因 *</view>
        <textarea class="input reason" v-model="form.reason" maxlength="500" :disabled="busy" placeholder="说明本次新增或修改的原因" aria-label="操作原因" />
        <view v-if="formError" class="error" role="alert">{{ formError }}</view>
        <view class="form-actions">
          <button class="btn ghost" :disabled="busy" @click="closeEditor">取消</button>
          <button :class="['btn', { danger: mode === 'delete' }]" :disabled="busy" :loading="busy" @click="submit">{{ submitLabel }}</button>
        </view>
      </view>

      <view v-if="success" class="notice" role="status">{{ success }}</view>
      <view v-if="error" class="card"><view class="error" role="alert">{{ error }}</view><button class="btn ghost" @click="load(1)">重新加载</button></view>
      <view v-if="loading" class="notice">正在加载账号…</view>
      <view v-else-if="!rows.length && !error" class="card message">{{ appliedKeyword ? '没有符合条件的账号，请更换关键词。' : '暂无账号，可点击上方按钮新增。' }}</view>
      <view v-if="rows.length" class="card account-list">
        <view class="list-summary">共 {{ total }} 个账号 · 第 {{ pageNo }} 页</view>
        <view v-for="item in rows" :key="type + ':' + item.id" class="account-row">
          <view class="identity-line"><text class="account-name">{{ item.name || '未填写名称' }}</text><text class="state">{{ isDisabled(item) ? '登录已停用' : businessLabel(item) }}</text></view>
          <view v-if="isAdmin(item)" class="admin-label">超级管理员{{ Number(item.id) === Number(operatorId) ? ' · 当前账号' : '' }}</view>
          <view class="detail">编号 {{ item.id }} · {{ item.phone || '未填写电话' }}</view>
          <view class="detail">登录账号：{{ item.username || '仅微信登录，尚未设置密码' }}</view>
          <view class="code-row"><text>{{ type === 'mp' ? '邀请码' : '云仓编码' }}：{{ item.inviteCode }}</text><button class="copy" @click="copy(item.inviteCode)">复制</button></view>
          <view class="row-actions">
            <button v-if="type === 'mp'" class="action" :disabled="busy" @click="begin('invite', item)">修改邀请码</button>
            <template v-if="!isAdmin(item)">
              <button class="action" :disabled="busy" @click="begin('status', item)">{{ isDisabled(item) ? '恢复登录' : '停用登录' }}</button>
              <button class="action delete" :disabled="busy" @click="begin('delete', item)">删除账号</button>
            </template>
          </view>
        </view>
      </view>
      <view v-if="total > pageSize" class="pagination">
        <button class="action" :disabled="loading || busy || pageNo <= 1" @click="load(pageNo - 1)">上一页</button>
        <text>{{ pageNo }} / {{ Math.ceil(total / pageSize) }}</text>
        <button class="action" :disabled="loading || busy || pageNo * pageSize >= total" @click="load(pageNo + 1)">下一页</button>
      </view>
    </template>
  </view>
</template>
<script>
import { appShareMixin } from '@/utils/share'
export default { mixins: [appShareMixin] }
</script>
<script setup>
import { ref, reactive, computed } from 'vue'
import { onShow, onHide, onUnload } from '@dcloudio/uni-app'
import { accountAdminApi, meApi } from '@/api'
const checking = ref(true), authorized = ref(false), accessError = ref('')
const type = ref('mp'), keyword = ref(''), appliedKeyword = ref(''), rows = ref([]), total = ref(0), operatorId = ref(null)
const pageNo = ref(1), pageSize = 20, loading = ref(false), busy = ref(false), error = ref(''), success = ref('')
const mode = ref(''), target = ref(null), formError = ref('')
const form = reactive({ name: '', phone: '', username: '', password: '', parentInviteCode: '', inviteCode: '', reason: '' })
let requestId = 0, authId = 0
const isDisabled = item => Number(item.loginDisabled) === 1
const isAdmin = item => Number(item.superAdmin) === 1
const nextDisabled = computed(() => target.value ? !isDisabled(target.value) : true)
const editorTitle = computed(() => ({ create: type.value === 'mp' ? '新增伙伴' : '新增云仓商家', invite: '修改本人邀请码', delete: '删除账号', status: nextDisabled.value ? '停用登录' : '恢复登录' }[mode.value]))
const submitLabel = computed(() => ({ create: '创建账号', invite: '保存邀请码', delete: '确认删除', status: nextDisabled.value ? '确认停用' : '确认恢复' }[mode.value]))
function businessLabel(item) {
  return type.value === 'mp' ? ({ 1: '正常', 2: '已冻结', 3: '待审核', 4: '已退出' }[item.businessStatus] || '状态待确认')
    : ({ 1: '待申请', 2: '资质审核中', 3: '接入准备中', 4: '待签协议', 5: '已上线', 6: '暂停业务', 7: '已退出' }[item.businessStatus] || '状态待确认')
}
async function authorize() {
  const seq = ++authId
  checking.value = true; authorized.value = false; accessError.value = ''; rows.value = []; requestId++
  try {
    const me = await meApi.me()
    if (seq !== authId) return
    if (!me.superAdmin) { accessError.value = '此页面仅对超级管理员开放。'; return }
    authorized.value = true; operatorId.value = me.id; await load(1)
  } catch (e) { if (seq === authId) accessError.value = e.message }
  finally { if (seq === authId) checking.value = false }
}
async function load(page = 1) {
  const seq = ++requestId
  loading.value = true; error.value = ''; rows.value = []
  try {
    const data = await accountAdminApi.page({ type: type.value, keyword: appliedKeyword.value, pageNo: page, pageSize })
    if (seq !== requestId) return
    rows.value = data.records || []; total.value = Number(data.total || 0); operatorId.value = data.operatorId; pageNo.value = page
  } catch (e) { if (seq === requestId) error.value = e.message }
  finally { if (seq === requestId) loading.value = false }
}
function closeEditor() { mode.value = ''; target.value = null; formError.value = ''; Object.keys(form).forEach(key => { form[key] = '' }) }
function switchType(value) {
  if (busy.value || type.value === value) return
  closeEditor(); type.value = value; keyword.value = ''; appliedKeyword.value = ''; success.value = ''; load(1)
}
function search() { if (!busy.value) { appliedKeyword.value = keyword.value.trim(); closeEditor(); load(1) } }
function revealEditor() { uni.pageScrollTo({ scrollTop: 230, duration: 200 }) }
function beginCreate() { closeEditor(); mode.value = 'create'; success.value = ''; revealEditor() }
function begin(action, item) { closeEditor(); mode.value = action; target.value = { ...item }; form.inviteCode = item.inviteCode || ''; success.value = ''; revealEditor() }
const copy = data => uni.setClipboardData({ data: String(data || '') })
async function submit() {
  if (busy.value) return
  formError.value = ''
  if (!form.reason.trim()) { formError.value = '请填写操作原因'; return }
  if (mode.value === 'create' && (!form.name.trim() || !form.username.trim() || !form.password)) { formError.value = '请填写名称、登录账号和初始密码'; return }
  const invite = form.inviteCode.trim().toUpperCase()
  if (mode.value === 'invite' && !/^[A-Z2-9]{8}$/.test(invite)) { formError.value = '邀请码需为 8 位字母或 2–9 数字'; return }
  if (mode.value === 'delete') {
    busy.value = true
    let confirmed = false
    try { confirmed = (await new Promise((resolve, reject) => uni.showModal({ title: '确认删除账号', content: `删除“${target.value.name}”后将无法登录。此操作不能在小程序内撤销。`, confirmText: '删除账号', confirmColor: '#c0392b', success: resolve, fail: reject }))).confirm }
    catch (_) { formError.value = '确认窗口未打开，请重试' }
    if (!confirmed) { busy.value = false; return }
  }
  busy.value = true
  try {
    const note = form.reason.trim()
    if (mode.value === 'create') {
      const result = await accountAdminApi.create({ type: type.value, name: form.name.trim(), phone: form.phone.trim(), username: form.username.trim(), password: form.password, parentInviteCode: type.value === 'mp' ? form.parentInviteCode.trim().toUpperCase() : '', reason: note })
      success.value = `已创建“${result.name}”，可使用刚设置的账号密码登录。${result.inviteCode ? '本人邀请码：' + result.inviteCode : ''}`
    } else if (mode.value === 'invite') {
      await accountAdminApi.invite(target.value.id, { inviteCode: invite, expectedInviteCode: target.value.inviteCode, reason: note }); success.value = '邀请码已更新，请重新分享邀请链接或小程序码。'
    } else if (mode.value === 'status') {
      await accountAdminApi.status(type.value, target.value.id, { disabled: nextDisabled.value, expectedDisabled: isDisabled(target.value), reason: note }); success.value = nextDisabled.value ? '已停用登录，历史业务记录保留。' : '已恢复登录，请用户重新登录。'
    } else if (mode.value === 'delete') {
      await accountAdminApi.remove(type.value, target.value.id, note); success.value = '账号已删除。'
    }
    closeEditor(); await load(1)
  } catch (e) { formError.value = e.message }
  finally { busy.value = false }
}
function clearSensitive() { authId++; requestId++; form.password = ''; rows.value = []; authorized.value = false }
onShow(authorize)
onHide(clearSensitive)
onUnload(clearSensitive)
</script>
<style scoped>
.admin-page{padding-bottom:60rpx}.message,.detail,.list-summary{color:#6b7386;line-height:1.65;font-size:27rpx;overflow-wrap:anywhere}.message{margin:12rpx 0 24rpx}.tabs{display:flex;border-bottom:1rpx solid #dce2ee;margin:20rpx 0}.tab{flex:1;border-radius:0;background:transparent;font-size:30rpx;line-height:88rpx;color:#6b7386}.tab.active{color:#263dc2;border-bottom:4rpx solid #2b4fd6;font-weight:600}.search-row{display:flex;gap:12rpx;align-items:center}.search{flex:1;min-width:0}.input{font-size:30rpx;min-height:88rpx;color:#141a2e}.field-label{font-size:28rpx;margin:24rpx 0 10rpx;font-weight:600}.reason{width:100%;height:150rpx;padding:20rpx}.code{letter-spacing:2rpx}.form-actions{display:flex;gap:20rpx;margin-top:24rpx}.form-actions .btn{flex:1;margin:0}.btn.danger{background:#c0392b}.notice{margin:24rpx 30rpx;line-height:1.65;color:#33437a;overflow-wrap:anywhere}.error{color:#c0392b;line-height:1.65;margin:16rpx 0}.account-list{padding:0 30rpx}.list-summary{padding:24rpx 0;border-bottom:1rpx solid #e8eaef}.account-row{padding:28rpx 0;border-bottom:1rpx solid #e8eaef}.account-row:last-child{border-bottom:0}.identity-line{display:flex;justify-content:space-between;align-items:flex-start;gap:16rpx}.account-name{font-size:32rpx;font-weight:600;overflow-wrap:anywhere;min-width:0}.state{font-size:24rpx;color:#6b7386;flex-shrink:0;padding-top:4rpx}.admin-label{color:#263dc2;font-size:25rpx;margin:8rpx 0}.detail{margin-top:8rpx}.code-row{display:flex;flex-wrap:wrap;gap:12rpx;align-items:center;margin-top:8rpx;overflow-wrap:anywhere;font-size:27rpx}.copy,.action{min-height:80rpx;line-height:80rpx;margin:0;padding:0 20rpx;background:#edf0ff;color:#263dc2;font-size:27rpx;border-radius:12rpx}.copy{background:transparent}.row-actions{display:flex;flex-wrap:wrap;gap:12rpx;margin-top:18rpx}.delete{color:#c0392b;background:#fbeef0}.pagination{display:flex;justify-content:space-between;align-items:center;margin:24rpx;gap:16rpx;color:#6b7386}.action[disabled],.tab[disabled]{opacity:.55}button:focus-visible,input:focus-visible,textarea:focus-visible{outline:3rpx solid #263dc2;outline-offset:4rpx}
</style>
