// 普通页面只分享小程序入口，避免带出客户 ID、订单参数或当前业务截图。
export function createAppShare() {
  return {
    title: '数智云仓全民营销助手',
    path: '/pages/entry/index',
    imageUrl: '/static/share/app-card.png',
  }
}

// 使用页面级 Options API mixin，让 uni-app 在编译后的微信页面注册转发入口。
// 邀请伙伴页保留自己的 onShareAppMessage，不接入此默认处理器。
export const appShareMixin = {
  onShareAppMessage() {
    return createAppShare()
  },
}
