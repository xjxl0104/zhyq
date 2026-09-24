package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.engine.PoolCalculator;
import com.zhyq.park.marketing.entity.MktCustomerGrade;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.MktWarehouse;
import org.springframework.dao.DuplicateKeyException;
import com.zhyq.park.marketing.mapper.MktCustomerGradeMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.service.MktCommissionService.CommissionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 出库单文件导入(PARK-MKT-001 §5.3 文件方式,阶段 A 让路径 B 在没有真实 ERP 时能跑通):
 * 园区签：每行出库单按服务合同计算园区服务费和冻结佣金；发货时间 + freeze_days 后可解冻。
 * 云仓直签：只保存真实运营出库记录，所有园区金额为零，不调用计佣服务；平台费实收另行登记。
 *
 * <p>模板列(按表头文字认列,不看顺序):出库单号 · 客户手机号 · 件数 · 包裹数 · 发货时间 · 物流单号 · 云仓编码(可选) · 货值(可选)。
 * 同一云仓内的出库单号重复导入跳过(唯一键 uk_referral_order_source 兜底)。单行失败不拖垮整批,按行号回报。</p>
 *
 * <p>服务费口径(§2.6 v6 修正):园区**自己算**,不信任文件里的金额:
 * {@code service_fee = price_table.perOrder × packages + price_table.perItem × qty}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MktReferralOrderImportService {

    static final String H_ORDER_NO = "出库单号";
    static final String H_PHONE = "客户手机号";
    static final String H_QTY = "件数";
    static final String H_PACKAGES = "包裹数";
    static final String H_SHIPPED_AT = "发货时间";
    static final String H_LOGISTICS = "物流单号";
    static final String H_WAREHOUSE = "云仓编码";
    static final String H_GOODS_AMOUNT = "货值";
    private static final int HEADER_SCAN_ROWS = 5;
    /** 导入上限:共用服务器上整个工作簿一次性读进内存,不设限会被一个大文件打挂 */
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_ROWS = 5000;
    private static final Set<String> ALLOWED_EXT = Set.of("xlsx", "xls");
    private static final String MODULE = "marketing";

    private final CustomerMapper customerMapper;
    private final MktServiceContractMapper contractMapper;
    private final MktCustomerGradeMapper gradeMapper;
    private final MktReferralOrderMapper orderMapper;
    private final MktCommissionService commissionService;
    private final BizSettings bizSettings;
    private final ObjectMapper objectMapper;
    private final com.zhyq.park.marketing.mapper.MktWarehouseMapper warehouseMapper;
    private final MktContractTermsService termsService;

    public record ImportResult(int imported, int skipped, List<String> errors) {
    }

    /** 一行出库单的解析结果(与 POI 解耦,方便单测)。 */
    public record OutboundRow(String orderNo, String customerPhone, int qty, int packages,
                              LocalDateTime shippedAt, String logisticsNo, String warehouseCode, BigDecimal goodsAmount) {
    }

    public ImportResult importWorkbook(MultipartFile file, Long projectId) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要导入的文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BizException("文件过大,请拆分后导入(单次 ≤ 10MB)");
        }
        String filename = file.getOriginalFilename();
        if (StringUtils.hasText(filename)) {
            int dot = filename.lastIndexOf('.');
            // 没有扩展名时不拦(交给 POI 判断);有扩展名就必须在白名单内
            if (dot >= 0) {
                String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (!ALLOWED_EXT.contains(ext)) {
                    throw new BizException("只支持 .xlsx / .xls 文件");
                }
            }
        }
        List<OutboundRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null) {
                throw new BizException("文件里没有可读的工作表");
            }
            int headerRow = findHeaderRow(sheet);
            if (headerRow < 0) {
                throw new BizException("没找到表头行(应含「" + H_ORDER_NO + "」列),请使用出库单导入模板");
            }
            if (sheet.getLastRowNum() - headerRow > MAX_ROWS) {
                throw new BizException("单次最多导入 " + MAX_ROWS + " 行,请拆分后重试");
            }
            Map<String, Integer> col = readHeader(sheet.getRow(headerRow));
            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String orderNo = text(row, col.get(H_ORDER_NO));
                if (orderNo.isBlank()) {
                    continue;
                }
                try {
                    rows.add(new OutboundRow(orderNo, text(row, col.get(H_PHONE)),
                            intOf(text(row, col.get(H_QTY)), 0), intOf(text(row, col.get(H_PACKAGES)), 1),
                            dateTime(row, col.get(H_SHIPPED_AT)), text(row, col.get(H_LOGISTICS)),
                            text(row, col.get(H_WAREHOUSE)), decimalOrNull(text(row, col.get(H_GOODS_AMOUNT)))));
                } catch (Exception ex) {
                    errors.add("第 " + (r + 1) + " 行:" + ex.getMessage());
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[mkt] 出库单导入解析失败", e);
            throw new BizException("文件解析失败,请确认是 .xlsx / .xls 格式的出库单模板");
        }
        ImportResult r = importRows(rows, projectId);
        List<String> all = new ArrayList<>(errors);
        all.addAll(r.errors());
        return new ImportResult(r.imported(), r.skipped(), all);
    }

    /** 逐行入账;已存在的单号跳过;单行异常记录不中断。 */
    public ImportResult importRows(List<OutboundRow> rows, Long projectId) {
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        int freezeDays = bizSettings.getInt(MODULE, "freeze_days", 7);
        for (int i = 0; i < rows.size(); i++) {
            OutboundRow row = rows.get(i);
            try {
                ResolvedOutbound resolved = resolve(row, projectId);
                if (orderExists(row.orderNo(), resolved)) {
                    skipped++;
                    continue;
                }
                if (Integer.valueOf(2).equals(resolved.contract().getSignMode())) {
                    if (saveDirectOutbound(row, resolved, projectId)) imported++;
                    else skipped++;
                } else {
                    commissionService.createAndSplit(toParkEvent(row, resolved, projectId, freezeDays));
                    imported++;
                }
            } catch (Exception ex) {
                errors.add("出库单 " + row.orderNo() + ":" + ex.getMessage());
            }
        }
        return new ImportResult(imported, skipped, errors);
    }

    /**
     * 一行出库单 → 计佣事件。基数 = 园区服务费(按客户履约中的服务合同单价表自算),池 = 基数 × 评级 erp_total_rate。
     * 客户无推荐伙伴 → 抛异常(不计佣,由调用方计入错误);客户没有履约中的合同 → 抛异常。
     */
    public CommissionEvent toEvent(OutboundRow row, Long projectId, int freezeDays) {
        return toParkEvent(row, resolve(row, projectId), projectId, freezeDays);
    }

    private record ResolvedOutbound(Customer customer, MktServiceContract contract, MktWarehouse warehouse) {}

    /** Both operating records and commission events must reference a real active contract and online warehouse. */
    private ResolvedOutbound resolve(OutboundRow row, Long projectId) {
        if (row == null || !StringUtils.hasText(row.orderNo()) || row.orderNo().length() > 64)
            throw new BizException("出库单号必填且不能超过64字");
        if (row.qty() < 0 || row.packages() <= 0) throw new BizException("件数不能为负,包裹数必须为正整数");
        if (row.shippedAt() == null || row.shippedAt().isAfter(LocalDateTime.now().plusMinutes(5)))
            throw new BizException("请填写有效且不晚于当前时间的发货时间");
        if (!StringUtils.hasText(row.customerPhone())) {
            throw new BizException("缺客户手机号");
        }
        Customer customer = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getPhone, row.customerPhone()).last("limit 1"));
        if (customer == null) {
            throw new BizException("手机号 " + row.customerPhone() + " 不是系统客户");
        }
        Long warehouseId = null;
        if (StringUtils.hasText(row.warehouseCode())) {
            var warehouse = warehouseMapper.selectOne(new LambdaQueryWrapper<com.zhyq.park.marketing.entity.MktWarehouse>()
                    .eq(com.zhyq.park.marketing.entity.MktWarehouse::getCode, row.warehouseCode()).last("limit 1"));
            if (warehouse == null) throw new BizException("云仓编码不存在");
            warehouseId = warehouse.getId();
        }
        MktServiceContract contract = contractMapper.selectOne(new LambdaQueryWrapper<MktServiceContract>()
                .eq(MktServiceContract::getCustomerId, customer.getId())
                .eq(warehouseId != null, MktServiceContract::getWarehouseId, warehouseId)
                .eq(projectId != null, MktServiceContract::getProjectId, projectId)
                .in(MktServiceContract::getStatus,
                        MktServiceContractService.ST_EFFECTIVE, MktServiceContractService.ST_PERFORMING,
                        MktServiceContractService.ST_AMENDING, MktServiceContractService.ST_EXPIRED)
                .orderByDesc(MktServiceContract::getId).last("limit 1"));
        if (contract == null) {
            throw new BizException("客户 " + customer.getName() + " 没有生效中的服务合同");
        }
        if (contract.getWarehouseId() == null) throw new BizException("合同尚未指定承接云仓");
        contract = termsService.atDate(contract, row.shippedAt().toLocalDate());
        if ((contract.getStartDate() != null && row.shippedAt().toLocalDate().isBefore(contract.getStartDate()))
                || (contract.getEndDate() != null && row.shippedAt().toLocalDate().isAfter(contract.getEndDate())))
            throw new BizException("发货时间不在合同有效期内");
        if (!Integer.valueOf(1).equals(contract.getSignMode()) && !Integer.valueOf(2).equals(contract.getSignMode()))
            throw new BizException("合同签约方式无效，请核对合同");
        MktWarehouse warehouse = warehouseMapper.selectById(contract.getWarehouseId());
        if (warehouse == null || !Integer.valueOf(5).equals(warehouse.getJoinStatus()))
            throw new BizException("承接云仓未上线,不能导入出库单");
        if (projectId != null && contract.getProjectId() != null && !projectId.equals(contract.getProjectId()))
            throw new BizException("合同不属于当前园区");
        if (row.logisticsNo() != null && row.logisticsNo().length() > 64) throw new BizException("物流单号不能超过64字");
        if (row.goodsAmount() != null && (row.goodsAmount().signum() < 0 || row.goodsAmount().stripTrailingZeros().scale() > 2
                || row.goodsAmount().compareTo(new BigDecimal("999999999999.99")) > 0))
            throw new BizException("货值须为非负金额且最多两位小数");
        return new ResolvedOutbound(customer, contract, warehouse);
    }

    private CommissionEvent toParkEvent(OutboundRow row, ResolvedOutbound resolved, Long projectId, int freezeDays) {
        Customer customer = resolved.customer(); MktServiceContract contract = resolved.contract();
        if (!Integer.valueOf(1).equals(contract.getSignMode()))
            throw new BizException("云仓直签出库仅记录运营数据，佣金须按实际收到的平台费登记");
        if (freezeDays < 0) throw new BizException("冻结天数配置不合法");
        if (contract.getPartnerId() == null && customer.getReferrerId() == null)
            throw new BizException("客户 " + customer.getName() + " 无推荐伙伴,不计佣");
        BigDecimal serviceFee = serviceFee(contract.getPriceTable(), row.qty(), row.packages());
        String gradeCode = StringUtils.hasText(contract.getGrade()) ? contract.getGrade()
                : StringUtils.hasText(customer.getGrade()) ? customer.getGrade() : "D";
        MktCustomerGrade grade = gradeMapper.selectOne(new LambdaQueryWrapper<MktCustomerGrade>()
                .eq(MktCustomerGrade::getCode, gradeCode).last("limit 1"));
        BigDecimal rate = grade == null ? null : grade.getErpTotalRate();
        if (serviceFee.signum() <= 0) throw new BizException("合同缺少有效的单票/按件服务单价");
        if (rate == null || rate.signum() < 0 || rate.compareTo(new BigDecimal("100")) > 0) throw new BizException("客户评级佣金比例配置不合法");
        BigDecimal pool = PoolCalculator.ratePool(serviceFee, rate);
        BigDecimal cost = serviceFee(resolved.warehouse().getFeeModel(), row.qty(), row.packages());
        if (cost.signum() <= 0) throw new BizException("云仓应付费率尚未配置,请先核算成本");
        if (serviceFee.subtract(cost).subtract(pool).signum() < 0)
            throw new BizException("本单园区毛利为负(服务费−云仓成本−佣金),请核对合同报价和云仓费率");
        LocalDateTime shipped = row.shippedAt() == null ? LocalDateTime.now() : row.shippedAt();
        return new CommissionEvent(MktCommissionService.SOURCE_OUTBOUND, row.orderNo(), contract.getId(),
                customer.getId(), contract.getPartnerId() == null ? customer.getReferrerId() : contract.getPartnerId(), gradeCode, rate, serviceFee, pool,
                shipped, shipped.plusDays(freezeDays), contract.getProjectId() == null ? projectId : contract.getProjectId(), contract.getWarehouseId(),
                row.qty(), row.packages(), row.goodsAmount(), row.logisticsNo());
    }

    /** A direct-sign shipment is an operating record, never a commission event or park receivable. */
    private boolean saveDirectOutbound(OutboundRow row, ResolvedOutbound resolved, Long projectId) {
        MktServiceContract contract = resolved.contract();
        MktReferralOrder order = new MktReferralOrder();
        order.setSourceType(MktCommissionService.SOURCE_OUTBOUND); order.setSourceNo(row.orderNo());
        order.setSourceId(contract.getId()); order.setCustomerId(resolved.customer().getId()); order.setWarehouseId(contract.getWarehouseId());
        // Keep operating rows out of partner income queries; attribution remains on the contract, not this shipment.
        order.setPromoterId(null); order.setBaseMode(1); order.setBaseAmount(BigDecimal.ZERO);
        order.setServiceFee(BigDecimal.ZERO); order.setPoolAmount(BigDecimal.ZERO); order.setPoolFactor(BigDecimal.ZERO);
        order.setQty(row.qty()); order.setPackages(row.packages()); order.setGoodsAmount(row.goodsAmount()); order.setLogisticsNo(row.logisticsNo());
        order.setStatus(MktCommissionService.ORDER_CONFIRMED); order.setEventTime(row.shippedAt()); order.setConfirmTime(LocalDateTime.now());
        order.setProjectId(contract.getProjectId() == null ? projectId : contract.getProjectId());
        order.setRemark("云仓直签运营出库记录，不计佣、不产生园区应收或云仓应付；平台费实际到账另行登记");
        try { orderMapper.insert(order); return true; }
        catch (DuplicateKeyException concurrentImport) {
            if (orderExists(row.orderNo(), resolved)) return false;
            throw concurrentImport;
        }
    }

    /** 供开放接口:货主编码映射到客户后,取手机号复用 toEvent 的归属链路。 */
    public String customerPhone(Long customerId) {
        Customer c = customerMapper.selectById(customerId);
        if (c == null || !StringUtils.hasText(c.getPhone())) {
            throw new BizException("货主编码映射的客户不存在或无手机号: " + customerId);
        }
        return c.getPhone();
    }

    /** service_fee = perOrder × packages + perItem × qty;单价表缺项按 0。 */
    BigDecimal serviceFee(String priceTableJson, int qty, int packages) {
        BigDecimal perOrder = BigDecimal.ZERO;
        BigDecimal perItem = BigDecimal.ZERO;
        if (StringUtils.hasText(priceTableJson)) {
            try {
                JsonNode n = objectMapper.readTree(priceTableJson);
                if ((n.hasNonNull("perOrder") && !n.get("perOrder").isNumber()) || (n.hasNonNull("perItem") && !n.get("perItem").isNumber())) throw new IllegalArgumentException();
                perOrder = n.hasNonNull("perOrder") ? n.get("perOrder").decimalValue() : BigDecimal.ZERO;
                perItem = n.hasNonNull("perItem") ? n.get("perItem").decimalValue() : BigDecimal.ZERO;
            } catch (Exception e) {
                throw new BizException("合同单价表 JSON 不合法");
            }
        }
        if (perOrder.signum() < 0 || perItem.signum() < 0) throw new BizException("合同单价不能为负数");
        return perOrder.multiply(BigDecimal.valueOf(packages))
                .add(perItem.multiply(BigDecimal.valueOf(qty)))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private boolean orderExists(String orderNo, ResolvedOutbound resolved) {
        MktReferralOrder existing = orderMapper.selectOne(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getSourceType, MktCommissionService.SOURCE_OUTBOUND)
                .eq(MktReferralOrder::getWarehouseId, resolved.warehouse().getId())
                .eq(MktReferralOrder::getSourceNo, orderNo).last("limit 1"));
        if (existing == null) return false;
        if (!Objects.equals(existing.getWarehouseId(), resolved.warehouse().getId())
                || !Objects.equals(existing.getCustomerId(), resolved.customer().getId())
                || !Objects.equals(existing.getSourceId(), resolved.contract().getId()))
            throw new BizException("该云仓出库单号已关联其他客户或合同，请核对原始单号");
        return true;
    }

    // ---------------- POI 工具(与 LeadImportService 同款) ----------------

    private static int findHeaderRow(Sheet sheet) {
        int last = Math.min(sheet.getLastRowNum(), HEADER_SCAN_ROWS);
        for (int r = 0; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell c : row) {
                if (H_ORDER_NO.equals(normalize(cellText(c)))) return r;
            }
        }
        return -1;
    }

    private static Map<String, Integer> readHeader(Row header) {
        Map<String, Integer> map = new HashMap<>();
        for (Cell c : header) {
            String t = normalize(cellText(c));
            if (!t.isEmpty()) map.putIfAbsent(t, c.getColumnIndex());
        }
        return map;
    }

    private static String text(Row row, Integer idx) {
        return idx == null ? "" : cellText(row.getCell(idx));
    }

    private static int intOf(String s, int dflt) {
        if (s == null || s.isBlank()) return dflt;
        return new BigDecimal(s).intValueExact();
    }

    private static BigDecimal decimalOrNull(String s) {
        return s == null || s.isBlank() ? null : new BigDecimal(s);
    }

    private static LocalDateTime dateTime(Row row, Integer idx) {
        if (idx == null) return null;
        Cell c = row.getCell(idx);
        if (c == null) return null;
        try {
            if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
                return c.getLocalDateTimeCellValue();
            }
            String s = cellText(c).replace('/', '-').replace('T', ' ');
            if (s.isBlank()) return null;
            return s.length() <= 10 ? java.time.LocalDate.parse(s).atStartOfDay()
                    : LocalDateTime.parse(s.replace(' ', 'T'));
        } catch (Exception e) {
            throw new BizException("发货时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\u00a0]", "");
    }

    static String cellText(Cell c) {
        if (c == null) return "";
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            case NUMERIC -> DateUtil.isCellDateFormatted(c)
                    ? c.getLocalDateTimeCellValue().toString()
                    : BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case FORMULA -> {
                try {
                    yield c.getStringCellValue().trim();
                } catch (IllegalStateException e) {
                    yield BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
                }
            }
            default -> "";
        };
    }
}
