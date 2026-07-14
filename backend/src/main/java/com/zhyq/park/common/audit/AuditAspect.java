package com.zhyq.park.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.audit.mapper.OperLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作审计切面(批次② §审计)。
 *
 * <p>拦截标注 {@link OperationLog} 的方法,记录模块/操作/方法/入参/操作人/IP/成败/耗时到 {@code sys_oper_log}。
 * 操作人当前恒为 "system"(无登录鉴权,#7 落地后从上下文取真实用户)。<b>审计写入失败一律吞掉,
 * 绝不影响业务主流程与原方法返回。</b></p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final OperLogMapper operLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int PARAMS_MAX = 1900; // 与列长对齐,防溢出

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        Throwable error = null;
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                writeLog(pjp, operationLog, start, error);
            } catch (Exception e) {
                log.warn("[audit] 写审计日志失败(已吞异常)", e);
            }
        }
    }

    private void writeLog(ProceedingJoinPoint pjp, OperationLog ann, long start, Throwable error) {
        OperLog logEntry = new OperLog();
        logEntry.setModule(ann.module());
        logEntry.setAction(ann.action());
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        logEntry.setMethod(sig.getDeclaringType().getSimpleName() + "." + sig.getName());
        logEntry.setOperator("system");
        logEntry.setSuccess(error == null ? 1 : 0);
        if (error != null) {
            logEntry.setErrorMsg(truncate(error.getMessage(), 500));
        }
        logEntry.setCostMs(System.currentTimeMillis() - start);
        logEntry.setCreateTime(LocalDateTime.now());

        if (ann.saveParams()) {
            try {
                logEntry.setParams(truncate(objectMapper.writeValueAsString(pjp.getArgs()), PARAMS_MAX));
            } catch (Exception ignore) {
                logEntry.setParams("<unserializable>");
            }
        }

        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            logEntry.setHttpMethod(req.getMethod());
            logEntry.setReqUri(truncate(req.getRequestURI(), 256));
            logEntry.setIp(clientIp(req));
        }

        operLogMapper.insert(logEntry);
    }

    private String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        return req.getRemoteAddr();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
