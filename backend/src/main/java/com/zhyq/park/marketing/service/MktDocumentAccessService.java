package com.zhyq.park.marketing.service;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.file.entity.SysFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.*;
/** Scope extra checks to marketing documents; other modules retain their existing rules. */
@Service
public class MktDocumentAccessService {
    public void read(SysFile file) { if(file != null) check(file.getBizType(), false); }
    public void write(String bizType) { check(bizType, true); }
    public void read(String bizType) { check(bizType, false); }
    private void check(String type, boolean write) {
        if(type == null || !type.startsWith("mkt_")) return;
        List<String> permissions = switch(type) {
            case "mkt_warehouse" -> write ? List.of("warehouse:edit","onboarding:audit","contract:edit","contract:audit") : List.of("warehouse:query","onboarding:query","contract:query");
            case "mkt_withdrawal" -> write ? List.of("withdrawal:pay") : List.of("withdrawal:pay","withdrawal:audit");
            case "mkt_settlement" -> write ? List.of("settlement:pay") : List.of("settlement:query");
            case "mkt_bill" -> write ? List.of("bill:pay") : List.of("bill:query");
            case "mkt_direct_payment" -> write ? List.of("bill:pay") : List.of("bill:query");
            default -> List.of();
        };
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || auth.getAuthorities().stream().noneMatch(a->permissions.stream().anyMatch(p->("crm:marketing:"+p).equals(a.getAuthority()))))
            throw new BizException(403,"无权访问此业务附件");
    }
}
