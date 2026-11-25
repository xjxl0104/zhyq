package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktPublicSeaMeta;
import com.zhyq.park.marketing.entity.MktPublicSeaWashBatch;
import com.zhyq.park.marketing.mapper.MktPublicSeaMetaMapper;
import com.zhyq.park.marketing.mapper.MktPublicSeaWashBatchMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 公海池三道防撞洗数引擎与公开 30 秒云仓底价测算留资服务。
 */
@Service
@RequiredArgsConstructor
public class MktPublicSeaWashService {

    private static final int MAX_IMPORT_ROWS = 1000;
    private static final Set<String> VALID_SOURCES = Set.of(
            "CALC_HOT", "CHANMAMA", "TUNGEE_JOB", "EXPIRE_RETURN", "PARK_CRM", "MANUAL_IMPORT");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CustomerMapper customers;
    private final MktPublicSeaMetaMapper seaMetas;
    private final MktPublicSeaWashBatchMapper washBatches;
    private final MktCompetitionProtectionService protectionService;
    private final MktAuditService audit;

    public record RawLeadRow(String name, String shopName, String contact, String phone,
                             String category, Integer estDailyOrders, BigDecimal avgWeightKg,
                             BigDecimal logisticsScore, String blockNote) {}

    /** 公开 30 秒云仓底价测算：优先读取当前园区已上线云仓真实价卡，缺失时透明回退园区基准价。 */
    @Transactional(readOnly = true)
    public Map<String, Object> estimatePublicQuote(Long projectId, String category, Integer dailyOrders, BigDecimal avgWeightKg) {
        Long effProject = resolveProjectId(projectId);
        Long tenantId = resolveTenantId(effProject);
        int daily = dailyOrders == null ? 500 : Math.max(10, Math.min(200_000, dailyOrders));
        BigDecimal weight = avgWeightKg == null ? new BigDecimal("0.50")
                : avgWeightKg.max(new BigDecimal("0.05")).min(new BigDecimal("30.00")).setScale(2, RoundingMode.HALF_UP);
        String cat = StringUtils.hasText(category) ? category.trim() : "女装服饰";

        List<BigDecimal> realTotals = loadRealWarehouseTotals(tenantId, effProject, weight);
        BigDecimal low;
        BigDecimal high;
        String rateSource;
        int matchedCount;
        if (!realTotals.isEmpty()) {
            Collections.sort(realTotals);
            low = realTotals.get(0).setScale(2, RoundingMode.HALF_UP);
            high = realTotals.get(realTotals.size() - 1).max(low.add(new BigDecimal("0.20"))).setScale(2, RoundingMode.HALF_UP);
            matchedCount = realTotals.size();
            rateSource = "REAL_RATE_CARD";
        } else {
            BigDecimal base = switch (cat) {
                case "美妆个护" -> new BigDecimal("2.55");
                case "休闲食品" -> new BigDecimal("2.48");
                case "鞋靴箱包", "数码小家电" -> new BigDecimal("2.85");
                default -> new BigDecimal("2.28");
            };
            BigDecimal weightExtra = weight.subtract(new BigDecimal("0.50")).max(BigDecimal.ZERO).multiply(new BigDecimal("0.90"));
            BigDecimal volDisc = daily >= 1000 ? new BigDecimal("-0.18")
                    : daily >= 500 ? new BigDecimal("-0.08") : new BigDecimal("0.12");
            low = base.add(weightExtra).add(volDisc).max(new BigDecimal("1.95")).setScale(2, RoundingMode.HALF_UP);
            high = low.add(new BigDecimal("0.32")).setScale(2, RoundingMode.HALF_UP);
            Integer onlineWh = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM crm_warehouse WHERE tenant_id = ? AND (project_id = ? OR project_id IS NULL) AND join_status = 5 AND deleted = 0",
                    Integer.class, tenantId, effProject);
            matchedCount = onlineWh != null && onlineWh > 0 ? onlineWh : 3;
            rateSource = "PARK_BENCHMARK";
        }

