package com.zhyq.park.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.marketing.entity.MktPromoterCommission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface MktPromoterCommissionMapper extends BaseMapper<MktPromoterCommission> {

    /** 伙伴未作废佣金按来源类型汇总(金额自带正负,扣回行直接抵减);无订单的行 sourceType 为空。 */
    @Select("SELECT o.source_type AS sourceType, COALESCE(SUM(c.amount), 0) AS amount "
            + "FROM crm_promoter_commission c "
            + "LEFT JOIN crm_referral_order o ON o.id = c.referral_order_id AND o.deleted = 0 "
            + "WHERE c.promoter_id = #{promoterId} AND c.status <> 5 AND c.deleted = 0 "
            + "GROUP BY o.source_type")
    List<Map<String, Object>> sumBySource(@Param("promoterId") Long promoterId);

    /** 待结算 = 冻结中 + 可结算,尚未进入结算批次的佣金合计。 */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM crm_promoter_commission "
            + "WHERE promoter_id = #{promoterId} AND status IN (1, 2) AND deleted = 0")
    BigDecimal sumPending(@Param("promoterId") Long promoterId);
}
