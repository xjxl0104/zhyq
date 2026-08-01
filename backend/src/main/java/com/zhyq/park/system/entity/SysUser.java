package com.zhyq.park.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {
    private String username;
    /** 密码哈希,序列化时不外泄 */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String nickname;
    private Long deptId;
    private Long postId;
    private String phone;
    private String email;
    private Integer gender;
    private String avatar;
    private Integer userType;
    private Integer status;
    private String remark;
    @TableField(exist = false)
    private List<String> roleNames;
}