        int packers = Math.max(1, (int) Math.ceil(daily / 350.0));
        BigDecimal selfMonthly = BigDecimal.valueOf(packers * 5800L)
                .add(BigDecimal.valueOf(daily * 30L).multiply(low.add(new BigDecimal("0.35"))));
        BigDecimal cloudMonthly = BigDecimal.valueOf(daily * 30L).multiply(low);
        long saveYearly = selfMonthly.subtract(cloudMonthly).multiply(BigDecimal.valueOf(12))
                .max(BigDecimal.valueOf(12000)).setScale(0, RoundingMode.HALF_UP).longValue();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectId", effProject);
        out.put("category", cat);
        out.put("dailyOrders", daily);
        out.put("avgWeightKg", weight);
        out.put("lowPrice", low);
        out.put("highPrice", high);
        out.put("matchedWarehouseCount", matchedCount);
        out.put("packersEquivalent", packers);
        out.put("estimatedYearlySaving", saveYearly);
        out.put("rateSource", rateSource);
        return out;
    }

    private List<BigDecimal> loadRealWarehouseTotals(Long tenantId, Long projectId, BigDecimal weightKg) {
        String weightKey = weightKg.compareTo(new BigDecimal("0.5")) <= 0 ? "0-0.5"
                : weightKg.compareTo(BigDecimal.ONE) <= 0 ? "0.5-1"
                : weightKg.compareTo(new BigDecimal("2")) <= 0 ? "1-2" : "2-3";
        List<String> cards = jdbc.queryForList("""
                SELECT CAST(r.card_json AS CHAR CHARACTER SET utf8mb4)
                  FROM crm_quote_rate_card r
                  JOIN crm_warehouse w ON w.id = r.warehouse_id AND w.tenant_id = r.tenant_id AND w.deleted = 0
                 WHERE r.tenant_id = ? AND r.project_id = ? AND w.join_status = 5
                """, String.class, tenantId, projectId);
        List<BigDecimal> totals = new ArrayList<>();
        for (String raw : cards) {
            try {
                JsonNode root = json.readTree(raw);
                JsonNode fields = root.path("fields");
                BigDecimal pack = decimalOr(fields.get("packBatch"), fields.get("packSingle"));
                BigDecimal mat = decimalOr(fields.get("material"), BigDecimal.ZERO);
                if (pack == null) continue;
                for (JsonNode carrier : root.path("carriers")) {
                    JsonNode rateNode = carrier.path("rates").get(weightKey);
                    if (rateNode != null && rateNode.isNumber()) {
                        totals.add(pack.add(mat).add(rateNode.decimalValue()));
                    }
                }
            } catch (Exception ignored) { /* 跳过格式残缺价卡 */ }
        }
        return totals;
    }

    private static BigDecimal decimalOr(JsonNode primary, Object fallback) {
        if (primary != null && primary.isNumber()) return primary.decimalValue();
        if (fallback instanceof JsonNode jn && jn.isNumber()) return jn.decimalValue();
        if (fallback instanceof BigDecimal bd) return bd;
        return null;
    }

    /** 公开 30 秒算价器主动授权手机号入池：过三道防撞筛子，不泄露白名单命中态。 */
    @Transactional
    public Map<String, Object> submitPublicCalcLead(Map<String, Object> body) {
        String shopOrName = cleanText(body.get("shopName"), 100);
        String phone = cleanPhone(body.get("phone"));
        if (!StringUtils.hasText(shopOrName)) throw new BizException(400, "请填写您的店铺名或企业简称");
        if (!StringUtils.hasText(phone) || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(400, "请填写有效的 11 位联系手机号");
        }
        Long projectId = resolveProjectId(asLong(body.get("projectId")));
        String cat = StringUtils.hasText(cleanText(body.get("category"), 64)) ? cleanText(body.get("category"), 64) : "女装服饰";
        Integer daily = asInt(body.get("dailyOrders"), 500);
        BigDecimal weight = asDecimal(body.get("avgWeightKg"), new BigDecimal("0.50"));
        Map<String, Object> quote = estimatePublicQuote(projectId, cat, daily, weight);
        BigDecimal low = (BigDecimal) quote.get("lowPrice");
        BigDecimal high = (BigDecimal) quote.get("highPrice");

        RawLeadRow row = new RawLeadRow(shopOrName, shopOrName, cleanText(body.get("contact"), 64), phone,
                cat, daily, weight, null,
                "30秒算价器主动留资（测算一口价 ¥" + low + "~" + high + "/单，申领300单免打包费试发券）");
        Map<String, Object> wash = washRowsInternal(projectId, "CALC_HOT", "30秒算价器线上留资", List.of(row), false, low, high, "public-calc");
        Map<String, Object> resp = new LinkedHashMap<>(quote);
        resp.put("accepted", true);
        resp.put("inserted", ((Number) wash.get("insertedRows")).intValue() > 0);
        return resp;
    }

    /** 后台上传 Excel / CSV 洗数预览或确认入池。 */
    @Transactional
    public Map<String, Object> washFile(Long projectId, String seaSource, MultipartFile file, boolean dryRun) {
        if (file == null || file.isEmpty()) throw new BizException(400, "请选择要清洗导入的名单文件");
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("import.xlsx");
        List<RawLeadRow> rows = parseFileRows(file, fileName);
        if (rows.isEmpty()) throw new BizException(400, "文件中未识别到有效货主数据行，请核对表头（需含企业/店铺名与手机号）");
        return washRowsInternal(resolveProjectId(projectId), normalizeSource(seaSource), fileName, rows, dryRun, null, null, MktAuditService.currentOperator());
    }

    /** 后台直接提交 JSON 名单行洗数预览或确认入池。 */
    @Transactional
    public Map<String, Object> washJsonRows(Long projectId, String seaSource, String fileName, List<Map<String, Object>> rawList, boolean dryRun) {
        if (rawList == null || rawList.isEmpty()) throw new BizException(400, "请提供至少 1 条待清洗名单");
        if (rawList.size() > MAX_IMPORT_ROWS) throw new BizException(400, "单次清洗最多支持 " + MAX_IMPORT_ROWS + " 条");
        List<RawLeadRow> rows = rawList.stream().map(m -> new RawLeadRow(
                cleanText(firstNonNull(m, "name", "companyName", "shopName"), 100),
                cleanText(firstNonNull(m, "shopName", "shop"), 120),
                cleanText(m.get("contact"), 64),
                cleanPhone(m.get("phone")),
                cleanText(firstNonNull(m, "category", "industry"), 64),
                asInt(firstNonNull(m, "estDailyOrders", "dailyOrders"), null),
                asDecimal(firstNonNull(m, "avgWeightKg", "weight"), null),
                asDecimal(m.get("logisticsScore"), null),
                cleanText(firstNonNull(m, "blockNote", "remark"), 450)
        )).toList();
        return washRowsInternal(resolveProjectId(projectId), normalizeSource(seaSource),
                StringUtils.hasText(fileName) ? fileName.trim() : "手工批量名单", rows, dryRun, null, null, MktAuditService.currentOperator());
    }

    private Map<String, Object> washRowsInternal(Long projectId, String seaSource, String fileName, List<RawLeadRow> rows,
                                                 boolean dryRun, BigDecimal calcLow, BigDecimal calcHigh, String operator) {
        Long tenantId = resolveTenantId(projectId);
        int inserted = 0, blockedPrivate = 0, blockedHmac = 0, blockedAgency = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        // 统计批次内同一手机号出现频次（>3家不同企业视为代理记账财务号）
        Map<String, Set<String>> batchPhoneCompanies = new HashMap<>();
        for (RawLeadRow r : rows) {
            if (StringUtils.hasText(r.phone()) && StringUtils.hasText(r.name())) {
                batchPhoneCompanies.computeIfAbsent(r.phone(), k -> new HashSet<>()).add(r.name());
            }
        }

        int lineNo = 0;
        for (RawLeadRow r : rows) {
            lineNo++;
            String name = StringUtils.hasText(r.name()) ? r.name() : r.shopName();
            String phone = r.phone();
            if (!StringUtils.hasText(name) || !StringUtils.hasText(phone)) {
                blockedAgency++;
                addDetail(details, lineNo, name, "DUP_OR_INVALID", "企业名或手机号缺失，已自动剔除");
                continue;
            }
            // 闸门③：代理记账高频重号（本批 + 库内合计关联 > 3 家不同企业）
            int dbSamePhoneDiffName = Optional.ofNullable(jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT name) FROM crm_customer WHERE tenant_id = ? AND phone = ? AND name <> ? AND deleted = 0",
                    Integer.class, tenantId, phone, name)).orElse(0);
            int batchDistinct = batchPhoneCompanies.getOrDefault(phone, Set.of()).size();
            if (dbSamePhoneDiffName + batchDistinct > 3) {
                blockedAgency++;
                addDetail(details, lineNo, maskName(name), "AGENCY_PHONE", "同一号码关联超 3 家企业，识别为代理记账号剔除");
                continue;
            }
            // 闸门②：加盟云仓 AES+HMAC 保密老客白名单盲比对
            if (isWarehouseHmacProtected(tenantId, name, r.shopName())) {
                blockedHmac++;
                addDetail(details, lineNo, maskName(name), "WH_HMAC", "命中加盟云仓竞争保护白名单，自动跳过护客");
                continue;
            }
            // 闸门①：撞外部伙伴 90 天在锁私海或已签约客户
            Customer existing = customers.selectOne(new LambdaQueryWrapper<Customer>()
                    .eq(Customer::getTenantId, tenantId)
                    .and(q -> q.eq(Customer::getName, name).or().eq(Customer::getPhone, phone))
                    .last("LIMIT 1"));
            if (existing != null) {
                if (isPrivateProtected(existing)) {
                    blockedPrivate++;
                    addDetail(details, lineNo, maskName(name), "PRIVATE_LOCK", "客户处于外部伙伴保护期或已签约，严禁冲单");
                    continue;
                }
                MktPublicSeaMeta meta = seaMetas.selectById(existing.getId());
                if (meta != null && "ARCHIVED_COLD".equals(meta.getClaimStatus())) {
                    blockedAgency++;
                    addDetail(details, lineNo, maskName(name), "COLD_ARCHIVE", "命中 3 次无效冷宫归档库，跳过重复外呼");
                    continue;
                }
                if (!dryRun) {
                    upsertSeaMeta(existing.getId(), tenantId, projectId, seaSource, r, calcLow, calcHigh);
                }
                inserted++;
                continue;
            }

            if (!dryRun) {
                Customer c = new Customer();
                c.setTenantId(tenantId);
                c.setProjectId(projectId);
                c.setName(name);
                c.setBrandName(r.shopName());
                c.setContact(StringUtils.hasText(r.contact()) ? r.contact() : "电商负责人");
                c.setPhone(phone);
                c.setIndustry(StringUtils.hasText(r.category()) ? r.category() : "综合电商");
                c.setDemandArea(r.estDailyOrders() != null ? "日估" + r.estDailyOrders() + "单" : "待核实单量");
                c.setIntakeRemark(r.blockNote());
                c.setRemark(r.blockNote());
                c.setServiceType(3);
                c.setBizLine(2);
                c.setStatus(1);
                c.setReferrerId(null); // 内部专属公海，0 外部团队佣金
                c.setWarehouseAssignmentStatus(0);
                c.setOwner("内部公海待领");
                c.setAttributionNote("内部公海洗数入池:" + seaSource);
                customers.insert(c);
                upsertSeaMeta(c.getId(), tenantId, projectId, seaSource, r, calcLow, calcHigh);
            }
            inserted++;
        }

        String batchNo = "WASH-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + "-" + (100 + new Random().nextInt(900));
        if (!dryRun) {
            MktPublicSeaWashBatch batch = new MktPublicSeaWashBatch();
            batch.setTenantId(tenantId);
            batch.setProjectId(projectId);
            batch.setBatchNo(batchNo);
            batch.setSeaSource(seaSource);
            batch.setFileName(fileName);
            batch.setTotalRows(rows.size());
            batch.setInsertedRows(inserted);
            batch.setBlockedPrivateLock(blockedPrivate);
            batch.setBlockedWhHmac(blockedHmac);
            batch.setBlockedDupAgency(blockedAgency);
            try { batch.setBlockedDetailsJson(json.writeValueAsString(details)); } catch (Exception ignored) {}
            batch.setOperator(operator);
            batch.setCreatedAt(LocalDateTime.now());
            washBatches.insert(batch);
            audit.log("public_sea.wash_import", "public_sea", batch.getId(),
                    "公海三道闸洗数入池 " + batchNo + " 净入 " + inserted + " 户");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dryRun", dryRun);
        out.put("batchNo", batchNo);
        out.put("seaSource", seaSource);
        out.put("fileName", fileName);
        out.put("totalRows", rows.size());
        out.put("insertedRows", inserted);
        out.put("blockedPrivateLock", blockedPrivate);
        out.put("blockedWhHmac", blockedHmac);
        out.put("blockedDupAgency", blockedAgency);
        out.put("blockedDetails", details);
        return out;
    }

    private void upsertSeaMeta(Long customerId, Long tenantId, Long projectId, String seaSource,
                               RawLeadRow r, BigDecimal calcLow, BigDecimal calcHigh) {
        MktPublicSeaMeta meta = seaMetas.selectById(customerId);
        boolean isNew = (meta == null);
        if (isNew) {
            meta = new MktPublicSeaMeta();
            meta.setCustomerId(customerId);
            meta.setTenantId(tenantId);
            meta.setProjectId(projectId);
            meta.setClaimStatus("UNCLAIMED");
            meta.setReleaseCount(0);
            meta.setCreatedAt(LocalDateTime.now());
        }
        meta.setSeaSource(seaSource);
        if (StringUtils.hasText(r.shopName())) meta.setShopName(r.shopName());
        if (StringUtils.hasText(r.category())) meta.setCategory(r.category());
        if (r.estDailyOrders() != null) meta.setEstDailyOrders(r.estDailyOrders());
        if (r.avgWeightKg() != null) meta.setAvgWeightKg(r.avgWeightKg());
        if (calcLow != null) meta.setCalcLowPrice(calcLow);
        if (calcHigh != null) meta.setCalcHighPrice(calcHigh);
        if (r.logisticsScore() != null) meta.setLogisticsScore(r.logisticsScore());
        if (StringUtils.hasText(r.blockNote())) meta.setBlockNote(r.blockNote());
        computeUrgency(meta, seaSource);
        meta.setUpdatedAt(LocalDateTime.now());
        if (isNew) seaMetas.insert(meta);
        else seaMetas.updateById(meta);
    }

    private static void computeUrgency(MktPublicSeaMeta meta, String seaSource) {
        if ("CALC_HOT".equals(seaSource)) {
            meta.setUrgencyLevel("S");
            meta.setUrgencyReason("客户刚通过 30 秒云仓算价器自测底价并授权手机申领试发券，属最高意向热询盘");
        } else if (meta.getLogisticsScore() != null && meta.getLogisticsScore().compareTo(new BigDecimal("4.60")) < 0) {
            meta.setUrgencyLevel("S");
            meta.setUrgencyReason("店铺物流体验分 " + meta.getLogisticsScore() + " (<4.60 飘绿预警)，存在慢发漏发痛点，急需 24h 极速揽收云仓");
        } else if ("TUNGEE_JOB".equals(seaSource) || (meta.getEstDailyOrders() != null && meta.getEstDailyOrders() >= 500)) {
            meta.setUrgencyLevel("A");
            meta.setUrgencyReason("正在招聘打包发货员或日估单量 ≥500 单，自包人工成本高于园区云仓集约价");
        } else {
            meta.setUrgencyLevel("B");
            meta.setUrgencyReason("公海标准储备线索，待园区招商专员首通电话核实单量与搬仓窗口");
        }
    }

    private boolean isPrivateProtected(Customer c) {
        if (c.getReferrerId() != null || Integer.valueOf(2).equals(c.getStatus())) return true;
        Integer activeLocks = jdbc.queryForObject(
                "SELECT COUNT(1) FROM crm_customer_lock WHERE customer_id = ? AND status IN (1, 2, 3) AND deleted = 0",
                Integer.class, c.getId());
        if (activeLocks != null && activeLocks > 0) return true;
        Integer activeContracts = jdbc.queryForObject(
                "SELECT COUNT(1) FROM crm_service_contract WHERE customer_id = ? AND status IN (2, 3, 4) AND deleted = 0",
                Integer.class, c.getId());
        return activeContracts != null && activeContracts > 0;
    }

    private boolean isWarehouseHmacProtected(Long tenantId, String companyName, String shopName) {
        try {
            protectionService.assertUnprotected(tenantId, companyName, shopName);
            return false;
        } catch (BizException e) {
            return true;
        }
    }

    private List<RawLeadRow> parseFileRows(MultipartFile file, String fileName) {
        List<RawLeadRow> out = new ArrayList<>();
        try {
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                    String headerLine = br.readLine();
                    if (headerLine == null) return out;
                    String[] headers = headerLine.replace("\uFEFF", "").split(",");
                    Map<String, Integer> idx = mapHeaderIndexes(Arrays.asList(headers));
                    String line;
                    while ((line = br.readLine()) != null && out.size() < MAX_IMPORT_ROWS) {
                        if (line.isBlank()) continue;
                        String[] cols = line.split(",", -1);
                        out.add(rowFromArray(cols, idx));
                    }
                }
                return out;
            }
            try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
                Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
                if (sheet == null) return out;
                DataFormatter fmt = new DataFormatter();
                Row headerRow = sheet.getRow(sheet.getFirstRowNum());
                if (headerRow == null) return out;
                List<String> headers = new ArrayList<>();
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    headers.add(fmt.formatCellValue(headerRow.getCell(c)));
                }
                Map<String, Integer> idx = mapHeaderIndexes(headers);
                for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum() && out.size() < MAX_IMPORT_ROWS; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    String[] cols = new String[headers.size()];
                    boolean any = false;
                    for (int c = 0; c < headers.size(); c++) {
                        cols[c] = fmt.formatCellValue(row.getCell(c)).trim();
                        if (!cols[c].isEmpty()) any = true;
                    }
                    if (any) out.add(rowFromArray(cols, idx));
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(400, "名单文件解析失败，请上传标准 .xlsx 或 UTF-8 .csv 文件");
        }
        return out;
    }

    private static Map<String, Integer> mapHeaderIndexes(List<String> headers) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i) == null ? "" : headers.get(i).trim();
            if (h.contains("公司") || h.contains("企业") || h.equals("客户名称") || h.equals("名称")) m.putIfAbsent("name", i);
            else if (h.contains("店铺") || h.contains("网店") || h.contains("抖店") || h.contains("品牌")) m.putIfAbsent("shop", i);
            else if (h.contains("联系人") || h.contains("法人") || h.contains("姓名")) m.putIfAbsent("contact", i);
            else if (h.contains("手机") || h.contains("电话") || h.contains("联系方式")) m.putIfAbsent("phone", i);
            else if (h.contains("类目") || h.contains("行业") || h.contains("品类")) m.putIfAbsent("cat", i);
            else if (h.contains("单量") || h.contains("日发") || h.contains("日均")) m.putIfAbsent("daily", i);
            else if (h.contains("重")) m.putIfAbsent("weight", i);
            else if (h.contains("物流分") || h.contains("体验分") || h.contains("评分")) m.putIfAbsent("score", i);
            else if (h.contains("痛点") || h.contains("卡点") || h.contains("备注")) m.putIfAbsent("note", i);
        }
        return m;
    }

    private static RawLeadRow rowFromArray(String[] cols, Map<String, Integer> idx) {
        String name = col(cols, idx.get("name"), 100);
        String shop = col(cols, idx.get("shop"), 120);
        return new RawLeadRow(
                StringUtils.hasText(name) ? name : shop,
                shop,
                col(cols, idx.get("contact"), 64),
                cleanPhone(col(cols, idx.get("phone"), 32)),
                col(cols, idx.get("cat"), 64),
                asInt(col(cols, idx.get("daily"), 20), null),
                asDecimal(col(cols, idx.get("weight"), 20), null),
                asDecimal(col(cols, idx.get("score"), 20), null),
                col(cols, idx.get("note"), 450)
        );
    }

    private static String col(String[] cols, Integer i, int max) {
        if (i == null || i < 0 || i >= cols.length) return null;
        return cleanText(cols[i], max);
    }

    private static void addDetail(List<Map<String, Object>> details, int rowNo, String maskedName, String type, String reason) {
        if (details.size() < 50) {
            details.add(Map.of("rowNo", rowNo, "target", maskedName == null ? "—" : maskedName, "type", type, "reason", reason));
        }
    }

    private static String maskName(String s) {
        if (!StringUtils.hasText(s)) return "未知主体";
        String t = s.trim();
        return t.length() <= 3 ? t.charAt(0) + "**" : t.substring(0, 2) + "***" + t.substring(t.length() - 2);
    }

    private Long resolveProjectId(Long projectId) {
        if (projectId != null && projectId > 0) return projectId;
        List<Long> ids = jdbc.queryForList("SELECT id FROM biz_project WHERE deleted = 0 ORDER BY id LIMIT 1", Long.class);
        return ids.isEmpty() ? 1L : ids.get(0);
    }

    private Long resolveTenantId(Long projectId) {
        if (projectId != null) {
            List<Long> t = jdbc.queryForList("SELECT tenant_id FROM biz_project WHERE id = ? AND deleted = 0 LIMIT 1", Long.class, projectId);
            if (!t.isEmpty() && t.get(0) != null) return t.get(0);
        }
        return MyMetaObjectHandler.DEFAULT_TENANT_ID;
    }

    private static String normalizeSource(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return VALID_SOURCES.contains(s) ? s : "CHANMAMA";
    }

    private static Object firstNonNull(Map<String, Object> m, String... keys) {
        for (String k : keys) if (m.get(k) != null && !String.valueOf(m.get(k)).isBlank()) return m.get(k);
        return null;
    }

    private static String cleanText(Object v, int max) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String cleanPhone(Object v) {
        if (v == null) return null;
        String digits = String.valueOf(v).replaceAll("[^0-9]", "");
        if (digits.startsWith("86") && digits.length() == 13) digits = digits.substring(2);
        return digits.isEmpty() ? null : digits;
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        try { return Long.valueOf(String.valueOf(v).trim()); } catch (Exception e) { return null; }
    }

    private static Integer asInt(Object v, Integer def) {
        if (v == null) return def;
        try {
            String clean = String.valueOf(v).replaceAll("[^0-9]", "");
            return clean.isEmpty() ? def : Integer.parseInt(clean);
        } catch (Exception e) { return def; }
    }

    private static BigDecimal asDecimal(Object v, BigDecimal def) {
        if (v == null) return def;
        try {
            String clean = String.valueOf(v).replaceAll("[^0-9.]", "");
            return clean.isEmpty() ? def : new BigDecimal(clean).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) { return def; }
    }
}
