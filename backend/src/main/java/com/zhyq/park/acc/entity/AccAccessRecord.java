package com.zhyq.park.acc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 门禁通行记录(acc_access_record,#21)。只增不改的流水,无状态机。
 * direction: 1进 2出。result: 1放行 2拒绝。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("acc_access_record")
public class AccAccessRecord extends BaseEntity {

    private String gateCode;
    private Long spaceId;
    private String personType;
    private String personRef;
    private Integer direction;
    private Integer result;
    private LocalDateTime accessTime;
}
