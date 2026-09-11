package com.zhyq.park.pur.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhyq.park.pur.entity.SupplierContract;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SupplierContractMapper extends BaseMapper<SupplierContract> {

    /**
     * 取指定前缀下的最大合同编号,<b>含已软删行</b>。理由同 SupplierMapper:
     * 唯一键跨软删生效,发号若看不见软删行就会重号撞键。
     */
    @Select("SELECT MAX(code) FROM pur_supplier_contract WHERE code LIKE CONCAT(#{prefix}, '%')")
    String selectMaxCodeIncludingDeleted(@Param("prefix") String prefix);
}
