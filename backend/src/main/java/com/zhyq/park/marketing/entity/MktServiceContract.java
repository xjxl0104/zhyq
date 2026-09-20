package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 云仓服务合同(表 crm_service_contract,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_service_contract")
public class MktServiceContract extends BaseEntity {
    /** CS-yyyyMM-0001 */
    private String contractNo;
    /** 1园区签 2云仓直签(合同期内快照) */
    private Integer signMode;
    private Long customerId;
    /** 主仓(冗余,明细在 crm_contract_warehouse) */
    private Long warehouseId;
    /** 成交伙伴(推荐人快照) */
    private Long partnerId;
    /** 评级快照 */
    private String grade;
    /** 1仓储 2代发 3仓配 */
    private Integer serviceType;
    /** 1仓租 2单票 3按件 4包月 */
    private Integer feeModel;
    /** {"perOrder":x,"perItem":y,"storage":z,"monthly":m} */
    private String priceTable;
    private BigDecimal deposit;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer autoRenew;
    /** 1周 2半月 3月 */
    private Integer payCycle;
    /** 1草稿 2待审核 3待客户签 4已生效 5履约中 6变更中 7到期 8终止 9作废 */
    private Integer status;
    /** 1线下上传 2电子签 */
    private Integer signMethod;
    private LocalDateTime signedAt;
    private LocalDateTime effectiveAt;
    /** 合同版本(变更/续签 +1) */
    private Integer contractVersion;
    private Long templateId;
    /** 签署件等附件 id 列表 */
    private String files;
    private String auditReason;
    private String terminateReason;
    private String remark;
    private Long projectId;
}
