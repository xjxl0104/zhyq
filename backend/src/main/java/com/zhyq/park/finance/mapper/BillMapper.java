package com.zhyq.park.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.finance.entity.Bill;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface BillMapper extends BaseMapper<Bill> {

    @Select("""
            SELECT COUNT(*)
            FROM fin_bill b
            INNER JOIN fin_receivable_register r ON r.id = b.receivable_register_id
            WHERE r.source_batch_id = #{batchId}
            FOR UPDATE
            """)
    Long countIncludingDeletedByReceivableSourceBatch(@Param("batchId") Long batchId);

    /**
     * 按 id 批量查账单,<b>包含软删</b>(绕过 @TableLogic)。
     *
     * <p>收款通知/流水/收据/发票是对账单的历史引用:账单即便后来被删(重新生成会软删
     * 旧账单换新 id),这些记录仍要显示它当时是谁的、哪张单、什么费用,否则整列变 "-"。
     * 展示口径 {@link com.zhyq.park.finance.service.FinanceViewEnricher} 专用。</p>
     */
    @Select("""
            <script>
            SELECT * FROM fin_bill
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Bill> selectByIdsIncludingDeleted(@Param("ids") Collection<Long> ids);
}
