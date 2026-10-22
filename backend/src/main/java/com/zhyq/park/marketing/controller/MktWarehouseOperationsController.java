package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.MktAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** 本地客户和作业仓库建档，与外部发货量接口完全独立。 */
@Tag(name = "全民营销-客户作业仓库")
@RestController
@RequestMapping("/crm/marketing/warehouse-operations")
@RequiredArgsConstructor
public class MktWarehouseOperationsController {
    private static final Long TENANT = MyMetaObjectHandler.DEFAULT_TENANT_ID;
    private final MktOperationWarehouseMapper sites;
    private final MktCustomerOperationWarehouseMapper links;
    private final MktWarehouseMapper warehouses;
    private final CustomerMapper customers;
    private final SysAuditMapper audits;
    private final ObjectMapper json;
    /** 非构造注入：仅用于删除前检查是否已配置发货量同步。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MktYunshanVolumeMapMapper yunshanMaps;

    public record SiteInput(@NotBlank @Size(max = 128) String name, @Size(max = 300) String address) {}
    public record LinkInput(@NotNull @Positive Long customerId, @NotNull @Positive Long operationWarehouseId) {}
    public record StatusInput(@NotNull @Min(0) @Max(1) Integer status) {}
    public record SiteOption(@JsonSerialize(using = ToStringSerializer.class) Long id, String name, String address, Integer status) {}
    public record LinkOption(@JsonSerialize(using = ToStringSerializer.class) Long id,
                             @JsonSerialize(using = ToStringSerializer.class) Long customerId, String customerName,
                             @JsonSerialize(using = ToStringSerializer.class) Long operationWarehouseId,
                             String operationWarehouseName, String address, Integer status) {}

    @Operation(summary = "本地作业仓库列表")
    @GetMapping("/{warehouseId}/sites") @PreAuthorize("hasAuthority('crm:marketing:erp:query')")
    public Result<PageResult<SiteOption>> sites(@PathVariable Long warehouseId,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        positive(warehouseId, "加盟云仓"); validatePage(pageNo, pageSize);
        if (status != null && status != 0 && status != 1) throw new BizException(400, "状态只能为启用或停用");
        String term = text(keyword, 128, false, "关键词");
        Page<MktOperationWarehouse> page = sites.selectPage(new Page<>(pageNo, pageSize), new LambdaQueryWrapper<MktOperationWarehouse>()
                .eq(MktOperationWarehouse::getTenantId, TENANT).eq(MktOperationWarehouse::getWarehouseId, warehouseId)
                .eq(status != null, MktOperationWarehouse::getStatus, status)
                .like(StringUtils.hasText(term), MktOperationWarehouse::getName, term).orderByDesc(MktOperationWarehouse::getId));
        return Result.ok(PageResult.of(page.getTotal(), page.getRecords().stream()
                .map(s -> new SiteOption(s.getId(), s.getName(), s.getAddress(), s.getStatus())).toList()));
    }

    @Operation(summary = "手动新增本地作业仓库")
    @PostMapping("/{warehouseId}/sites") @PreAuthorize("hasAuthority('crm:marketing:erp:config')")
    @Transactional
    public Result<String> createSite(@PathVariable Long warehouseId, @Valid @RequestBody SiteInput input) {
        String name = text(input.name(), 128, true, "作业仓库名称");
        String address = text(input.address(), 300, false, "作业仓库地址");
        MktWarehouse warehouse = lockWarehouse(warehouseId);
        MktOperationWarehouse site = new MktOperationWarehouse();
        site.setWarehouseId(warehouseId); site.setName(name); site.setAddress(address); site.setStatus(1);
        site.setTenantId(TENANT); site.setProjectId(warehouse.getProjectId());
        try { changed(sites.insert(site)); }
        catch (DuplicateKeyException duplicate) { throw new BizException(409, "该加盟云仓已录入同名作业仓库，请直接选择已有仓库"); }
        audit("operation_warehouse.create", "operation_warehouse", site.getId(), site.getProjectId(),
                "手动新增本地作业仓库", null, Map.of("warehouseId", warehouseId, "name", name, "address", address));
        return Result.ok(String.valueOf(site.getId()));
    }

    @Operation(summary = "客户作业仓库关系列表（保留停用记录）")
    @GetMapping("/{warehouseId}/links") @PreAuthorize("hasAuthority('crm:marketing:erp:query')")
    public Result<PageResult<LinkOption>> links(@PathVariable Long warehouseId,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        positive(warehouseId, "加盟云仓"); validatePage(pageNo, pageSize);
        Page<MktCustomerOperationWarehouse> page = links.selectPage(new Page<>(pageNo, pageSize), new LambdaQueryWrapper<MktCustomerOperationWarehouse>()
                .eq(MktCustomerOperationWarehouse::getTenantId, TENANT).eq(MktCustomerOperationWarehouse::getWarehouseId, warehouseId)
                .orderByDesc(MktCustomerOperationWarehouse::getId));
        Map<Long, String> customerNames = new HashMap<>();
        Map<Long, MktOperationWarehouse> siteRows = new HashMap<>();
        if (!page.getRecords().isEmpty()) {
            customers.selectList(new LambdaQueryWrapper<Customer>().eq(Customer::getTenantId, TENANT)
                    .in(Customer::getId, page.getRecords().stream().map(MktCustomerOperationWarehouse::getCustomerId).distinct().toList()))
                    .forEach(c -> customerNames.put(c.getId(), c.getName()));
            sites.selectList(new LambdaQueryWrapper<MktOperationWarehouse>().eq(MktOperationWarehouse::getTenantId, TENANT)
                    .eq(MktOperationWarehouse::getWarehouseId, warehouseId)
                    .in(MktOperationWarehouse::getId, page.getRecords().stream().map(MktCustomerOperationWarehouse::getOperationWarehouseId).distinct().toList()))
                    .forEach(s -> siteRows.put(s.getId(), s));
        }
        return Result.ok(PageResult.of(page.getTotal(), page.getRecords().stream().map(link -> {
            MktOperationWarehouse site = siteRows.get(link.getOperationWarehouseId());
            return new LinkOption(link.getId(), link.getCustomerId(), customerNames.getOrDefault(link.getCustomerId(), "客户已删除"),
                    link.getOperationWarehouseId(), site == null ? "作业仓库已停用或删除" : site.getName(),
                    site == null ? "" : site.getAddress(), link.getStatus());
        }).toList()));
    }

    @Operation(summary = "新增客户与作业仓库关系")
    @PostMapping("/{warehouseId}/links") @PreAuthorize("hasAuthority('crm:marketing:erp:config')")
    @Transactional
    public Result<Void> createLink(@PathVariable Long warehouseId, @Valid @RequestBody LinkInput input) {
        positive(input.customerId(), "客户"); positive(input.operationWarehouseId(), "作业仓库");
        // 与加盟云仓删除及 ERP 关联保持相同锁顺序：加盟云仓 -> 客户 -> 作业仓库 -> 关系。
        MktWarehouse warehouse = lockWarehouse(warehouseId);
        lockCustomer(input.customerId(), warehouse);
        lockSite(input.operationWarehouseId(), warehouseId);
        MktCustomerOperationWarehouse link = new MktCustomerOperationWarehouse();
        link.setWarehouseId(warehouseId); link.setCustomerId(input.customerId()); link.setOperationWarehouseId(input.operationWarehouseId());
        link.setStatus(1); link.setTenantId(TENANT); link.setProjectId(warehouse.getProjectId());
        try { changed(links.insert(link)); }
        catch (DuplicateKeyException duplicate) { throw new BizException(409, "该客户已关联此作业仓库；如已停用，请在列表中重新启用"); }
        audit("operation_warehouse.link.create", "customer_operation_warehouse", link.getId(), link.getProjectId(),
                "新增客户作业仓库关系", null, snapshot(link));
        return Result.ok();
    }

    @Operation(summary = "启停客户作业仓库关系")
    @PutMapping("/{warehouseId}/links/{id}/status") @PreAuthorize("hasAuthority('crm:marketing:erp:config')")
    @Transactional
    public Result<Void> status(@PathVariable Long warehouseId, @PathVariable Long id, @Valid @RequestBody StatusInput input) {
        positive(warehouseId, "加盟云仓"); positive(id, "客户作业仓库关系");
        if (input.status() == null || input.status() < 0 || input.status() > 1) throw new BizException(400, "状态只能为启用或停用");
        if (input.status() == 1) {
            MktWarehouse warehouse = lockWarehouse(warehouseId);
            MktCustomerOperationWarehouse found = links.selectOne(new LambdaQueryWrapper<MktCustomerOperationWarehouse>()
                    .eq(MktCustomerOperationWarehouse::getTenantId, TENANT).eq(MktCustomerOperationWarehouse::getWarehouseId, warehouseId)
                    .eq(MktCustomerOperationWarehouse::getId, id));
            if (found == null) throw new BizException(404, "客户作业仓库关系不存在");
            lockCustomer(found.getCustomerId(), warehouse); lockSite(found.getOperationWarehouseId(), warehouseId);
        }
        MktCustomerOperationWarehouse link = links.selectForUpdate(id, warehouseId, TENANT);
        if (link == null) throw new BizException(404, "客户作业仓库关系不存在");
        if (Objects.equals(link.getStatus(), input.status())) return Result.ok();
        changed(links.update(null, new LambdaUpdateWrapper<MktCustomerOperationWarehouse>()
                .eq(MktCustomerOperationWarehouse::getTenantId, TENANT).eq(MktCustomerOperationWarehouse::getWarehouseId, warehouseId)
                .eq(MktCustomerOperationWarehouse::getId, id).eq(MktCustomerOperationWarehouse::getStatus, link.getStatus())
                .set(MktCustomerOperationWarehouse::getStatus, input.status()).setSql("version=version+1")
                .set(MktCustomerOperationWarehouse::getUpdateBy, MyMetaObjectHandler.currentOperator())
                .set(MktCustomerOperationWarehouse::getUpdateTime, LocalDateTime.now())));
        Map<String, Object> before = snapshot(link); link.setStatus(input.status());
        audit("operation_warehouse.link.status", "customer_operation_warehouse", id, link.getProjectId(),
                input.status() == 1 ? "启用客户作业仓库关系" : "停用客户作业仓库关系", before, snapshot(link));
        return Result.ok();
    }

    @Operation(summary = "删除客户作业仓库关系（已配置发货量同步的关系不能删除）")
    @DeleteMapping("/{warehouseId}/links/{id}") @PreAuthorize("hasAuthority('crm:marketing:erp:config')")
    @Transactional
    public Result<Void> deleteLink(@PathVariable Long warehouseId, @PathVariable Long id) {
        positive(warehouseId, "加盟云仓"); positive(id, "客户作业仓库关系");
        MktCustomerOperationWarehouse link = links.selectForUpdate(id, warehouseId, TENANT);
        if (link == null) throw new BizException(404, "客户作业仓库关系不存在或已删除");
        if (yunshanMaps != null && yunshanMaps.selectCount(new LambdaQueryWrapper<MktYunshanVolumeMap>()
                .eq(MktYunshanVolumeMap::getWarehouseId, warehouseId)
                .eq(MktYunshanVolumeMap::getCustomerId, link.getCustomerId())
                .eq(MktYunshanVolumeMap::getOperationWarehouseId, link.getOperationWarehouseId())) > 0)
            throw new BizException(409, "该客户在此作业仓库已配置发货量同步，需保留历史快照，不能删除；不再使用请先停用");
        Map<String, Object> before = snapshot(link);
        changed(links.hardDelete(id, warehouseId, TENANT));
        audit("operation_warehouse.link.delete", "customer_operation_warehouse", id, link.getProjectId(),
                "删除客户作业仓库关系", before, Map.of("deleted", true));
        return Result.ok();
    }

    private MktWarehouse lockWarehouse(Long id) {
        positive(id, "加盟云仓");
        MktWarehouse warehouse = warehouses.selectForUpdate(id);
        if (warehouse == null || !TENANT.equals(warehouse.getTenantId())) throw new BizException(404, "加盟云仓不存在或已删除");
        if (Integer.valueOf(7).equals(warehouse.getJoinStatus()) || Integer.valueOf(8).equals(warehouse.getJoinStatus()))
            throw new BizException(409, "加盟云仓已退出，不能新增或启用作业仓库关系");
        return warehouse;
    }
    private void lockCustomer(Long id, MktWarehouse warehouse) {
        Customer customer = customers.selectForUpdate(id);
        if (customer == null || !TENANT.equals(customer.getTenantId())) throw new BizException(404, "客户不存在或已删除");
        if (warehouse.getProjectId() != null && customer.getProjectId() != null && !warehouse.getProjectId().equals(customer.getProjectId()))
            throw new BizException(409, "不能关联其他园区的客户");
    }
    private void lockSite(Long id, Long warehouseId) {
        MktOperationWarehouse site = sites.selectForUpdate(id, TENANT);
        if (site == null || !Objects.equals(warehouseId, site.getWarehouseId())) throw new BizException(404, "作业仓库不属于当前加盟云仓或已删除");
        if (!Integer.valueOf(1).equals(site.getStatus())) throw new BizException(409, "作业仓库已停用，不能新增或启用客户关系");
    }
    private void audit(String action, String type, Long id, Long projectId, String reason, Object before, Object after) {
        SysAudit audit = new SysAudit(); audit.setModule(MktAuditService.MODULE); audit.setAction(action); audit.setBizType(type);
        audit.setBizId(id); audit.setTenantId(TENANT); audit.setProjectId(projectId); audit.setReason(reason);
        audit.setOperator(MyMetaObjectHandler.currentOperator());
        try { audit.setBeforeJson(before == null ? null : json.writeValueAsString(before)); audit.setAfterJson(json.writeValueAsString(after)); }
        catch (JsonProcessingException failure) { throw new BizException("作业仓库审计生成失败，请重试"); }
        if (audits.insert(audit) != 1) throw new BizException("作业仓库审计保存失败，请重试");
    }
    private static Map<String, Object> snapshot(MktCustomerOperationWarehouse link) {
        return Map.of("warehouseId", link.getWarehouseId(), "customerId", link.getCustomerId(),
                "operationWarehouseId", link.getOperationWarehouseId(), "status", link.getStatus());
    }
    private static String text(String value, int max, boolean required, String label) {
        String normalized = value == null ? "" : value.trim();
        if ((required && normalized.isEmpty()) || normalized.length() > max)
            throw new BizException(400, label + (required ? "必填且" : "") + "不能超过" + max + "字");
        return normalized;
    }
    private static void positive(Long id, String label) { if (id == null || id <= 0) throw new BizException(400, "请选择有效的" + label); }
    private static void validatePage(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) throw new BizException(400, "页码必须为正整数，每页数量为1至100");
    }
    private static void changed(int count) { if (count != 1) throw new BizException(409, "作业仓库关系已变化，请刷新后重试"); }
}
