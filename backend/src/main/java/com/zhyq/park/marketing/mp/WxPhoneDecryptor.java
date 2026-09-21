package com.zhyq.park.marketing.mp;

public interface WxPhoneDecryptor {
    String decryptPhoneNumber(String sessionKey, String encryptedData, String iv);
}
