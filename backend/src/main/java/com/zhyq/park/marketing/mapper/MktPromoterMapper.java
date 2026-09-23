package com.zhyq.park.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.marketing.entity.MktPromoter;

public interface MktPromoterMapper extends BaseMapper<MktPromoter> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM crm_promoter WHERE id=#{id} AND deleted=0 FOR UPDATE")
    MktPromoter selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);
}
