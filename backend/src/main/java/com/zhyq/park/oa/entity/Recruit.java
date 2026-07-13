package com.zhyq.park.oa.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_recruit")
public class Recruit extends BaseEntity {
    private String postName;
    private String dept;
    private Integer headcount;
    private String salaryRange;
    private Integer applicants;
    private Integer status;
    private String remark;
}
