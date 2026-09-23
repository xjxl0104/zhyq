package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import java.util.Objects;
import com.zhyq.park.marketing.entity.MktCustomerLock;
import com.zhyq.park.marketing.entity.MktPosition;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktCustomerLockMapper;
import com.zhyq.park.marketing.mapper.MktPositionMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 伙伴锁客(PARK-MKT-001 §2.2a 两段式报备):
 * 1预锁(7 天,专员确认)→ 2有效锁定(180 天,可延期一次 +90)→ 3已成交 / 4已释放(进公海)。
 *
 * <p>并发:一个客户同一时刻只能有一把活锁,靠 crm_customer_lock.active_key 生成列 + 唯一键兜底,
 * 后到者撞 {@link DuplicateKeyException} 直接报「该客户已被报备」,不需要 Redis。
 * 上限按岗位 lock_cap(含预锁);释放后原伙伴 lock_cooldown_days 内不可再锁同一客户。</p>
 */
@Service
@RequiredArgsConstructor
public class MktLockService {

    public static final int LS_PRELOCK = 1;
    public static final int LS_LOCKED = 2;
    public static final int LS_DEAL = 3;
    public static final int LS_RELEASED = 4;

    static final String MODULE = "marketing";
    private static final String BIZ_TYPE = "customer_lock";
    private static final int MAX_EXTENSIONS = 1;

    private final MktCustomerLockMapper lockMapper;
    private final MktPromoterMapper promoterMapper;
    private final MktPositionMapper positionMapper;
    private final BizSettings bizSettings;
    private final MktAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final CustomerMapper customerMapper;
    private final MktCustomerAssignmentService assignmentService;

    /** 报备 = 预锁。校验:伙伴正常、未超岗位上限、不在冷却期;唯一键兜底并发。 */
    @Transactional
    public MktCustomerLock prelock(Long customerId, Long promoterId) {
        Customer customer = requireCustomer(customerId);
        assignmentService.assertNoActiveContracts(customerId);
        if (customer.getReferrerId() != null && !Objects.equals(customer.getReferrerId(), promoterId)) {
            throw new BizException("报备伙伴与客户归属不一致");
        }
        MktPromoter p = promoterMapper.selectById(promoterId);
        if (p == null || p.getStatus() == null || p.getStatus() != 1) {
            throw new BizException("伙伴状态异常,不能报备");
        }
        validatePromoter(customer, p);
        checkCap(p);
        checkCooldown(customerId, promoterId);
        if (customer.getReferrerId() == null) setReferrer(customer, promoterId);

        MktCustomerLock lock = new MktCustomerLock();
        lock.setCustomerId(customerId);
        lock.setPromoterId(promoterId);
        lock.setStatus(LS_PRELOCK);
        lock.setPrelockUntil(LocalDateTime.now().plusDays(bizSettings.getInt(MODULE, "prelock_days", 7)));
        lock.setExtendedCount(0);
        lock.setProjectId(p.getProjectId());
        try {
            lockMapper.insert(lock);
        } catch (DuplicateKeyException dup) {
            throw new BizException("该客户已被报备");
        }
        publish(lock);
        auditService.log("lock.prelock", BIZ_TYPE, lock.getId(), null);
        return lock;
    }

    /** 专员确认「联系上·非重复·真实需求」→ 有效锁定 180 天。 */
    @Transactional
    public void confirm(Long lockId, String confirmedBy) {
        int days = bizSettings.getInt(MODULE, "lock_days", 180);
        int updated = lockMapper.update(null, new LambdaUpdateWrapper<MktCustomerLock>()
                .eq(MktCustomerLock::getId, lockId)
                .eq(MktCustomerLock::getStatus, LS_PRELOCK)
                .set(MktCustomerLock::getStatus, LS_LOCKED)
                .set(MktCustomerLock::getLockUntil, LocalDateTime.now().plusDays(days))
                .set(MktCustomerLock::getConfirmedBy, confirmedBy)
                .set(MktCustomerLock::getConfirmedAt, LocalDateTime.now()));
        requireUpdated(updated, lockId);
        auditService.log("lock.confirm", BIZ_TYPE, lockId, null);
        publish(require(lockId));
    }

