package com.zhyq.park.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.space.entity.SpaceNode;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SpaceNodeMapper extends BaseMapper<SpaceNode> {

    @Select("SELECT * FROM sys_space WHERE path LIKE #{pathPrefix} AND deleted = 0 ORDER BY level, sort")
    List<SpaceNode> selectSubtree(@Param("pathPrefix") String pathPrefix);
}
