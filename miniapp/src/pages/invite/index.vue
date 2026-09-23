<template><view class="page-wrap">
  <view class="card"><view class="title">邀请伙伴加入</view><view class="muted">分享给朋友，注册时使用您的邀请码即可建立团队关系。</view>
    <view class="invite-code">{{ info.inviteCode || '正在读取…' }}</view>
    <button class="btn" :disabled="!info.inviteCode" @click="copy">复制邀请码</button>
    <!-- #ifdef MP-WEIXIN -->
    <button class="btn ghost" open-type="share" :disabled="!info.inviteCode">分享给微信好友</button>
    <!-- #endif -->
  </view>
  <view class="card"><view class="title">邀请海报</view><view class="muted">海报中的小程序码对应当前微信版本。体验版仅对有体验权限的账号开放。</view>
    <view v-if="error" class="error">{{ error }}</view>
    <button class="btn ghost" :disabled="busy || !info.inviteCode" :loading="busy" @click="makePoster">{{ poster ? '重新生成海报' : '生成邀请海报' }}</button>
    <image v-if="poster" :src="poster" mode="widthFix" class="poster" @click="preview" />
    <button v-if="poster" class="btn" @click="save">保存海报</button>
  </view>
  <canvas canvas-id="invitePoster" id="invitePoster" class="canvas" style="width:320px;height:460px" />
</view></template>
<script setup>
import {ref,getCurrentInstance} from 'vue'
import {onLoad,onShareAppMessage} from '@dcloudio/uni-app'
import {meApi} from '@/api'
import {downloadInvitationCode} from '@/utils/request'
const info=ref({}),error=ref(''),busy=ref(false),poster=ref('');const instance=getCurrentInstance()
onLoad(async()=>{try{info.value=await meApi.poster()}catch(e){error.value=e.message}})
onShareAppMessage(()=>({title:'邀请你成为园区伙伴',path:'/'+info.value.path}))
const copy=()=>uni.setClipboardData({data:info.value.inviteCode})
const preview=()=>uni.previewImage({urls:[poster.value]})
async function makePoster(){
  if(busy.value)return;busy.value=true;error.value='';let code
  try{
    let env='release'
    // #ifdef MP-WEIXIN
    env=uni.getAccountInfoSync().miniProgram.envVersion || 'release'
    // #endif
    code=await downloadInvitationCode(env)
    const ctx=uni.createCanvasContext('invitePoster',instance.proxy)
    ctx.setFillStyle('#f4f6fb');ctx.fillRect(0,0,320,460)
    ctx.setFillStyle('#15254a');ctx.setFontSize(28);ctx.fillText('园区伙伴',30,54)
    ctx.setFontSize(16);ctx.fillText('连接客户与园区服务',30,86)
    ctx.setFillStyle('#ffffff');ctx.fillRect(30,116,260,238)
    ctx.drawImage(code,70,132,180,180)
    ctx.setFillStyle('#15254a');ctx.setFontSize(16);ctx.fillText('微信扫码，加入伙伴团队',64,337)
    ctx.setFontSize(14);ctx.fillText('邀请码',30,390);ctx.setFontSize(26);ctx.fillText(info.value.inviteCode,104,392)
    ctx.setFillStyle('#58617d');ctx.setFontSize(12);ctx.fillText('推荐客户 · 查看进度 · 团队协作',30,430)
    await new Promise(resolve=>ctx.draw(false,resolve))
    const result=await new Promise((resolve,reject)=>uni.canvasToTempFilePath({canvasId:'invitePoster',width:320,height:460,destWidth:640,destHeight:920,success:resolve,fail:reject},instance.proxy))
    poster.value=result.tempFilePath
  }catch(e){error.value=e.message||e.errMsg||'海报生成失败，请重试'}finally{
    busy.value=false
    // #ifdef H5
    if(code?.startsWith('blob:'))URL.revokeObjectURL(code)
    // #endif
  }
}
async function save(){
  // #ifdef H5
  const link=document.createElement('a');link.href=poster.value;link.download='园区伙伴邀请海报.png';link.click()
  // #endif
  // #ifdef MP-WEIXIN
  try{await uni.saveImageToPhotosAlbum({filePath:poster.value});uni.showToast({title:'已保存'})}catch(e){error.value='保存未完成，请允许保存到相册，或点击海报长按保存'}
  // #endif
}
</script>
<style scoped>.invite-code{font-size:48rpx;font-weight:600;letter-spacing:4rpx;margin:32rpx 0;color:#263c78}.poster{width:100%;margin-top:24rpx}.error{color:#a3293e;margin:20rpx 0;line-height:1.6}.canvas{position:absolute;left:-10000px;top:0}</style>
