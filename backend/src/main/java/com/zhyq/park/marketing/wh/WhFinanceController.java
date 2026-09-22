package com.zhyq.park.marketing.wh;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.MktNotice;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseSettlement;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseSettlementMapper;
import com.zhyq.park.marketing.service.MktNoticeService;
import com.zhyq.park.marketing.settlement.MktWarehouseSettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "云仓端-结算合同消息")
@RestController
@RequestMapping("/wh/v1")
@RequiredArgsConstructor
public class WhFinanceController {
    private final MktWarehouseMapper warehouseMapper;
    private final MktWarehouseSettlementMapper settlementMapper;
    private final MktServiceContractMapper contractMapper;
    private final MktWarehouseSettlementService settlementService;
    private final MktNoticeService noticeService;

    @GetMapping("/settlement/page")
    public Result<PageResult<MktWarehouseSettlement>> settlements(@RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        IPage<MktWarehouseSettlement> p = settlementMapper.selectPage(new Page<>(pageNo, pageSize), new LambdaQueryWrapper<MktWarehouseSettlement>().eq(MktWarehouseSettlement::getWarehouseId, w.getId()).eq(w.getProjectId() != null, MktWarehouseSettlement::getProjectId, w.getProjectId()).orderByDesc(MktWarehouseSettlement::getId));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @PostMapping("/settlement/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) { settlementService.confirm(id, WhAuthContext.currentWarehouseId()); return Result.ok(); }
    @PostMapping("/settlement/{id}/dispute")
    public Result<Void> dispute(@PathVariable Long id, @RequestBody Map<String, String> body) { settlementService.dispute(id, WhAuthContext.currentWarehouseId(), body.get("reason")); return Result.ok(); }

    @GetMapping("/contracts")
    public Result<PageResult<MktServiceContract>> contracts(@RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        IPage<MktServiceContract> p = contractMapper.selectPage(new Page<>(pageNo, pageSize), new LambdaQueryWrapper<MktServiceContract>().eq(MktServiceContract::getWarehouseId, w.getId()).eq(w.getProjectId() != null, MktServiceContract::getProjectId, w.getProjectId()).orderByDesc(MktServiceContract::getId));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @PostMapping("/contracts")
    public Result<MktServiceContract> createContract(@RequestBody MktServiceContract body) {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        MktServiceContract c = new MktServiceContract(); c.setContractNo("WH-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + UUID.randomUUID().toString().substring(0, 6));
        c.setWarehouseId(w.getId()); c.setProjectId(w.getProjectId()); c.setCustomerId(body.getCustomerId()); c.setPartnerId(body.getPartnerId()); c.setSignMode(2); c.setServiceType(body.getServiceType()); c.setFeeModel(body.getFeeModel()); c.setPriceTable(body.getPriceTable()); c.setDeposit(body.getDeposit()); c.setStartDate(body.getStartDate()); c.setEndDate(body.getEndDate()); c.setPayCycle(body.getPayCycle()); c.setStatus(1); c.setContractVersion(1); c.setRemark(body.getRemark());
        contractMapper.insert(c); return Result.ok(c);
    }

    @GetMapping("/agreement")
    public Result<Map<String, Object>> agreement() { MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper); return Result.ok(Map.of("warehouseId", w.getId(), "contractFile", w.getContractFile() == null ? "" : w.getContractFile())); }

    @PostMapping("/agreement/upload")
    public Result<Void> uploadAgreement(@RequestBody Map<String, String> body) {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper); String file = body.get("file"); if (file == null || file.isBlank()) throw new BizException("协议文件引用必填");
        warehouseMapper.update(null, new LambdaUpdateWrapper<MktWarehouse>().eq(MktWarehouse::getId, w.getId()).set(MktWarehouse::getContractFile, file)); return Result.ok();
    }

    @GetMapping("/notice/page")
    public Result<PageResult<Map<String, Object>>> notices(@RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        IPage<MktNotice> p = noticeService.page(w.getId(), pageNo, pageSize);
        List<Map<String, Object>> rows = p.getRecords().stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId()); m.put("type", n.getType()); m.put("title", n.getTitle());
            m.put("content", n.getContent()); m.put("bizType", n.getBizType()); m.put("bizId", n.getBizId());
            m.put("readAt", n.getReadAt()); m.put("createTime", n.getCreateTime());
            return m;
        }).collect(java.util.stream.Collectors.toList());
        return Result.ok(PageResult.of(p.getTotal(), rows));
    }

    @GetMapping("/notice/unread-count")
    public Result<Long> noticeUnread() {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        return Result.ok(noticeService.unread(w.getId()));
    }

    @PostMapping("/notice/{id}/read")
    public Result<Void> noticeRead(@PathVariable Long id) {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        noticeService.markRead(id, w.getId());
        return Result.ok();
    }
}
