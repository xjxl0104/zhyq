package com.zhyq.park.marketing.finance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Fixed monthly rent is separate from per-order service bills; unique billing keys also cover the first period. */
@Service @RequiredArgsConstructor @Slf4j
public class MktFixedFeeBillingService {
    private final MktServiceContractMapper contracts;
    private final BillMapper bills;
    private final com.zhyq.park.marketing.service.MktContractTermsService terms;
    @org.springframework.transaction.annotation.Transactional
    public int generateDue(LocalDate today) {
        int count=0;
        var active=contracts.selectList(new LambdaQueryWrapper<MktServiceContract>().eq(MktServiceContract::getSignMode,1)
                .in(MktServiceContract::getStatus,4,5,6).in(MktServiceContract::getFeeModel,1,4).le(MktServiceContract::getStartDate,today)
                .orderByAsc(MktServiceContract::getId).last("FOR UPDATE"));
        for(var c:active) {
            try {
                if(c.getStartDate()==null||c.getEndDate()==null)continue;
                for(int month=0;month<1200;month++) {
                    LocalDate start=c.getStartDate().plusMonths(month), next=c.getStartDate().plusMonths(month+1L);
                    if(start.isAfter(today)||start.isAfter(c.getEndDate()))break;
                    String key="mkt_service:"+c.getId()+":rent:"+start;
                    if(bills.selectCount(new LambdaQueryWrapper<Bill>().eq(Bill::getBillingKey,key))>0)continue;
                    var applicable=terms.atDate(c,start);
                    if(applicable.getEndDate()!=null && start.isAfter(applicable.getEndDate()))continue;
                    BigDecimal amount=fixedAmount(applicable,start,next);
                    if(amount.signum()<=0)throw new BizException("固定月费须为正数");
                    Bill b=new Bill();b.setCode("MR-"+c.getId()+"-"+start);b.setBillingKey(key);b.setContractId(c.getId());b.setProjectId(c.getProjectId());
                    b.setSource("mkt_service");b.setDirection(1);b.setFeeType("租金");b.setStatus(3);b.setAmount(amount);
                    b.setPaidAmount(BigDecimal.ZERO);b.setLateFee(BigDecimal.ZERO);b.setPeriodStart(start);b.setPeriodEnd(periodEnd(applicable,next));b.setDueDate(start);
                    b.setRemark("云仓服务合同固定月费("+c.getContractNo()+")，不足完整周期按日折算");
                    try{bills.insert(b);count++;}catch(DuplicateKeyException ignored){/* another scheduler already created this exact period */}
                }
            }catch(BizException e){log.warn("固定月费生成失败 contract={} reason={}",c.getId(),e.getMessage());}
        }
        return count;
    }
    public static LocalDate periodEnd(MktServiceContract c,LocalDate nextPeriodStart) {
        LocalDate end=nextPeriodStart.minusDays(1);return c.getEndDate()!=null&&c.getEndDate().isBefore(end)?c.getEndDate():end;
    }
    public static BigDecimal fixedAmount(MktServiceContract c,LocalDate start,LocalDate nextPeriodStart) {
        try {
            var n=new ObjectMapper().readTree(c.getPriceTable());String field=Integer.valueOf(1).equals(c.getFeeModel())?"storage":"monthly";
            var value=n.get(field);if(value==null||!value.isNumber()||value.decimalValue().signum()<=0)throw new BizException("合同固定月费未填写");
            long whole=ChronoUnit.DAYS.between(start,nextPeriodStart),actual=ChronoUnit.DAYS.between(start,periodEnd(c,nextPeriodStart).plusDays(1));
            if(whole<=0||actual<=0)throw new BizException("月费账期不合法");
            return value.decimalValue().multiply(BigDecimal.valueOf(actual)).divide(BigDecimal.valueOf(whole),2,RoundingMode.HALF_UP);
        }catch(BizException e){throw e;}catch(Exception e){throw new BizException("固定月费单价表格式不合法");}
    }
}
