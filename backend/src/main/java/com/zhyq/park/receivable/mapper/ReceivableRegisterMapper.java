package com.zhyq.park.receivable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReceivableRegisterMapper extends BaseMapper<ReceivableRegister> {

    @Select("""
            SELECT *
            FROM fin_receivable_register
            WHERE id = #{id} AND deleted = 0
            FOR UPDATE
            """)
    ReceivableRegister selectByIdForUpdate(@Param("id") Long id);
}
