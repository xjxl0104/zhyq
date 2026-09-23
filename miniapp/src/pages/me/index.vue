<template>
  <view class="page-wrap">
    <view v-if="error" class="card"><text class="error">{{ error }}</text><button @click="load">重新加载</button></view>
    <view v-if="loading && !me" class="card muted">正在读取个人资料…</view>
    <template v-if="me">
      <view class="card">
        <view class="title">{{ me.name }} <text class="tag">{{ POS[me.positionCode] }}</text></view>
        <view class="muted">{{ me.phone }}</view>
        <view class="row"><text>我的邀请码</text><button size="mini" @click="copy(me.inviteCode)">{{ me.inviteCode }} · 复制</button></view>
        <view class="muted">朋友注册时填写邀请码，即可加入您的团队。</view>
      </view>
      <view v-if="!me.hasParent && me.inviteDeadline" class="card">
        <view class="muted">可在 {{ me.inviteDeadline.slice(0, 10) }} 前补填一次上级邀请码。</view>
        <input class="input" v-model="invite" maxlength="8" placeholder="上级邀请码" aria-label="上级邀请码" />
        <button class="btn" :disabled="busy" @click="bindInvite">绑定上级</button>
      </view>
      <view class="card">
        <view class="row"><text class="title">收款资料</text><text class="tag">{{ accountLabel }}</text></view>
        <view class="muted">提交后由园区人工审核，通过后可申请提现。修改资料需要重新审核。</view>
        <view v-if="account.reviewReason" class="review-note">审核说明：{{ account.reviewReason }}</view>
        <view v-if="account.submitted && !showAccount">
          <view class="row"><text>{{ account.realName }}</text><text>{{ TYPES[account.accountType - 1] }} · 尾号 {{ account.accountTail }}</text></view>
          <view class="muted" v-if="account.bankName">{{ account.bankName }}</view>
          <button class="btn ghost" @click="showAccount = true">修改并重新提交</button>
        </view>
        <view v-else>
          <input class="input" v-model="acc.realName" maxlength="80" placeholder="收款人真实姓名" aria-label="真实姓名" />
          <input class="input" v-model="acc.idNo" maxlength="18" placeholder="身份证号（加密存储）" aria-label="身份证号" />
          <picker :range="TYPES" :value="acc.accountType - 1" @change="acc.accountType = Number($event.detail.value) + 1"><view class="input">收款方式：{{ TYPES[acc.accountType - 1] }}</view></picker>
          <input class="input" v-model="acc.accountNo" maxlength="100" placeholder="收款账号（加密存储）" aria-label="收款账号" />
          <input v-if="acc.accountType === 2" class="input" v-model="acc.bankName" maxlength="120" placeholder="开户行" aria-label="开户行" />
          <view v-if="formError" class="error">{{ formError }}</view>
          <button class="btn" :loading="busy" :disabled="busy" @click="saveAccount">提交审核</button>
          <button v-if="account.submitted" class="btn ghost" :disabled="busy" @click="showAccount = false">取消修改</button>
        </view>
      </view>
      <view class="card">
        <button class="menu-row" @click="go('/pages/account-security/index?role=mp')">账号与密码 <text>设置密码登录 ›</text></button>
        <button class="menu-row" @click="go('/pages/team/index')">我的团队 <text>›</text></button>
        <button class="menu-row" @click="go('/pages/position/index')">我的岗位 <text>›</text></button>
        <button class="menu-row" @click="go('/pages/invite/index')">邀请伙伴 <text>分享与海报 ›</text></button>
        <button class="menu-row" @click="go('/pages/notices/index')">消息通知 <text>›</text></button>
        <button class="menu-row" @click="logout">切换身份 / 退出登录 <text>›</text></button>
      </view>
    </template>
  </view>
</template>
<script setup>
import { ref, reactive, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { authApi, meApi } from '@/api'
import { token } from '@/utils/request'
const POS = { P1:'园区伙伴', P2:'银牌合伙人', P3:'金牌合伙人', P4:'钻石合伙人' }
const TYPES = ['微信', '银行卡', '支付宝']
const me = ref(null), account = ref({}), loading = ref(false), busy = ref(false), error = ref(''), formError = ref(''), invite = ref(''), showAccount = ref(false)
const acc = reactive({ realName:'', idNo:'', accountType:2, accountNo:'', bankName:'' })
const accountLabel = computed(() => !account.value.submitted ? '未提交' : ({0:'待人工审核',1:'审核通过',2:'已退回'}[account.value.reviewStatus] || '待审核'))
async function load() {
  loading.value = true; error.value = ''
  try { [me.value, account.value] = await Promise.all([meApi.me(), meApi.accountStatus()]) }
  catch (e) { error.value = e.message }
  finally { loading.value = false }
}
onShow(load)
const copy = data => uni.setClipboardData({ data })
const go = url => uni.navigateTo({ url })
async function bindInvite() {
  if (busy.value) return
  busy.value = true
  try { await authApi.bindInvite(invite.value.trim()); uni.showToast({title:'已绑定'}); await load() }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
async function saveAccount() {
  if (busy.value) return
  formError.value = ''
  if (!acc.realName.trim() || !acc.idNo.trim() || !acc.accountNo.trim()) { formError.value = '请填写姓名、身份证号和收款账号'; return }
  busy.value = true
  try {
    await meApi.account(acc); uni.showToast({ title:'已提交审核' }); showAccount.value = false
    acc.idNo = ''; acc.accountNo = ''; await load()
  } catch (e) { formError.value = e.message } finally { busy.value = false }
}
function logout() { token.clear(); uni.reLaunch({url:'/pages/entry/index'}) }
</script>
<style scoped>
.error{color:#a3293e;line-height:1.6;margin:18rpx 0}.review-note{margin:20rpx 0;line-height:1.6;overflow-wrap:anywhere}.menu-row{display:flex;justify-content:space-between;gap:20rpx;background:transparent;padding:26rpx 0;text-align:left;font-size:30rpx;line-height:1.5;border-bottom:1rpx solid #e2e7f1;border-radius:0}.menu-row text{color:#58617d}.menu-row:last-child{border-bottom:0}.input{font-size:32rpx}.row{flex-wrap:wrap;gap:12rpx}
</style>
