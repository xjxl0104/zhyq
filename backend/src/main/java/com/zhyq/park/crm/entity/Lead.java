package com.zhyq.park.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_lead")
public class Lead extends BaseEntity {
    private String contact;
    private String phone;
    private String company;
    /** 状态:1新建 2待分配 3跟进中 4意向 5已转化 6无效 7公海 8审核中 */
    private Integer status;
    private String source;
    private Long intentProject;
    private String demandArea;
    private Long ownerId;
    private Long channelId;
    private LocalDateTime nextFollow;
    private String remark;
}
