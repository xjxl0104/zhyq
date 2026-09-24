package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 佣金规则(覆盖评级默认)(表 crm_commission_rule,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_commission_rule")
public class MktCommissionRule extends BaseEntity {
    private String name;
    /** 1租赁签约 2出库单 3平台费收款 4签约奖 5增值服务 */
    private Integer sourceType;
    /** 品类/需求类型限定,空=全部 */
    private String category;
    /** 1园区服务费 2固定每单 3货值 */
    private Integer baseMode;
    /** base_mode=2 时每单基数 */
    private BigDecimal fixedPerOrder;
    /** 覆盖冻结天数,空=用规则参数 */
    private Integer freezeDays;
    /** 按评级覆盖总比例 {"A":8,"B":6} */
    private String gradeRateOverride;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Integer status;
    private Long projectId;
}
