package com.zhyq.park.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 跟进记录,字段对齐《…登记表·回访跟进记录表》。一条线索对应多行,可反复追加。
 * 新增后由 FollowController 回写线索的 lastFollowDate / followCount。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_follow")
public class Follow extends BaseEntity {

    /** 跟进编号 GF-0001,服务端生成 */
    private String followNo;
    private Long leadId;
    private LocalDate followDate;
    /** 跟进方式:电话/微信·在线沟通/上门拜访/邀约看仓/邮件/其他 */
    private String type;
    /** 跟进人 */
    private String followBy;
    /** 沟通要点/客户反馈 */
    private String content;
    /** 客户意向变化:明显升温/持平/降温/已成交/已流失 */
    private String intentChange;
    /** 遇到的问题/需协调事项 */
    private String issue;
    /** 下次跟进计划时间 */
    private LocalDateTime nextFollow;
    /** 跟进结果/待办事项 */
    private String result;
}
