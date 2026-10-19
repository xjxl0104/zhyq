package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.yunshan.YunshanApiException;
import com.zhyq.park.marketing.yunshan.YunshanClient;
import com.zhyq.park.marketing.yunshan.YunshanVolumeSyncService;
import com.zhyq.park.marketing.yunshan.YunshanCustomerScope;
import com.zhyq.park.marketing.yunshan.YunshanConnections;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.IntFunction;

/** 主动拉取云山日汇总，沿用 ERP 查询/配置权限；不创建佣金或订单。 */
@Tag(name = "全民营销-发货量同步（云山云仓接口）")
@RestController
@RequestMapping("/crm/marketing/yunshan")
@RequiredArgsConstructor
public class MktYunshanController {
    private final YunshanClient client;
    private final YunshanVolumeSyncService syncService;
    private final MktYunshanVolumeMapMapper maps;
    private final MktYunshanDailyVolumeMapper volumes;
    private final MktYunshanSyncLogMapper logs;
    private final MktWarehouseMapper warehouses;
    private final CustomerMapper customers;
    private final MktAuditService audit;
    private final YunshanCustomerScope customerScope;
    private final YunshanConnections connections;

    public record MapInput(@NotNull @Positive Long customerId,
                           @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,32}") String parentOwnerNo,
                           @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,32}") String parentWarehouseNo,
                           @NotNull @AssertTrue(message = "请核对并确认接口货主确为该客户") Boolean identityConfirmed,
                           @NotNull @Positive Long operationWarehouseId,
                           @NotNull @Valid YunshanConnections.Input connection) {}
    public record DirectoryInput(@NotNull @Positive Long customerId, @NotNull @Valid YunshanConnections.Input connection,
                                 String parentOwnerNo, @Min(1) int current) {}
    public record SiteOption(@JsonSerialize(using = ToStringSerializer.class) Long id, String name, String address) {}

    @GetMapping("/warehouse/{warehouseId}/customers/{customerId}/sites")
    @PreAuthorize("hasAuthority('crm:marketing:erp:config')")
    public Result<List<SiteOption>> customerSites(@PathVariable Long warehouseId, @PathVariable Long customerId) {
        return Result.ok(customerScope.sites(warehouseId, customerId).stream()
            .map(s -> new SiteOption(s.getId(), s.getName(), s.getAddress())).toList());
    }

    @PostMapping("/warehouse/{warehouseId}/directory")
    @PreAuthorize("hasAuthority('crm:marketing:erp:config')")
    public Result<YunshanClient.EntityPage> directory(@PathVariable Long warehouseId, @Valid @RequestBody DirectoryInput input) {
        customerScope.requireSigned(warehouseId, input.customerId());
        validateDirectory(null, input.parentOwnerNo(), input.current());
        YunshanClient source = connections.preview(input.connection());
        return Result.ok(input.parentOwnerNo() == null ? source.owners(null, null, input.current())
            : source.warehouses(null, input.parentOwnerNo(), input.current()));
    }
    public record StatusInput(@NotNull @Min(0) @Max(1) Integer status) {}
    public record SyncInput(@NotNull LocalDate startDate, @NotNull LocalDate endDate) {}
    public record CustomerOption(@JsonSerialize(using = ToStringSerializer.class) Long id, String name) {}
    public record WarehouseOption(@JsonSerialize(using = ToStringSerializer.class) Long id, String code, String name, Integer joinStatus, String orderMode, Integer erpStatus) {}
    public record VolumeResult(List<MktYunshanDailyVolume> records, long total, String totalConsignNum,
                               int daysWithData, int daysMissing, String dataState) {}

    @Operation(summary = "云山云仓接口配置状态（不返回凭证）")
    @GetMapping("/config") @PreAuthorize("hasAuthority('crm:marketing:erp:query')")
    public Result<Map<String, Object>> config() {
        return Result.ok(Map.of("configured", client.configured(), "baseUrl", client.baseUrl(), "appId", client.maskedAppId()));
    }

    @Operation(summary = "ERP页面云仓候选（仅接入所需字段）")
    @GetMapping("/warehouses") @PreAuthorize("hasAuthority('crm:marketing:erp:query')")
    public Result<PageResult<WarehouseOption>> warehouseOptions(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        validatePage(pageNo, pageSize);
        if (keyword != null && keyword.length() > 128) throw new BizException("关键词不能超过128字");
        Page<MktWarehouse> page = warehouses.selectPage(new Page<>(pageNo, pageSize), new LambdaQueryWrapper<MktWarehouse>()
                .select(MktWarehouse::getId, MktWarehouse::getCode, MktWarehouse::getName, MktWarehouse::getJoinStatus,
                        MktWarehouse::getOrderMode, MktWarehouse::getErpStatus)
                .like(StringUtils.hasText(keyword), MktWarehouse::getName, keyword).orderByDesc(MktWarehouse::getId));
        return Result.ok(PageResult.of(page.getTotal(), page.getRecords().stream().map(MktYunshanController::warehouseView).toList()));
    }

    @Operation(summary = "ERP页面云仓接入状态")
    @GetMapping("/warehouses/{id}") @PreAuthorize("hasAuthority('crm:marketing:erp:query')")
    public Result<WarehouseOption> warehouseOption(@PathVariable Long id) {
        MktWarehouse warehouse = warehouses.selectById(id);
        if (warehouse == null) throw new BizException("云仓不存在或已删除");
        return Result.ok(warehouseView(warehouse));
    }

    @Operation(summary = "授权货主目录")
    @GetMapping("/entity/owners") @PreAuthorize("hasAuthority('crm:marketing:erp:config')")
    public Result<YunshanClient.EntityPage> owners(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String parentWarehouseNo, @RequestParam(defaultValue = "1") int current) {
        validateDirectory(keyword, parentWarehouseNo, current);
        return Result.ok(client.owners(keyword, parentWarehouseNo, current));
    }

    @Operation(summary = "授权仓库目录（按所选货主过滤）")
    @GetMapping("/entity/warehouses") @PreAuthorize("hasAuthority('crm:marketing:erp:config')")
    public Result<YunshanClient.EntityPage> entityWarehouses(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String parentOwnerNo, @RequestParam(defaultValue = "1") int current) {
        validateDirectory(keyword, parentOwnerNo, current);
        return Result.ok(client.warehouses(keyword, parentOwnerNo, current));
    }

    @Operation(summary = "映射客户候选（仅编号和名称）")
    @GetMapping("/customers") @PreAuthorize("hasAuthority('crm:marketing:erp:config')")
    public Result<PageResult<CustomerOption>> customerOptions(@RequestParam Long warehouseId, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        validatePage(pageNo, pageSize);
        if (keyword != null && keyword.length() > 128) throw new BizException("关键词不能超过128字");
        Page<Customer> page = customers.selectPage(new Page<>(pageNo, pageSize), customerScope.signedCustomers(warehouseId)
                .select(Customer::getId, Customer::getName).like(StringUtils.hasText(keyword), Customer::getName, keyword)
                .orderByDesc(Customer::getId));
        return Result.ok(PageResult.of(page.getTotal(), page.getRecords().stream()
                .map(c -> new CustomerOption(c.getId(), c.getName())).toList()));
    }

    @Operation(summary = "云仓的发货量客户关联")
    @GetMapping("/warehouse/{warehouseId}/maps") @PreAuthorize("hasAuthority('crm:marketing:erp:query')")
    public Result<List<Map<String, Object>>> mappings(@PathVariable Long warehouseId) {
        List<MktYunshanVolumeMap> rows = maps.selectList(new LambdaQueryWrapper<MktYunshanVolumeMap>()
                .eq(MktYunshanVolumeMap::getWarehouseId, warehouseId).orderByDesc(MktYunshanVolumeMap::getId));
        Map<Long, String> names = new HashMap<>();
        if (!rows.isEmpty()) customers.selectBatchIds(rows.stream().map(MktYunshanVolumeMap::getCustomerId).distinct().toList())
                .forEach(c -> names.put(c.getId(), c.getName()));
        return Result.ok(rows.stream().map(m -> mappingView(m, names.get(m.getCustomerId()))).toList());
    }

    @Operation(summary = "新增发货量客户关联")
    @PostMapping("/warehouse/{warehouseId}/maps") @PreAuthorize("hasAuthority('crm:marketing:erp:config')")
    @Transactional
    public Result<String> createMapping(@PathVariable Long warehouseId, @Valid @RequestBody MapInput input) {
        if (!Boolean.TRUE.equals(input.identityConfirmed())) throw new BizException("请核对并确认接口货主确为该客户");
        MktOperationWarehouse site = customerScope.requireSite(warehouseId, input.customerId(), input.operationWarehouseId());
        YunshanClient source = connections.preview(input.connection());
        YunshanClient.Entity owner = findEntity(input.parentOwnerNo(), page -> source.owners(null, input.parentWarehouseNo(), page));
        YunshanClient.Entity remoteWarehouse = findEntity(input.parentWarehouseNo(),
                page -> source.warehouses(null, input.parentOwnerNo(), page));
        if (owner == null || remoteWarehouse == null) throw new BizException("该货主与仓库组合未获云山授权，请重新选择");
        // 与客户/云仓删除流程共用行锁，避免刚创建的映射指向已删除主体。
        Customer customer = customers.selectForUpdate(input.customerId());
        if (customer == null) throw new BizException("客户不存在或已删除");
        YunshanVolumeSyncService.requireMatchingIdentity(customer.getName(), owner.name());
        MktWarehouse warehouse = warehouses.selectForUpdate(warehouseId);
        requireUsableWarehouse(warehouse);
        MktYunshanVolumeMap mapping = new MktYunshanVolumeMap();
        mapping.setCustomerId(customer.getId()); mapping.setWarehouseId(warehouseId);
        customerScope.requireSite(warehouseId, input.customerId(), input.operationWarehouseId());
        mapping.setOperationWarehouseId(site.getId()); mapping.setOperationWarehouseName(site.getName());
        connections.store(mapping, input.connection());
        mapping.setParentOwnerNo(owner.no()); mapping.setParentOwnerName(owner.name());
        mapping.setParentWarehouseNo(remoteWarehouse.no()); mapping.setParentWarehouseName(remoteWarehouse.name());
        mapping.setStatus(1); mapping.setProjectId(warehouse.getProjectId()); mapping.setTenantId(warehouse.getTenantId());
        try {
            if (maps.insert(mapping) != 1) throw new BizException("保存映射失败，请重试");
        } catch (DuplicateKeyException e) {
            throw new BizException("该接口货主与作业仓库组合已绑定，请查看原映射（停用的映射可重新启用）");
        }
        audit.log("yunshan.mapping.create", "yunshan_map", mapping.getId(), "新增发货量映射", null,
                Map.of("customerId", customer.getId(), "warehouseId", warehouseId,
                        "parentOwnerNo", owner.no(), "parentWarehouseNo", remoteWarehouse.no(), "identityConfirmed", true));
        return Result.ok(String.valueOf(mapping.getId()));
    }

    @Operation(summary = "启停发货量客户关联（保留历史）")
    @PutMapping("/maps/{id}/status") @PreAuthorize("hasAuthority('crm:marketing:erp:config')")
    @Transactional
    public Result<Void> status(@PathVariable Long id, @Valid @RequestBody StatusInput input) {
        MktYunshanVolumeMap mapping = requireMap(id);
        if (input.status() == 1) {
            Customer customer = customers.selectForUpdate(mapping.getCustomerId());
            if (customer == null) throw new BizException("客户不存在或已删除");
            YunshanVolumeSyncService.requireMatchingIdentity(customer.getName(), mapping.getParentOwnerName());
            requireUsableWarehouse(warehouses.selectForUpdate(mapping.getWarehouseId()));
            customerScope.requireSigned(mapping.getWarehouseId(), mapping.getCustomerId());
            if (mapping.getOperationWarehouseId() != null)
                customerScope.requireSite(mapping.getWarehouseId(), mapping.getCustomerId(), mapping.getOperationWarehouseId());
        }
        if (Objects.equals(mapping.getStatus(), input.status())) return Result.ok();
        int changed = maps.update(null, new LambdaUpdateWrapper<MktYunshanVolumeMap>()
                .eq(MktYunshanVolumeMap::getId, id).eq(MktYunshanVolumeMap::getStatus, 1 - input.status())
                .set(MktYunshanVolumeMap::getStatus, input.status())
                .set(MktYunshanVolumeMap::getUpdateTime, LocalDateTime.now(YunshanVolumeSyncService.SHANGHAI))
                .set(MktYunshanVolumeMap::getUpdateBy, MyMetaObjectHandler.currentOperator()).setSql("version=version+1"));
        if (changed != 1) throw new BizException("映射状态已变化，请刷新后重试");
        audit.log("yunshan.mapping.status", "yunshan_map", id, input.status() == 1 ? "启用发货量映射" : "停用发货量映射",
                Map.of("status", mapping.getStatus()), Map.of("status", input.status()));
        return Result.ok();
    }

    @Operation(summary = "补拉指定日期区间")
    @PostMapping("/maps/{id}/sync") @PreAuthorize("hasAuthority('crm:marketing:erp:config')")
    public Result<MktYunshanSyncLog> sync(@PathVariable Long id, @Valid @RequestBody SyncInput input) {
        MktYunshanSyncLog result = syncService.sync(id, input.startDate(), input.endDate(), 2);
        audit.log("yunshan.sync", "yunshan_map", id, "手动补拉 " + input.startDate() + " 至 " + input.endDate());
        return Result.ok(result);
    }

    @Operation(summary = "本地逐日票数及完整区间合计")
    @GetMapping("/volume") @PreAuthorize("hasAuthority('crm:marketing:erp:query')")
    public Result<VolumeResult> volume(@RequestParam Long mapId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        validatePage(pageNo, pageSize); YunshanVolumeSyncService.validateRange(startDate, endDate); requireMap(mapId);
        // 最多92行；合计涵盖整个区间，不受当前显示页影响。
        List<MktYunshanDailyVolume> rows = volumes.selectList(new LambdaQueryWrapper<MktYunshanDailyVolume>()
                .eq(MktYunshanDailyVolume::getMapId, mapId).between(MktYunshanDailyVolume::getStatDate, startDate, endDate)
                .orderByAsc(MktYunshanDailyVolume::getStatDate));
        BigInteger sum = rows.stream().map(r -> BigInteger.valueOf(r.getConsignNum())).reduce(BigInteger.ZERO, BigInteger::add);
        int from = (int) Math.min((long) (pageNo - 1) * pageSize, rows.size()), to = Math.min(from + pageSize, rows.size());
        int missing = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1 - rows.size();
        return Result.ok(new VolumeResult(rows.subList(from, to), rows.size(), sum.toString(), rows.size(), missing,
                rows.isEmpty() ? "NO_STAT_DATA" : "HAS_STAT_DATA"));
    }

    @Operation(summary = "只读对账：本地、云山合计及每日差异")
    @GetMapping("/reconcile") @PreAuthorize("hasAuthority('crm:marketing:erp:query')")
    public Result<YunshanVolumeSyncService.ReconcileResult> reconcile(@RequestParam Long mapId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(syncService.reconcile(mapId, startDate, endDate));
    }

    @Operation(summary = "云山拉取日志")
    @GetMapping("/logs") @PreAuthorize("hasAuthority('crm:marketing:erp:query')")
    public Result<PageResult<MktYunshanSyncLog>> logs(@RequestParam Long mapId,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        validatePage(pageNo, pageSize); requireMap(mapId);
        Page<MktYunshanSyncLog> page = logs.selectPage(new Page<>(pageNo, pageSize), new LambdaQueryWrapper<MktYunshanSyncLog>()
                .eq(MktYunshanSyncLog::getMapId, mapId).orderByDesc(MktYunshanSyncLog::getId));
        return Result.ok(PageResult.of(page.getTotal(), page.getRecords()));
    }

    private MktYunshanVolumeMap requireMap(Long id) {
        MktYunshanVolumeMap map = maps.selectById(id);
        if (map == null) throw new BizException("发货量客户关联不存在");
        return map;
    }
    private static void requireUsableWarehouse(MktWarehouse warehouse) {
        if (warehouse == null) throw new BizException("云仓不存在或已删除");
        if (Integer.valueOf(8).equals(warehouse.getJoinStatus()) || Integer.valueOf(7).equals(warehouse.getJoinStatus()))
            throw new BizException("云仓已退出或冻结待删除，不能启用映射");
    }
    private static void validatePage(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) throw new BizException("页码必须为正整数，每页数量为1至100");
    }
    private static void validateDirectory(String keyword, String entityNo, int current) {
        validatePage(current, 20);
        if (keyword != null && keyword.length() > 128) throw new BizException("关键词不能超过128字");
        if (StringUtils.hasText(entityNo) && !entityNo.matches("[A-Za-z0-9_-]{1,32}")) throw new BizException("云山实体编号格式不正确");
    }
    private static YunshanClient.Entity findEntity(String no, IntFunction<YunshanClient.EntityPage> fetch) {
        for (int current = 1; current <= 1000; current++) {
            YunshanClient.EntityPage page = fetch.apply(current);
            for (YunshanClient.Entity entity : page.records()) if (no.equals(entity.no())) return entity;
            if (current >= page.pages()) return null;
            if (page.records().isEmpty()) throw new YunshanApiException("BAD_RESPONSE", "实体目录分页不完整，请重试");
        }
        throw new BizException("授权实体目录过大，请联系管理员核对授权范围");
    }
    private static Map<String, Object> mappingView(MktYunshanVolumeMap m, String name) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", m.getId()); out.put("customerId", m.getCustomerId()); out.put("warehouseId", m.getWarehouseId());
        out.put("customerName", name == null ? "客户已删除" : name);
        out.put("parentOwnerNo", m.getParentOwnerNo()); out.put("parentOwnerName", m.getParentOwnerName());
        out.put("parentWarehouseNo", m.getParentWarehouseNo()); out.put("parentWarehouseName", m.getParentWarehouseName());
        out.put("operationWarehouseId", m.getOperationWarehouseId()); out.put("operationWarehouseName", m.getOperationWarehouseName());
        out.put("connectionMode", m.getConnectionBaseUrl() == null ? "SHARED" : "CUSTOM");
        out.put("connectionBaseUrl", m.getConnectionBaseUrl());
        out.put("identityMismatch", !YunshanVolumeSyncService.namesMatch(name, m.getParentOwnerName()));
        out.put("status", m.getStatus()); out.put("lastSyncAt", m.getLastSyncAt()); out.put("lastSyncOk", m.getLastSyncOk());
        return out;
    }
    private static WarehouseOption warehouseView(MktWarehouse w) {
        return new WarehouseOption(w.getId(), w.getCode(), w.getName(), w.getJoinStatus(), w.getOrderMode(), w.getErpStatus());
    }
}
