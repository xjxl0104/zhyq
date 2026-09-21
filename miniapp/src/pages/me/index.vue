<template>
  <view v-if="me">
    <view class="card">
      <view style="font-size:34rpx;font-weight:600">{{ me.name }} <text class="tag">{{ POS[me.positionCode] }}</text></view>
      <view class="muted">{{ me.phone }}</view>
      <view class="row"><text class="muted">我的邀请码</text><text style="font-size:36rpx;letter-spacing:4rpx;font-weight:600" @click="copy(me.inviteCode)">{{ me.inviteCode }} 📋</text></view>
      <view class="muted">朋友注册时填写您的邀请码,即成为您的团队成员。</view>
    </view>

    <view class="card" v-if="!me.hasParent && me.inviteDeadline">
      <view class="muted">您注册时未填邀请码,可在 {{ me.inviteDeadline.slice(0, 10) }} 前补填一次</view>
      <input class="input" v-model="invite" maxlength="8" placeholder="上级邀请码" />
      <button class="btn" @click="bindInvite">绑定上级</button>
    </view>

    <view class="card">
      <view class="row"><text>实名与收款账户</text><text class="tag" :class="me.idVerified ? 'ok' : 'warn'">{{ me.idVerified ? '已完成' : '未完成' }}</text></view>
      <view v-if="showAccount || !me.idVerified">
        <input class="input" v-model="acc.realName" placeholder="真实姓名" />
        <input class="input" v-model="acc.idNo" maxlength="18" placeholder="身份证号(加密存储)" />
        <picker :range="['微信', '银行卡', '支付宝']" @change="e => acc.accountType = e.detail.value + 1"><view class="input">收款方式:{{ ['微信', '银行卡', '支付宝'][acc.accountType - 1] }}</view></picker>
        <input class="input" v-model="acc.accountNo" placeholder="收款账号(加密存储)" />
        <input class="input" v-if="acc.accountType === 2" v-model="acc.bankName" placeholder="开户行" />
        <button class="btn" @click="saveAccount">保存</button>
      </view>
      <view v-else class="muted" @click="showAccount = true">点击修改</view>
    </view>

    <view class="card">
      <view class="row" @click="uni.navigateTo({ url: '/pages/team/index' })"><text>我的团队</text><text class="muted">›</text></view>
      <view class="row" @click="uni.navigateTo({ url: '/pages/position/index' })"><text>我的岗位</text><text class="muted">›</text></view>
      <view class="row" @click="share"><text>邀请海报</text><text class="muted">›</text></view>
      <view class="row" @click="logout"><text style="color:#c00">退出登录</text><text class="muted">›</text></view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { authApi, meApi } from '@/api'
import { token } from '@/utils/request'
const POS = { P1: '园区伙伴', P2: '银牌合伙人', P3: '金牌合伙人', P4: '钻石合伙人' }
const me = ref(null); const invite = ref(''); const showAccount = ref(false)
const acc = reactive({ realName: '', idNo: '', accountType: 2, accountNo: '', bankName: '' })
onShow(async () => { me.value = await meApi.me() })
const copy = (t) => uni.setClipboardData({ data: t })
async function bindInvite() { await authApi.bindInvite(invite.value); uni.showToast({ title: '已绑定' }); me.value = await meApi.me() }
async function saveAccount() { await meApi.account(acc); uni.showToast({ title: '已保存' }); showAccount.value = false; me.value = await meApi.me() }
async function share() {
  const p = await meApi.poster()
  uni.showModal({ title: '邀请海报', content: `邀请码 ${p.inviteCode}\n小程序路径 ${p.path}\n(接入微信后此处生成带小程序码的海报图)`, showCancel: false })
}
function logout() { token.clear(); uni.reLaunch({ url: '/pages/login/index' }) }
</script>
