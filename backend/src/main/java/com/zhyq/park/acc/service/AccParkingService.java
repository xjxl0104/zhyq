package com.zhyq.park.acc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.acc.entity.AccParking;
import com.zhyq.park.acc.mapper.AccParkingMapper;
import com.zhyq.park.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 停车进出场服务(#21)。状态机:1在场 → 2已离场。
 *
 * <p><b>费用边界(设计 §2/§7):</b>出场时用 {@link ParkingFeeCalculator} 试算费用,
 * <b>只存于 acc_parking.fee,绝不写 fin_bill / 触发收款</b>。本服务不注入、不引用任何 finance 组件。</p>
 *
 * <p><b>并发手法:</b>出场用条件 UPDATE 抢状态(eq status=在场)防重复结算;
 * 入场查重同车牌是否已在场。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccParkingService {

    public static final int ST_IN = 1;   // 在场
    public static final int ST_OUT = 2;  // 已离场

    private final AccParkingMapper parkingMapper;

    /** 入场:同车牌已在场则拒;否则建在场记录(status=1, enterTime=now)。 */
    @Transactional(rollbackFor = Exception.class)
    public Long enter(String plateNo, String ownerType) {
        if (plateNo == null || plateNo.isBlank()) {
            throw new BizException("车牌必填");
        }
        Long inCount = parkingMapper.selectCount(new LambdaQueryWrapper<AccParking>()
                .eq(AccParking::getPlateNo, plateNo)
                .eq(AccParking::getStatus, ST_IN));
        if (inCount != null && inCount > 0) {
            throw new BizException("该车牌已在场,不可重复入场");
        }
        AccParking p = new AccParking();
        p.setPlateNo(plateNo);
        p.setOwnerType((ownerType == null || ownerType.isBlank()) ? "temp" : ownerType);
        p.setEnterTime(LocalDateTime.now());
        p.setStatus(ST_IN);
        p.setFeeRule(ParkingFeeCalculator.DEFAULT_RULE.describe());
        parkingMapper.insert(p);
        return p.getId();
    }

    /**
     * 出场:1 → 2,leaveTime=now,试算 fee 存记录。
     * 条件 UPDATE 抢状态防并发重复结算:受影响 0 行则说明非在场态。
     */
    @Transactional(rollbackFor = Exception.class)
    public AccParking leave(Long id) {
        AccParking p = parkingMapper.selectById(id);
        if (p == null) {
            throw new BizException("停车记录不存在");
        }
        LocalDateTime leaveTime = LocalDateTime.now();
        BigDecimal fee = ParkingFeeCalculator.calc(p.getEnterTime(), leaveTime, ParkingFeeCalculator.DEFAULT_RULE);

        LambdaUpdateWrapper<AccParking> uw = new LambdaUpdateWrapper<AccParking>()
                .eq(AccParking::getId, id)
                .eq(AccParking::getStatus, ST_IN)
                .set(AccParking::getStatus, ST_OUT)
                .set(AccParking::getLeaveTime, leaveTime)
                .set(AccParking::getFee, fee)
                .set(AccParking::getFeeRule, ParkingFeeCalculator.DEFAULT_RULE.describe());
        int rows = parkingMapper.update(null, uw);
        if (rows == 0) {
            throw new BizException("该车辆非在场状态,不可出场");
        }
        return parkingMapper.selectById(id);
    }
}
