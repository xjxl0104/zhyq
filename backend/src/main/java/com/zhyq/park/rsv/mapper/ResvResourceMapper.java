package com.zhyq.park.rsv.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.rsv.entity.ResvResource;

public interface ResvResourceMapper extends BaseMapper<ResvResource> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM rsv_resource WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ResvResource lockForBooking(Long id);
}
