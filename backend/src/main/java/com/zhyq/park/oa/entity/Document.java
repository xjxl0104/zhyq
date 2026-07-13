package com.zhyq.park.oa.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 公文(oa_document)
 * 状态:1拟稿 2核稿 3签发 4归档
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_document")
public class Document extends BaseEntity {

    /** 文号 */
    private String docNo;
    /** 标题 */
    private String title;
    /** 收文/发文 */
    private String docType;
    /** 来文单位/拟稿部门 */
    private String fromUnit;
    /** 正文 */
    private String content;
    /** 状态:1拟稿 2核稿 3签发 4归档 */
    private Integer status;
    /** 签发人 */
    private String signBy;
    /** 签发时间 */
    private LocalDateTime signTime;
}
