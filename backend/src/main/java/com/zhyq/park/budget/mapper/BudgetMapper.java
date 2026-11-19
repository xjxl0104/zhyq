package com.zhyq.park.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.budget.entity.Budget;

public interface BudgetMapper extends BaseMapper<Budget> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM bud_budget WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Budget lockForRemoval(Long id);
}
