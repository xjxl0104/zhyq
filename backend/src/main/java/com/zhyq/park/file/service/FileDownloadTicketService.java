package com.zhyq.park.file.service;

import com.zhyq.park.common.exception.BizException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** 单实例下载交接凭证：不携带登录权限，两分钟内仅可兑换指定附件一次。 */
@Service
public class FileDownloadTicketService {
    private static final int MAX_PENDING_TICKETS = 2048;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Ticket> tickets = new HashMap<>();

    public FileDownloadTicketService() {
        this(Clock.systemUTC());
    }

    FileDownloadTicketService(Clock clock) {
        this.clock = clock;
    }

    public synchronized String issue(Long fileId) {
        Instant now = clock.instant();
        tickets.values().removeIf(ticket -> !now.isBefore(ticket.expiresAt()));
        if (tickets.size() >= MAX_PENDING_TICKETS) {
            throw new BizException(429, "下载请求过多，请稍后重试");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(token, new Ticket(fileId, now.plusSeconds(120)));
        return token;
    }

    public synchronized boolean consume(Long fileId, String token) {
        if (token == null || token.isBlank()) return false;
        Ticket ticket = tickets.remove(token);
        return ticket != null && ticket.fileId().equals(fileId)
            && clock.instant().isBefore(ticket.expiresAt());
    }

    private record Ticket(Long fileId, Instant expiresAt) {}
}
