package com.zhyq.park.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.marketing.entity.MktWithdrawal;

public interface MktWithdrawalMapper extends BaseMapper<MktWithdrawal> {
    @org.apache.ibatis.annotations.Select("""
            SELECT COUNT(*) FROM crm_withdrawal w
            WHERE w.promoter_id = #{promoterId} AND (w.auto_period = DATE_FORMAT(#{start}, '%Y-%m')
              OR (w.auto_period IS NULL AND EXISTS (
                  SELECT 1 FROM sys_audit a WHERE a.biz_id = w.id AND a.module = 'crm.marketing'
                    AND a.biz_type = 'withdrawal' AND a.action = 'withdrawal.auto-apply'
                    AND a.create_time >= #{start} AND a.create_time < #{end})))
            """)
    int countAutomatic(@org.apache.ibatis.annotations.Param("promoterId") Long promoterId,
                       @org.apache.ibatis.annotations.Param("start") java.time.LocalDate start,
                       @org.apache.ibatis.annotations.Param("end") java.time.LocalDate end);
}
