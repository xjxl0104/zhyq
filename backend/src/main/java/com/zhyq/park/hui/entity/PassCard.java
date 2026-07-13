package com.zhyq.park.hui.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 出入证(srv_pass_card)
 * 状态:1有效 2已挂失 3已注销
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_pass_card")
public class PassCard extends BaseEntity {
    private String cardNo;
    private String holder;
    private String phone;
    private Long tenantRefId;
    private String cardType;
    private LocalDate validStart;
    private LocalDate validEnd;
    private Integer status;
}