    /** 延期一次 +90 天(运营审核,需近 60 天有到访/方案记录 —— 由调用方校验后传原因)。 */
    @Transactional
    public void extend(Long lockId, String reason) {
        requireReason(reason);
        MktCustomerLock lock = require(lockId);
        if (lock.getExtendedCount() != null && lock.getExtendedCount() >= MAX_EXTENSIONS) {
            throw new BizException("该锁定已延期过,不能再延");
        }
        int days = bizSettings.getInt(MODULE, "lock_extend_days", 90);
        LocalDateTime base = lock.getLockUntil() == null ? LocalDateTime.now() : lock.getLockUntil();
        int updated = lockMapper.update(null, new LambdaUpdateWrapper<MktCustomerLock>()
                .eq(MktCustomerLock::getId, lockId)
                .eq(MktCustomerLock::getStatus, LS_LOCKED)
                .eq(MktCustomerLock::getExtendedCount, lock.getExtendedCount() == null ? 0 : lock.getExtendedCount())
                .set(MktCustomerLock::getLockUntil, base.plusDays(days))
                .set(MktCustomerLock::getExtendedCount, (lock.getExtendedCount() == null ? 0 : lock.getExtendedCount()) + 1)
                .set(MktCustomerLock::getExtendReason, reason));
        requireUpdated(updated, lockId);
        auditService.log("lock.extend", BIZ_TYPE, lockId, reason);
    }

    /** 合同生效 → 已成交(归属固定到合同期)。 */
    @Transactional
    public void markDeal(Long customerId) {
        markDeal(customerId, requireCustomer(customerId).getReferrerId());
    }

    /** 合同生效 → 已成交,可校验合同伙伴与锁客伙伴一致后再落状态。 */
    @Transactional
    public void markDeal(Long customerId, Long expectedPromoterId) {
        Customer customer = requireCustomer(customerId);
        if (expectedPromoterId != null && !Objects.equals(expectedPromoterId, customer.getReferrerId())) {
            throw new BizException("合同伙伴与客户归属不一致,不能成交");
        }
        MktCustomerLock active = activeLockOf(customerId);
        if (active == null) {
            MktCustomerLock last = displayLockOf(customerId);
            if (last != null && Integer.valueOf(LS_DEAL).equals(last.getStatus())) {
                if (!Objects.equals(last.getPromoterId(), expectedPromoterId)) throw new BizException("成交锁归属不一致");
                return;
            }
            if (expectedPromoterId == null) return;
            // 报备保护期结束后正式签约仍需明确展示合同的归属，而不是显示未锁定。
            MktCustomerLock deal = new MktCustomerLock();
            deal.setCustomerId(customerId); deal.setPromoterId(expectedPromoterId);
            deal.setStatus(LS_DEAL); deal.setExtendedCount(0); deal.setProjectId(customer.getProjectId());
            lockMapper.insert(deal);
            auditService.log("lock.deal", BIZ_TYPE, deal.getId(), "合同生效确认归属");
            publish(deal);
            return;
        }
        if (!Objects.equals(expectedPromoterId, active.getPromoterId())) {
            throw new BizException("合同伙伴与锁客伙伴不一致,不能成交");
        }
        if (Integer.valueOf(LS_PRELOCK).equals(active.getStatus())) throw new BizException("请先确认客户报备归属，再签署生效合同");
        int updated = lockMapper.update(null, new LambdaUpdateWrapper<MktCustomerLock>()
                .eq(MktCustomerLock::getId, active.getId())
                .eq(MktCustomerLock::getStatus, LS_LOCKED)
                .set(MktCustomerLock::getStatus, LS_DEAL));
        requireUpdated(updated, active.getId());
        auditService.log("lock.deal", BIZ_TYPE, active.getId(), "合同生效");
        active.setStatus(LS_DEAL);
        publish(active);
    }

    /** 最后一份有效合同关闭后释放成交锁，保留历史供两端展示。 */
    @Transactional
    public void releaseDeal(Long customerId, String reason) {
        requireCustomer(customerId);
        assignmentService.assertNoActiveContracts(customerId);
        MktCustomerLock deal = displayLockOf(customerId);
        if (deal == null || !Integer.valueOf(LS_DEAL).equals(deal.getStatus())) return;
        requireUpdated(lockMapper.update(null, new LambdaUpdateWrapper<MktCustomerLock>()
                .eq(MktCustomerLock::getId, deal.getId()).eq(MktCustomerLock::getStatus, LS_DEAL)
                .set(MktCustomerLock::getStatus, LS_RELEASED).set(MktCustomerLock::getReleasedReason, reason)
                .set(MktCustomerLock::getReleasedBy, "contract").set(MktCustomerLock::getReleasedAt, LocalDateTime.now())), deal.getId());
        auditService.log("lock.contract.release", BIZ_TYPE, deal.getId(), reason);
        deal.setStatus(LS_RELEASED);
        publish(deal);
    }

