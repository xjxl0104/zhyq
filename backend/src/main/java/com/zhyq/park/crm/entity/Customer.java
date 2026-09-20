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
    /** 以下为 V57 全民营销加列 */
    /** 客户评级 A/B/C/D */
    private String grade;
    /** 推荐伙伴 crm_promoter.id */
    private Long referrerId;
    /** 需求类型:1仓储 2代发 3仓配 4园区入驻 */
    private Integer serviceType;
    /** 归因业务线:1租赁 2云仓 */
    private Integer bizLine;
    /** 签约方式:1园区签 2云仓直签,空=园区默认 */
    private Integer signMode;
    /** 归因判定记录 */
    private String attributionNote;
    private Long projectId;
}
