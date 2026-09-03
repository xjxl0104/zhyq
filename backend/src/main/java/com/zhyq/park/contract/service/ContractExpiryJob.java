package com.zhyq.park.contract.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.contract.entity.Contract;
import com.zhyq.park.contract.mapper.ContractMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 合同到期自动置状态。
 *
 * <p>此前没有任何机制把执行中的合同翻成「已到期」——状态只能靠人手工改,
 * 结果合同归档页的「已到期」页签永远是空的,合同真到期了也不会出现。</p>
 *
 * <p>每天跑一次:执行中(5) 且 结束日 < 今天 → 已到期(8)。条件更新一次性完成,
 * 重复执行幂等(第二次跑时这些合同已不是执行中,命中 0 行)。</p>
 *
 * <p><b>只翻状态,不动房源</b>:到期未必等于退租(常见续签、宽限期),
 * 自动把房源放回可租会误伤在租房间。房源释放仍走退租(terminate)这条明确的人工动作。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractExpiryJob {

    /** 状态:执行中 */
    private static final int ST_RUNNING = 5;
    /** 状态:已到期 */
    private static final int ST_EXPIRED = 8;

    private final ContractMapper contractMapper;

    /** 启动 3 分钟后首跑(错开账单自愈任务),此后每 24 小时一次 */
    @Scheduled(initialDelay = 180_000, fixedDelay = 86_400_000)
    public void sync() {
        try {
            int updated = expireDueContracts();
            if (updated > 0) {
                log.info("[contract-expiry] 合同到期状态同步:{} 份执行中合同已置为已到期", updated);
            }
        } catch (Exception e) {
            log.error("[contract-expiry] 合同到期状态同步失败", e);
        }
    }

    /** 供手动触发与测试复用;返回实际翻状态的合同数 */
    public int expireDueContracts() {
        return contractMapper.update(null, new LambdaUpdateWrapper<Contract>()
                .eq(Contract::getStatus, ST_RUNNING)
                .isNotNull(Contract::getEndDate)
                .lt(Contract::getEndDate, LocalDate.now())
                .set(Contract::getStatus, ST_EXPIRED));
    }
}
