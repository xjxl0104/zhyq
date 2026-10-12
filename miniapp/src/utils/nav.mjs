// 小程序模板里没有全局 `uni`，页面跳转必须通过脚本里的函数调用。
export const navigateTo = (url) => uni.navigateTo({
  url,
  fail: () => uni.showToast({ title: '页面打开失败，请返回首页重试或更新小程序', icon: 'none' })
})
export const switchTab = (url) => uni.switchTab({ url })