    /** 释放(到期 / 客户拒绝 / 专员判无效 / 运营释放)→ 公海。 */
    @Transactional
    public void release(Long lockId, String reason, String releasedBy) {
        requireReason(reason);
        int updated = lockMapper.update(null, new LambdaUpdateWrapper<MktCustomerLock>()
                .eq(MktCustomerLock::getId, lockId)
                .in(MktCustomerLock::getStatus, LS_PRELOCK, LS_LOCKED)
                .set(MktCustomerLock::getStatus, LS_RELEASED)
                .set(MktCustomerLock::getReleasedReason, reason)
                .set(MktCustomerLock::getReleasedBy, releasedBy)
                .set(MktCustomerLock::getReleasedAt, LocalDateTime.now()));
        requireUpdated(updated, lockId);
        auditService.log("lock.release", BIZ_TYPE, lockId, reason);
        publish(require(lockId));
    }

    /** 运营转移给另一位伙伴:释放旧锁 + 为新伙伴建有效锁定(跳过预锁),一个事务。 */
    @Transactional
    public MktCustomerLock transfer(Long lockId, Long toPromoterId, String reason) {
        requireReason(reason);
        MktCustomerLock old = require(lockId);
        Customer customer = requireCustomer(old.getCustomerId());
        assignmentService.assertNoActiveContracts(old.getCustomerId());
        if (!Objects.equals(customer.getReferrerId(), old.getPromoterId())) throw new BizException("锁定与客户归属不一致，请先核对");
        if (Objects.equals(old.getPromoterId(), toPromoterId)) throw new BizException("请选择其他伙伴");
        release(lockId, "转移:" + reason, MktAuditService.currentOperator());
        MktPromoter to = promoterMapper.selectById(toPromoterId);
        if (to == null || to.getStatus() == null || to.getStatus() != 1) {
            throw new BizException("目标伙伴状态异常");
        }
        validatePromoter(customer, to);
        checkCap(to);
        setReferrer(customer, toPromoterId);
        MktCustomerLock lock = new MktCustomerLock();
        lock.setCustomerId(old.getCustomerId());
        lock.setPromoterId(toPromoterId);
        lock.setStatus(LS_LOCKED);
        lock.setLockUntil(old.getLockUntil() == null
                ? LocalDateTime.now().plusDays(bizSettings.getInt(MODULE, "lock_days", 180)) : old.getLockUntil());
        lock.setConfirmedBy(MktAuditService.currentOperator());
        lock.setConfirmedAt(LocalDateTime.now());
        lock.setExtendedCount(old.getExtendedCount());
        lock.setProjectId(old.getProjectId());
        lockMapper.insert(lock);
        auditService.log("lock.transfer", BIZ_TYPE, lock.getId(), reason, old.getPromoterId(), toPromoterId);
        publish(lock);
        return lock;
    }

    /** LockExpireJob:预锁超时未确认 → 释放;有效锁定到期 → 释放。返回释放条数。 */
    @Transactional
    public int expire(LocalDateTime now) {
        List<MktCustomerLock> due = lockMapper.selectList(new LambdaQueryWrapper<MktCustomerLock>()
                .and(w -> w.eq(MktCustomerLock::getStatus, LS_PRELOCK).le(MktCustomerLock::getPrelockUntil, now))
                .or(w -> w.eq(MktCustomerLock::getStatus, LS_LOCKED).le(MktCustomerLock::getLockUntil, now)));
        int n = 0;
        for (MktCustomerLock l : due) {
            int updated = lockMapper.update(null, new LambdaUpdateWrapper<MktCustomerLock>()
                    .eq(MktCustomerLock::getId, l.getId())
                    .eq(MktCustomerLock::getStatus, l.getStatus())
                    .set(MktCustomerLock::getStatus, LS_RELEASED)
                    .set(MktCustomerLock::getReleasedReason, l.getStatus() == LS_PRELOCK ? "预锁到期未确认" : "锁定期满未成交")
                    .set(MktCustomerLock::getReleasedBy, "system")
                    .set(MktCustomerLock::getReleasedAt, now));
            if (updated == 1) {
                n++;
                publish(require(l.getId()));
            }
        }
        return n;
    }

