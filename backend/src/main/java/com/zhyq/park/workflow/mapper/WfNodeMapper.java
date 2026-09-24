package com.zhyq.park.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.workflow.entity.WfNode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WfNodeMapper extends BaseMapper<WfNode> {
    /** Task node ids are immutable references; replaced definitions leave historical nodes logically deleted. */
    @org.apache.ibatis.annotations.Select("SELECT * FROM wf_node WHERE id=#{id}")
    WfNode selectHistoricalApprovalNode(@org.apache.ibatis.annotations.Param("id") Long id);
}
