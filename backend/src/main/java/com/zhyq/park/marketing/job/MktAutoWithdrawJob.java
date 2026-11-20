package com.zhyq.park.marketing.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.service.MktAlertService;
import com.zhyq.park.marketing.service.MktPromoterService;
import com.zhyq.park.marketing.service.MktWithdrawalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 每月自动提现:默认每月 25 日 10:00(上海时间),把每位伙伴的全部可提现余额自动提交一张提现申请,
 * 申请进入「伙伴提现审批流」由数智云仓财务审批,通过后财务登记打款。
 * 自动提现日由 MktWithdrawalService.autoWithdrawDay() 约束在提现申请开放窗口内,
 * 避免系统自动提交绕过「仅开放期可申请」的规则;每日 2 点的自动结算在此之前已把上月佣金结完。
 * 跳过:收款资料未审核通过、余额不足 0.01 元、已有处理中的提现单的伙伴。
 * 改日期:规则参数 auto_withdraw_day(0 关闭);整体暂停:zhyq.marketing.auto-withdraw-enabled=false。
 */
@Slf4j
@Component
public class MktAutoWithdrawJob {
    private final MktPromoterMapper promoters;
    private final MktWithdrawalService withdrawals;
    private final MktAlertService alerts;
    private final boolean enabled;

    @Autowired
    public MktAutoWithdrawJob(MktPromoterMapper promoters, MktWithdrawalService withdrawals, MktAlertService alerts,
                              @Value("${zhyq.marketing.auto-withdraw-enabled:true}") boolean enabled) {
        this.promoters = promoters;
        this.withdrawals = withdrawals;
        this.alerts = alerts;
        this.enabled = enabled;
    }

    public record Result(int submitted, BigDecimal amount, int skipped, int failed, List<String> errors) {}

    @Scheduled(cron = "${zhyq.marketing.auto-withdraw-cron:0 0 10 * * ?}", zone = "Asia/Shanghai")
    public void run() {
        if (!enabled) return;
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        int day = withdrawals.autoWithdrawDay();
        if (day <= 0 || today.getDayOfMonth() < Math.min(day, today.lengthOfMonth())) return;
        try {
            Result r = runOnce();
            if (r.failed() > 0) {
                alerts.alert("auto-withdraw-failed", "伙伴自动提现有失败", r.failed() + " 位伙伴自动提现失败，例如："
                        + (r.errors().isEmpty() ? "未知原因" : r.errors().get(0)) + "。已提交 " + r.submitted() + " 张提现单。");
            } else if (r.submitted() > 0) {
                alerts.notify("伙伴自动提现已提交", "共 " + r.submitted() + " 张提现单，合计 " + r.amount() + " 元，已进入提现审批，请财务处理。");
            }
        } catch (RuntimeException failure) {
            log.error("[mkt] 自动提现任务异常", failure);
            alerts.alert("auto-withdraw-crash", "伙伴自动提现任务异常", String.valueOf(failure.getMessage()));
        }
    }

    /** 对所有正常且收款资料已审核的伙伴提交一次自动提现;每位伙伴独立事务,互不影响。 */
    public Result runOnce() {
        List<MktPromoter> list = promoters.selectList(new LambdaQueryWrapper<MktPromoter>()
                .eq(MktPromoter::getStatus, MktPromoterService.ST_NORMAL).eq(MktPromoter::getIdVerified, 1));
        int submitted = 0, skipped = 0, failed = 0;
        BigDecimal total = BigDecimal.ZERO;
        List<String> errors = new ArrayList<>();
        for (MktPromoter p : list) {
            try {
                BigDecimal amount = withdrawals.autoWithdrawAmount(p.getId());
                if (amount.compareTo(MktWithdrawalService.MIN_WITHDRAWAL) < 0 || withdrawals.hasOpenWithdrawal(p.getId()) || withdrawals.autoAppliedThisMonth(p.getId())) { skipped++; continue; }
                withdrawals.apply(p.getId(), amount, true);
                submitted++;
                total = total.add(amount);
            } catch (RuntimeException failure) {
                failed++;
                if (errors.size() < 20) errors.add(p.getName() + "(#" + p.getId() + ")：" + failure.getMessage());
                log.warn("[mkt] 自动提现失败 promoter={}: {}", p.getId(), failure.getMessage());
            }
        }
        log.info("[mkt] 自动提现：提交 {} 张，合计 {} 元，跳过 {}，失败 {}", submitted, total, skipped, failed);
        return new Result(submitted, total, skipped, failed, List.copyOf(errors));
    }
}
