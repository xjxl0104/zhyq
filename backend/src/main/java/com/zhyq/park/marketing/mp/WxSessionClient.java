package com.zhyq.park.marketing.mp;

public interface WxSessionClient {
    Session exchange(String appId, String appSecret, String jsCode);
    record Session(String openid, String sessionKey) {}
}
