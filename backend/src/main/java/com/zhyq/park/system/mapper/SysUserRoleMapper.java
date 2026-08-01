package com.zhyq.park.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.system.dto.UserRoleLabel;
import com.zhyq.park.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId} ORDER BY id")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*)
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
            WHERE ur.user_id = #{userId} AND r.code = #{roleCode}
            """)
    int hasRoleCode(@Param("userId") Long userId, @Param("roleCode") String roleCode);

    @Select("""
            SELECT COUNT(DISTINCT u.id)
            FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE u.deleted = 0 AND u.status = 1
              AND r.deleted = 0 AND r.status = 1 AND r.code = 'admin'
            """)
    int countActiveAdminUsers();

    @Select("""
            <script>
            SELECT ur.user_id AS user_id,
                   GROUP_CONCAT(DISTINCT r.name ORDER BY r.sort, r.id SEPARATOR ',') AS role_names
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
            WHERE ur.user_id IN
            <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                #{userId}
            </foreach>
            GROUP BY ur.user_id
            </script>
            """)
    List<UserRoleLabel> selectRoleLabelsByUserIds(@Param("userIds") Collection<Long> userIds);
}
