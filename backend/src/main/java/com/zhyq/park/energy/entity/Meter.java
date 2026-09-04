package com.zhyq.park.energy.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhyq.park.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_meter")
public class Meter extends BaseEntity {
    private String code;
    private String name;
    private String energyType;
    /**
     * 表计角色,决定它在公摊里的位置:
     * TENANT=租户分表(分摊分母 + 出账对象) MAIN=园区总表(分摊被减数,不出账)
     * PROPERTY=物业公司分表(计入分母但不出账,是园区内部成本)
     */
    private String meterRole;
    private Long projectId;
    private Long buildingId;
    private Long roomId;
    private BigDecimal ratio;
    private BigDecimal lastReading;
    private Integer status;

    // ---- 展示字段(不落库)。表计表原来只有编号/名称/倍率/上次读数,光看这几列答不了
    //      「谁在用、抄到哪天、这期用了多少」。租户经 房间 → 合同 → 租客 反查,
    //      抄表数据取该表计最近一次 eng_reading。口径由 MeterController 统一填充。

    /** 房间号 */
    @TableField(exist = false)
    private String roomCode;
    /** 在租租户(房间对应的执行中合同);查不到时为 null,前端显示 - */
    @TableField(exist = false)
    private Long tenantRefId;
    @TableField(exist = false)
    private String tenantName;
    /** 最近一次抄表:时间、当前读数、本期用量、金额 */
    @TableField(exist = false)
    private LocalDateTime lastReadTime;
    @TableField(exist = false)
    private BigDecimal currReading;
    @TableField(exist = false)
    private BigDecimal usageAmount;
    @TableField(exist = false)
    private BigDecimal latestFee;
    /** 本期账期 = 上次抄表日 ~ 本次抄表日;只有一次抄表时起始日为空 */
    @TableField(exist = false)
    private LocalDate periodStart;
    @TableField(exist = false)
    private LocalDate periodEnd;
    /** 最近一次抄表记录 id,「账单/计费」按它出账并做幂等 */
    @TableField(exist = false)
    private Long latestReadingId;
    /** 该次抄表是否已生成过能源费账单 */
    @TableField(exist = false)
    private Boolean billed;
}
