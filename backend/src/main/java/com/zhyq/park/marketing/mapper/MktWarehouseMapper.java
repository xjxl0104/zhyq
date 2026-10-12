package com.zhyq.park.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.marketing.entity.MktWarehouse;

public interface MktWarehouseMapper extends BaseMapper<MktWarehouse> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM crm_warehouse WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    MktWarehouse selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);
}
