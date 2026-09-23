package com.zhyq.park.marketing.job;
import com.zhyq.park.marketing.finance.MktFixedFeeBillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
@Component @RequiredArgsConstructor @Slf4j
public class MktFixedFeeBillingJob {
    private final MktFixedFeeBillingService service;
    @Scheduled(initialDelay=360_000,fixedDelay=86_400_000)
    public void run(){try{int count=service.generateDue(LocalDate.now());if(count>0)log.info("生成云仓固定月费账单 {} 笔",count);}catch(Exception e){log.error("云仓固定月费账单任务失败",e);}}
}
