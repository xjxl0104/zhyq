package com.zhyq.park.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.crm.entity.Follow;
import org.apache.ibatis.annotations.Select;

public interface FollowMapper extends BaseMapper<Follow> {

    /** 跟进编号最大顺序号,含已逻辑删除的行(同 LeadMapper#maxNoSeq) */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(follow_no, CHAR_LENGTH(#{prefix}) + 1) AS UNSIGNED)), 0) "
            + "FROM crm_follow WHERE follow_no REGEXP CONCAT('^', #{prefix}, '[0-9]+$')")
    int maxNoSeq(String prefix);
}
