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
import com.zhyq.park.marketing.service.MktWarehouseFileService;
import com.zhyq.park.marketing.service.MktServiceContractService;
import com.zhyq.park.crm.mapper.CustomerMapper;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
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
    private final MktWarehouseFileService files;
    private final MktServiceContractService contractService;
    private final com.zhyq.park.marketing.mapper.MktContractWarehouseMapper contractWarehouses;
    private final CustomerMapper customers;

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
    public Result<PageResult<Map<String, Object>>> contracts(@RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        IPage<MktServiceContract> p = contractMapper.selectPage(new Page<>(Math.max(1, pageNo), Math.min(100, Math.max(1,pageSize))), new LambdaQueryWrapper<MktServiceContract>()
                .eq(MktServiceContract::getWarehouseId, w.getId()).eq(w.getProjectId() != null, MktServiceContract::getProjectId, w.getProjectId()).orderByDesc(MktServiceContract::getId));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords().stream().map(this::contractView).toList()));
    }

    @PostMapping("/contracts")
    @Transactional
    public Result<Map<String, Object>> createContract(@RequestBody MktServiceContract body) {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        MktServiceContract draft = merchantDraft(body, w);
        draft.setFiles(files.validateReferences(body.getFiles() == null ? "[]" : body.getFiles(), w.getId()));
        MktServiceContract created = contractService.createDraft(draft);
        com.zhyq.park.marketing.entity.MktContractWarehouse link = new com.zhyq.park.marketing.entity.MktContractWarehouse();
        link.setContractId(created.getId()); link.setWarehouseId(created.getWarehouseId()); link.setIsPrimary(1);
        link.setEffectiveFrom(created.getStartDate()); link.setProjectId(created.getProjectId());
        contractWarehouses.insert(link);
        return Result.ok(contractView(created));
    }

    @PutMapping("/contracts/{id}")
    @Transactional
    public Result<Void> updateContract(@PathVariable Long id, @RequestBody MktServiceContract body) {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        MktServiceContract current = ownedContract(id, w.getId());
        if (!Integer.valueOf(2).equals(current.getSignMode())) throw new BizException("园区签合同由园区维护");
        MktServiceContract draft = merchantDraft(body, w);
        draft.setId(id); draft.setCustomerId(current.getCustomerId()); draft.setSignMode(current.getSignMode());
        draft.setFiles(files.validateReferences(body.getFiles() == null ? "[]" : body.getFiles(), w.getId()));
        contractService.updateDraft(draft);
        return Result.ok();
    }

    @PostMapping("/contracts/{id}/submit")
    @Transactional
    public Result<Void> submitContract(@PathVariable Long id) {
        MktServiceContract c = ownedContract(id, WhAuthContext.currentWarehouseId());
        if (!Integer.valueOf(2).equals(c.getSignMode())) throw new BizException("园区签合同由园区提交审核");
        files.validateReferences(c.getFiles(), c.getWarehouseId());
        contractService.submit(id); return Result.ok();
    }

    private MktServiceContract ownedContract(Long id, Long warehouseId) {
        MktServiceContract c = contractMapper.selectById(id);
        if (c == null || !Objects.equals(warehouseId, c.getWarehouseId())) throw new BizException(403, "合同不存在或无权访问");
        return c;
    }

    private MktServiceContract merchantDraft(MktServiceContract body, MktWarehouse w) {
        MktServiceContract c = new MktServiceContract();
        c.setWarehouseId(w.getId()); c.setProjectId(w.getProjectId()); c.setCustomerId(body.getCustomerId());
        c.setSignMode(2); c.setServiceType(body.getServiceType()); c.setFeeModel(body.getFeeModel()); c.setPriceTable(body.getPriceTable());
        c.setDeposit(body.getDeposit()); c.setStartDate(body.getStartDate()); c.setEndDate(body.getEndDate()); c.setPayCycle(body.getPayCycle());
        c.setRemark(body.getRemark()); return c;
    }

    private Map<String, Object> contractView(MktServiceContract c) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", c.getId()); out.put("contractNo", c.getContractNo()); out.put("customerId", c.getCustomerId());
        var customer = c.getCustomerId() == null ? null : customers.selectById(c.getCustomerId());
        out.put("customerName", customer == null ? "" : customer.getName()); out.put("signMode", c.getSignMode());
        out.put("status", c.getStatus()); out.put("serviceType", c.getServiceType()); out.put("feeModel", c.getFeeModel());
        out.put("priceTable", c.getPriceTable()); out.put("deposit", c.getDeposit()); out.put("startDate", c.getStartDate());
        out.put("endDate", c.getEndDate()); out.put("payCycle", c.getPayCycle()); out.put("files", c.getFiles());
        out.put("auditReason", c.getAuditReason()); out.put("remark", c.getRemark()); return out;
    }

    @GetMapping("/agreement")
    public Result<Map<String, Object>> agreement() {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        Map<String, Object> out = new LinkedHashMap<>(); out.put("warehouseId", w.getId()); out.put("joinStatus", w.getJoinStatus());
        out.put("contractFile", w.getContractFile() == null ? "" : w.getContractFile());
        if (w.getContractFile() != null && w.getContractFile().startsWith("file:"))
            out.put("file", MktWarehouseFileService.view(files.requireReference(w.getContractFile(), w.getId())));
        return Result.ok(out);
    }

    @PostMapping("/agreement/upload")
    public Result<Void> uploadAgreement(@RequestBody Map<String, String> body) {
        MktWarehouse w = WhAuthContext.requireWarehouse(warehouseMapper);
        String reference = body.get("file"); files.requireReference(reference, w.getId());
        int updated = warehouseMapper.update(null, new LambdaUpdateWrapper<MktWarehouse>().eq(MktWarehouse::getId, w.getId())
                .eq(MktWarehouse::getJoinStatus, 4).set(MktWarehouse::getContractFile, reference));
        if (updated != 1) throw new BizException("仅待签协议阶段可提交加盟协议，已上线协议不能自行替换");
        return Result.ok();
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
