package com.zhyq.park.marketing.service;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.FileBusinessAccessMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Business permission checks for every attachment entry point; unknown types are denied. */
@Service
@RequiredArgsConstructor
public class MktDocumentAccessService {
    private final FileBusinessAccessMapper businessAccess;
    private static final Map<String, String> RESOURCES = Map.ofEntries(
            Map.entry("contract", "contract"), Map.entry("customer", "crm:customer"),
            Map.entry("crm_lead", "crm:lead"), Map.entry("budget", "budget"),
            Map.entry("pur_request", "pur:request"), Map.entry("supplier", "pur:supplier"),
            Map.entry("supplier_contract", "pur:supplierContract"), Map.entry("invoice", "finance:invoice"),
            Map.entry("receipt", "finance:receipt"), Map.entry("finance_flow_receipt", "finance:flow"),
            Map.entry("message", "system:message"), Map.entry("tenant", "tenant"),
            Map.entry("device", "iot:device"), Map.entry("vendor", "iot:vendor"),
            Map.entry("decoration", "service:decoration"), Map.entry("goods_pass", "service:pass"),
            Map.entry("declare", "service:declare"), Map.entry("policy", "service:policy"),
            Map.entry("pm_asset", "property:asset"), Map.entry("inspection", "property:inspection"),
            Map.entry("complaint", "property:feedback"), Map.entry("meeting", "property:meeting"),
            Map.entry("work_order", "property:workorder"), Map.entry("patrol", "property:patrol"),
            Map.entry("activity", "property:activity"), Map.entry("pm_check", "property:check"),
            Map.entry("oa_notice", "oa:notice"), Map.entry("article", "oa:article"),
            Map.entry("recruit", "oa:recruit"), Map.entry("oa_task", "oa:task"),
            Map.entry("oa_document", "oa:document"), Map.entry("energy_reading", "energy:reading"));

    public void read(SysFile file) {
        if (file == null) throw denied();
        Authentication auth = authentication();
        // Untyped legacy screenshots are private, never a route around business permissions.
        if (blank(file.getBizType())) {
            if (file.getBizId() != null) throw denied();
            if (owner(file, auth) || has(auth, "ROLE_admin")) return;
            if (has(auth, "suggestion:manage") && businessAccess.countOwnedSuggestionLinks(file.getId()) > 0) return;
            throw denied();
        }
        check(file.getBizType(), false);
        if (file.getBizId() == null && !owner(file, auth) && !has(auth, "ROLE_admin")) throw denied();
    }

    public void write(String type) {
        if (blank(type)) { authentication(); return; } // authenticated private upload; no business association allowed
        check(type, true);
    }

    public void read(String type) { check(type, false); }

    public void attach(SysFile file, String targetType) {
        Authentication auth = authentication();
        if (file == null || file.getBizId() != null || (!owner(file, auth) && !has(auth, "ROLE_admin"))) throw denied();
        if (!blank(file.getBizType()) && !file.getBizType().equals(targetType)) throw denied();
        write(targetType);
    }

    private void check(String type, boolean write) {
        Authentication auth = authentication();
        List<String> permissions;
        if (type != null && type.startsWith("mkt_")) {
            List<String> marketing = switch (type) {
                case "mkt_warehouse" -> write ? List.of("warehouse:edit", "onboarding:audit", "contract:edit", "contract:audit") : List.of("warehouse:query", "onboarding:query", "contract:query");
                case "mkt_withdrawal" -> write ? List.of("withdrawal:pay") : List.of("withdrawal:pay", "withdrawal:audit");
                case "mkt_settlement" -> write ? List.of("settlement:pay") : List.of("settlement:query");
                case "mkt_bill", "mkt_direct_payment" -> write ? List.of("bill:pay") : List.of("bill:query");
                default -> List.of();
            };
            permissions = marketing.stream().map(p -> "crm:marketing:" + p).toList();
        } else {
            String resource = type == null ? null : RESOURCES.get(type);
            if (resource == null) throw denied();
            if ("finance_flow_receipt".equals(type) && write) permissions = List.of("finance:payment:pay");
            else permissions = write ? List.of(resource + ":add", resource + ":edit") : List.of(resource + ":query");
        }
        if (permissions.isEmpty()) throw denied();
        if (!has(auth, "ROLE_admin") && permissions.stream().noneMatch(p -> has(auth, p))) throw denied();
    }

    private static Authentication authentication() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || "anonymousUser".equals(a.getName())
                || has(a, "ROLE_MP") || has(a, "ROLE_WH")) throw denied();
        return a;
    }
    private static boolean owner(SysFile f, Authentication a) { return a.getName() != null && a.getName().equals(f.getCreateBy()); }
    private static boolean has(Authentication a, String permission) { return a.getAuthorities().stream().anyMatch(p -> permission.equals(p.getAuthority())); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static BizException denied() { return new BizException(403, "无权访问此业务附件"); }
}
