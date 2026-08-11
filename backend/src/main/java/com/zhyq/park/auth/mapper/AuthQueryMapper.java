package com.zhyq.park.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 认证/鉴权查询:用户 → 角色 → 菜单权限标识。
 * 走注解 SQL,只读,不侵入既有 system mapper。
 * 必须在 *.mapper 包内以匹配主类 @MapperScan("com.zhyq.park.**.mapper")。
 */
@Mapper
public interface AuthQueryMapper {

    /** 用户的权限标识集合(sys_menu.perm),经 用户-角色-菜单 关联 + 用户直接菜单,去空去重 */
    @Select("""
            SELECT DISTINCT m.perm
            FROM (
                SELECT rm.menu_id
                FROM sys_user_role ur
                JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
                JOIN sys_role_menu rm ON rm.role_id = r.id
                WHERE ur.user_id = #{userId}
                UNION
                SELECT um.menu_id
                FROM sys_user_menu um
                WHERE um.user_id = #{userId}
            ) combined
            JOIN sys_menu m ON m.id = combined.menu_id AND m.deleted = 0 AND m.status = 1
            WHERE m.perm IS NOT NULL AND m.perm <> ''
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

    /** 用户可见的菜单 ID 集合 = 角色菜单 ∪ 用户直接菜单 */
    @Select("""
            SELECT DISTINCT menu_id FROM (
                SELECT rm.menu_id
                FROM sys_user_role ur
                JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
                JOIN sys_role_menu rm ON rm.role_id = r.id
                WHERE ur.user_id = #{userId}
                UNION
                SELECT um.menu_id
                FROM sys_user_menu um
                WHERE um.user_id = #{userId}
            ) combined
            """)
    List<Long> selectMenuIdsByUserId(@Param("userId") Long userId);
}
