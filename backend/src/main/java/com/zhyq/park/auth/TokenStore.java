package com.zhyq.park.auth;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 token 仓库(演示级):token → 用户名。8 小时过期,应用重启全部失效。
 */
@Component
public class TokenStore {

    private static final long TTL_MS = 8 * 60 * 60 * 1000L;

    private record Entry(String username, long expireAt) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public String issue(String username) {
        String token = UUID.randomUUID().toString().replace("-", "");
        store.put(token, new Entry(username, System.currentTimeMillis() + TTL_MS));
        return token;
    }

    /** 校验并返回用户名;无效/过期返回 null */
    public String validate(String token) {
        if (token == null) return null;
        Entry e = store.get(token);
        if (e == null) return null;
        if (System.currentTimeMillis() > e.expireAt()) {
            store.remove(token);
            return null;
        }
        return e.username();
    }

    public void revoke(String token) {
        store.remove(token);
    }
}
