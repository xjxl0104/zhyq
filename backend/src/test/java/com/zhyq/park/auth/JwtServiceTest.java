package com.zhyq.park.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtService 纯单元测试:无需 Spring 上下文/数据库。
 * 覆盖签发-解析往返、权限 claim、过期、错误密钥。
 */
class JwtServiceTest {

    private static final String SECRET = "test-secret-key-least-32-bytes-long-000";

    @Test
    void issueThenParse_roundTrip() {
        JwtService svc = new JwtService(SECRET, 3600);
        String token = svc.issue(42L, "alice", List.of("ROLE_admin", "file:upload"));
        Claims c = svc.parse(token);
        assertEquals("alice", c.getSubject());
        assertEquals(42, ((Number) c.get("uid")).intValue());
        assertTrue(((List<?>) c.get("auth")).contains("ROLE_admin"));
        assertTrue(((List<?>) c.get("auth")).contains("file:upload"));
    }

    @Test
    void expiredToken_rejected() throws InterruptedException {
        JwtService svc = new JwtService(SECRET, 1); // 1 秒过期
        String token = svc.issue(1L, "bob", List.of());
        Thread.sleep(1200);
        assertThrows(Exception.class, () -> svc.parse(token));
    }

    @Test
    void wrongSecret_rejected() {
        JwtService signer = new JwtService(SECRET, 3600);
        String token = signer.issue(1L, "bob", List.of());
        JwtService other = new JwtService("another-secret-key-least-32-bytes-xx", 3600);
        assertThrows(Exception.class, () -> other.parse(token));
    }

    @Test
    void expireSecondsExposed() {
        assertEquals(28800, new JwtService(SECRET, 28800).getExpireSeconds());
    }
}
