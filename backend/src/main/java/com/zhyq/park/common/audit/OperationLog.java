package com.zhyq.park.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解(批次② §审计)。
 *
 * <p>标注在 Controller 方法上,由 {@link AuditAspect} 拦截并写入 {@code sys_oper_log}。
 * 未标注的方法不记审计,避免全量拦截产生噪声。示例:{@code @OperationLog(module="合同", action="审批通过")}。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 业务模块,如 "合同"/"财务"/"工单"。 */
    String module() default "";

    /** 操作描述,如 "审批通过"/"新增"/"删除"。 */
    String action() default "";

    /** 是否记录入参(默认记;敏感接口可关)。 */
    boolean saveParams() default true;
}
