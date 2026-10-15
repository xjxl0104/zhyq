package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.mapper.CustomerMapper;
import org.springframework.transaction.annotation.Transactional;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.marketing.entity.MktCustomerErpMap;
import com.zhyq.park.marketing.entity.MktErpSyncLog;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseErp;
import com.zhyq.park.marketing.mapper.MktCustomerErpMapMapper;
import com.zhyq.park.marketing.mapper.MktErpSyncLogMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseErpMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.open.ErpSignatureService;
import com.zhyq.park.marketing.open.ErpConnectionService;
import com.zhyq.park.marketing.open.ErpExceptionService;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ERP 对接(阶段 C 只留接口):签发/重置凭证、货主编码映射、同步日志。secret 只在签发/重置时明文回一次。 */
@Tag(name = "全民营销-ERP 对接")
@RestController
@RequestMapping("/crm/marketing/erp")
@RequiredArgsConstructor
public class MktErpController {

    private final MktWarehouseMapper warehouseMapper;
    private final MktWarehouseErpMapper erpMapper;
    private final MktCustomerErpMapMapper mapMapper;
    private final MktErpSyncLogMapper logMapper;
    private final FieldEncryptionService encryption;
    private final MktAuditService auditService;
    private final CustomerMapper customerMapper;
    private final ErpConnectionService connections;
    private final ErpExceptionService exceptions;

    @Operation(summary = "云仓的凭证(不含 secret)") @PreAuthorize("hasAuthority('crm:marketing:erp:query')") @GetMapping("/warehouse/{warehouseId}")
    public Result<Map<String, Object>> get(@PathVariable Long warehouseId) {
        MktWarehouseErp e = erpMapper.selectOne(new LambdaQueryWrapper<MktWarehouseErp>().eq(MktWarehouseErp::getWarehouseId, warehouseId));
        if (e != null) connections.refresh(e, java.time.LocalDateTime.now());
        return Result.ok(e == null ? null : view(e, null));
    }

    @Operation(summary = "签发凭证(沙箱);已有则报错,用 reset") @PreAuthorize("hasAuthority('crm:marketing:erp:config')") @PostMapping("/warehouse/{warehouseId}/issue")
    @Transactional
    public Result<Map<String, Object>> issue(@PathVariable Long warehouseId) {
        MktWarehouse w = lockWarehouse(warehouseId);
        if (erpMapper.selectCount(new LambdaQueryWrapper<MktWarehouseErp>().eq(MktWarehouseErp::getWarehouseId, warehouseId)) > 0) {
            throw new BizException("该云仓已有凭证,请用「重置密钥」");
        }
        String secret = ErpSignatureService.newSecret();
        MktWarehouseErp e = new MktWarehouseErp();
        e.setWarehouseId(warehouseId);
        e.setMode(1);
        e.setAppId(ErpSignatureService.newAppId());
        e.setSecretEnc(encryption.encrypt(secret));
        e.setEnv(1);
        e.setStatus(1);
        e.setProjectId(w.getProjectId());
        try {
            erpMapper.insert(e);
        } catch (DuplicateKeyException dup) {
            throw new BizException("App-Id 撞号,请重试");
        }
        auditService.log("erp.issue", "warehouse", warehouseId, "签发沙箱凭证 " + e.getAppId());
        connections.invalidate(e);
        return Result.ok(view(e, secret));
    }

    @Operation(summary = "重置密钥(旧密钥立即失效)") @PreAuthorize("hasAuthority('crm:marketing:erp:config')") @PostMapping("/warehouse/{warehouseId}/reset-secret")
    @Transactional
    public Result<Map<String, Object>> reset(@PathVariable Long warehouseId) {
        lockWarehouse(warehouseId);
        MktWarehouseErp e = require(warehouseId);
        String secret = ErpSignatureService.newSecret();
        e.setSecretEnc(encryption.encrypt(secret));
        erpMapper.update(null, new LambdaUpdateWrapper<MktWarehouseErp>().eq(MktWarehouseErp::getId, e.getId())
                .set(MktWarehouseErp::getSecretEnc, e.getSecretEnc()));
        connections.invalidate(e);
        auditService.log("erp.reset", "warehouse", warehouseId, "重置密钥 " + e.getAppId());
        return Result.ok(view(e, secret));
    }

    @Operation(summary = "切正式 / 启停") @PreAuthorize("hasAuthority('crm:marketing:erp:config')") @PutMapping("/warehouse/{warehouseId}")
    @Transactional
    public Result<Void> update(@PathVariable Long warehouseId, @RequestBody Map<String, Integer> body) {
        Integer env = body.get("env"), status = body.get("status");
        if (env != null && env != 1 && env != 2) throw new BizException("环境只能为沙箱或正式");
        if (status != null && status != 0 && status != 1) throw new BizException("凭证状态只能为启用或停用");
        if (env == null && status == null) throw new BizException("请选择要更新的环境或状态");
        lockWarehouse(warehouseId);
        MktWarehouseErp e = require(warehouseId);
        Map<String, Integer> before = Map.of("env", e.getEnv(), "status", e.getStatus());
        boolean changed = (env != null && !env.equals(e.getEnv())) || (status != null && !status.equals(e.getStatus()));
        erpMapper.update(null, new LambdaUpdateWrapper<MktWarehouseErp>().eq(MktWarehouseErp::getId, e.getId())
                .set(body.get("env") != null, MktWarehouseErp::getEnv, body.get("env"))
                .set(body.get("status") != null, MktWarehouseErp::getStatus, body.get("status")));
        if (env != null) e.setEnv(env);
        if (status != null) e.setStatus(status);
        if (changed) connections.invalidate(e);
        auditService.log("erp.update", "warehouse", warehouseId, null, before, body);
        return Result.ok();
    }

