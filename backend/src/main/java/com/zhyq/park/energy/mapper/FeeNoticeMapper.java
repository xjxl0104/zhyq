package com.zhyq.park.energy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.energy.entity.FeeNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FeeNoticeMapper extends BaseMapper<FeeNotice> {
    /** Current read after the period lock: an old snapshot must not revive a regenerated notice. */
    @Select("SELECT * FROM eng_fee_notice WHERE id=#{id} AND deleted=0 FOR UPDATE")
    FeeNotice lockActive(@Param("id") Long id);
}
