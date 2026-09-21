package com.zhyq.park.marketing.open;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.marketing.entity.MktWarehouseErp;
import com.zhyq.park.marketing.mapper.MktWarehouseErpMapper;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 开放接口验签(PARK-MKT-001 §5.3):
 * <pre>
 *   X-App-Id     wh_xxx
 *   X-Timestamp  Unix 秒,与服务器差 ≤ 300s
 *   X-Nonce      随机串,5 分钟内不得重复
 *   X-Signature  hex( HMAC-SHA256( secret, timestamp + "\n" + nonce + "\n" + body ) )
 * </pre>
 * nonce 用 crm_erp_nonce 主键唯一兜底(项目无 Redis),每小时清理过期行。
 * 签名比较用常量时间,避免时序侧信道。
 */
@Service
@RequiredArgsConstructor
public class ErpSignatureService {

    public static final long MAX_SKEW_SECONDS = 300;
    private static final long NONCE_TTL_SECONDS = 300;
    private static final SecureRandom RND = new SecureRandom();

    private final MktWarehouseErpMapper erpMapper;
    private final FieldEncryptionService encryption;
    private final JdbcTemplate jdbc;

    public sealed interface VerifyResult permits Ok, Fail {
    }

    public record Ok(MktWarehouseErp credential) implements VerifyResult {
    }

    public record Fail(String code, String message) implements VerifyResult {
    }

    public VerifyResult verify(String appId, String timestamp, String nonce, String signature, String body) {
        if (isBlank(appId) || isBlank(timestamp) || isBlank(nonce) || isBlank(signature)) {
            return new Fail("SIGN_INVALID", "缺少签名头 X-App-Id / X-Timestamp / X-Nonce / X-Signature");
        }
        MktWarehouseErp cred = erpMapper.selectOne(new LambdaQueryWrapper<MktWarehouseErp>()
                .eq(MktWarehouseErp::getAppId, appId).last("limit 1"));
        if (cred == null) {
            return new Fail("SIGN_INVALID", "App-Id 不存在");
        }
        if (cred.getStatus() == null || cred.getStatus() != 1) {
            return new Fail("APP_DISABLED", "凭证已停用");
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return new Fail("SIGN_INVALID", "X-Timestamp 须为 Unix 秒");
        }
        if (Math.abs(Instant.now().getEpochSecond() - ts) > MAX_SKEW_SECONDS) {
            return new Fail("SIGN_INVALID", "时间戳与服务器相差超过 " + MAX_SKEW_SECONDS + " 秒");
        }
        String expected = sign(encryption.decrypt(cred.getSecretEnc()), timestamp.trim(), nonce.trim(), body == null ? "" : body);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            return new Fail("SIGN_INVALID", "签名不匹配");
        }
        if (!consumeNonce(appId, nonce.trim())) {
            return new Fail("NONCE_REPLAY", "nonce 重复");
        }
        return new Ok(cred);
    }

    /** 计算签名(云仓侧同一算法);public 便于契约文档示例与测试。 */
    public static String sign(String secret, String timestamp, String nonce, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = timestamp + "\n" + nonce + "\n" + body;
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    public static String newAppId() {
        return "wh_" + randomToken(12);
    }

    public static String newSecret() {
        return randomToken(48);
    }

    public static String sha256Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    private boolean consumeNonce(String appId, String nonce) {
        try {
            jdbc.update("INSERT INTO crm_erp_nonce(app_id, nonce, expire_at) VALUES (?,?,?)",
                    appId, nonce, LocalDateTime.now().plusSeconds(NONCE_TTL_SECONDS));
            return true;
        } catch (DuplicateKeyException dup) {
            return false;
        }
    }

    @Scheduled(initialDelay = 600_000, fixedDelay = 3_600_000)
    public void purgeExpiredNonce() {
        jdbc.update("DELETE FROM crm_erp_nonce WHERE expire_at < ?", LocalDateTime.now());
    }

    private static String randomToken(int len) {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(alphabet.charAt(RND.nextInt(alphabet.length())));
        return sb.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
