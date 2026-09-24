// A mock identity must be explicitly requested; device builds use real WeChat by default.
export const partnerMock = import.meta.env.VITE_MP_MOCK_LOGIN === 'true'
export const warehouseMock = import.meta.env.VITE_WH_MOCK_LOGIN === 'true'

export async function getWechatLogin(mock, testCode) {
  if (mock) {
    if (!testCode?.trim()) throw new Error('请输入开发测试标识')
    return { code: testCode.trim(), appId: '' }
  }
  // #ifdef MP-WEIXIN
  try {
    const { code } = await uni.login({ provider: 'weixin' })
    if (!code) throw new Error('微信未返回登录凭证，请重试')
    return { code, appId: uni.getAccountInfoSync().miniProgram.appId }
  } catch (error) {
    throw new Error(error.message || '微信登录未完成，请重试或使用账号密码登录')
  }
  // #endif
  // #ifndef MP-WEIXIN
  throw new Error('请在微信小程序中使用微信登录，浏览器内可使用账号密码登录')
  // #endif
}

export function readPhoneAuthorization(event) {
  const detail = event.detail || {}
  if (detail.errMsg && !detail.errMsg.endsWith(':ok')) {
    throw new Error('手机号授权未完成，请重试或使用账号密码登录')
  }
  if (detail.code) return { phoneCode: detail.code }
  if (detail.encryptedData && detail.iv) return { encryptedData: detail.encryptedData, iv: detail.iv }
  throw new Error('未取得手机号授权，请重新点击授权')
}
