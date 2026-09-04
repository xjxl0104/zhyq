package com.zhyq.park.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.energy.entity.EnergyAllocation;
import com.zhyq.park.energy.entity.Meter;
import com.zhyq.park.energy.entity.UtilityBill;
import com.zhyq.park.energy.mapper.EnergyAllocationMapper;
import com.zhyq.park.energy.mapper.MeterMapper;
import com.zhyq.park.energy.mapper.UtilityBillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公用事业设施费用分摊。严格照《附件二》的四个公式实现,变量名与原文一一对应,
 * 便于财务拿着合同逐条核。
 *
 * <pre>
 * ① 当月不含税单价 = 发票不含税总额 / 发票总用量
 * ② 分摊系数 = (公共区域用量 + 损耗) / (租户实际抄表用量 + 物业公司实际抄表用量)
 *    其中 (公共区域用量 + 损耗) = 发票总用量 - 租户实际抄表用量 - 物业公司实际抄表用量
 * ③ 乙方公摊费 = 单价 × (乙方实际用量 × 分摊系数) × (1 + 税率)
 * ④ 乙方总费用 = 乙方用量 × 单价 × (1 + 税率) + 乙方公摊费
 * </pre>
 *
 * <p><b>口径注意</b>:公式②的分母只含「租户 + 物业公司」的分表,不含总表 ——
 * 总表是分摊的源头(被减数),把它算进分母会把系数摊薄一倍。</p>
 */
@Service
@RequiredArgsConstructor
public class AllocationService {

    /** 金额保留 2 位;系数保留 8 位,免得几十家摊下来尾差累积到元级 */
    private static final int MONEY_SCALE = 2;
    private static final int COEFFICIENT_SCALE = 8;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final UtilityBillMapper utilityBillMapper;
    private final EnergyAllocationMapper allocationMapper;
    private final MeterMapper meterMapper;
    private final JdbcTemplate jdbc;

    /** 一次测算的汇总,给前端把中间量摊开显示(下钻用) */
    public record AllocationSummary(
            Long utilityBillId, String period, String energyType, String status,
            BigDecimal invoiceUsage, BigDecimal invoiceAmountExTax, BigDecimal taxRate,
            BigDecimal unitPriceExTax,
            BigDecimal tenantUsage, BigDecimal propertyUsage, BigDecimal publicUsage,
            BigDecimal allocCoefficient,
            BigDecimal totalOwnFee, BigDecimal totalAllocFee, BigDecimal totalFee,
            int meterCount, List<EnergyAllocation> items, List<String> warnings) {}

