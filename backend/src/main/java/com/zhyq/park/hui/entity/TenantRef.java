package com.zhyq.park.hui.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 租客只读引用(仅取政策匹配需要的字段),避免跨模块依赖 crm 包的完整 Tenant 实体。
 */
@Data
@TableName("biz_tenant")
public class TenantRef {
    @TableId
    private Long id;
    private String name;
    private String industry;
}
