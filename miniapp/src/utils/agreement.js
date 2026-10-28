// Agreement links on the login/register forms must open real content (WeChat review rejects silent taps).
export function openAgreement(type) {
  uni.navigateTo({ url: `/pages/agreement/index?type=${type}` })
}

// Prefer WeChat's official privacy guide configured in the MP console; fall back to the in-app policy page.
export function openPrivacy() {
  // #ifdef MP-WEIXIN
  if (typeof wx !== 'undefined' && wx.openPrivacyContract) {
    wx.openPrivacyContract({ fail: () => openAgreement('privacy') })
    return
  }
  // #endif
  openAgreement('privacy')
}
