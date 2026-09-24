package com.zhyq.park.marketing.password;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Resolve a rate-limit address from the actual socket peer and an explicitly trusted proxy chain.
 * Never use the first XFF entry blindly. Keep server.forward-headers-strategy=none so remoteAddr
 * remains the socket peer; each trusted reverse proxy must append or replace XFF itself.
 */
@Component
public class ClientAddressResolver {
    private static final int MAX_HEADER_LENGTH = 2048;
    private static final int MAX_HOPS = 16;
    private final List<IpAddressMatcher> trustedProxies;

    public ClientAddressResolver(@Value("${zhyq.auth.trusted-proxies:}") String configuredProxies) {
        List<IpAddressMatcher> matchers = new ArrayList<>();
        if (configuredProxies != null && !configuredProxies.isBlank()) {
            for (String configured : configuredProxies.split(",", -1)) {
                String[] parts = configured.trim().split("/", -1);
                String address = numericAddress(parts[0]);
                if (address == null || parts.length > 2) throw new IllegalArgumentException("trusted-proxies 必须是数值 IP 或 CIDR");
                String cidr = address;
                if (parts.length == 2) {
                    try {
                        int prefix = Integer.parseInt(parts[1]);
                        int max = address.contains(":") ? 128 : 32;
                        // An all-address trust entry defeats this boundary and is never valid.
                        if (prefix <= 0 || prefix > max) throw new NumberFormatException();
                        cidr += "/" + prefix;
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("trusted-proxies 的 CIDR 掩码无效，不能信任所有来源");
                    }
                }
                matchers.add(new IpAddressMatcher(cidr));
            }
        }
        trustedProxies = List.copyOf(matchers);
    }

    public String resolve(HttpServletRequest request) {
        String peer = numericAddress(request.getRemoteAddr());
        if (peer == null) return "unknown-peer";
        if (!isTrusted(peer)) return peer;
        Enumeration<String> headers = request.getHeaders("X-Forwarded-For");
        if (headers == null || !headers.hasMoreElements()) return peer;
        List<String> hops = new ArrayList<>();
        int totalLength = 0;
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            if (header == null || (totalLength += header.length()) > MAX_HEADER_LENGTH) return peer;
            for (String part : header.split(",", -1)) {
                String address = numericAddress(part.trim());
                if (address == null || hops.size() >= MAX_HOPS) return peer;
                hops.add(address);
            }
        }
        String current = peer;
        for (int index = hops.size() - 1; index >= 0 && isTrusted(current); index--) {
            current = hops.get(index);
        }
        return current;
    }

    private boolean isTrusted(String address) {
        return trustedProxies.stream().anyMatch(proxy -> proxy.matches(address));
    }

    /** Validate literals before InetAddress parsing, preventing DNS lookups and ambiguous host names. */
    private static String numericAddress(String value) {
        if (value == null || value.isEmpty() || value.length() > 45) return null;
        if (value.contains(":")) {
            if (!value.matches("[0-9A-Fa-f:.]+")) return null;
        } else if (!value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) {
            return null;
        }
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (UnknownHostException e) { return null; }
    }
}
