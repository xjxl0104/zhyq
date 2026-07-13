package com.zhyq.park.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer")
public class Customer extends BaseEntity {
    /** 客户/公司名 */
    private String name;
    private String contact;
    private String phone;
    private String industry;
    /** 需求面积段 */
    private String demandArea;
    /** 意向等级 A/B/C */
    private String intentLevel;
    /** 来源线索 */
    private Long sourceLeadId;
    private String owner;
    /** 状态:1跟进中 2已签约 3已流失 */
    private Integer status;
    private String remark;
}
