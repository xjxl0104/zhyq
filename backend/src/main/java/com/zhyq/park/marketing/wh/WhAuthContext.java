package com.zhyq.park.marketing.wh;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Token-derived warehouse identity. Request parameters are intentionally never used for ownership. */
@Component
public class WhAuthContext {
    private final MktWarehouseMapper warehouseMapper;

    public WhAuthContext(MktWarehouseMapper warehouseMapper) { this.warehouseMapper = warehouseMapper; }

    public static Long currentWarehouseId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a.getName() == null || !a.getName().startsWith(WhAuthService.SUBJECT_PREFIX))
            throw new BizException(401, "请先登录云仓");
        try { return Long.valueOf(a.getName().substring(WhAuthService.SUBJECT_PREFIX.length())); }
        catch (NumberFormatException e) { throw new BizException(401, "云仓身份无效"); }
    }

    public MktWarehouse requireWarehouse() { return requireWarehouse(warehouseMapper, currentWarehouseId()); }

    public static MktWarehouse requireWarehouse(MktWarehouseMapper mapper) {
        return requireWarehouse(mapper, currentWarehouseId());
    }

    public static MktWarehouse requireWarehouse(MktWarehouseMapper mapper, Long id) {
        MktWarehouse w = mapper.selectById(id);
        if (w == null) throw new BizException(403, "云仓不存在或无权访问");
        return w;
    }
}
