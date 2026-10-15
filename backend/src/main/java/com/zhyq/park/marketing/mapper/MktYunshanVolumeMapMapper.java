package com.zhyq.park.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.marketing.entity.MktYunshanVolumeMap;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MktYunshanVolumeMapMapper extends BaseMapper<MktYunshanVolumeMap> {
    @Select("SELECT * FROM crm_yunshan_volume_map WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=0 FOR UPDATE")
    MktYunshanVolumeMap selectForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("""
            SELECT COUNT(*) FROM crm_yunshan_volume_map m
            JOIN crm_customer c ON c.id=m.customer_id AND c.tenant_id=m.tenant_id AND c.deleted=0
            JOIN crm_warehouse w ON w.id=m.warehouse_id AND w.tenant_id=m.tenant_id AND w.deleted=0 AND w.join_status NOT IN (7,8)
            WHERE m.id=#{id} AND m.tenant_id=#{tenantId} AND m.deleted=0
            """)
    int countAvailableSubjects(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
