package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 伙伴岗位与份额(表 crm_position,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_position")
public class MktPosition extends BaseEntity {
    /** P1..P4 */
    private String code;
    /** 园区伙伴/银牌合伙人/金牌合伙人/钻石合伙人 */
    private String name;
    private Integer sort;
    /** 份额%:该岗位可拿总比例的百分比,顶格 100 */
    private Integer sharePct;
    /** 钻石合伙人可下调的下限 */
    private Integer shareMinPct;
    /** 锁定客户数上限(含预锁) */
    private Integer lockCap;
    /** 晋升门槛:近12月成交额 */
    private BigDecimal promoteAmount;
    /** 晋升门槛:近12月成交单数 */
    private Integer promoteOrders;
    /** 门槛是否计直属团队成交 */
    private Integer teamCounted;
    /** 是否允许自动降级 */
    private Integer demoteEnabled;
    private Integer status;
    private Long projectId;
}
