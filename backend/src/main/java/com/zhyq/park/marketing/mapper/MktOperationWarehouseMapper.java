package com.zhyq.park.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.marketing.entity.MktOperationWarehouse;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MktOperationWarehouseMapper extends BaseMapper<MktOperationWarehouse> {
    @Select("SELECT * FROM crm_operation_warehouse WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=0 FOR UPDATE")
    MktOperationWarehouse selectForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
