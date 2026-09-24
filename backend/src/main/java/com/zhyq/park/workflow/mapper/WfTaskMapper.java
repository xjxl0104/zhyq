package com.zhyq.park.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.workflow.entity.WfTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WfTaskMapper extends BaseMapper<WfTask> {
    @org.apache.ibatis.annotations.Select("""
            <script>
            SELECT t.* FROM wf_task t
            JOIN wf_instance i ON i.id=t.instance_id AND i.deleted=0 AND i.status=1 AND i.current_seq=t.seq
            JOIN wf_node n ON n.id=t.node_id
            WHERE t.deleted=0 AND t.status=1
            AND (#{admin}=TRUE OR (n.approver_type='user' AND t.assignee=#{username})
              <if test="roles != null and !roles.isEmpty()">
                OR (n.approver_type='role' AND t.assignee IN
                    <foreach collection="roles" item="role" open="(" separator="," close=")">#{role}</foreach>)
              </if>)
            <if test="assignee != null and assignee != ''">AND t.assignee=#{assignee}</if>
            ORDER BY t.id DESC
            </script>
            """)
    java.util.List<WfTask> selectAuthorizedPending(@org.apache.ibatis.annotations.Param("username") String username,
            @org.apache.ibatis.annotations.Param("roles") java.util.List<String> roles,
            @org.apache.ibatis.annotations.Param("admin") boolean admin,
            @org.apache.ibatis.annotations.Param("assignee") String assignee);

    @org.apache.ibatis.annotations.Select("""
            <script>
            SELECT DISTINCT i.id FROM wf_instance i
            WHERE i.deleted=0 AND (i.create_by=#{username} OR EXISTS (
                SELECT 1 FROM wf_task t JOIN wf_node n ON n.id=t.node_id
                WHERE t.instance_id=i.id AND t.deleted=0 AND (
                    (n.approver_type='user' AND t.assignee=#{username})
                    <if test="roles != null and !roles.isEmpty()">
                    OR (n.approver_type='role' AND t.assignee IN
                        <foreach collection="roles" item="role" open="(" separator="," close=")">#{role}</foreach>)
                    </if>
                )
            ))
            </script>
            """)
    java.util.List<Long> selectParticipatingInstanceIds(@org.apache.ibatis.annotations.Param("username") String username,
            @org.apache.ibatis.annotations.Param("roles") java.util.List<String> roles);
}
