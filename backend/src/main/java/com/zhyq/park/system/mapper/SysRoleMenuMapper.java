package com.zhyq.park.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.system.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId} ORDER BY id")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);
}
