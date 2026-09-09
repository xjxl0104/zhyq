package com.zhyq.park.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 招商线索,字段对齐《云仓产业园客户信息收集与回访登记表·客户信息登记表》。
 *
 * <p>状态(V52 起为登记表 6 态):1待跟进 2跟进中 3已约看仓/已对接 4已报价/洽谈中
 * 5已签约/已成交 6已流失/暂缓。</p>
 *
 * <p>ownerName 是自由文本(登记表里填「丁超」这类姓名,不是系统账号);ownerId 为历史字段,
 * 仍保留给既有统计用,新流程不写。</p>
 *
 * <p>lastFollowDate / followCount 由跟进记录自动回写,不接受前端入参。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_lead")
public class Lead extends BaseEntity {

    /** 客户编号 KH-0001,服务端生成 */
    private String leadNo;
    private LocalDate registerDate;
    /** 客户来源:电话咨询/上门到访/朋友介绍·转介绍/线上广告·短视频/招商中介/展会·园区活动/老客户复购/其他 */
    private String source;
    /** 客户姓名 */
    private String contact;
    private String phone;
    /** 公司名称/店铺名称 */
    private String company;
    private String customerType;
    private String coopMode;
    /** 仓库面积/库容需求(㎡) */
    private String demandArea;
    private String goodsType;
    private String orderVolume;
    private String coopPeriod;
    private String budgetPrice;
    private String intentPark;
    private String region;
    private String grade;
    private String ownerName;
    private Integer status;
    private LocalDate lastFollowDate;
    private Integer followCount;
    /** 下次跟进计划日期 */
    private LocalDateTime nextFollow;
    /** 跟进记录(登记表最后一列的备注式速记,逐次明细见 crm_follow) */
    private String remark;

    // —— 历史字段,保留兼容,新流程不再写入 ——
    private Long intentProject;
    private Long ownerId;
    private Long channelId;
}
