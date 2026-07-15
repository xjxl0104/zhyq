package com.zhyq.park.acc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 停车记录(acc_parking,#21):车辆 + 进出场 + 费用试算。
 * status: 1在场 2已离场。
 * fee: 由 {@link com.zhyq.park.acc.service.ParkingFeeCalculator} 算出并仅存于本记录,
 * <b>绝不写 fin_bill / 触发收款</b>(设计 §7 财务边界)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("acc_parking")
public class AccParking extends BaseEntity {

    private String plateNo;
    private String ownerType;
    private LocalDateTime enterTime;
    private LocalDateTime leaveTime;
    private BigDecimal fee;
    private String feeRule;
    private Integer status;
}
