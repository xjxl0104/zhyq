package com.zhyq.park.pur.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.pur.entity.PurRequest;

public interface PurRequestMapper extends BaseMapper<PurRequest> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM pur_request WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    PurRequest lockForRemoval(Long id);
}
