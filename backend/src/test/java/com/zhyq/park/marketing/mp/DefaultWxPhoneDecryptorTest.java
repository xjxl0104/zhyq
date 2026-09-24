package com.zhyq.park.marketing.mp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import org.junit.jupiter.api.Test;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class DefaultWxPhoneDecryptorTest {
    @Test void legacyPhoneAuthorizationChecksAppIdWatermark() throws Exception {
        byte[] key = "1234567890123456".getBytes(StandardCharsets.UTF_8);
        byte[] iv = "abcdefghijklmnop".getBytes(StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        String encrypted = Base64.getEncoder().encodeToString(cipher.doFinal(
                "{\"purePhoneNumber\":\"13800138000\",\"countryCode\":\"86\",\"watermark\":{\"appid\":\"app\"}}".getBytes(StandardCharsets.UTF_8)));
        String session = Base64.getEncoder().encodeToString(key), vector = Base64.getEncoder().encodeToString(iv);
        DefaultWxPhoneDecryptor decryptor = new DefaultWxPhoneDecryptor(new ObjectMapper());
        assertThat(decryptor.decryptPhoneNumber(session, encrypted, vector, "app")).isEqualTo("13800138000");
        assertThatThrownBy(() -> decryptor.decryptPhoneNumber(session, encrypted, vector, "other-app"))
                .isInstanceOf(BizException.class).hasMessageContaining("来源不匹配");
    }
}
