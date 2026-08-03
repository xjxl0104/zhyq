package com.zhyq.park.receivable.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_collection_account")
public class CollectionAccount extends BaseEntity {
    private String accountType;
    private String accountName;
    private String bankName;
    @JsonIgnore
    private String accountNoCipher;
    private String accountNoMasked;
    @JsonIgnore
    private String accountFingerprint;
    private String status;
}
