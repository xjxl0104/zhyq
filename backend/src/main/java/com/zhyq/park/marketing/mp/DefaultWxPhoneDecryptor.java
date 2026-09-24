package com.zhyq.park.marketing.mp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class DefaultWxPhoneDecryptor implements WxPhoneDecryptor {
    private final ObjectMapper objectMapper;
    @Override public String decryptPhoneNumber(String sessionKey, String encryptedData, String iv) {
        return decryptPhoneNumber(sessionKey, encryptedData, iv, null);
    }
    @Override public String decryptPhoneNumber(String sessionKey, String encryptedData, String iv, String appId) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(sessionKey), "AES"),
                    new IvParameterSpec(Base64.getDecoder().decode(iv)));
            var json = objectMapper.readTree(cipher.doFinal(Base64.getDecoder().decode(encryptedData)));
            if (appId != null && !appId.equals(json.path("watermark").path("appid").asText()))
                throw new BizException("手机号授权来源不匹配");
            if (json.hasNonNull("countryCode") && !"86".equals(json.path("countryCode").asText()))
                throw new BizException("暂仅支持中国大陆手机号");
            return json.path("purePhoneNumber").asText(json.path("phoneNumber").asText(null));
        } catch (BizException e) { throw e; }
        catch (Exception e) { throw new BizException("手机号授权解密失败"); }
    }
}
