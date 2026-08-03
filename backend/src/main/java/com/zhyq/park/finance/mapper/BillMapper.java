package com.zhyq.park.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.finance.entity.Bill;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BillMapper extends BaseMapper<Bill> {

    @Select("""
            SELECT COUNT(*)
            FROM fin_bill b
            INNER JOIN fin_receivable_register r ON r.id = b.receivable_register_id
            WHERE r.source_batch_id = #{batchId}
            FOR UPDATE
            """)
    Long countIncludingDeletedByReceivableSourceBatch(@Param("batchId") Long batchId);
}
