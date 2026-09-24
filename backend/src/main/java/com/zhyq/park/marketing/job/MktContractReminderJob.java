package com.zhyq.park.marketing.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.notify.NotificationService;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.service.MktServiceContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 服务合同提醒(PARK-MKT-001 §2.4 / §5.5):每日 09:00
 * 到期前 30 / 7 天提醒;待客户签超 7 天提醒;已过 end_date 的履约中合同自动置为到期。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MktContractReminderJob {

    private static final int[] REMIND_DAYS = {30, 7};
    private static final int PENDING_SIGN_DAYS = 7;

    private final MktServiceContractMapper contractMapper;
    private final MktServiceContractService contractService;
    private final com.zhyq.park.marketing.service.MktPartnerNoticeService notificationService;

    @Scheduled(cron = "0 0 9 * * ?")
    public void run() {
        try {
            LocalDate today = LocalDate.now();
            for (int d : REMIND_DAYS) {
                for (MktServiceContract c : performingEndingOn(today.plusDays(d))) {
                    notificationService.push(c.getPartnerId(), "服务合同即将到期",
                            "合同 " + c.getContractNo() + " 将于 " + c.getEndDate() + " 到期");
                }
            }
            for (MktServiceContract c : contractMapper.selectList(new LambdaQueryWrapper<MktServiceContract>()
                    .eq(MktServiceContract::getStatus, MktServiceContractService.ST_PENDING_SIGN)
                    .le(MktServiceContract::getUpdateTime, today.minusDays(PENDING_SIGN_DAYS).atStartOfDay()))) {
                notificationService.push(c.getPartnerId(), "服务合同待签超时",
                        "合同 " + c.getContractNo() + " 待客户签署已超 " + PENDING_SIGN_DAYS + " 天");
            }
            for (MktServiceContract c : contractMapper.selectList(new LambdaQueryWrapper<MktServiceContract>()
                    .in(MktServiceContract::getStatus, MktServiceContractService.ST_EFFECTIVE, MktServiceContractService.ST_PERFORMING, MktServiceContractService.ST_AMENDING)
                    .lt(MktServiceContract::getEndDate, today))) {
                contractService.expire(c.getId());
            }
        } catch (Exception e) {
            log.error("[mkt] ContractReminderJob 失败", e);
        }
    }

    private List<MktServiceContract> performingEndingOn(LocalDate day) {
        return contractMapper.selectList(new LambdaQueryWrapper<MktServiceContract>()
                .in(MktServiceContract::getStatus, MktServiceContractService.ST_EFFECTIVE, MktServiceContractService.ST_PERFORMING, MktServiceContractService.ST_AMENDING)
                .eq(MktServiceContract::getEndDate, day));
    }
}
