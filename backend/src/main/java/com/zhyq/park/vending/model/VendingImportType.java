package com.zhyq.park.vending.model;

import com.zhyq.park.common.exception.BizException;

import java.util.List;

public enum VendingImportType {
    MACHINE("vending_machine", "机器数据",
            List.of("厂商机器编号", "机器名称", "点位", "型号", "运行状态", "最后在线时间"),
            List.of("厂商机器编号")),
    SALE("vending_sale", "销售数据",
            List.of("厂商订单号", "行号", "机器编号", "商品编号", "商品名称", "数量",
                    "原价金额", "优惠金额", "实付金额", "支付方式", "支付时间", "订单状态"),
            List.of("厂商订单号", "行号")),
    RESTOCK("vending_restock", "补货数据",
            List.of("厂商补货单号", "机器编号", "商品编号", "商品名称", "补货数量", "补货人", "补货时间"),
            List.of("厂商补货单号")),
    FAULT("vending_fault", "故障数据",
            List.of("厂商故障编号", "机器编号", "故障类型", "发生时间", "恢复时间", "状态", "描述"),
            List.of("厂商故障编号")),
    RECONCILIATION("vending_reconciliation", "对账数据",
            List.of("厂商结算单号", "结算周期开始", "结算周期结束", "销售总额", "退款", "平台费用", "结算净额", "状态"),
            List.of("厂商结算单号"));

    private final String bizType;
    private final String sheetName;
    private final List<String> headers;
    private final List<String> keyHeaders;

    VendingImportType(String bizType, String sheetName, List<String> headers, List<String> keyHeaders) {
        this.bizType = bizType;
        this.sheetName = sheetName;
        this.headers = headers;
        this.keyHeaders = keyHeaders;
    }

    public String bizType() {
        return bizType;
    }

    public String sheetName() {
        return sheetName;
    }

    public List<String> headers() {
        return headers;
    }

    public List<String> keyHeaders() {
        return keyHeaders;
    }

    public static VendingImportType fromBizType(String bizType) {
        for (VendingImportType type : values()) {
            if (type.bizType.equalsIgnoreCase(bizType)) {
                return type;
            }
        }
        throw new BizException("不支持的售货机导入类型: " + bizType);
    }

    public static VendingImportType fromName(String value) {
        if (value == null) {
            throw new BizException("售货机导入类型不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fromBizType(value.trim());
        }
    }
}
