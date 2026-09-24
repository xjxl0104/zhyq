package com.zhyq.park.marketing.listener;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.marketing.service.MktPartnerNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
@Component
@RequiredArgsConstructor
public class MktPartnerNoticeListener {
    private final MktPartnerNoticeService notices;
    @EventListener
    public void lock(DomainEvent.CustomerLockChanged e) {
        String status = switch(e.status()) { case 1 -> "预锁"; case 2 -> "有效锁定"; case 3 -> "已成交"; default -> "已释放"; };
        notices.push(e.promoterId(),"客户归属更新","客户编号"+e.customerId()+"的归属状态已更新为"+status+"，可在我的客户查看。");
    }
    @EventListener
    public void commission(DomainEvent.CommissionUnfrozen e) {
        notices.push(e.promoterId(),"佣金已解冻","流水"+e.commissionId()+"已解冻，金额"+e.amount()+"元，待园区结算后可申请提现。");
    }
    @EventListener
    public void position(DomainEvent.PositionChanged e) {
        notices.push(e.promoterId(),"岗位变更","您的岗位已从"+e.fromCode()+"调整为"+e.toCode()+"，请到我的岗位查看。");
    }
}
