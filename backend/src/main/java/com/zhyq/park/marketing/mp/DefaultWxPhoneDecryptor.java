package com.zhyq.park.marketing.mp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class DefaultWxPhoneDecryptor implements WxPhoneDecryptor {
    private final ObjectMapper objectMapper;
    @Override public String decryptPhoneNumber(String sessionKey, String encryptedData, String iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(sessionKey), "AES"),
                    new IvParameterSpec(Base64.getDecoder().decode(iv)));
            var json = objectMapper.readTree(cipher.doFinal(Base64.getDecoder().decode(encryptedData)));
            return json.path("phoneNumber").asText(null);
        } catch (Exception e) { throw new BizException("手机号授权解密失败"); }
    }
}
