package com.zhyq.park.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.marketing.entity.MktServiceContract;

public interface MktServiceContractMapper extends BaseMapper<MktServiceContract> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM crm_service_contract WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    MktServiceContract selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);
}