    /**
     * 按月度账单重算分摊。幂等:每次先清掉该账单下的旧结果再重算,
     * 反复点不会累积出多份。已确认(出过账)的账单不允许重算。
     */
    @Transactional(rollbackFor = Exception.class)
    public AllocationSummary calculate(Long utilityBillId) {
        UtilityBill bill = utilityBillMapper.selectById(utilityBillId);
        if (bill == null) {
            throw new BizException("月度公用事业账单不存在: " + utilityBillId);
        }
        if (UtilityBill.ST_CONFIRMED.equals(bill.getStatus())) {
            throw new BizException("该账期已确认出账,不能重算;需要改动请先撤销确认");
        }
        BigDecimal invoiceUsage = nz(bill.getInvoiceUsage());
        if (invoiceUsage.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("发票总用量必须大于 0,否则单价与分摊系数都算不出来");
        }

        // ① 当月不含税单价
        BigDecimal unitPrice = nz(bill.getInvoiceAmountExTax())
                .divide(invoiceUsage, 6, RoundingMode.HALF_UP);
        BigDecimal taxFactor = BigDecimal.ONE.add(nz(bill.getTaxRate()).divide(HUNDRED, 6, RoundingMode.HALF_UP));

        // 该账期各分表的实际抄表用量(总表不参与分母,它是被减数)
        List<Map<String, Object>> rows = readMeterUsage(bill);
        List<String> warnings = new ArrayList<>();

        BigDecimal tenantUsage = BigDecimal.ZERO;
        BigDecimal propertyUsage = BigDecimal.ZERO;
        for (Map<String, Object> r : rows) {
            BigDecimal usage = nz((BigDecimal) r.get("usage_amount"));
            if ("PROPERTY".equals(r.get("meter_role"))) {
                propertyUsage = propertyUsage.add(usage);
            } else {
                tenantUsage = tenantUsage.add(usage);
            }
        }
        BigDecimal submeterUsage = tenantUsage.add(propertyUsage);
        if (submeterUsage.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("该账期没有任何分表抄表记录,无法分摊;请先录入 " + bill.getPeriod() + " 的抄表数据");
        }

        // ② 公共区域用量 + 损耗,以及分摊系数
        BigDecimal publicUsage = invoiceUsage.subtract(submeterUsage);
        if (publicUsage.compareTo(BigDecimal.ZERO) < 0) {
            // 分表加起来比发票总量还大:多半是漏了总表口径或抄表串了月份,先出账再纠错代价更大
            warnings.add("分表用量合计 " + submeterUsage.toPlainString() + " 已超过发票总用量 "
                    + invoiceUsage.toPlainString() + "，公共区域用量为负；分摊系数按 0 处理，请先核对抄表与发票口径");
            publicUsage = BigDecimal.ZERO;
        }
        BigDecimal coefficient = publicUsage.divide(submeterUsage, COEFFICIENT_SCALE, RoundingMode.HALF_UP);

        // 重算前清旧结果,保证幂等
        allocationMapper.delete(new LambdaQueryWrapper<EnergyAllocation>()
                .eq(EnergyAllocation::getUtilityBillId, utilityBillId));

        List<EnergyAllocation> items = new ArrayList<>();
        BigDecimal totalOwn = BigDecimal.ZERO;
        BigDecimal totalAlloc = BigDecimal.ZERO;
        for (Map<String, Object> r : rows) {
            BigDecimal usage = nz((BigDecimal) r.get("usage_amount"));
            BigDecimal allocUsage = usage.multiply(coefficient);
            // ③ 公摊费 = 单价 × (自身用量 × 系数) × (1+税率)
            BigDecimal allocFee = unitPrice.multiply(allocUsage).multiply(taxFactor)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            // ④ 总费用 = 自身用量 × 单价 × (1+税率) + 公摊费
            BigDecimal ownFee = usage.multiply(unitPrice).multiply(taxFactor)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            EnergyAllocation a = new EnergyAllocation();
            a.setUtilityBillId(utilityBillId);
            a.setPeriod(bill.getPeriod());
            a.setEnergyType(bill.getEnergyType());
            a.setMeterId(((Number) r.get("meter_id")).longValue());
            a.setMeterRole((String) r.get("meter_role"));
            Number tid = (Number) r.get("tenant_ref_id");
            a.setTenantRefId(tid == null ? null : tid.longValue());
            a.setOwnUsage(usage);
            a.setAllocCoefficient(coefficient);
            a.setAllocUsage(allocUsage.setScale(4, RoundingMode.HALF_UP));
            a.setUnitPriceExTax(unitPrice);
            a.setTaxRate(nz(bill.getTaxRate()));
            a.setOwnFee(ownFee);
            a.setAllocFee(allocFee);
            a.setTotalFee(ownFee.add(allocFee));
            allocationMapper.insert(a);

            a.setMeterCode((String) r.get("code"));
            a.setMeterName((String) r.get("name"));
            a.setTenantName((String) r.get("tenant_name"));
            if ("TENANT".equals(a.getMeterRole()) && a.getTenantRefId() == null) {
                warnings.add("表计 " + a.getMeterCode() + " 找不到在租租户，出账时会被跳过；请先在合同里关联房源");
            }
            items.add(a);
            totalOwn = totalOwn.add(ownFee);
            totalAlloc = totalAlloc.add(allocFee);
        }

        return new AllocationSummary(utilityBillId, bill.getPeriod(), bill.getEnergyType(), bill.getStatus(),
                invoiceUsage, nz(bill.getInvoiceAmountExTax()), nz(bill.getTaxRate()), unitPrice,
                tenantUsage, propertyUsage, publicUsage, coefficient,
                totalOwn, totalAlloc, totalOwn.add(totalAlloc), items.size(), items, warnings);
    }

