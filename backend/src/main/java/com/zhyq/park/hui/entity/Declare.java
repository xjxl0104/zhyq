package com.zhyq.park.hui.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_declare")
public class Declare extends BaseEntity {
    private String title;
    private String dtype;
    private Long tenantRefId;
    private String materials;
    private LocalDate deadline;
    private Integer status;
    private String remark;
}
