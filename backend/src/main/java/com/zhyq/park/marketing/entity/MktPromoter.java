package com.zhyq.park.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 园区伙伴(表 crm_promoter,V57) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_promoter")
public class MktPromoter extends BaseEntity {
    /** 微信 openid(后台手工录入的伙伴为空) */
    private String openid;
    private String unionid;
    private String phone;
    private String name;
    private String avatar;
    /** 8 位,去掉 0/O/1/I */
    private String inviteCode;
    /** 上级,终身绑定,只有运营可改 */
    private Long parentId;
    /** 物化路径 /根id/…/自己id/ */
    private String path;
    private String positionCode;
    private LocalDateTime positionSince;
    /** 1正常 2冻结 3待审核 4已退出 */
    private Integer status;
    /** 手机号命中 sys_user → 内部人员,默认不计佣 */
    private Integer isInternal;
    private String agreementVersion;
    private LocalDateTime agreedAt;
    private Integer idVerified;
    /** 绑定上级时间 */
    private LocalDateTime bindTime;
    /** 无邀请码注册后可补填的截止时间 */
    private LocalDateTime inviteDeadline;
    private LocalDateTime lastLogin;
    private String registerIp;
    private String deviceId;
    /** mp 小程序 / admin 后台录入 */
    private String source;
    private String remark;
    private Long projectId;
}