    /** 到期前 N 天需要提醒的有效锁定。 */
    public List<MktCustomerLock> expiringWithin(LocalDateTime now, int days) {
        return lockMapper.selectList(new LambdaQueryWrapper<MktCustomerLock>()
                .eq(MktCustomerLock::getStatus, LS_LOCKED)
                .between(MktCustomerLock::getLockUntil, now, now.plusDays(days)));
    }

    public MktCustomerLock activeLockOf(Long customerId) {
        return lockMapper.selectOne(new LambdaQueryWrapper<MktCustomerLock>()
                .eq(MktCustomerLock::getCustomerId, customerId)
                .in(MktCustomerLock::getStatus, LS_PRELOCK, LS_LOCKED)
                .last("limit 1"));
    }

    /** 展示最新一条锁客历史，成交和释放状态不得退化为“未锁定”。 */
    public MktCustomerLock displayLockOf(Long customerId) {
        return lockMapper.selectOne(new LambdaQueryWrapper<MktCustomerLock>()
                .eq(MktCustomerLock::getCustomerId, customerId).orderByDesc(MktCustomerLock::getId).last("limit 1"));
    }

    private Customer requireCustomer(Long customerId) {
        Customer customer = customerMapper.selectForUpdate(customerId);
        if (customer == null) throw new BizException("客户不存在");
        return customer;
    }

    private void setReferrer(Customer customer, Long promoterId) {
        int updated = customerMapper.update(null, new LambdaUpdateWrapper<Customer>()
                .eq(Customer::getId, customer.getId()).eq(customer.getVersion() != null, Customer::getVersion, customer.getVersion())
                .set(Customer::getReferrerId, promoterId).setSql("version = version + 1"));
        if (updated != 1) throw new BizException("客户归属已变化，请刷新后重试");
    }

    private void validatePromoter(Customer customer, MktPromoter promoter) {
        if (customer.getProjectId() != null && promoter.getProjectId() != null && !customer.getProjectId().equals(promoter.getProjectId())) {
            throw new BizException("不能跨园区调整客户归属");
        }
        if (StringUtils.hasText(customer.getPhone()) && customer.getPhone().equals(promoter.getPhone())) {
            throw new BizException("不能将伙伴自己的业务绑定给该伙伴");
        }
    }

    // ---------------- 内部 ----------------

    private void checkCap(MktPromoter p) {
        MktPosition pos = positionMapper.selectOne(new LambdaQueryWrapper<MktPosition>()
                .eq(MktPosition::getCode, p.getPositionCode()).last("limit 1"));
        int cap = pos == null || pos.getLockCap() == null ? 50 : pos.getLockCap();
        Long used = lockMapper.selectCount(new LambdaQueryWrapper<MktCustomerLock>()
                .eq(MktCustomerLock::getPromoterId, p.getId())
                .in(MktCustomerLock::getStatus, LS_PRELOCK, LS_LOCKED));
        if (used != null && used >= cap) {
            throw new BizException("已达岗位锁定上限 " + cap + " 个,请先释放或等到期");
        }
    }

    private void checkCooldown(Long customerId, Long promoterId) {
        int cooldown = bizSettings.getInt(MODULE, "lock_cooldown_days", 30);
        Long recent = lockMapper.selectCount(new LambdaQueryWrapper<MktCustomerLock>()
                .eq(MktCustomerLock::getCustomerId, customerId)
                .eq(MktCustomerLock::getPromoterId, promoterId)
                .eq(MktCustomerLock::getStatus, LS_RELEASED)
                .ge(MktCustomerLock::getReleasedAt, LocalDateTime.now().minusDays(cooldown)));
        if (recent != null && recent > 0) {
            throw new BizException("该客户释放后 " + cooldown + " 天内不可再次报备");
        }
    }

    private MktCustomerLock require(Long id) {
        MktCustomerLock l = lockMapper.selectById(id);
        if (l == null) {
            throw new BizException("锁定记录不存在: " + id);
        }
        return l;
    }

    private void publish(MktCustomerLock l) {
        eventPublisher.publishEvent(new DomainEvent.CustomerLockChanged(
                l.getId(), l.getCustomerId(), l.getPromoterId(), l.getStatus(), LocalDateTime.now()));
    }

    private static void requireUpdated(int updated, Long id) {
        if (updated == 0) {
            throw new BizException("锁定 " + id + " 状态已变化,请刷新后重试");
        }
    }

    private static void requireReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BizException("必须填写原因");
        }
    }
}
