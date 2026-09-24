package com.zhyq.park.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.exception.BizException;
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

    @Operation(summary = "云仓的凭证(不含 secret)") @PreAuthorize("hasAuthority('crm:marketing:erp:query')") @GetMapping("/warehouse/{warehouseId}")
    public Result<Map<String, Object>> get(@PathVariable Long warehouseId) {
        MktWarehouseErp e = erpMapper.selectOne(new LambdaQueryWrapper<MktWarehouseErp>().eq(MktWarehouseErp::getWarehouseId, warehouseId));
        return Result.ok(e == null ? null : view(e, null));
    }

    @Operation(summary = "签发凭证(沙箱);已有则报错,用 reset") @PreAuthorize("hasAuthority('crm:marketing:erp:config')") @PostMapping("/warehouse/{warehouseId}/issue")
    public Result<Map<String, Object>> issue(@PathVariable Long warehouseId) {
        MktWarehouse w = warehouseMapper.selectById(warehouseId);
        if (w == null) throw new BizException("云仓不存在");
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
        return Result.ok(view(e, secret));
    }

    @Operation(summary = "重置密钥(旧密钥立即失效)") @PreAuthorize("hasAuthority('crm:marketing:erp:config')") @PostMapping("/warehouse/{warehouseId}/reset-secret")
    public Result<Map<String, Object>> reset(@PathVariable Long warehouseId) {
        MktWarehouseErp e = require(warehouseId);
        String secret = ErpSignatureService.newSecret();
        erpMapper.update(null, new LambdaUpdateWrapper<MktWarehouseErp>().eq(MktWarehouseErp::getId, e.getId())
                .set(MktWarehouseErp::getSecretEnc, encryption.encrypt(secret)));
        auditService.log("erp.reset", "warehouse", warehouseId, "重置密钥 " + e.getAppId());
        return Result.ok(view(e, secret));
    }

    @Operation(summary = "切正式 / 启停") @PreAuthorize("hasAuthority('crm:marketing:erp:config')") @PutMapping("/warehouse/{warehouseId}")
    public Result<Void> update(@PathVariable Long warehouseId, @RequestBody Map<String, Integer> body) {
        MktWarehouseErp e = require(warehouseId);
        erpMapper.update(null, new LambdaUpdateWrapper<MktWarehouseErp>().eq(MktWarehouseErp::getId, e.getId())
                .set(body.get("env") != null, MktWarehouseErp::getEnv, body.get("env"))
                .set(body.get("status") != null, MktWarehouseErp::getStatus, body.get("status")));
        auditService.log("erp.update", "warehouse", warehouseId, null, Map.of("env", e.getEnv(), "status", e.getStatus()), body);
        return Result.ok();
    }

    @Operation(summary = "货主编码映射列表") @PreAuthorize("hasAuthority('crm:marketing:erp:query')") @GetMapping("/warehouse/{warehouseId}/mappings")
    public Result<List<MktCustomerErpMap>> mappings(@PathVariable Long warehouseId) {
        return Result.ok(mapMapper.selectList(new LambdaQueryWrapper<MktCustomerErpMap>().eq(MktCustomerErpMap::getWarehouseId, warehouseId).orderByDesc(MktCustomerErpMap::getId)));
    }

    @Operation(summary = "新增/更新映射(客户在该云仓 ERP 的货主编码)") @PreAuthorize("hasAuthority('crm:marketing:erp:config')") @PostMapping("/warehouse/{warehouseId}/mappings")
    public Result<Void> saveMapping(@PathVariable Long warehouseId, @RequestBody Map<String, Object> body) {
        Long customerId = Long.valueOf(body.get("customerId").toString());
        String code = String.valueOf(body.get("customerCode")).trim();
        if (!StringUtils.hasText(code)) throw new BizException("货主编码必填");
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
        return Result.ok();
    }

    @Operation(summary = "删除映射") @PreAuthorize("hasAuthority('crm:marketing:erp:config')") @DeleteMapping("/mappings/{id}")
    public Result<Void> deleteMapping(@PathVariable Long id) {
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

    private static Map<String, Object> view(MktWarehouseErp e, String plainSecret) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.getId()); m.put("warehouseId", e.getWarehouseId()); m.put("appId", e.getAppId()); m.put("env", e.getEnv());
        m.put("status", e.getStatus()); m.put("mode", e.getMode()); m.put("lastSyncAt", e.getLastSyncAt()); m.put("createTime", e.getCreateTime());
        m.put("secret", plainSecret);
        m.put("endpoint", "/api/open/v1/erp/order-event");
        return m;
    }
}
