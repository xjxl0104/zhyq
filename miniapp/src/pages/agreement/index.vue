<template>
  <view class="page-wrap">
    <view class="card">
      <view class="wh-title">{{ doc.title }}</view>
      <view class="wh-note">{{ doc.intro }}</view>
      <view v-for="section in doc.sections" :key="section.heading" class="section">
        <view class="heading">{{ section.heading }}</view>
        <view v-for="(line, index) in section.lines" :key="index" class="line">{{ line }}</view>
      </view>
      <button v-if="type === 'privacy' && canOpenWechatGuide" class="wh-secondary" @click="openWechatGuide">查看微信小程序隐私保护指引</button>
    </view>
  </view>
</template>

<script>
import { appShareMixin } from '@/utils/share'

export default { mixins: [appShareMixin] }
</script>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const type = ref('partner')
let canOpenWechatGuide = false
// #ifdef MP-WEIXIN
canOpenWechatGuide = typeof wx !== 'undefined' && !!wx.openPrivacyContract
// #endif

const contact = '如对本协议或个人信息处理有疑问、需要查询、更正、删除信息或注销账号，可联系园区运营人员处理。'

const docs = {
  partner: {
    title: '园区伙伴协议',
    intro: '欢迎使用“园区伙伴”小程序。注册或登录即表示你已阅读并同意本协议。',
    sections: [
      { heading: '一、服务内容', lines: ['本小程序为园区伙伴提供客户推荐、团队管理、收益查询与提现申请等服务。', '具体功能以小程序实际提供为准，园区可根据业务需要调整。'] },
      { heading: '二、账号注册与使用', lines: ['首次注册需填写邀请人提供的上级邀请码，注册后加入邀请人的团队。', '请妥善保管账号和密码，不得将账号转让、出借或用于他人业务。', '因账号保管不当造成的损失由账号持有人自行承担。'] },
      { heading: '三、客户推荐规则', lines: ['推荐客户时应如实填写客户名称与联系方式，并事先取得客户同意。', '客户归属、定价与分佣以园区审核确认的结果及双方签订的合同为准。', '不得虚构客户、重复报备或以不正当方式争抢客户。'] },
      { heading: '四、收益与提现', lines: ['收益依据实际成交合同与园区结算规则计算，以小程序展示的结算结果为准。', '提现需按页面提示申请，园区审核后发放；如发现违规所得，园区有权暂停结算或追回。'] },
      { heading: '五、协议的变更与终止', lines: ['园区可根据法律法规或业务调整更新本协议，更新后在小程序内公布。', '违反本协议的，园区有权限制或终止账号使用。'] },
      { heading: '六、联系我们', lines: [contact] },
    ],
  },
  warehouse: {
    title: '云仓入驻协议',
    intro: '欢迎使用“园区伙伴”小程序的云仓入驻服务。注册或登录即表示你已阅读并同意本协议。',
    sections: [
      { heading: '一、服务内容', lines: ['本小程序为云仓商家提供加盟申请、承接客户、出库单与结算单查询、合同协议管理等服务。'] },
      { heading: '二、入驻资料', lines: ['申请入驻时应如实填写云仓名称、联系人、联系电话、场地及服务能力等资料。', '加盟协议与客户服务合同以双方签署并经园区审核确认的版本为准。'] },
      { heading: '三、业务规则', lines: ['承接客户、计费与结算以园区确认的合同和结算单为准。', '竞争保护白名单仅本云仓账号可见，名称加密保存。'] },
      { heading: '四、协议的变更与终止', lines: ['园区可根据法律法规或业务调整更新本协议，更新后在小程序内公布。', '违反本协议的，园区有权暂停或终止入驻服务。'] },
      { heading: '五、联系我们', lines: [contact] },
    ],
  },
  privacy: {
    title: '隐私协议',
    intro: '我们重视你的个人信息保护。本协议说明“园区伙伴”小程序收集哪些信息、如何使用以及你享有的权利。',
    sections: [
      { heading: '一、我们收集的信息', lines: [
        '1. 登录信息：微信登录时获取的微信用户标识，用于识别账号；你选择账号密码登录时使用的账号与密码。',
        '2. 手机号：仅在你点击“手机号一键登录”并同意授权后获取，用于账号注册、登录和业务联系。',
        '3. 个人资料：你主动填写的姓名、联系电话。',
        '4. 业务信息：上级邀请码与团队关系、你推荐的客户名称及联系方式、收益与提现记录；云仓商家填写的云仓资料及上传的合同文件。',
      ] },
      { heading: '二、信息的使用', lines: ['上述信息仅用于账号登录、客户推荐与归属确认、收益结算、提现发放及必要的业务联系，不用于与本服务无关的用途。'] },
      { heading: '三、信息的共享与保存', lines: ['我们不会向园区业务以外的第三方出售或提供你的个人信息，法律法规另有规定的除外。', '信息保存在境内服务器，并采取访问权限控制、加密传输等措施保护。'] },
      { heading: '四、你的权利', lines: ['你可以在“我的—个人信息”中查看和修改姓名、联系电话；可以拒绝手机号授权，改用微信一键登录或账号密码登录。', contact] },
      { heading: '五、协议更新', lines: ['本协议更新后会在小程序内公布，重大变更会在登录页提示。'] },
    ],
  },
}

const doc = computed(() => docs[type.value] || docs.partner)

onLoad(query => {
  type.value = docs[query?.type] ? query.type : 'partner'
  uni.setNavigationBarTitle({ title: doc.value.title })
})

function openWechatGuide() {
  // #ifdef MP-WEIXIN
  wx.openPrivacyContract({ fail: () => uni.showToast({ title: '暂时无法打开，请稍后重试', icon: 'none' }) })
  // #endif
}
</script>

<style scoped>
.section { margin-top: 28rpx; }
.heading { color: var(--park-text); font-size: 30rpx; font-weight: 600; line-height: 1.6; }
.line { margin-top: 10rpx; color: #58617d; font-size: 28rpx; line-height: 1.7; }
</style>
