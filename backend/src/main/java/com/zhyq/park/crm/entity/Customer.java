package com.zhyq.park.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

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
    /** 伙伴填报的意向，不等于正式分派。仅业务端点写入，通用客户 CRUD 不可绕过分派流程。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long intendedWarehouseId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long assignedWarehouseId;
    /** 0未分派 1待商家确认 2已承接 3已拒绝 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer warehouseAssignmentStatus;
    /** 经明确发布、可向伙伴展示的进度，不复用内部备注。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String publicProgress;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime progressUpdatedAt;
}
