package com.zhyq.park.pur.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.pur.entity.Supplier;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SupplierMapper extends BaseMapper<Supplier> {

    /**
     * 取指定前缀下的最大编号,<b>含已软删行</b>。
     *
     * <p>唯一键 uk_supplier_code 是单列 (code),对软删行同样生效 —— 软删后编号仍被占用。
     * 而 BaseMapper 的查询会被 @TableLogic 自动追加 deleted=0、看不见软删行,
     * 若用它发号,删除一条后会再次生成同一个号并撞唯一键。故这里用原生 SQL 绕过逻辑删除。
     */
    @Select("SELECT MAX(code) FROM pur_supplier WHERE code LIKE CONCAT(#{prefix}, '%')")
    String selectMaxCodeIncludingDeleted(@Param("prefix") String prefix);
}