    /**
     * 读取该账期内各分表的抄表用量(总表排除);同一表计当月多次抄表按合计算。
     *
     * <p>用量与租户分两步查,不揉在一条 JOIN 里:一个房间常挂多条合同房源记录
     * (历史合同 + 当前合同),JOIN 会把同一表计裂成多行 —— 轻则撞
     * uk_allocation_bill_meter 唯一键,重则把用量重复计一遍(真测踩到)。</p>
     */
    private List<Map<String, Object>> readMeterUsage(UtilityBill bill) {
        StringBuilder sql = new StringBuilder("""
                SELECT m.id AS meter_id, m.code, m.name, m.meter_role, m.room_id,
                       COALESCE(SUM(e.usage_amount), 0) AS usage_amount
                FROM eng_meter m
                JOIN eng_reading e ON e.meter_id = m.id AND e.deleted = 0
                     AND DATE_FORMAT(e.read_time, '%Y-%m') = ?
                WHERE m.deleted = 0 AND m.status = 1
                  AND m.energy_type = ?
                  AND m.meter_role <> 'MAIN'
                """);
        List<Object> args = new ArrayList<>(List.of(bill.getPeriod(), bill.getEnergyType()));
        if (bill.getProjectId() != null) {
            sql.append(" AND m.project_id = ? ");
            args.add(bill.getProjectId());
        }
        sql.append(" GROUP BY m.id, m.code, m.name, m.meter_role, m.room_id ORDER BY m.meter_role DESC, m.id");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        // 在租租户:INNER JOIN 只认活合同,同房间多份执行中合同按起租日取最新(首行胜出)
        Map<Long, Map<String, Object>> tenantByRoom = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT cr.room_id, t.id AS tenant_ref_id, t.name AS tenant_name
                FROM biz_contract_room cr
                JOIN biz_contract c ON c.id = cr.contract_id AND c.deleted = 0 AND c.status = 5
                JOIN biz_tenant t ON t.id = c.tenant_ref_id AND t.deleted = 0
                WHERE cr.deleted = 0
                ORDER BY c.start_date DESC, c.id DESC
                """)) {
            tenantByRoom.putIfAbsent(((Number) r.get("room_id")).longValue(), r);
        }
        for (Map<String, Object> row : rows) {
            Number roomId = (Number) row.get("room_id");
            Map<String, Object> t = roomId == null ? null : tenantByRoom.get(roomId.longValue());
            row.put("tenant_ref_id", t == null ? null : t.get("tenant_ref_id"));
            row.put("tenant_name", t == null ? null : t.get("tenant_name"));
        }
        return rows;
    }

    /** 查已保存的分摊结果(不重算),给详情/下钻用 */
    public List<EnergyAllocation> listSaved(Long utilityBillId) {
        List<EnergyAllocation> list = allocationMapper.selectList(
                new LambdaQueryWrapper<EnergyAllocation>()
                        .eq(EnergyAllocation::getUtilityBillId, utilityBillId)
                        .orderByAsc(EnergyAllocation::getId));
        if (list.isEmpty()) {
            return list;
        }
        Map<Long, Meter> meters = new LinkedHashMap<>();
        meterMapper.selectBatchIds(list.stream().map(EnergyAllocation::getMeterId).distinct().toList())
                .forEach(m -> meters.put(m.getId(), m));
        Map<Long, String> tenantNames = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT id, name FROM biz_tenant WHERE deleted = 0")) {
            tenantNames.put(((Number) r.get("id")).longValue(), (String) r.get("name"));
        }
        for (EnergyAllocation a : list) {
            Meter m = meters.get(a.getMeterId());
            if (m != null) {
                a.setMeterCode(m.getCode());
                a.setMeterName(m.getName());
            }
            if (a.getTenantRefId() != null) {
                a.setTenantName(tenantNames.get(a.getTenantRefId()));
            }
        }
        return list;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
