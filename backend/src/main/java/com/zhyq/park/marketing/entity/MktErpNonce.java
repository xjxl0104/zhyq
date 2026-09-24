package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** ERP 请求 nonce(表 crm_erp_nonce,V59)。复合主键,不继承 BaseEntity;写入靠 JdbcTemplate。 */
@Data
@TableName("crm_erp_nonce")
public class MktErpNonce {
    private String appId;
    private String nonce;
    private LocalDateTime expireAt;
}
