package com.zhyq.park.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class SysUserSaveRequest {
    private Long id;
    private String username;
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
    private List<Long> roleIds;
}
