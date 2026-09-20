package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 加盟云仓(表 crm_warehouse,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_warehouse")
public class MktWarehouse extends BaseEntity {
    /** WH-HZ-001 */
    private String code;
    private String name;
    private String region;
    private String address;
    private String contact;
    private String phone;
    /** 云仓端小程序登录用(阶段 C/D) */
    private String contactOpenid;
    private BigDecimal areaSqm;
    /** 日处理单量 */
    private Integer dailyCapacity;
    private String categories;
    /** 1申请 2资质审核 3ERP对接中 4待签协议 5已上线 6暂停 7退出 */
    private Integer joinStatus;
    /** 0未对接 1已联通(沙箱) 2已联通(正式) 3断连 */
    private Integer erpStatus;
    /** 阶段 A 人工标记已联通的运营 */
    private String erpMarkedBy;
    private LocalDateTime erpMarkedAt;
    private BigDecimal rating;
    /** 1周 2半月 3月 */
    private Integer settleCycle;
    /** 云仓费率表(园区应付) {"perOrder":x,"perItem":y,"storage":z} */
    private String feeModel;
    /** 直签平台费 {"mode":"perOrder|ratio","value":x} */
    private String platformFeeModel;
    /** 加盟协议签署件 */
    private String contractFile;
    private String remark;
    private Long projectId;
}
