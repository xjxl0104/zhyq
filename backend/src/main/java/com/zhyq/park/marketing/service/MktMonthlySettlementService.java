package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.marketing.entity.MktPromoterCommission;
import com.zhyq.park.marketing.mapper.MktPromoterCommissionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 按月自动结算：把“当月之前”各个月份里已经解冻、可结算的出库佣金，按（服务合同，月份）各生成一个结算批次。
 * 每月只结算已经结束的月份；冻结期没到的流水留到下次再结，已结算/已提现/已申请提现的不会重复结算。
 */
@Slf4j
@Service
public class MktMonthlySettlementService {
    public static final String OPERATOR = "system";

    public record Result(int batches, int commissions, BigDecimal amount, int failed, List<String> errors) {}

    private final JdbcTemplate jdbc;
    private final MktPromoterCommissionMapper commissionMapper;
    private final MktCommissionService commissionService;
    private final TransactionTemplate transaction;
    private final ReentrantLock lock = new ReentrantLock();

    public MktMonthlySettlementService(JdbcTemplate jdbc, MktPromoterCommissionMapper commissionMapper,
                                       MktCommissionService commissionService, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.commissionMapper = commissionMapper;
        this.commissionService = commissionService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** 结算 today 所在月份之前的所有“合同 × 月份”。 */
    public Result settleBefore(LocalDate today) {
        lock.lock();
        try {
            String currentMonthStart = YearMonth.from(today).atDay(1) + " 00:00:00";
            List<Map<String, Object>> pairs = jdbc.queryForList(
                    "SELECT o.source_id AS contract_id, DATE_FORMAT(o.event_time, '%Y-%m') AS ym "
                            + "FROM crm_promoter_commission c JOIN crm_referral_order o ON o.id = c.referral_order_id "
                            + "AND o.deleted = 0 AND o.source_type = " + MktCommissionService.SOURCE_OUTBOUND + " "
                            + "WHERE c.deleted = 0 AND c.status = " + MktCommissionService.C_SETTLEABLE
                            + " AND c.sign = 1 AND c.withdrawal_id IS NULL AND o.event_time < ? "
                            + "GROUP BY o.source_id, ym ORDER BY ym, contract_id", currentMonthStart);
            int batches = 0, commissions = 0, failed = 0;
            BigDecimal amount = BigDecimal.ZERO;
            List<String> errors = new ArrayList<>();
            for (Map<String, Object> pair : pairs) {
                Object contract = pair.get("contract_id"), ym = pair.get("ym");
                if (!(contract instanceof Number number) || ym == null) continue;
                try {
                    Settled settled = settleMonth(number.longValue(), YearMonth.parse(ym.toString()));
                    if (settled.count() > 0) { batches++; commissions += settled.count(); amount = amount.add(settled.amount()); }
                } catch (RuntimeException failure) {
                    failed++;
                    if (errors.size() < 20) errors.add("合同 " + contract + " · " + ym + "：" + failure.getMessage());
                    log.warn("[mkt] 月结失败 合同 {} {}: {}", contract, ym, failure.getMessage());
                }
            }
            log.info("[mkt] 自动月结：{} 个批次，{} 条流水，合计 {} 元，失败 {}", batches, commissions, amount, failed);
            return new Result(batches, commissions, amount, failed, List.copyOf(errors));
        } finally {
            lock.unlock();
        }
    }

    record Settled(int count, BigDecimal amount, String batchNo) {}

    /** 一个合同、一个月份的可结算佣金生成一个批次；没有可结算流水时不生成。 */
    Settled settleMonth(Long contractId, YearMonth month) {
        return transaction.execute(status -> {
            String inSql = "SELECT id FROM crm_referral_order WHERE deleted = 0 AND source_type = "
                    + MktCommissionService.SOURCE_OUTBOUND + " AND source_id = " + contractId
                    + " AND event_time >= '" + month.atDay(1) + " 00:00:00' AND event_time < '"
                    + month.plusMonths(1).atDay(1) + " 00:00:00'";
            List<MktPromoterCommission> rows = commissionMapper.selectList(new LambdaQueryWrapper<MktPromoterCommission>()
                    .inSql(MktPromoterCommission::getReferralOrderId, inSql)
                    .eq(MktPromoterCommission::getSign, 1)
                    .eq(MktPromoterCommission::getStatus, MktCommissionService.C_SETTLEABLE)
                    .isNull(MktPromoterCommission::getWithdrawalId));
            if (rows.isEmpty()) return new Settled(0, BigDecimal.ZERO, null);
            BigDecimal amount = rows.stream().map(c -> c.getAmount() == null ? BigDecimal.ZERO : c.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String batchNo = commissionService.settle(rows.stream().map(MktPromoterCommission::getId).toList(), OPERATOR);
            return new Settled(rows.size(), amount, batchNo);
        });
    }
}
