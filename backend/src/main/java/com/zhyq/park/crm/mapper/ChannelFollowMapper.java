package com.zhyq.park.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.crm.entity.ChannelFollow;
import org.apache.ibatis.annotations.Select;

public interface ChannelFollowMapper extends BaseMapper<ChannelFollow> {

    /** 跟进编号最大顺序号,含已逻辑删除的行(同 ChannelMapper#maxNoSeq) */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(follow_no, CHAR_LENGTH(#{prefix}) + 1) AS UNSIGNED)), 0) "
            + "FROM crm_channel_follow WHERE follow_no REGEXP CONCAT('^', #{prefix}, '[0-9]+$')")
    int maxNoSeq(String prefix);
}
