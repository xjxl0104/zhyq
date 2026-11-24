package com.zhyq.park.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.energy.entity.LeaseContract;
import com.zhyq.park.energy.entity.LeaseFeeEntry;
import com.zhyq.park.energy.entity.LeaseFeeNotice;
import com.zhyq.park.energy.entity.LeaseFeeNoticeItem;
import com.zhyq.park.energy.entity.LeaseFeePeriod;
import com.zhyq.park.energy.mapper.LeaseContractMapper;
import com.zhyq.park.energy.mapper.LeaseFeeEntryMapper;
import com.zhyq.park.energy.mapper.LeaseFeeNoticeItemMapper;
import com.zhyq.park.energy.mapper.LeaseFeeNoticeMapper;
import com.zhyq.park.energy.mapper.LeaseFeePeriodMapper;
import com.zhyq.park.energy.service.LeaseFeeCalculator.ContractMonthRow;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaseFeeService {

    private static final String DEFAULT_PARK = "DIPARK数智云仓产业园";
    private static final int MAX_MANUAL_ITEMS = 20;

    private final LeaseContractMapper contractMapper;
    private final LeaseFeePeriodMapper periodMapper;
    private final LeaseFeeEntryMapper entryMapper;
    private final LeaseFeeNoticeMapper noticeMapper;
    private final LeaseFeeNoticeItemMapper itemMapper;
    private final BillMapper billMapper;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final LeaseFeePaymentService payments;

    public record Sheet(LeaseFeePeriod period, String feeType,
                        BigDecimal totalArea, BigDecimal standardRentTotal, BigDecimal standardPropertyTotal,
                        BigDecimal payableRentTotal, BigDecimal payablePropertyTotal, BigDecimal payableGrandTotal,
                        List<ContractMonthRow> rows) {}

    public record EntryInput(Long contractId, BigDecimal rentFactor, BigDecimal propertyFactor,
                             BigDecimal rentAmountOverride, BigDecimal propertyAmountOverride,
                             String remark, Boolean resetDefault) {}

    public record ManualItemInput(String feeItem, BigDecimal leaseArea, BigDecimal unitPrice,
                                  BigDecimal amount, String remark) {}

    public record NoticeEdit(Long id, String tenantName, String unitName, String payeeName, String paymentAccount,
                             BigDecimal paidAmount, BigDecimal priorArrears, String remark,
                             Map<Long, String> itemRemarks, List<ManualItemInput> manualItems) {}

    public record GenerateResult(LeaseFeePeriod period, List<LeaseFeeNotice> notices) {}

    public record MonthOverview(String period, Long periodId, String status, Boolean noticeStale,
                                int contractCount, BigDecimal rentTotal, BigDecimal propertyTotal,
                                BigDecimal grandTotal, int noticeCount, int rentNoticeCount, int propertyNoticeCount,
                                int rentFiles, int propertyFiles) {}

    public record DepositUpdate(BigDecimal rentDeposit, BigDecimal propertyDeposit, String depositTerms,
                                Integer rentDepositStatus, BigDecimal rentDepositPaid, LocalDate rentDepositPaidDate,
                                Integer propertyDepositStatus, BigDecimal propertyDepositPaid, LocalDate propertyDepositPaidDate,
                                String depositRemark) {}

    // ------------------------------------------------------------------ 合同台账 CRUD

    public List<LeaseContract> listContracts(boolean includeDisabled) {
        return contractMapper.selectList(new LambdaQueryWrapper<LeaseContract>()
                .eq(!includeDisabled, LeaseContract::getStatus, 1)
                .orderByAsc(LeaseContract::getSeqNo).orderByAsc(LeaseContract::getId));
    }

    public List<LeaseContract> listDeposits(boolean includeDisabled) {
        List<LeaseContract> list = listContracts(includeDisabled);
        if (list.isEmpty()) return list;
        String ids = list.stream().map(c -> String.valueOf(c.getId())).collect(Collectors.joining(","));
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT biz_type, biz_id, COUNT(*) AS c FROM sys_file WHERE deleted = 0"
                        + " AND biz_type IN ('lease_deposit_rent_receipt','lease_deposit_property_receipt')"
                        + " AND biz_id IN (" + ids + ") GROUP BY biz_type, biz_id")) {
            counts.put(r.get("biz_type") + ":" + r.get("biz_id"), ((Number) r.get("c")).intValue());
        }
        for (LeaseContract c : list) {
            if (c.getRentDepositStatus() == null) c.setRentDepositStatus(0);
            if (c.getRentDepositPaid() == null) c.setRentDepositPaid(BigDecimal.ZERO);
            if (c.getPropertyDepositStatus() == null) c.setPropertyDepositStatus(0);
            if (c.getPropertyDepositPaid() == null) c.setPropertyDepositPaid(BigDecimal.ZERO);
            c.setRentReceiptCount(counts.getOrDefault("lease_deposit_rent_receipt:" + c.getId(), 0));
            c.setPropertyReceiptCount(counts.getOrDefault("lease_deposit_property_receipt:" + c.getId(), 0));
        }
        return list;
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaseContract updateDeposit(Long contractId, DepositUpdate req) {
        if (contractId == null || req == null) throw new BizException("保证金参数不能为空");
        LeaseContract existed = contractMapper.selectById(contractId);
        if (existed == null) throw new BizException(404, "合同不存在: " + contractId);
        if (req.rentDeposit() != null && req.rentDeposit().signum() < 0) throw new BizException("租金保证金不能为负");
        if (req.propertyDeposit() != null && req.propertyDeposit().signum() < 0) throw new BizException("物业保证金不能为负");
        if (req.rentDepositPaid() != null && req.rentDepositPaid().signum() < 0) throw new BizException("租金保证金已缴金额不能为负");
        if (req.propertyDepositPaid() != null && req.propertyDepositPaid().signum() < 0) throw new BizException("物业保证金已缴金额不能为负");
        int rentSt = normalizeDepositStatus(req.rentDepositStatus(), existed.getRentDepositStatus());
        int propSt = normalizeDepositStatus(req.propertyDepositStatus(), existed.getPropertyDepositStatus());
        LambdaUpdateWrapper<LeaseContract> uw = new LambdaUpdateWrapper<LeaseContract>()
                .eq(LeaseContract::getId, contractId)
                .set(LeaseContract::getRentDepositStatus, rentSt)
                .set(LeaseContract::getRentDepositPaid, req.rentDepositPaid() != null ? req.rentDepositPaid() : nz(existed.getRentDepositPaid()))
                .set(LeaseContract::getRentDepositPaidDate, req.rentDepositPaidDate())
                .set(LeaseContract::getPropertyDepositStatus, propSt)
                .set(LeaseContract::getPropertyDepositPaid, req.propertyDepositPaid() != null ? req.propertyDepositPaid() : nz(existed.getPropertyDepositPaid()))
                .set(LeaseContract::getPropertyDepositPaidDate, req.propertyDepositPaidDate())
                .set(LeaseContract::getDepositRemark, req.depositRemark());
        if (req.rentDeposit() != null) uw.set(LeaseContract::getRentDeposit, req.rentDeposit());
        if (req.propertyDeposit() != null) uw.set(LeaseContract::getPropertyDeposit, req.propertyDeposit());
        if (req.depositTerms() != null) uw.set(LeaseContract::getDepositTerms, req.depositTerms());
        contractMapper.update(null, uw);
        return listDeposits(true).stream().filter(c -> Objects.equals(c.getId(), contractId)).findFirst()
                .orElseGet(() -> contractMapper.selectById(contractId));
    }

    private static int normalizeDepositStatus(Integer status, Integer fallback) {
        int val = status != null ? status : (fallback != null ? fallback : 0);
        if (val < 0 || val > 2) throw new BizException("保证金状态只能为 0(未缴纳)、1(部分缴纳) 或 2(已缴纳)");
        return val;
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaseContract saveContract(LeaseContract input) {
        validateContract(input);
        normalizeFixedRent(input);
        if (input.getTenantRefId() == null) {
            input.setTenantRefId(lookupTenantRefId(input.getTenantName()));
        }
        if (input.getId() == null) {
            if (input.getSeqNo() == null) {
                Integer maxSeq = contractMapper.selectList(new LambdaQueryWrapper<LeaseContract>())
                        .stream().map(LeaseContract::getSeqNo).filter(Objects::nonNull).max(Integer::compareTo).orElse(0);
                input.setSeqNo(maxSeq + 1);
            }
            if (input.getStatus() == null) input.setStatus(1);
            if (input.getEscalationMonths() == null || input.getEscalationMonths() <= 0) input.setEscalationMonths(36);
            if (input.getEscalationRate() == null) input.setEscalationRate(BigDecimal.ZERO);
            if (input.getPropertyUnitPrice() == null) input.setPropertyUnitPrice(new BigDecimal("2"));
            if (input.getRentDepositStatus() == null) input.setRentDepositStatus(0);
            if (input.getRentDepositPaid() == null) input.setRentDepositPaid(BigDecimal.ZERO);
            if (input.getPropertyDepositStatus() == null) input.setPropertyDepositStatus(0);
            if (input.getPropertyDepositPaid() == null) input.setPropertyDepositPaid(BigDecimal.ZERO);
            contractMapper.insert(input);
        } else {
            LeaseContract existed = contractMapper.selectById(input.getId());
            if (existed == null) throw new BizException("合同不存在: " + input.getId());
            if (input.getRentDepositStatus() == null) input.setRentDepositStatus(existed.getRentDepositStatus());
            if (input.getRentDepositPaid() == null) input.setRentDepositPaid(existed.getRentDepositPaid());
            if (input.getRentDepositPaidDate() == null) input.setRentDepositPaidDate(existed.getRentDepositPaidDate());
            if (input.getPropertyDepositStatus() == null) input.setPropertyDepositStatus(existed.getPropertyDepositStatus());
            if (input.getPropertyDepositPaid() == null) input.setPropertyDepositPaid(existed.getPropertyDepositPaid());
            if (input.getPropertyDepositPaidDate() == null) input.setPropertyDepositPaidDate(existed.getPropertyDepositPaidDate());
            if (input.getDepositRemark() == null) input.setDepositRemark(existed.getDepositRemark());
            contractMapper.updateById(input);
        }
        touchDrafts();
        return contractMapper.selectById(input.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteContract(Long id) {
        LeaseContract existed = contractMapper.selectById(id);
        if (existed == null) return;
        contractMapper.deleteById(id);
        touchDrafts();
    }

    private static void normalizeFixedRent(LeaseContract c) {
        if (c.getFixedMonthlyRent() != null && c.getFixedMonthlyRent().signum() <= 0) {
            c.setFixedMonthlyRent(null);
        }
        if (c.getRentUnitPrice() == null) {
            if (c.getFixedMonthlyRent() != null && c.getLeaseArea() != null && c.getLeaseArea().signum() > 0) {
                c.setRentUnitPrice(c.getFixedMonthlyRent().divide(c.getLeaseArea(), 12, java.math.RoundingMode.HALF_UP));
            } else {
                c.setRentUnitPrice(BigDecimal.ZERO);
            }
        }
    }

    private static void validateContract(LeaseContract c) {
        if (c == null) throw new BizException("合同参数不能为空");
        if (!StringUtils.hasText(c.getTenantName())) throw new BizException("请填写租户名称");
        if (!StringUtils.hasText(c.getUnitName())) throw new BizException("请填写楼层/区域");
        if (c.getStartDate() == null || c.getEndDate() == null) throw new BizException("请填写合同起租日与到期日");
        if (c.getEndDate().isBefore(c.getStartDate())) throw new BizException("合同到期日不能早于起租日");
        if (c.getLeaseArea() == null || c.getLeaseArea().signum() < 0) throw new BizException("计租面积不能为负");
        if (c.getFixedMonthlyRent() != null && c.getFixedMonthlyRent().signum() < 0) throw new BizException("月固定总价不能为负");
        boolean hasFixed = c.getFixedMonthlyRent() != null && c.getFixedMonthlyRent().signum() > 0;
        if (!hasFixed && (c.getRentUnitPrice() == null || c.getRentUnitPrice().signum() < 0)) throw new BizException("请填写月租金单价或月固定总价");
        if (c.getRentUnitPrice() != null && c.getRentUnitPrice().signum() < 0) throw new BizException("月租金单价不能为负");
        if (c.getPropertyUnitPrice() != null && c.getPropertyUnitPrice().signum() < 0) throw new BizException("月物业费单价不能为负");
        if (c.getEscalationRate() != null && c.getEscalationRate().signum() < 0) throw new BizException("递增比例不能为负");
    }

    // ------------------------------------------------------------------ 年月表 & 账期

    public List<MonthOverview> overview(int year) {
        if (year < 2000 || year > 2100) throw new BizException("年份不合法");
        List<LeaseContract> contracts = listContracts(false);
        List<LeaseFeePeriod> periods = decorate(periodMapper.selectList(new LambdaQueryWrapper<LeaseFeePeriod>()
                .ge(LeaseFeePeriod::getPeriod, year + "-01").le(LeaseFeePeriod::getPeriod, year + "-12")
                .orderByAsc(LeaseFeePeriod::getPeriod)));
        Map<String, LeaseFeePeriod> periodMap = periods.stream()
                .collect(Collectors.toMap(LeaseFeePeriod::getPeriod, p -> p, (a, b) -> a, LinkedHashMap::new));

        Map<Long, List<LeaseFeeEntry>> entriesByPeriod = new LinkedHashMap<>();
        Map<Long, int[]> noticeCounts = new LinkedHashMap<>();
        Map<Long, BigDecimal[]> noticeTotals = new LinkedHashMap<>();
        Map<String, Integer> files = new LinkedHashMap<>();

        if (!periods.isEmpty()) {
            String ids = periods.stream().map(x -> String.valueOf(x.getId())).collect(Collectors.joining(","));
            for (LeaseFeeEntry e : entryMapper.selectList(new LambdaQueryWrapper<LeaseFeeEntry>()
                    .in(LeaseFeeEntry::getLeasePeriodId, periods.stream().map(LeaseFeePeriod::getId).toList()))) {
                entriesByPeriod.computeIfAbsent(e.getLeasePeriodId(), k -> new ArrayList<>()).add(e);
            }
            for (Map<String, Object> r : jdbc.queryForList(
                    "SELECT lease_period_id, fee_category, COUNT(*) AS c, COALESCE(SUM(total_due), 0) AS s"
                            + " FROM eng_lease_fee_notice WHERE deleted = 0 AND lease_period_id IN (" + ids + ")"
                            + " GROUP BY lease_period_id, fee_category")) {
                Long pid = ((Number) r.get("lease_period_id")).longValue();
                String cat = String.valueOf(r.get("fee_category"));
                int c = ((Number) r.get("c")).intValue();
                BigDecimal s = (BigDecimal) r.get("s");
                int[] cnt = noticeCounts.computeIfAbsent(pid, k -> new int[2]);
                BigDecimal[] tot = noticeTotals.computeIfAbsent(pid, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                if (LeaseFeeNotice.CAT_RENT.equals(cat)) { cnt[0] = c; tot[0] = s; }
                else { cnt[1] = c; tot[1] = s; }
            }
            for (Map<String, Object> r : jdbc.queryForList(
                    "SELECT biz_type, biz_id, COUNT(*) AS c FROM sys_file WHERE deleted = 0"
                            + " AND biz_type IN ('energy_fee_rent','energy_fee_property')"
                            + " AND biz_id IN (" + ids + ") GROUP BY biz_type, biz_id")) {
                files.put(r.get("biz_type") + ":" + r.get("biz_id"), ((Number) r.get("c")).intValue());
            }
        }

        List<MonthOverview> out = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            String key = ym.toString();
            LeaseFeePeriod p = periodMap.get(key);
            if (p != null && LeaseFeePeriod.ST_CONFIRMED.equals(p.getStatus())) {
                Sheet frozen = readSnapshot(p);
                int[] cnt = noticeCounts.getOrDefault(p.getId(), new int[2]);
                BigDecimal[] tot = noticeTotals.getOrDefault(p.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                BigDecimal rentTot = cnt[0] > 0 ? tot[0] : (frozen != null ? nz(frozen.payableRentTotal()) : nz(p.getRentTotalAmount()));
                BigDecimal propTot = cnt[1] > 0 ? tot[1] : (frozen != null ? nz(frozen.payablePropertyTotal()) : nz(p.getPropertyTotalAmount()));
                out.add(new MonthOverview(key, p.getId(), p.getStatus(), false,
                        frozen == null ? 0 : frozen.rows().size(),
                        rentTot, propTot, rentTot.add(propTot),
                        cnt[0] + cnt[1], cnt[0], cnt[1],
                        files.getOrDefault("energy_fee_rent:" + p.getId(), 0),
                        files.getOrDefault("energy_fee_property:" + p.getId(), 0)));
                continue;
            }
            Map<Long, LeaseFeeEntry> entryMap = new LinkedHashMap<>();
            if (p != null) {
                for (LeaseFeeEntry e : entriesByPeriod.getOrDefault(p.getId(), List.of())) {
                    entryMap.put(e.getContractId(), e);
                }
            }
            List<ContractMonthRow> rows = LeaseFeeCalculator.calculateMonth(ym, contracts, entryMap);
            if (p == null && rows.isEmpty()) {
                continue;
            }
            BigDecimal rentTot = rows.stream().map(ContractMonthRow::getPayableRent).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal propTot = rows.stream().map(ContractMonthRow::getPayableProperty).reduce(BigDecimal.ZERO, BigDecimal::add);
            int[] cnt = p == null ? new int[2] : noticeCounts.getOrDefault(p.getId(), new int[2]);
            if (p != null && (cnt[0] > 0 || cnt[1] > 0)) {
                BigDecimal[] tot = noticeTotals.get(p.getId());
                if (tot != null) {
                    if (cnt[0] > 0) rentTot = tot[0];
                    if (cnt[1] > 0) propTot = tot[1];
                }
            }
            out.add(new MonthOverview(key, p == null ? null : p.getId(),
                    p == null ? LeaseFeePeriod.ST_DRAFT : p.getStatus(),
                    p != null && Boolean.TRUE.equals(p.getNoticeStale()),
                    rows.size(), LeaseFeeCalculator.money(rentTot), LeaseFeeCalculator.money(propTot),
                    LeaseFeeCalculator.money(rentTot.add(propTot)),
                    cnt[0] + cnt[1], cnt[0], cnt[1],
                    p == null ? 0 : files.getOrDefault("energy_fee_rent:" + p.getId(), 0),
                    p == null ? 0 : files.getOrDefault("energy_fee_property:" + p.getId(), 0)));
        }
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaseFeePeriod open(String period, Long ignoredProjectId) {
        YearMonth ym = parse(period);
        LeaseFeePeriod existed = periodMapper.selectOne(new LambdaQueryWrapper<LeaseFeePeriod>()
                .eq(LeaseFeePeriod::getPeriod, ym.toString()).last("LIMIT 1"));
        if (existed != null) {
            return decorate(existed);
        }
        LeaseFeePeriod latest = periodMapper.selectOne(new LambdaQueryWrapper<LeaseFeePeriod>()
                .orderByDesc(LeaseFeePeriod::getPeriod).last("LIMIT 1"));
        LeaseFeePeriod p = new LeaseFeePeriod();
        p.setProjectId(null);
        p.setPeriod(ym.toString());
        p.setPeriodStart(ym.atDay(1));
        p.setPeriodEnd(ym.atEndOfMonth());
        p.setStatus(LeaseFeePeriod.ST_DRAFT);
        p.setIssueDate(LocalDate.now());
        p.setDueDate(ym.atEndOfMonth());
        if (latest != null) {
            p.setParkName(latest.getParkName());
            p.setNoticePrefix(latest.getNoticePrefix());
            p.setRentPayeeName(latest.getRentPayeeName());
            p.setRentPaymentAccount(latest.getRentPaymentAccount());
            p.setRentContactName(latest.getRentContactName());
            p.setRentContactPhone(latest.getRentContactPhone());
            p.setPropertyPayeeName(latest.getPropertyPayeeName());
            p.setPropertyPaymentAccount(latest.getPropertyPaymentAccount());
            p.setPropertyContactName(latest.getPropertyContactName());
            p.setPropertyContactPhone(latest.getPropertyContactPhone());
        } else {
            p.setParkName(DEFAULT_PARK);
            p.setNoticePrefix("DIPARK");
            populateDefaultPayeesFromCollectionAccounts(p);
        }
        try {
            periodMapper.insert(p);
        } catch (DuplicateKeyException e) {
            return decorate(periodMapper.selectOne(new LambdaQueryWrapper<LeaseFeePeriod>()
                    .eq(LeaseFeePeriod::getPeriod, ym.toString()).last("LIMIT 1")));
        }
        return decorate(p);
    }

    /** 首次建账期时尝试从财务收款账户表读取租金/物业管理费默认收取单位(各自独立) */
    private void populateDefaultPayeesFromCollectionAccounts(LeaseFeePeriod p) {
        try {
            List<Map<String, Object>> accounts = jdbc.queryForList(
                    "SELECT account_type, account_name, bank_name, account_no_masked FROM fin_collection_account"
                            + " WHERE deleted = 0 AND status = 'ACTIVE' ORDER BY id ASC");
            for (Map<String, Object> a : accounts) {
                String type = String.valueOf(a.get("account_type") == null ? "" : a.get("account_type")).toUpperCase();
                String name = Objects.toString(a.get("account_name"), "");
                String bank = Objects.toString(a.get("bank_name"), "");
                String mask = Objects.toString(a.get("account_no_masked"), "");
                String detail = (name + (bank.isBlank() ? "" : "\n开户行：" + bank) + (mask.isBlank() ? "" : "\n账号：" + mask)).trim();
                if (type.contains("RENT") && !StringUtils.hasText(p.getRentPayeeName())) {
                    p.setRentPayeeName(name);
                    p.setRentPaymentAccount(detail);
                } else if ((type.contains("PROPERTY") || type.contains("PM")) && !StringUtils.hasText(p.getPropertyPayeeName())) {
                    p.setPropertyPayeeName(name);
                    p.setPropertyPaymentAccount(detail);
                }
            }
        } catch (Exception ignored) {
            // 财务收款账户为空时允许留空,运营在「通知单抬头」中自行维护
        }
        if (!StringUtils.hasText(p.getRentPayeeName())) {
            p.setRentPayeeName("园区出租方（租金收取单位）");
        }
        if (!StringUtils.hasText(p.getPropertyPayeeName())) {
            p.setPropertyPayeeName("园区物业服务公司（物业管理费收取单位）");
        }
    }

    public LeaseFeePeriod require(Long id) {
        LeaseFeePeriod p = periodMapper.selectById(id);
        if (p == null) throw new BizException("租金/物业费账期不存在: " + id);
        return decorate(p);
    }

    private LeaseFeePeriod requireDraft(Long id) {
        LeaseFeePeriod p = require(id);
        if (LeaseFeePeriod.ST_CONFIRMED.equals(p.getStatus())) {
            throw new BizException("该账期已确认出账，请先撤销确认再改动");
        }
        return p;
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaseFeePeriod updatePeriod(LeaseFeePeriod input) {
        LeaseFeePeriod existed = requireDraft(input.getId());
        if (input.getPeriodStart() != null && input.getPeriodEnd() != null
                && input.getPeriodEnd().isBefore(input.getPeriodStart())) {
            throw new BizException("计费期间止不能早于起");
        }
        LeaseFeePeriod p = new LeaseFeePeriod();
        p.setId(existed.getId());
        p.setPeriodStart(input.getPeriodStart());
        p.setPeriodEnd(input.getPeriodEnd());
        p.setParkName(input.getParkName());
        p.setNoticePrefix(input.getNoticePrefix());
        p.setRentPayeeName(input.getRentPayeeName());
        p.setRentPaymentAccount(input.getRentPaymentAccount());
        p.setRentContactName(input.getRentContactName());
        p.setRentContactPhone(input.getRentContactPhone());
        p.setPropertyPayeeName(input.getPropertyPayeeName());
        p.setPropertyPaymentAccount(input.getPropertyPaymentAccount());
        p.setPropertyContactName(input.getPropertyContactName());
        p.setPropertyContactPhone(input.getPropertyContactPhone());
        p.setIssueDate(input.getIssueDate());
        p.setDueDate(input.getDueDate());
        p.setRemark(input.getRemark());
        periodMapper.updateById(p);
        touch(existed.getId());
        return require(existed.getId());
    }

    // ------------------------------------------------------------------ 工作表 & 调整

    public Sheet sheet(Long periodId, String feeType) {
        LeaseFeePeriod p = require(periodId);
        if (LeaseFeePeriod.ST_CONFIRMED.equals(p.getStatus())) {
            Sheet frozen = readSnapshot(p);
            if (frozen != null) {
                return new Sheet(p, feeType, frozen.totalArea(), frozen.standardRentTotal(),
                        frozen.standardPropertyTotal(), frozen.payableRentTotal(), frozen.payablePropertyTotal(),
                        frozen.payableGrandTotal(), frozen.rows());
            }
        }
        return buildLiveSheet(p, feeType);
    }

    private Sheet buildLiveSheet(LeaseFeePeriod p, String feeType) {
        YearMonth ym = parse(p.getPeriod());
        List<LeaseContract> contracts = listContracts(false);
        Map<Long, LeaseFeeEntry> entryMap = entryMapper.selectList(new LambdaQueryWrapper<LeaseFeeEntry>()
                .eq(LeaseFeeEntry::getLeasePeriodId, p.getId()))
                .stream().collect(Collectors.toMap(LeaseFeeEntry::getContractId, e -> e, (a, b) -> b, LinkedHashMap::new));
        List<ContractMonthRow> rows = LeaseFeeCalculator.calculateMonth(ym, contracts, entryMap);
        BigDecimal totalArea = BigDecimal.ZERO;
        BigDecimal stdRent = BigDecimal.ZERO;
        BigDecimal stdProp = BigDecimal.ZERO;
        BigDecimal payRent = BigDecimal.ZERO;
        BigDecimal payProp = BigDecimal.ZERO;
        for (ContractMonthRow r : rows) {
            totalArea = totalArea.add(nz(r.getLeaseArea()));
            stdRent = stdRent.add(nz(r.getStandardRent()));
            stdProp = stdProp.add(nz(r.getStandardProperty()));
            payRent = payRent.add(nz(r.getPayableRent()));
            payProp = payProp.add(nz(r.getPayableProperty()));
        }
        return new Sheet(p, feeType, totalArea, LeaseFeeCalculator.money(stdRent), LeaseFeeCalculator.money(stdProp),
                LeaseFeeCalculator.money(payRent), LeaseFeeCalculator.money(payProp),
                LeaseFeeCalculator.money(payRent.add(payProp)), rows);
    }

    @Transactional(rollbackFor = Exception.class)
    public int saveEntries(Long periodId, List<EntryInput> inputs) {
        LeaseFeePeriod p = requireDraft(periodId);
        if (inputs == null || inputs.isEmpty()) return 0;
        int saved = 0;
        for (EntryInput in : inputs) {
            if (in == null || in.contractId() == null) continue;
            LeaseFeeEntry existed = entryMapper.selectOne(new LambdaQueryWrapper<LeaseFeeEntry>()
                    .eq(LeaseFeeEntry::getLeasePeriodId, periodId)
                    .eq(LeaseFeeEntry::getContractId, in.contractId()).last("LIMIT 1"));
            if (Boolean.TRUE.equals(in.resetDefault())) {
                if (existed != null) {
                    entryMapper.deleteById(existed.getId());
                    saved++;
                }
                continue;
            }
            if (in.rentFactor() != null && in.rentFactor().signum() < 0) throw new BizException("租金减免系数不能为负");
            if (in.propertyFactor() != null && in.propertyFactor().signum() < 0) throw new BizException("物业费减免系数不能为负");
            if (existed == null) {
                LeaseFeeEntry e = new LeaseFeeEntry();
                e.setLeasePeriodId(periodId);
                e.setPeriod(p.getPeriod());
                e.setContractId(in.contractId());
                e.setRentFactor(in.rentFactor());
                e.setPropertyFactor(in.propertyFactor());
                e.setRentAmountOverride(in.rentAmountOverride());
                e.setPropertyAmountOverride(in.propertyAmountOverride());
                e.setRemark(in.remark());
                entryMapper.insert(e);
            } else {
                LambdaUpdateWrapper<LeaseFeeEntry> uw = new LambdaUpdateWrapper<LeaseFeeEntry>()
                        .eq(LeaseFeeEntry::getId, existed.getId())
                        .set(LeaseFeeEntry::getRentFactor, in.rentFactor() != null ? in.rentFactor() : existed.getRentFactor())
                        .set(LeaseFeeEntry::getPropertyFactor, in.propertyFactor() != null ? in.propertyFactor() : existed.getPropertyFactor())
                        .set(LeaseFeeEntry::getRentAmountOverride, in.rentAmountOverride())
                        .set(LeaseFeeEntry::getPropertyAmountOverride, in.propertyAmountOverride())
                        .set(LeaseFeeEntry::getRemark, in.remark() != null ? in.remark() : existed.getRemark());
                entryMapper.update(null, uw);
            }
            saved++;
        }
        if (saved > 0) touch(periodId);
        return saved;
    }

    // ------------------------------------------------------------------ 通知单生成 / 查询 / 调整 / 删除

    @Transactional(rollbackFor = Exception.class)
    public GenerateResult generate(Long periodId) {
        payments.lockPeriod(periodId);
        LeaseFeePeriod p = requireDraft(periodId);
        Sheet live = buildLiveSheet(p, "ALL");
        List<LeaseFeeNotice> fresh = LeaseFeeCalculator.buildNotices(p, live.rows());

        List<LeaseFeeNotice> oldNotices = notices(periodId);
        Map<String, List<Long>> attachmentSources = payments.prepareRegeneration(oldNotices, fresh);
        Map<String, LeaseFeeNotice> oldByKey = new LinkedHashMap<>();
        for (LeaseFeeNotice old : oldNotices) {
            oldByKey.put(LeaseFeeCalculator.noticeKey(old), old);
        }

        // 保留既有缴费状态、已付/欠费/备注/手工增项
        List<Long> oldIds = oldNotices.stream().map(LeaseFeeNotice::getId).filter(Objects::nonNull).toList();
        if (!oldIds.isEmpty()) {
            itemMapper.delete(new LambdaQueryWrapper<LeaseFeeNoticeItem>().in(LeaseFeeNoticeItem::getNoticeId, oldIds));
            noticeMapper.deleteBatchIds(oldIds);
        }

        BigDecimal rentDueSum = BigDecimal.ZERO;
        BigDecimal propDueSum = BigDecimal.ZERO;
        for (LeaseFeeNotice n : fresh) {
            if (n.getTenantRefId() == null) {
                n.setTenantRefId(lookupTenantRefId(n.getTenantName()));
            }
            LeaseFeeNotice old = oldByKey.get(LeaseFeeCalculator.noticeKey(n));
            List<LeaseFeeNoticeItem> mergedItems = new ArrayList<>(n.getItems());
            if (old != null) {
                n.setPaymentStatus(old.getPaymentStatus() == null ? 0 : old.getPaymentStatus());
                n.setInvoiceStatus(old.getInvoiceStatus() == null ? 0 : old.getInvoiceStatus());
                n.setPaidAmount(nz(old.getPaidAmount()));
                n.setPriorArrears(nz(old.getPriorArrears()));
                if (StringUtils.hasText(old.getRemark())) n.setRemark(old.getRemark());
                if (old.getItems() != null) {
                    int mIdx = 0;
                    for (LeaseFeeNoticeItem oi : old.getItems()) {
                        if (oi.isManualItem()) {
                            mIdx++;
                            LeaseFeeNoticeItem copy = new LeaseFeeNoticeItem();
                            copy.setLeasePeriodId(periodId);
                            copy.setItemType(LeaseFeeNoticeItem.T_CUSTOM);
                            copy.setManualFlag(1);
                            copy.setSeq(String.valueOf(mergedItems.size() + 1));
                            copy.setFeeItem(oi.getFeeItem());
                            copy.setUnitName(oi.getUnitName());
                            copy.setLeaseArea(oi.getLeaseArea());
                            copy.setUnitPrice(oi.getUnitPrice());
                            copy.setAmount(oi.getAmount());
                            copy.setRemark(oi.getRemark());
                            copy.setSort(900 + mIdx);
                            mergedItems.add(copy);
                        }
                    }
                }
            }
            LeaseFeeCalculator.recalcNoticeTotals(n, mergedItems);
            noticeMapper.insert(n);
            for (LeaseFeeNoticeItem it : mergedItems) {
                it.setNoticeId(n.getId());
                it.setLeasePeriodId(periodId);
                itemMapper.insert(it);
            }
            payments.moveAttachments(attachmentSources, n);
            n.setItems(mergedItems);
            if (LeaseFeeNotice.CAT_RENT.equals(n.getFeeCategory())) {
                rentDueSum = rentDueSum.add(nz(n.getTotalDue()));
            } else {
                propDueSum = propDueSum.add(nz(n.getTotalDue()));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        periodMapper.update(null, new LambdaUpdateWrapper<LeaseFeePeriod>()
                .eq(LeaseFeePeriod::getId, periodId)
                .set(LeaseFeePeriod::getRentTotalAmount, LeaseFeeCalculator.money(rentDueSum))
                .set(LeaseFeePeriod::getPropertyTotalAmount, LeaseFeeCalculator.money(propDueSum))
                .set(LeaseFeePeriod::getNoticeCount, fresh.size())
                .set(LeaseFeePeriod::getGeneratedTime, now));
        return new GenerateResult(require(periodId), fresh);
    }

    public List<LeaseFeeNotice> notices(Long periodId) {
        List<LeaseFeeNotice> list = noticeMapper.selectList(new LambdaQueryWrapper<LeaseFeeNotice>()
                .eq(LeaseFeeNotice::getLeasePeriodId, periodId)
                .orderByAsc(LeaseFeeNotice::getSort).orderByAsc(LeaseFeeNotice::getId));
        if (list.isEmpty()) return list;
        List<Long> ids = list.stream().map(LeaseFeeNotice::getId).toList();
        Map<Long, List<LeaseFeeNoticeItem>> byNotice = itemMapper.selectList(new LambdaQueryWrapper<LeaseFeeNoticeItem>()
                .in(LeaseFeeNoticeItem::getNoticeId, ids)
                .orderByAsc(LeaseFeeNoticeItem::getSort).orderByAsc(LeaseFeeNoticeItem::getId))
                .stream().collect(Collectors.groupingBy(LeaseFeeNoticeItem::getNoticeId));
        for (LeaseFeeNotice n : list) {
            n.setItems(byNotice.getOrDefault(n.getId(), List.of()));
            LeaseFeePaymentService.normalizeStatus(n);
        }
        return list;
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaseFeeNotice editNotice(NoticeEdit in) {
        LeaseFeeNotice n = noticeMapper.selectById(in.id());
        if (n == null) throw new BizException("通知单不存在");
        requireDraft(n.getLeasePeriodId());
        if (StringUtils.hasText(in.tenantName())) n.setTenantName(in.tenantName().trim());
        if (StringUtils.hasText(in.unitName())) n.setUnitName(in.unitName().trim());
        if (in.payeeName() != null) n.setPayeeName(in.payeeName().trim());
        if (in.paymentAccount() != null) n.setPaymentAccount(in.paymentAccount().trim());
        n.setPaidAmount(nz(in.paidAmount()));
        n.setPriorArrears(nz(in.priorArrears()));
        n.setRemark(in.remark());

        List<LeaseFeeNoticeItem> current = itemMapper.selectList(new LambdaQueryWrapper<LeaseFeeNoticeItem>()
                .eq(LeaseFeeNoticeItem::getNoticeId, n.getId()).orderByAsc(LeaseFeeNoticeItem::getSort));
        if (in.itemRemarks() != null) {
            for (LeaseFeeNoticeItem it : current) {
                if (!it.isManualItem() && in.itemRemarks().containsKey(it.getId())) {
                    it.setRemark(in.itemRemarks().get(it.getId()));
                    itemMapper.updateById(it);
                }
            }
        }
        if (in.manualItems() != null) {
            if (in.manualItems().size() > MAX_MANUAL_ITEMS) {
                throw new BizException("手工收费项最多 " + MAX_MANUAL_ITEMS + " 行");
            }
            for (LeaseFeeNoticeItem it : current) {
                if (it.isManualItem()) itemMapper.deleteById(it.getId());
            }
            long autoCount = current.stream().filter(i -> !i.isManualItem()).count();
            int idx = 0;
            for (ManualItemInput m : in.manualItems()) {
                idx++;
                if (!StringUtils.hasText(m.feeItem())) throw new BizException("第 " + idx + " 行手工项未填名称");
                BigDecimal amt = m.amount();
                if (amt == null && m.leaseArea() != null && m.unitPrice() != null) {
                    amt = m.leaseArea().multiply(m.unitPrice());
                }
                if (amt == null) throw new BizException("第 " + idx + " 行未填金额");
                LeaseFeeNoticeItem it = new LeaseFeeNoticeItem();
                it.setNoticeId(n.getId());
                it.setLeasePeriodId(n.getLeasePeriodId());
                it.setItemType(LeaseFeeNoticeItem.T_CUSTOM);
                it.setManualFlag(1);
                it.setSeq(String.valueOf(autoCount + idx));
                it.setFeeItem(m.feeItem().trim());
                it.setLeaseArea(m.leaseArea());
                it.setUnitPrice(m.unitPrice());
                it.setAmount(LeaseFeeCalculator.money(amt));
                it.setRemark(m.remark());
                it.setSort(900 + idx);
                itemMapper.insert(it);
            }
        }
        List<LeaseFeeNoticeItem> after = itemMapper.selectList(new LambdaQueryWrapper<LeaseFeeNoticeItem>()
                .eq(LeaseFeeNoticeItem::getNoticeId, n.getId()).orderByAsc(LeaseFeeNoticeItem::getSort));
        LeaseFeeCalculator.recalcNoticeTotals(n, after);
        noticeMapper.updateById(n);
        refreshPeriodTotals(n.getLeasePeriodId());
        n.setItems(after);
        return LeaseFeePaymentService.normalizeStatus(n);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deleteNotices(Long periodId, List<Long> ids, boolean all) {
        payments.lockPeriod(periodId);
        LeaseFeePeriod p = require(periodId);
        LambdaQueryWrapper<LeaseFeeNotice> qw = new LambdaQueryWrapper<LeaseFeeNotice>()
                .eq(LeaseFeeNotice::getLeasePeriodId, periodId);
        if (!all) {
            if (ids == null || ids.isEmpty()) throw new BizException("请选择要删除的通知单");
            qw.in(LeaseFeeNotice::getId, ids);
        }
        List<LeaseFeeNotice> targets = noticeMapper.selectList(qw);
        if (targets.isEmpty()) return Map.of("deleted", 0, "bills", 0);
        payments.assertCanDelete(targets);
        for (LeaseFeeNotice n : targets) {
            Bill b = n.getBillId() == null ? null : billMapper.lockActive(n.getBillId());
            if (b != null && b.getPaidAmount() != null && b.getPaidAmount().signum() > 0) {
                throw new BizException("通知单「" + n.getNoticeNo() + "」对应账单已收款，不能删除");
            }
        }
        int bills = 0;
        List<Long> noticeIds = new ArrayList<>();
        for (LeaseFeeNotice n : targets) {
            noticeIds.add(n.getId());
            if (n.getBillId() != null && billMapper.lockActive(n.getBillId()) != null) {
                billMapper.deleteById(n.getBillId());
                bills++;
            }
        }
        itemMapper.delete(new LambdaQueryWrapper<LeaseFeeNoticeItem>().in(LeaseFeeNoticeItem::getNoticeId, noticeIds));
        noticeMapper.deleteBatchIds(noticeIds);
        refreshPeriodTotals(periodId);
        if (noticeMapper.selectCount(new LambdaQueryWrapper<LeaseFeeNotice>().eq(LeaseFeeNotice::getLeasePeriodId, periodId)) == 0
                && LeaseFeePeriod.ST_CONFIRMED.equals(p.getStatus())) {
            periodMapper.update(null, new LambdaUpdateWrapper<LeaseFeePeriod>().eq(LeaseFeePeriod::getId, periodId)
                    .set(LeaseFeePeriod::getStatus, LeaseFeePeriod.ST_DRAFT)
                    .set(LeaseFeePeriod::getSnapshotJson, null));
        }
        return Map.of("deleted", noticeIds.size(), "bills", bills);
    }

    // ------------------------------------------------------------------ 确认出账 / 撤销确认

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirm(Long periodId) {
        LeaseFeePeriod p = requireDraft(periodId);
        List<LeaseFeeNotice> notices = notices(periodId);
        if (notices.isEmpty()) throw new BizException("还没有生成通知单，请先点「生成通知单」");
        if (Boolean.TRUE.equals(p.getNoticeStale())) {
            throw new BizException("生成通知单之后合同或减免系数又有改动，请先重新生成通知单再确认出账");
        }
        YearMonth ym = parse(p.getPeriod());
        int inserted = 0;
        int skipped = 0;
        for (LeaseFeeNotice n : notices) {
            Long tenantRefId = n.getTenantRefId() != null ? n.getTenantRefId() : lookupTenantRefId(n.getTenantName());
            if (tenantRefId == null || nz(n.getSubtotal()).signum() <= 0) {
                skipped++;
                continue;
            }
            boolean isRent = LeaseFeeNotice.CAT_RENT.equals(n.getFeeCategory());
            String key = "lease-fee-notice:" + n.getId();
            Bill existed = billMapper.selectOne(new LambdaQueryWrapper<Bill>().eq(Bill::getBillingKey, key).last("LIMIT 1"));
            if (existed != null) {
                n.setBillId(existed.getId());
                n.setTenantRefId(tenantRefId);
                noticeMapper.updateById(n);
                skipped++;
                continue;
            }
            Bill b = new Bill();
            b.setCode((isRent ? "ZJ" : "WY") + ym.toString().replace("-", "") + "-" + n.getId());
            b.setBillingKey(key);
            b.setTenantRefId(tenantRefId);
            b.setProjectId(p.getProjectId());
            b.setDirection(1);
            b.setFeeType(isRent ? "租金" : "物业费");
            b.setSource("合同计划");
            b.setStatus(3);
            b.setAmount(nz(n.getSubtotal()));
            b.setPaidAmount(BigDecimal.ZERO);
            b.setLateFee(BigDecimal.ZERO);
            b.setPeriodStart(p.getPeriodStart() != null ? p.getPeriodStart() : ym.atDay(1));
            b.setPeriodEnd(p.getPeriodEnd() != null ? p.getPeriodEnd() : ym.atEndOfMonth());
            b.setDueDate(p.getDueDate() != null ? p.getDueDate() : ym.atEndOfMonth());
            b.setInvoiceStatus(0);
            b.setRemark(String.format("%s %s %s通知单 %s（收取单位：%s）",
                    p.getPeriod(), n.getTenantName(), isRent ? "租金" : "物业管理费",
                    n.getNoticeNo(), Objects.toString(n.getPayeeName(), "-")));
            billMapper.insert(b);
            n.setTenantRefId(tenantRefId);
            n.setBillId(b.getId());
            noticeMapper.updateById(n);
            inserted++;
        }

        Sheet live = buildLiveSheet(p, "ALL");
        String snapshot;
        try {
            snapshot = objectMapper.writeValueAsString(new Sheet(null, "ALL", live.totalArea(),
                    live.standardRentTotal(), live.standardPropertyTotal(),
                    live.payableRentTotal(), live.payablePropertyTotal(), live.payableGrandTotal(), live.rows()));
        } catch (JsonProcessingException e) {
            throw new BizException("冻结账期快照失败");
        }
        int updated = periodMapper.update(null, new LambdaUpdateWrapper<LeaseFeePeriod>()
                .eq(LeaseFeePeriod::getId, periodId).eq(LeaseFeePeriod::getStatus, LeaseFeePeriod.ST_DRAFT)
                .set(LeaseFeePeriod::getStatus, LeaseFeePeriod.ST_CONFIRMED)
                .set(LeaseFeePeriod::getSnapshotJson, snapshot));
        if (updated == 0) throw new BizException("账期状态已变化，请刷新后重试");
        return Map.of("inserted", inserted, "skipped", skipped);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> revoke(Long periodId) {
        LeaseFeePeriod p = require(periodId);
        if (!LeaseFeePeriod.ST_CONFIRMED.equals(p.getStatus())) {
            throw new BizException("该账期不是已确认状态");
        }
        List<LeaseFeeNotice> notices = noticeMapper.selectList(new LambdaQueryWrapper<LeaseFeeNotice>()
                .eq(LeaseFeeNotice::getLeasePeriodId, periodId).isNotNull(LeaseFeeNotice::getBillId));
        for (LeaseFeeNotice n : notices) {
            Bill b = billMapper.lockActive(n.getBillId());
            if (b != null && b.getPaidAmount() != null && b.getPaidAmount().signum() > 0) {
                throw new BizException("有账单已收款，不能撤销；请先在收款记录里红冲后重试");
            }
        }
        int removed = 0;
        for (LeaseFeeNotice n : notices) {
            if (billMapper.lockActive(n.getBillId()) != null) {
                billMapper.deleteById(n.getBillId());
                removed++;
            }
            noticeMapper.update(null, new LambdaUpdateWrapper<LeaseFeeNotice>()
                    .eq(LeaseFeeNotice::getId, n.getId()).set(LeaseFeeNotice::getBillId, null));
        }
        periodMapper.update(null, new LambdaUpdateWrapper<LeaseFeePeriod>()
                .eq(LeaseFeePeriod::getId, periodId).eq(LeaseFeePeriod::getStatus, LeaseFeePeriod.ST_CONFIRMED)
                .set(LeaseFeePeriod::getStatus, LeaseFeePeriod.ST_DRAFT)
                .set(LeaseFeePeriod::getSnapshotJson, null));
        return Map.of("removed", removed);
    }

    // ------------------------------------------------------------------ Helpers

    private void refreshPeriodTotals(Long periodId) {
        List<LeaseFeeNotice> left = noticeMapper.selectList(new LambdaQueryWrapper<LeaseFeeNotice>()
                .eq(LeaseFeeNotice::getLeasePeriodId, periodId));
        BigDecimal rentSum = BigDecimal.ZERO;
        BigDecimal propSum = BigDecimal.ZERO;
        for (LeaseFeeNotice n : left) {
            if (LeaseFeeNotice.CAT_RENT.equals(n.getFeeCategory())) rentSum = rentSum.add(nz(n.getTotalDue()));
            else propSum = propSum.add(nz(n.getTotalDue()));
        }
        periodMapper.update(null, new LambdaUpdateWrapper<LeaseFeePeriod>()
                .eq(LeaseFeePeriod::getId, periodId)
                .set(LeaseFeePeriod::getNoticeCount, left.size())
                .set(LeaseFeePeriod::getRentTotalAmount, LeaseFeeCalculator.money(rentSum))
                .set(LeaseFeePeriod::getPropertyTotalAmount, LeaseFeeCalculator.money(propSum)));
    }

    private LeaseFeePeriod decorate(LeaseFeePeriod p) {
        if (p != null) {
            p.setNoticeStale(p.getGeneratedTime() != null && LeaseFeePeriod.ST_DRAFT.equals(p.getStatus())
                    && p.getInputsChangedTime() != null && p.getInputsChangedTime().isAfter(p.getGeneratedTime()));
        }
        return p;
    }

    private List<LeaseFeePeriod> decorate(List<LeaseFeePeriod> list) {
        list.forEach(this::decorate);
        return list;
    }

    private void touch(Long periodId) {
        periodMapper.update(null, new LambdaUpdateWrapper<LeaseFeePeriod>()
                .eq(LeaseFeePeriod::getId, periodId).set(LeaseFeePeriod::getInputsChangedTime, LocalDateTime.now()));
    }

    private void touchDrafts() {
        periodMapper.update(null, new LambdaUpdateWrapper<LeaseFeePeriod>()
                .eq(LeaseFeePeriod::getStatus, LeaseFeePeriod.ST_DRAFT)
                .set(LeaseFeePeriod::getInputsChangedTime, LocalDateTime.now()));
    }

    private Sheet readSnapshot(LeaseFeePeriod p) {
        if (p == null || !StringUtils.hasText(p.getSnapshotJson())) return null;
        try {
            return objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(p.getSnapshotJson(), Sheet.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Long lookupTenantRefId(String tenantName) {
        if (!StringUtils.hasText(tenantName)) return null;
        try {
            List<Long> ids = jdbc.queryForList(
                    "SELECT id FROM crm_tenant WHERE deleted = 0 AND name = ? ORDER BY id ASC LIMIT 1",
                    Long.class, tenantName.trim());
            return ids.isEmpty() ? null : ids.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private static YearMonth parse(String period) {
        try {
            return YearMonth.parse(period);
        } catch (Exception e) {
            throw new BizException("账期格式应为 yyyy-MM，如 " + YearMonth.now());
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
