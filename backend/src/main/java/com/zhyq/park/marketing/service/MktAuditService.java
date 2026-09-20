package com.zhyq.park.marketing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.marketing.entity.SysAudit;
import com.zhyq.park.marketing.mapper.SysAuditMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 全民营销业务审计:改上级 / 调岗 / 作废 / 打款 / 改份额等所有写操作落 sys_audit。
 * 与 {@code common/audit/AuditAspect}(按接口记请求)互补:这里记的是业务语义 + 前后快照 + 原因。
 * 审计失败只打日志,不能把业务事务一起回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MktAuditService {

    public static final String MODULE = "crm.marketing";
    private static final int JSON_MAX = 4000;

    private final SysAuditMapper auditMapper;
    private final ObjectMapper objectMapper;

    public void log(String action, String bizType, Long bizId, String reason) {
        log(action, bizType, bizId, reason, null, null);
    }

    public void log(String action, String bizType, Long bizId, String reason, Object before, Object after) {
        try {
            SysAudit a = new SysAudit();
            a.setModule(MODULE);
            a.setAction(action);
            a.setBizType(bizType);
            a.setBizId(bizId);
            a.setReason(reason);
            a.setBeforeJson(toJson(before));
            a.setAfterJson(toJson(after));
            a.setOperator(currentOperator());
            auditMapper.insert(a);
        } catch (Exception e) {
            log.warn("写审计失败 action={} bizType={} bizId={}: {}", action, bizType, bizId, e.getMessage());
        }
    }

    public static String currentOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }

    private String toJson(Object o) {
        if (o == null) return null;
        try {
            String s = o instanceof String str ? str : objectMapper.writeValueAsString(o);
            return s.length() > JSON_MAX ? s.substring(0, JSON_MAX) : s;
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
