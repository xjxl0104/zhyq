package com.zhyq.park.contract.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 简易合同台账(主要用于公司注册地址挂靠,按楼层 + 自编号房号 X01/X02 管理)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_simple_contract")
public class SimpleContract extends BaseEntity {

    private Long projectId;
    /** 楼层号: 1, 2, 3... */
    private Integer floorNo;
    /** 自编号房号: 如 101, 102, 201, 202 */
    private String roomCode;
    private String contractNo;
    private String companyName;
    private String creditCode;
    private String contactName;
    private String contactPhone;
    private String regAddress;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal annualFee;
    /** 1生效中 2已到期 3已注销 */
    private Integer status;
    private String remark;

    @TableField(exist = false)
    private Integer attachmentCount;
}
