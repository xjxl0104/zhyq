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

    /**
     * 生成下一个供应商编号 GYS-0001:取当前最大编号(含软删)+1。建档与导入共用。
     *
     * <p>已知边界:极端并发下可能算出同号,后插入的因唯一键失败并提示,不重试;
     * 编号按字符串取最大,超过 9999 后排序失真;历史脏编号解析失败时退回 1。</p>
     */
    default String nextCode() {
        final String prefix = "GYS-";
        String max = selectMaxCodeIncludingDeleted(prefix);
        int next = 1;
        if (max != null && !max.isBlank()) {
            try {
                next = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
                // 历史编号格式异常:退回从 1 起,若撞号则本次请求失败提示重试
            }
        }
        return prefix + String.format("%04d", next);
    }
}
