package com.zhyq.park.iot.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iot_vendor")
public class Vendor extends BaseEntity {
    private String name;
    private String platform;
    private String apiUrl;
    private String appKey;
    private String appSecret;
    private Integer status;
    private String remark;
}
