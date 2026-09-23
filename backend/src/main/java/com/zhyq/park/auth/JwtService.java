package com.zhyq.park.auth;

import com.zhyq.park.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** Signed identity type + current account validation; password hashes never appear in claims. */
@Service
public class JwtService {
    private final SecretKey key;
    private final long expireSeconds;
    private final JwtAccountService accounts;

    @Autowired
    public JwtService(@Value("${zhyq.jwt.secret}") String secret,
                      @Value("${zhyq.jwt.expire-seconds:28800}") long expireSeconds, JwtAccountService accounts) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
        this.accounts = accounts;
    }

    /** Crypto-only constructor for isolated signing tests; cannot issue a validated business login. */
    public JwtService(String secret, long expireSeconds) { this(secret, expireSeconds, null); }

    public String issueForIdentity(String type, Long id) {
        if (accounts == null) throw new IllegalStateException("Account validation is required");
        JwtAccountService.Account account = accounts.load(type, id);
        return issue(account);
    }

    private String issue(JwtAccountService.Account account) {
        Date now = new Date();
        return Jwts.builder().subject(account.subject())
                .claims(Map.of("uid", account.id(), "subjectType", account.type(), "auth", account.authorities(),
                        "credentialVersion", credentialVersion(account.credentialState())))
                .issuedAt(now).expiration(new Date(now.getTime() + expireSeconds * 1000L)).signWith(key).compact();
    }

    /** Legacy signing helper always means backend identity; it cannot infer a portal role from a username. */
    public String issue(Long userId, String username, List<String> authorities) {
        return issue(new JwtAccountService.Account("admin", userId, username, authorities, "legacy-unvalidated"));
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /** Called on every authenticated request, so disabled identities and revoked permissions apply immediately. */
    public JwtAccountService.Account authenticate(Claims claims) {
        if (accounts == null) throw new IllegalStateException("Account validation is required");
        String type = claims.get("subjectType", String.class);
        Object rawId = claims.get("uid");
        if (!(rawId instanceof Number id)) throw invalid();
        JwtAccountService.Account current = accounts.load(type, id.longValue());
        String version = claims.get("credentialVersion", String.class);
        if (!current.subject().equals(claims.getSubject()) || version == null
                || !MessageDigest.isEqual(version.getBytes(StandardCharsets.UTF_8),
                        credentialVersion(current.credentialState()).getBytes(StandardCharsets.UTF_8))) throw invalid();
        return current;
    }

    private String credentialVersion(String state) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(state.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("Credential validation unavailable", e); }
    }

    private static BizException invalid() { return new BizException(401, "登录状态已失效，请重新登录"); }
    public long getExpireSeconds() { return expireSeconds; }
}
