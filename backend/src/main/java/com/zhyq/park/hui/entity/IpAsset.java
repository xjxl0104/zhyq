package com.zhyq.park.hui.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_ip")
public class IpAsset extends BaseEntity {
    private String title;
    private String ipType;
    private Long tenantRefId;
    private String agency;
    private LocalDate applyDate;
    private Integer status;
}
