package com.zhyq.park.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.marketing.entity.MktCustomerOperationWarehouse;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MktCustomerOperationWarehouseMapper extends BaseMapper<MktCustomerOperationWarehouse> {
    @Select("SELECT * FROM crm_customer_operation_warehouse WHERE id=#{id} AND warehouse_id=#{warehouseId} AND tenant_id=#{tenantId} AND deleted=0 FOR UPDATE")
    MktCustomerOperationWarehouse selectForUpdate(@Param("id") Long id, @Param("warehouseId") Long warehouseId,
                                                @Param("tenantId") Long tenantId);

    /** 物理删除：唯一键不含 deleted，逻辑删除会让同一客户和作业仓库无法重新建立关联。删除前由控制器写审计快照。 */
    @Delete("DELETE FROM crm_customer_operation_warehouse WHERE id=#{id} AND warehouse_id=#{warehouseId} AND tenant_id=#{tenantId}")
    int hardDelete(@Param("id") Long id, @Param("warehouseId") Long warehouseId, @Param("tenantId") Long tenantId);
}
