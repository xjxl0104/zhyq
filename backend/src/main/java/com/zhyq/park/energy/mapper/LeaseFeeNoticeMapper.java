package com.zhyq.park.energy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.energy.entity.LeaseFeeNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LeaseFeeNoticeMapper extends BaseMapper<LeaseFeeNotice> {
    @Select("SELECT * FROM eng_lease_fee_notice WHERE id=#{id} AND deleted=0 FOR UPDATE")
    LeaseFeeNotice lockActive(@Param("id") Long id);
}
