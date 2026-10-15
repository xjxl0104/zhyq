package com.zhyq.park.marketing.yunshan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * 云山外部接口签名(《云山外部接口调用说明 V1.1》§2.2):
 * 实际出现的 Query 参数 → 按参数名 ASCII 升序的 UTF-8 紧凑 JSON(值一律字符串)→ 小写十六进制 MD5
 * → 云山 RSA 公钥加密 MD5 文本 → Base64,放 {@code sign} 请求头。
 */
public final class YunshanSigner {

    private static final ObjectMapper JSON = new ObjectMapper();

    private YunshanSigner() {
    }

    static String canonicalJson(Map<String, String> params) {
        try {
            // TreeMap 的自然序即 String.compareTo,对 ASCII 参数名等同 ASCII 升序
            return JSON.writeValueAsString(new TreeMap<>(params));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String md5Hex(String text) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sign(Map<String, String> params, PublicKey key) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] enc = cipher.doFinal(md5Hex(canonicalJson(params)).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(enc);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("云山签名失败,请核对公钥配置", e);
        }
    }

    /** 接受 PEM(含 BEGIN/END 头尾)或纯 Base64 的 X.509 公钥。 */
    public static PublicKey parsePublicKey(String pemOrBase64) {
        try {
            if (pemOrBase64 == null || pemOrBase64.isBlank()) throw new IllegalArgumentException("公钥为空");
            // 环境变量也可使用字面量 \\n 保存 PEM 换行。
            String b64 = pemOrBase64.replace("\\n", "\n").replace("\\r", "\r")
                    .replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("云山公钥格式无效", e);
        }
    }
}
