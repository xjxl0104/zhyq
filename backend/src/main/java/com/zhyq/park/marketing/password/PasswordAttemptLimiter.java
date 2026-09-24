package com.zhyq.park.marketing.password;

import com.zhyq.park.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** 单实例窗口限速；同时按账号与连接 IP 限制，限制 key 总量以防匿名请求撑满内存。 */
@Component
public class PasswordAttemptLimiter {
    private static final long WINDOW_MS = 15 * 60_000L;
    private static final int MAX_KEYS = 20_000;
    private record Window(long expiresAt, int count) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;

    public PasswordAttemptLimiter() { this(Clock.systemUTC()); }
    PasswordAttemptLimiter(Clock clock) { this.clock = clock; }

    public synchronized void check(String key, int maxAttempts) {
        long now = clock.millis();
        Window current = windows.get(key);
        if (current != null && current.expiresAt() > now && current.count() >= maxAttempts) {
            throw new BizException(429, "尝试次数过多，请 15 分钟后重试");
        }
        if (current == null || current.expiresAt() <= now) {
            if (windows.size() >= MAX_KEYS) windows.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
            if (windows.size() >= MAX_KEYS && !windows.containsKey(key)) {
                throw new BizException(429, "登录请求较多，请稍后重试");
            }
            windows.put(key, new Window(now + WINDOW_MS, 1));
        } else {
            windows.put(key, new Window(current.expiresAt(), current.count() + 1));
        }
    }

    public synchronized void clear(String key) { windows.remove(key); }
}
