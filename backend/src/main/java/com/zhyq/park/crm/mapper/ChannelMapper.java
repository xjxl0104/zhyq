package com.zhyq.park.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.crm.entity.Channel;
import org.apache.ibatis.annotations.Select;

public interface ChannelMapper extends BaseMapper<Channel> {

    /**
     * 中介编号最大顺序号。故意手写 SQL:含已逻辑删除的行 —— 唯一索引不区分 deleted,
     * 只看未删除行的话,删掉最大号后会重发同一个号,插入撞唯一键。
     */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(agency_no, CHAR_LENGTH(#{prefix}) + 1) AS UNSIGNED)), 0) "
            + "FROM crm_channel WHERE agency_no REGEXP CONCAT('^', #{prefix}, '[0-9]+$')")
    int maxNoSeq(String prefix);
}
