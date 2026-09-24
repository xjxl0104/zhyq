package com.zhyq.park.workflow.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Fixed business-table reads avoid allowing clients to choose SQL identifiers. */
public interface WorkflowBusinessMapper {
    @Select("SELECT status FROM biz_contract WHERE id=#{id} AND deleted=0 FOR UPDATE")
    Integer lockContractStatus(@Param("id") Long id);
    @Select("SELECT status FROM bud_budget WHERE id=#{id} AND deleted=0 FOR UPDATE")
    Integer lockBudgetStatus(@Param("id") Long id);
    @Select("SELECT status FROM pur_request WHERE id=#{id} AND deleted=0 FOR UPDATE")
    Integer lockProcurementStatus(@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM biz_approval WHERE id=#{approvalId} AND biz_type=#{type} AND biz_id=#{bizId} AND status=2 AND deleted=0")
    int countMatchingApproval(@Param("approvalId") Long approvalId, @Param("type") String type, @Param("bizId") Long bizId);
    @org.apache.ibatis.annotations.Update("""
            UPDATE biz_approval SET status=#{status}, approve_by=#{operator}, approve_time=NOW(), opinion=#{opinion}
            WHERE id=#{approvalId} AND biz_type=#{type} AND biz_id=#{bizId} AND status=2 AND deleted=0
            """)
    int completeApproval(@Param("approvalId") Long approvalId, @Param("type") String type,
            @Param("bizId") Long bizId, @Param("status") int status,
            @Param("operator") String operator, @Param("opinion") String opinion);
}
