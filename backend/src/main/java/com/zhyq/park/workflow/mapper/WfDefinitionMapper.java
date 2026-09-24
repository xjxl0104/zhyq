package com.zhyq.park.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.workflow.entity.WfDefinition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WfDefinitionMapper extends BaseMapper<WfDefinition> {
    @org.apache.ibatis.annotations.Select("""
            SELECT COUNT(*) FROM wf_node n WHERE n.deleted=0 AND n.definition_id=(
              SELECT d.id FROM wf_definition d WHERE d.biz_type=#{type} AND d.status=1 AND d.deleted=0
              ORDER BY d.id DESC LIMIT 1
            )
            """)
    int countEnabledWithNodes(@org.apache.ibatis.annotations.Param("type") String type);
}
