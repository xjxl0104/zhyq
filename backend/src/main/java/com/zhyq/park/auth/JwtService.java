package com.zhyq.park.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JWT 签发/校验(HS256)。密钥与有效期由配置提供:
 *   zhyq.jwt.secret / zhyq.jwt.expire-seconds
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expireSeconds;

    public JwtService(@Value("${zhyq.jwt.secret}") String secret,
                      @Value("${zhyq.jwt.expire-seconds:28800}") long expireSeconds) {
        // HS256 要求密钥 >=32 字节;不足会抛错,提示配置更强密钥
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    /** 签发:subject=username,附带 uid 与权限标识列表 */
    public String issue(Long userId, String username, List<String> authorities) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireSeconds * 1000L);
        return Jwts.builder()
                .subject(username)
                .claims(Map.of("uid", userId, "auth", authorities))
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /** 解析并校验签名/过期;失败抛异常 */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }
}
