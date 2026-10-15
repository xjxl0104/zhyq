package com.zhyq.park.marketing.yunshan;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YunshanSignerTest {
    private static KeyPair keyPair;

    @BeforeAll static void keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @Test void canonicalJsonAndMd5MatchPublishedContract() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("startDate", "2026-10-01");
        params.put("queryType", "daily");
        params.put("endDate", "2026-10-02");
        String canonical = YunshanSigner.canonicalJson(params);
        assertThat(canonical).isEqualTo("{\"endDate\":\"2026-10-02\",\"queryType\":\"daily\",\"startDate\":\"2026-10-01\"}");
        assertThat(YunshanSigner.md5Hex(canonical)).isEqualTo("d58346c866cfc350725565f07f9a9fec");
    }

    @Test void signatureIsStandardBase64OfRsaPkcs1EncryptedLowercaseMd5Text() throws Exception {
        Map<String, String> params = Map.of("startDate", "2026-10-01", "endDate", "2026-10-02", "queryType", "daily");
        String sign = YunshanSigner.sign(params, keyPair.getPublic());
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        assertThat(new String(cipher.doFinal(Base64.getDecoder().decode(sign)), StandardCharsets.UTF_8))
                .isEqualTo("d58346c866cfc350725565f07f9a9fec");
        assertThat(sign).doesNotContain("\n", "\r");
    }

    @Test void canonicalJsonKeepsStringsAndUtf8AndEscapesOnlyJsonCharacters() {
        assertThat(YunshanSigner.canonicalJson(Map.of("size", "20", "keyword", "棒棒猪 + \"测试\"", "empty", "")))
                .isEqualTo("{\"empty\":\"\",\"keyword\":\"棒棒猪 + \\\"测试\\\"\",\"size\":\"20\"}");
        assertThat(YunshanSigner.canonicalJson(Map.of())).isEqualTo("{}");
    }

    @Test void parsesPemRawBase64AndEnvironmentEscapedPem() {
        String b64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String pem = "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----";
        assertThat(YunshanSigner.parsePublicKey(b64)).isEqualTo(keyPair.getPublic());
        assertThat(YunshanSigner.parsePublicKey(pem)).isEqualTo(keyPair.getPublic());
        assertThat(YunshanSigner.parsePublicKey(pem.replace("\n", "\\n"))).isEqualTo(keyPair.getPublic());
    }

    @Test void invalidKeysProduceConfigurationErrorWithoutEchoingKey() {
        assertThatThrownBy(() -> YunshanSigner.parsePublicKey("not-a-public-key"))
                .isInstanceOf(IllegalStateException.class).hasMessage("云山公钥格式无效");
        assertThatThrownBy(() -> YunshanSigner.parsePublicKey(null))
                .isInstanceOf(IllegalStateException.class).hasMessage("云山公钥格式无效");
    }
}
