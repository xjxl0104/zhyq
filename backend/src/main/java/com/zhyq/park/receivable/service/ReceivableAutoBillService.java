package com.zhyq.park.receivable.service;

import com.zhyq.park.receivable.dto.ReceivableGenerateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 登记表 → 账单的自动生成编排。
 *
 * <p>应收明细登记表是账单/收银台/逾期页的唯一源头,下游必须自动派生,不能靠人
 * 挨条点"生成账单"。三个自动入口共用这里:导入确认后立即生成、每日自愈任务兜底、
 * (保留的)手动单条生成。</p>
 *
 * <p>逐条调用 {@link ReceivablePlanService#generate}(各自独立事务、billingKey 幂等),
 * 单条失败只计数不中断 —— 一条绑定不全的登记不能拖垮整批确认。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceivableAutoBillService {

    private final ReceivablePlanService planService;

    /** 一批登记的自动生成汇总,前端确认提示与自愈任务日志共用 */
    public record AutoBillSummary(int registers, int inserted, int updated, int skipped, int failed) {}

    public AutoBillSummary generateFor(Collection<Long> registerIds) {
        List<Long> ids = registerIds == null ? List.of() : registerIds.stream()
                .filter(Objects::nonNull).distinct().toList();
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        for (Long id : ids) {
            try {
                ReceivableGenerateResult r = planService.generate(id);
                inserted += r.inserted();
                updated += r.updated();
                skipped += r.skipped();
            } catch (Exception e) {
                failed++;
                log.warn("[auto-bill] 登记表 {} 自动生成账单失败: {}", id, e.getMessage());
            }
        }
        return new AutoBillSummary(ids.size(), inserted, updated, skipped, failed);
    }
}
