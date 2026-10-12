// 小程序模板里没有全局 `uni`，页面跳转必须通过脚本里的函数调用。
export const navigateTo = (url) => uni.navigateTo({ url })
export const switchTab = (url) => uni.switchTab({ url })
