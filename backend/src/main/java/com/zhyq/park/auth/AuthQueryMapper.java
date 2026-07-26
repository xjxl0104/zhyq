package com.zhyq.park.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 认证/鉴权查询:用户 → 角色 → 菜单权限标识。
 * 走注解 SQL,只读,不侵入既有 system mapper。
 */
@Mapper
public interface AuthQueryMapper {

    /** 用户的权限标识集合(sys_menu.perm),经 用户-角色-菜单 关联,去空去重 */
    @Select("""
            SELECT DISTINCT m.perm
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
            JOIN sys_role_menu rm ON rm.role_id = r.id
            JOIN sys_menu m ON m.id = rm.menu_id AND m.deleted = 0 AND m.status = 1
            WHERE ur.user_id = #{userId}
              AND m.perm IS NOT NULL AND m.perm <> ''
            """)
    List<String> selectPermsByUserId(@Param("userId") Long userId);

    /** 用户的角色编码集合(sys_role.code),用于 ROLE_ 前缀权限 */
    @Select("""
            SELECT DISTINCT r.code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
            WHERE ur.user_id = #{userId}
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
