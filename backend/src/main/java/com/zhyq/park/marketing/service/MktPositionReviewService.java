package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktPosition;
import com.zhyq.park.marketing.entity.MktPositionHistory;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktReferralOrder;
import com.zhyq.park.marketing.mapper.MktPositionHistoryMapper;
import com.zhyq.park.marketing.mapper.MktPositionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktReferralOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 岗位晋升/降级(PARK-MKT-001 §2.11):门槛只含近 12 个月成交额 / 单数(不含人数,合规),
 * 晋升到满足的最高岗位;当前岗位 demote_enabled 且不满足其门槛则降一级;手动调岗 90 天内不自动降。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MktPositionReviewService {

    public static final String REASON_AUTO = "auto";
    public static final String REASON_MANUAL = "manual";
    private static final int MANUAL_GUARD_DAYS = 90;
    private static final int LOOKBACK_MONTHS = 12;

    private final MktPromoterMapper promoterMapper;
    private final MktPositionMapper positionMapper;
    private final MktPositionHistoryMapper historyMapper;
    private final MktReferralOrderMapper orderMapper;
    private final MktAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    /** 遍历正常伙伴复核一遍。返回变更人数。 */
    @Transactional
    public int reviewAll() {
        List<MktPosition> ladder = positionMapper.selectList(new LambdaQueryWrapper<MktPosition>()
                .eq(MktPosition::getStatus, 1).orderByAsc(MktPosition::getSort));
        if (ladder.isEmpty()) {
            return 0;
        }
        List<MktPromoter> all = promoterMapper.selectList(new LambdaQueryWrapper<MktPromoter>()
                .eq(MktPromoter::getStatus, 1));
        int changed = 0;
        for (MktPromoter p : all) {
            if (reviewOne(p, ladder)) {
                changed++;
            }
        }
        return changed;
    }

    boolean reviewOne(MktPromoter p, List<MktPosition> ladder) {
        LocalDateTime since = LocalDateTime.now().minusMonths(LOOKBACK_MONTHS);
        List<MktReferralOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<MktReferralOrder>()
                .eq(MktReferralOrder::getPromoterId, p.getId())
                .eq(MktReferralOrder::getStatus, MktCommissionService.ORDER_CONFIRMED)
                .ge(MktReferralOrder::getEventTime, since));
        BigDecimal amount = orders.stream().map(MktReferralOrder::getBaseAmount)
                .filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        int count = orders.size();

        MktPosition current = ladder.stream().filter(x -> x.getCode().equals(p.getPositionCode())).findFirst().orElse(null);
        if (current == null) {
            return false;
        }
        // 晋升:满足门槛的最高岗位(sort 更大)
        MktPosition target = ladder.stream()
                .filter(x -> x.getSort() > current.getSort() && meets(x, amount, count))
                .max(Comparator.comparing(MktPosition::getSort)).orElse(null);
        if (target != null) {
            change(p, target.getCode(), REASON_AUTO, "近12月成交额 " + amount + " / 单数 " + count);
            return true;
        }
        // 降级:当前岗位允许降、且不满足当前门槛、且 90 天内没有手动调岗
        boolean demotable = Integer.valueOf(1).equals(current.getDemoteEnabled()) && !meets(current, amount, count);
        if (demotable && !recentlyManuallyChanged(p.getId())) {
            MktPosition lower = ladder.stream().filter(x -> x.getSort() < current.getSort())
                    .max(Comparator.comparing(MktPosition::getSort)).orElse(null);
            if (lower != null) {
                change(p, lower.getCode(), REASON_AUTO, "不满足当前岗位门槛");
                return true;
            }
        }
        return false;
    }

    /** 后台手动调岗(写原因)。 */
    @Transactional
    public void changeManually(Long promoterId, String toCode, String note) {
        if (!StringUtils.hasText(note)) {
            throw new BizException("手动调岗必须填写原因");
        }
        MktPromoter p = promoterMapper.selectById(promoterId);
        if (p == null) {
            throw new BizException("伙伴不存在: " + promoterId);
        }
        if (positionMapper.selectCount(new LambdaQueryWrapper<MktPosition>().eq(MktPosition::getCode, toCode)) == 0) {
            throw new BizException("岗位不存在: " + toCode);
        }
        change(p, toCode, REASON_MANUAL, note);
    }

    private static boolean meets(MktPosition pos, BigDecimal amount, int count) {
        BigDecimal needAmount = pos.getPromoteAmount() == null ? BigDecimal.ZERO : pos.getPromoteAmount();
        int needOrders = pos.getPromoteOrders() == null ? 0 : pos.getPromoteOrders();
        return amount.compareTo(needAmount) >= 0 && count >= needOrders;
    }

    private boolean recentlyManuallyChanged(Long promoterId) {
        return historyMapper.selectCount(new LambdaQueryWrapper<MktPositionHistory>()
                .eq(MktPositionHistory::getPromoterId, promoterId)
                .eq(MktPositionHistory::getReason, REASON_MANUAL)
                .ge(MktPositionHistory::getCreateTime, LocalDateTime.now().minusDays(MANUAL_GUARD_DAYS))) > 0;
    }

    private void change(MktPromoter p, String toCode, String reason, String note) {
        String from = p.getPositionCode();
        if (toCode.equals(from)) {
            return;
        }
        int updated = promoterMapper.update(null, new LambdaUpdateWrapper<MktPromoter>()
                .eq(MktPromoter::getId, p.getId())
                .eq(MktPromoter::getPositionCode, from)
                .set(MktPromoter::getPositionCode, toCode)
                .set(MktPromoter::getPositionSince, LocalDateTime.now()));
        if (updated == 0) {
            throw new BizException("伙伴 " + p.getId() + " 岗位已被修改,请刷新");
        }
        MktPositionHistory h = new MktPositionHistory();
        h.setPromoterId(p.getId());
        h.setFromCode(from);
        h.setToCode(toCode);
        h.setReason(reason);
        h.setOperator(MktAuditService.currentOperator());
        h.setNote(note);
        h.setProjectId(p.getProjectId());
        historyMapper.insert(h);
        auditService.log("position.change", "promoter", p.getId(), note, from, toCode);
        eventPublisher.publishEvent(new DomainEvent.PositionChanged(p.getId(), from, toCode, reason, LocalDateTime.now()));
    }
}
