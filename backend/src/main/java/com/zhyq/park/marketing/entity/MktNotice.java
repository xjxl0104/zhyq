package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 云仓端通知(表 crm_notice,V64) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_notice")
public class MktNotice extends BaseEntity {
    /** 收件云仓,查询永远带这个条件,不用请求体 */
    private Long warehouseId;
    /** settlement.confirmed / settlement.disputed / bill.ready ... */
    private String type;
    private String title;
    private String content;
    /** 关联业务类型(settlement/bill),供前端跳转 */
    private String bizType;
    private Long bizId;
    /** 已读时间,null=未读 */
    private LocalDateTime readAt;
    private Long projectId;
}
