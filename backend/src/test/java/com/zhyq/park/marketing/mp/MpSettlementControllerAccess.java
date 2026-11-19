package com.zhyq.park.marketing.mp;

import java.util.List;
import java.util.Map;

/** 测试访问包内静态方法。 */
public final class MpSettlementControllerAccess {
    private MpSettlementControllerAccess() {}
    public static List<Map<String, Object>> contracts(List<Map<String, Object>> rows) { return MpSettlementController.contracts(rows); }
}
