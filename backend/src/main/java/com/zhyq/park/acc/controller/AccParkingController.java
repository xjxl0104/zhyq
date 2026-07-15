package com.zhyq.park.acc.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.acc.entity.AccParking;
import com.zhyq.park.acc.mapper.AccParkingMapper;
import com.zhyq.park.acc.service.AccParkingService;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 便捷通行-停车(#21)。状态机:1在场 2已离场。
 * 出场时试算停车费,<b>只存 acc_parking.fee,绝不写账单/收款</b>。
 */
@Tag(name = "便捷通行-停车")
@RestController
@RequestMapping("/acc/parking")
@RequiredArgsConstructor
public class AccParkingController {

    private final AccParkingService parkingService;
    private final AccParkingMapper parkingMapper;

    @Operation(summary = "分页查询停车记录")
    @GetMapping("/page")
    public Result<PageResult<AccParking>> page(@RequestParam(defaultValue = "1") int pageNo,
                                               @RequestParam(defaultValue = "10") int pageSize,
                                               @RequestParam(required = false) String plateNo,
                                               @RequestParam(required = false) String ownerType,
                                               @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<AccParking> qw = new LambdaQueryWrapper<>();
        qw.like(plateNo != null && !plateNo.isBlank(), AccParking::getPlateNo, plateNo)
          .eq(ownerType != null && !ownerType.isBlank(), AccParking::getOwnerType, ownerType)
          .eq(status != null, AccParking::getStatus, status)
          .orderByDesc(AccParking::getId);
        IPage<AccParking> p = parkingMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "停车详情")
    @GetMapping("/{id}")
    public Result<AccParking> get(@PathVariable Long id) {
        return Result.ok(parkingMapper.selectById(id));
    }

    @Operation(summary = "入场(同车牌已在场则拒)")
    @PostMapping("/enter")
    public Result<Long> enter(@RequestParam String plateNo,
                              @RequestParam(required = false) String ownerType) {
        return Result.ok(parkingService.enter(plateNo, ownerType));
    }

    @Operation(summary = "出场(试算费用只存不入账)")
    @PostMapping("/{id}/leave")
    public Result<AccParking> leave(@PathVariable Long id) {
        return Result.ok(parkingService.leave(id));
    }
}
