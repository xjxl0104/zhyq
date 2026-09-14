package com.zhyq.park.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 中介(原「渠道商」)。表名沿用 crm_channel,佣金按 channel_id 关联。
 * 编号与跟进统计(lastFollowDate/followCount/nextFollow)由服务端维护,修改接口不接收。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_channel")
public class Channel extends BaseEntity {
    /** 中介编号 ZJ-0001,服务端生成 */
    private String agencyNo;
    /** 中介名称 */
    private String name;
    /** 中介类型:中介公司/个人经纪人/物流·电商服务商/商会·协会/其他 */
    private String agencyType;
    private String contact;
    private String phone;
    private String wechat;
    /** 所在地区/覆盖区域 */
    private String region;
    private String address;
    /** 擅长业务/客户资源方向 */
    private String resourceDesc;
    /** 合作等级:A-核心合作/B-一般合作/C-潜在合作 */
    private String grade;
    /** 是否已签合作协议 0否 1是 */
    private Integer agreementSigned;
    /** 佣金比例% */
    private BigDecimal commissionRate;
    /** 园区对接负责人 */
    private String ownerName;
    /** 累计推荐客户数 */
    private Integer referralCount;
    /** 累计成交客户数 */
    private Integer dealCount;
    /** 1合作中 0暂停合作 */
    private Integer status;
    private String remark;
    private LocalDate lastFollowDate;
    private Integer followCount;
    private LocalDateTime nextFollow;
}
