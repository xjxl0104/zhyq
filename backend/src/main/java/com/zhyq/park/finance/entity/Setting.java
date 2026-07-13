package com.zhyq.park.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务配置(biz_setting)。财务/合同/招商/生态共用,module 区分。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_setting")
public class Setting extends BaseEntity {

    /** 模块:finance/contract/crm/ecosystem */
    private String module;
    /** 配置键 */
    private String skey;
    /** 配置值 */
    private String svalue;
    private String remark;
}
