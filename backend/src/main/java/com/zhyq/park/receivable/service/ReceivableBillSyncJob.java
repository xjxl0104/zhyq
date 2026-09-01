package com.zhyq.park.receivable.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.finance.service.LateFeeService;
import com.zhyq.park.receivable.entity.ReceivableRegister;
import com.zhyq.park.receivable.mapper.ReceivableRegisterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 登记表 → 下游的自愈定时任务。
 *
 * <p>启动后 2 分钟先跑一遍(部署即回填存量已确认登记的账单),之后每 24 小时一次:
 * ① 对全部已确认/已生效登记跑幂等生成(缺的补上、未收款的同步、已收款的跳过);
 * ② 随后重算逾期与滞纳金 —— 新补出的历史账期账单要立刻进入逾期口径,
 * 逾期页/驾驶舱不再依赖有人手动点"计算滞纳金"。</p>
 *
 * <p>这是"账单/收银台/逾期自动从登记表获取"的兜底:即使确认时的自动生成失败
 * (当时绑定不全等),补好数据后最迟一天内自愈,无需任何人工触发。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceivableBillSyncJob {

    private final ReceivableRegisterMapper registerMapper;
    private final ReceivableAutoBillService autoBillService;
    private final LateFeeService lateFeeService;

    /** 启动 2 分钟后首跑(避开启动高峰),此后每 24 小时自愈一次 */
    @Scheduled(initialDelay = 120_000, fixedDelay = 86_400_000)
    public void sync() {
        try {
            doSync();
        } catch (Exception e) {
            log.error("[auto-bill] 登记表自愈同步失败", e);
        }
    }

    /** 供手动触发/测试复用同一套逻辑 */
    public void doSync() {
        List<Long> ids = registerMapper.selectList(new LambdaQueryWrapper<ReceivableRegister>()
                        .in(ReceivableRegister::getStatus, "CONFIRMED", "ACTIVE"))
                .stream().map(ReceivableRegister::getId).toList();
        ReceivableAutoBillService.AutoBillSummary bills = autoBillService.generateFor(ids);
        int lateFees = lateFeeService.recalc();
        log.info("[auto-bill] 自愈同步完成:登记 {} 条,账单 新增{}/同步{}/跳过{}/失败{};逾期滞纳金更新 {} 条",
                bills.registers(), bills.inserted(), bills.updated(), bills.skipped(), bills.failed(), lateFees);
    }
}
