package com.zhyq.park.system.dto;

import com.zhyq.park.system.entity.SysUser;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SysUserDetailResponse {
    private SysUser user;
    private List<Long> roleIds;
}
