package com.zhyq.park.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.crm.entity.Customer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface CustomerMapper extends BaseMapper<Customer> {
    /** 分派、归属与合同激活共享此行锁，避免并发改变同一客户的承接方。 */
    @Select("SELECT * FROM crm_customer WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Customer selectForUpdate(@Param("id") Long id);

    @Select("""
        SELECT COALESCE(MAX(COALESCE(a.status,'pending')),'no_demand')
        FROM crm_customer c JOIN crm_quote_intake q
          ON q.customer_id=c.id AND q.tenant_id=c.tenant_id AND q.project_id=c.project_id
        LEFT JOIN crm_quote_advice a ON a.customer_id=q.customer_id AND a.tenant_id=q.tenant_id AND a.project_id=q.project_id
         WHERE c.id=#{id} AND c.deleted=0 AND q.submitted_at IS NOT NULL
        """)
    String initialQuoteState(@Param("id") Long id);

    /** 当天报备额度包含已删除档案，防止删除后重新报备绕过日上限。 */
    @Select("SELECT COUNT(*) FROM crm_customer WHERE referrer_id = #{promoterId} AND create_time >= #{since}")
    long countReferralsSince(@Param("promoterId") Long promoterId, @Param("since") LocalDateTime since);
}
