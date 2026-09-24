package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 伙伴实名与收款账户(表 crm_promoter_account,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_promoter_account")
public class MktPromoterAccount extends BaseEntity {
    private Long promoterId;
    private String realName;
    /** 身份证号 AES-GCM 密文 */
    private String idNoEnc;
    /** 1微信 2银行卡 3支付宝 */
    private Integer accountType;
    /** 收款账号 AES-GCM 密文 */
    private String accountNoEnc;
    /** 账号尾号,列表脱敏展示用 */
    private String accountTail;
    private String bankName;
    private LocalDateTime verifiedAt;
    private Integer reviewStatus;
    private String reviewReason;
    private String reviewedBy;
    private Long projectId;
}
