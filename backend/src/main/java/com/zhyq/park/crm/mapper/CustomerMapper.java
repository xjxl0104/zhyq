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

    /** 当天报备额度包含已删除档案，防止删除后重新报备绕过日上限。 */
    @Select("SELECT COUNT(*) FROM crm_customer WHERE referrer_id = #{promoterId} AND create_time >= #{since}")
    long countReferralsSince(@Param("promoterId") Long promoterId, @Param("since") LocalDateTime since);
}
