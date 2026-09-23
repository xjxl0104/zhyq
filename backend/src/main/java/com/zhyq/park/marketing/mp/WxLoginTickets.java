package com.zhyq.park.marketing.mp;

import com.zhyq.park.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-only session data addressed by an unpredictable, short-lived, single-use login ticket. */
public final class WxLoginTickets {
    private final Map<String, PendingLogin> pending = new ConcurrentHashMap<>();
    private final Clock clock;
    private record PendingLogin(String openid, String sessionKey, long expiresAt) {}

    public WxLoginTickets() { this(Clock.systemUTC()); }
    WxLoginTickets(Clock clock) { this.clock = clock; }

    public String issue(WxSessionClient.Session session) {
        long now = clock.millis();
        pending.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
        if (pending.size() >= 10_000) throw new BizException("登录请求较多,请稍后重试");
        String ticket = UUID.randomUUID().toString();
        pending.put(ticket, new PendingLogin(session.openid(), session.sessionKey(), now + 300_000));
        return ticket;
    }

    public String consume(String ticket, String openid) {
        if (!StringUtils.hasText(ticket) || !StringUtils.hasText(openid))
            throw new BizException("缺少登录凭证,请重新微信登录");
        PendingLogin login = pending.get(ticket);
        if (login == null || login.expiresAt() <= clock.millis() || !login.openid().equals(openid)
                || !pending.remove(ticket, login))
            throw new BizException("登录凭证已失效,请重新微信登录");
        return login.sessionKey();
    }
}
