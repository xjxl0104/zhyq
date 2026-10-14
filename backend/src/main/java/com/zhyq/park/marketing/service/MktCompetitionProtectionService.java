package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.marketing.entity.MktCompetitionProtection;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktCompetitionProtectionMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.wh.WhAuthContext;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MktCompetitionProtectionService {
    private final MktCompetitionProtectionMapper entries;
    private final MktWarehouseMapper warehouses;
    private final FieldEncryptionService encryption;
    private final MktAuditService audit;

    public static String normalize(String name) {
        return name == null ? "" : Normalizer.normalize(name, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}]+", "").toLowerCase(Locale.ROOT);
    }

    private LambdaQueryWrapper<MktCompetitionProtection> owned(MktWarehouse warehouse) {
        return new LambdaQueryWrapper<MktCompetitionProtection>()
                .eq(MktCompetitionProtection::getTenantId, tenant(warehouse.getTenantId()))
                .eq(MktCompetitionProtection::getWarehouseId, warehouse.getId());
    }

    public List<Map<String, Object>> list(Long warehouseId) {
        MktWarehouse warehouse = WhAuthContext.requireWarehouse(warehouses, warehouseId);
        return entries.selectList(owned(warehouse).orderByDesc(MktCompetitionProtection::getId)).stream()
                .map(row -> Map.<String, Object>of("id", row.getId(), "name", encryption.decrypt(row.getNameEnc())))
                .toList();
    }

    /** Restricted park-admin view. The project scope is mandatory; never expose another project's names. */
    public PageResult<Map<String, Object>> adminPage(Long projectId, Long warehouseId, int pageNo, int pageSize) {
        if (projectId == null) throw new BizException("请先选择园区，再查看竞争保护白名单");
        List<MktWarehouse> projectWarehouses = warehouses.selectList(new LambdaQueryWrapper<MktWarehouse>()
                // Historical warehouses may predate project scoping. Other marketing warehouse
                // screens already treat those rows as belonging to the current park.
                .and(q -> q.eq(MktWarehouse::getProjectId, projectId).or().isNull(MktWarehouse::getProjectId))
                .select(MktWarehouse::getId, MktWarehouse::getName, MktWarehouse::getCode));
        if (projectWarehouses.isEmpty()) return PageResult.of(0, List.of());

        Map<Long, MktWarehouse> warehouseById = new HashMap<>();
        projectWarehouses.forEach(w -> warehouseById.put(w.getId(), w));
        if (warehouseId != null && !warehouseById.containsKey(warehouseId)) return PageResult.of(0, List.of());

        LambdaQueryWrapper<MktCompetitionProtection> query = new LambdaQueryWrapper<MktCompetitionProtection>()
                .in(MktCompetitionProtection::getWarehouseId, warehouseById.keySet())
                .eq(warehouseId != null, MktCompetitionProtection::getWarehouseId, warehouseId)
                .orderByDesc(MktCompetitionProtection::getId);
        IPage<MktCompetitionProtection> page = entries.selectPage(new Page<>(pageNo, pageSize), query);
        List<Map<String, Object>> records = page.getRecords().stream().map(row -> {
            MktWarehouse warehouse = warehouseById.get(row.getWarehouseId());
            Map<String, Object> item = new HashMap<>();
            item.put("id", row.getId());
            item.put("warehouseId", row.getWarehouseId());
            item.put("warehouseName", warehouse.getName());
            item.put("warehouseCode", warehouse.getCode());
            item.put("name", encryption.decrypt(row.getNameEnc()));
            item.put("createTime", row.getCreateTime());
            return item;
        }).toList();
        // Audit the access itself without writing confidential names to audit logs.
        audit.log("protection.admin.view", "competition_protection", null,
                "管理员查看园区竞争保护白名单（名单内容不写入审计）");
        return PageResult.of(page.getTotal(), records);
    }

    @Transactional
    public void add(Long warehouseId, String name) {
        // Lock before any consistent read, so concurrent quota checks see committed entries.
        MktWarehouse warehouse = warehouses.selectForUpdate(warehouseId);
        if (warehouse == null) throw new BizException(403, "云仓不存在或无权访问");
        if (Integer.valueOf(7).equals(warehouse.getJoinStatus()) || Integer.valueOf(8).equals(warehouse.getJoinStatus()))
            throw new BizException("已退出或冻结待删除云仓不能新增保护");
        String normalized = normalize(name);
        if (normalized.isEmpty() || name.length() > 100) throw new BizException("请填写 1–100 字的品牌或公司名称");
        requireEncryption();
        String fingerprint = encryption.businessNameFingerprint(normalized);
        if (entries.selectCount(owned(warehouse).eq(MktCompetitionProtection::getNameFingerprint, fingerprint)) > 0)
            throw new BizException("该名称已在您的保护名单中");
        if (entries.selectCount(owned(warehouse)) >= 500) throw new BizException("保护名单最多 500 条，请先整理已有名单");
        MktCompetitionProtection row = new MktCompetitionProtection();
        row.setTenantId(tenant(warehouse.getTenantId())); row.setWarehouseId(warehouseId);
        row.setNameEnc(encryption.encrypt(name.trim())); row.setNameFingerprint(fingerprint);
        try { entries.insert(row); }
        catch (DuplicateKeyException e) { throw new BizException("该名称已在您的保护名单中"); }
        audit.log("protection.add", "competition_protection", row.getId(), "新增保护名称（内容保密）");
    }

    @Transactional
    public void remove(Long warehouseId, Long id) {
        MktWarehouse warehouse = WhAuthContext.requireWarehouse(warehouses, warehouseId);
        if (entries.delete(owned(warehouse).eq(MktCompetitionProtection::getId, id)) != 1)
            throw new BizException(404, "记录不存在或已移除");
        audit.log("protection.remove", "competition_protection", id, "移除保护名称（内容保密）");
    }

    /** No lookup endpoint: callers receive neither matched name nor owner identity. */
    public void assertUnprotected(Long tenantId, String... names) {
        for (String name : names) {
            String normalized = normalize(name);
            if (normalized.isEmpty()) continue;
            // If there are no entries, existing referral workflows need no encryption key.
            if (entries.selectCount(new LambdaQueryWrapper<MktCompetitionProtection>()
                    .eq(MktCompetitionProtection::getTenantId, tenant(tenantId))) == 0) return;
            requireEncryption();
            String fingerprint = encryption.businessNameFingerprint(normalized);
            if (entries.selectCount(new LambdaQueryWrapper<MktCompetitionProtection>()
                    .eq(MktCompetitionProtection::getTenantId, tenant(tenantId))
                    .eq(MktCompetitionProtection::getNameFingerprint, fingerprint)) > 0)
                throw new BizException("该客户暂不可进入营销推荐流程，请联系园区运营核验");
        }
    }

    private static Long tenant(Long tenantId) {
        return tenantId == null ? MyMetaObjectHandler.DEFAULT_TENANT_ID : tenantId;
    }

    private void requireEncryption() {
        if (!encryption.isConfigured()) throw new BizException("保护名单加密服务未配置，请联系园区运营");
    }
}