    @Operation(summary = "货主编码映射列表") @PreAuthorize("hasAuthority('crm:marketing:erp:query')") @GetMapping("/warehouse/{warehouseId}/mappings")
    public Result<List<MktCustomerErpMap>> mappings(@PathVariable Long warehouseId) {
        return Result.ok(mapMapper.selectList(new LambdaQueryWrapper<MktCustomerErpMap>().eq(MktCustomerErpMap::getWarehouseId, warehouseId).orderByDesc(MktCustomerErpMap::getId)));
    }

    @Operation(summary = "新增/更新映射(客户在该云仓 ERP 的货主编码)") @PreAuthorize("hasAuthority('crm:marketing:erp:config')") @PostMapping("/warehouse/{warehouseId}/mappings")
    @Transactional
    public Result<Map<String, Integer>> saveMapping(@PathVariable Long warehouseId, @RequestBody Map<String, Object> body) {
        MktWarehouse warehouse = lockWarehouse(warehouseId);
        Long customerId;
        try { customerId = Long.valueOf(String.valueOf(body.get("customerId"))); }
        catch (Exception e) { throw new BizException("请选择有效客户"); }
        var customer = customerMapper.selectForUpdate(customerId);
        if (customer == null) throw new BizException("客户不存在或已删除");
        if (warehouse.getProjectId() != null && customer.getProjectId() != null && !warehouse.getProjectId().equals(customer.getProjectId()))
            throw new BizException("不能为其他园区客户配置货主映射");
        String code = body.get("customerCode") == null ? "" : body.get("customerCode").toString().trim();
        if (!StringUtils.hasText(code) || code.length() > 64) throw new BizException("请填写 1–64 字的货主编码");
        MktCustomerErpMap m = mapMapper.selectOne(new LambdaQueryWrapper<MktCustomerErpMap>()
                .eq(MktCustomerErpMap::getWarehouseId, warehouseId).eq(MktCustomerErpMap::getCustomerId, customerId));
        try {
            if (m == null) {
                m = new MktCustomerErpMap(); m.setWarehouseId(warehouseId); m.setCustomerId(customerId); m.setCustomerCode(code); m.setStatus(1);
                mapMapper.insert(m);
            } else {
                m.setCustomerCode(code); m.setStatus(1); mapMapper.updateById(m);
            }
        } catch (DuplicateKeyException dup) {
            throw new BizException("该货主编码已被其他客户占用");
        }
        auditService.log("erp.mapping", "customer", customerId, "云仓 " + warehouseId + " 货主编码 " + code);
        return Result.ok(Map.of("queued", exceptions.enqueueForMapping(warehouseId, code)));
    }

    @Operation(summary = "删除映射") @PreAuthorize("hasAuthority('crm:marketing:erp:config')") @DeleteMapping("/mappings/{id}")
    @Transactional
    public Result<Void> deleteMapping(@PathVariable Long id) {
        MktCustomerErpMap mapping = mapMapper.selectById(id);
        if (mapping == null) throw new BizException("映射不存在");
        lockWarehouse(mapping.getWarehouseId());
        mapMapper.deleteById(id);
        auditService.log("erp.mapping.delete", "erp_map", id, null);
        return Result.ok();
    }

    @Operation(summary = "同步日志") @PreAuthorize("hasAuthority('crm:marketing:erp:query')") @GetMapping("/logs")
    public Result<PageResult<MktErpSyncLog>> logs(@RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize,
                                                  @RequestParam(required = false) Long warehouseId, @RequestParam(required = false) Integer ok,
                                                  @RequestParam(required = false) String orderNo) {
        IPage<MktErpSyncLog> p = logMapper.selectPage(new Page<>(pageNo, pageSize), new LambdaQueryWrapper<MktErpSyncLog>()
                .eq(warehouseId != null, MktErpSyncLog::getWarehouseId, warehouseId)
                .eq(ok != null, MktErpSyncLog::getOk, ok)
                .like(StringUtils.hasText(orderNo), MktErpSyncLog::getOrderNo, orderNo)
                .orderByDesc(MktErpSyncLog::getId));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    private MktWarehouseErp require(Long warehouseId) {
        MktWarehouseErp e = erpMapper.selectOne(new LambdaQueryWrapper<MktWarehouseErp>().eq(MktWarehouseErp::getWarehouseId, warehouseId));
        if (e == null) throw new BizException("该云仓尚未签发凭证");
        return e;
    }

    private MktWarehouse lockWarehouse(Long warehouseId) {
        MktWarehouse warehouse = warehouseMapper.selectForUpdate(warehouseId);
        if (warehouse == null) throw new BizException("云仓不存在");
        if (Integer.valueOf(8).equals(warehouse.getJoinStatus()) || Integer.valueOf(7).equals(warehouse.getJoinStatus()))
            throw new BizException("云仓已退出或冻结待删除，不能变更 ERP 配置");
        return warehouse;
    }

    private static Map<String, Object> view(MktWarehouseErp e, String plainSecret) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.getId()); m.put("warehouseId", e.getWarehouseId()); m.put("appId", e.getAppId()); m.put("env", e.getEnv());
        m.put("status", e.getStatus()); m.put("mode", e.getMode()); m.put("lastSyncAt", e.getLastSyncAt()); m.put("createTime", e.getCreateTime());
        m.put("secret", plainSecret);
        m.put("lastHeartbeatAt", e.getLastHeartbeatAt());
        m.put("heartbeatEndpoint", "/api/open/v1/erp/heartbeat");
        m.put("endpoint", "/api/open/v1/erp/order-event");
        return m;
    }
}
