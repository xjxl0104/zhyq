package com.zhyq.park.contract.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import com.zhyq.park.file.entity.SysFile;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 园区合同模板库(租赁合同 / 物业合同 / 简易合同 / 补充协议等)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_contract_template")
public class ParkContractTemplate extends BaseEntity {

    private String name;
    private String category;
    private String versionTag;
    private String applicableScope;
    private Integer status;
    private Integer sort;
    private String remark;

    @TableField(exist = false)
    private List<SysFile> files;
}
