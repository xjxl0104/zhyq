package com.zhyq.park.marketing.mp;

public interface WxSessionClient {
    Session exchange(String appId, String appSecret, String jsCode);
    /** The phone authorization code is independent from the wx.login code. */
    default String exchangePhone(String appId, String appSecret, String phoneCode) {
        throw new UnsupportedOperationException("WeChat phone authorization is not configured");
    }
    record Session(String openid, String sessionKey) {}
}
