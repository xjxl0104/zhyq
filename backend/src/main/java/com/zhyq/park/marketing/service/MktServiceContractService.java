package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.event.DomainEvent;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import com.zhyq.park.marketing.entity.MktCustomerGrade;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.MktServiceContractVersion;
import com.zhyq.park.marketing.mapper.MktCustomerGradeMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractVersionMapper;
import com.zhyq.park.marketing.service.MktCommissionService.CommissionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 云仓服务合同状态机(PARK-MKT-001 §2.4):
 * 1草稿 → 2待审核 → 3待客户签 → 4已生效 → 5履约中 → 6变更中 / 7到期 / 8终止;3 → 9作废。
 *
 * <p>每条迁移都是条件更新 {@code where id=? and status in (前态)},影响行数 0 抛 {@link BizException};
 * → 已生效时发 {@link DomainEvent.ServiceContractEffective},按评级签约奖生成路径 A 冻结佣金,
 * 园区签生成首期应收 {@code fin_bill(source='mkt_service')};直签只备案,不出客户账单。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MktServiceContractService {

    public static final int ST_DRAFT = 1;
    public static final int ST_PENDING_AUDIT = 2;
    public static final int ST_PENDING_SIGN = 3;
    public static final int ST_EFFECTIVE = 4;
    public static final int ST_PERFORMING = 5;
    public static final int ST_AMENDING = 6;
    public static final int ST_EXPIRED = 7;
    public static final int ST_TERMINATED = 8;
    public static final int ST_VOID = 9;

    public static final int SIGN_MODE_PARK = 1;
    public static final int SIGN_MODE_DIRECT = 2;
    public static final int SIGN_METHOD_OFFLINE = 1;

    static final String BILL_SOURCE = "mkt_service";
    static final String BILL_FEE_TYPE = "service";
    static final int BILL_DIRECTION_RECEIVABLE = 1;
    static final int BILL_STATUS_UNPAID = 3;
    private static final String BIZ_TYPE = "service_contract";
    private static final DateTimeFormatter NO_MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final MktServiceContractMapper contractMapper;
    private final MktServiceContractVersionMapper versionMapper;
    private final MktCustomerGradeMapper gradeMapper;
    private final CustomerMapper customerMapper;
    private final BillMapper billMapper;
    private final MktCommissionService commissionService;
    private final MktAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    /** 新建草稿:自动编号 CS-yyyyMM-xxxx,sign_mode 取客户设置(空则园区签),评级/推荐人从客户快照。 */
    @Transactional
    public MktServiceContract createDraft(MktServiceContract draft) {
        Customer customer = customerMapper.selectById(draft.getCustomerId());
        if (customer == null) {
            throw new BizException("客户不存在: " + draft.getCustomerId());
        }
        draft.setContractNo("CS-" + LocalDate.now().format(NO_MONTH) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        draft.setStatus(ST_DRAFT);
        draft.setContractVersion(1);
        if (draft.getSignMode() == null) {
            draft.setSignMode(customer.getSignMode() == null ? SIGN_MODE_PARK : customer.getSignMode());
        }
        if (!StringUtils.hasText(draft.getGrade())) {
            draft.setGrade(customer.getGrade());
        }
        if (draft.getPartnerId() == null) {
            draft.setPartnerId(customer.getReferrerId());
        }
        contractMapper.insert(draft);
        auditService.log("contract.create", BIZ_TYPE, draft.getId(), null);
        return draft;
    }

    @Transactional
    public void submit(Long id) {
        transition(id, ST_PENDING_AUDIT, "contract.submit", null, ST_DRAFT);
    }

    @Transactional
    public void audit(Long id, boolean pass, String reason) {
        if (pass) {
            transition(id, ST_PENDING_SIGN, "contract.audit.pass", reason, ST_PENDING_AUDIT);
        } else {
            if (!StringUtils.hasText(reason)) {
                throw new BizException("驳回必须填写原因");
            }
            int updated = contractMapper.update(null, new LambdaUpdateWrapper<MktServiceContract>()
                    .eq(MktServiceContract::getId, id)
                    .eq(MktServiceContract::getStatus, ST_PENDING_AUDIT)
                    .set(MktServiceContract::getStatus, ST_DRAFT)
                    .set(MktServiceContract::getAuditReason, reason));
            requireUpdated(updated, id);
            auditService.log("contract.audit.reject", BIZ_TYPE, id, reason);
        }
    }

    /** 线下签署:上传盖章件后置为已生效(签署完成即生效,§2.4)。 */
    @Transactional
    public void signOffline(Long id, String filesJson) {
        MktServiceContract c = require(id);
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, id)
                .eq(MktServiceContract::getStatus, ST_PENDING_SIGN)
                .set(MktServiceContract::getStatus, ST_EFFECTIVE)
                .set(MktServiceContract::getSignMethod, SIGN_METHOD_OFFLINE)
                .set(MktServiceContract::getSignedAt, LocalDateTime.now())
                .set(MktServiceContract::getEffectiveAt, LocalDateTime.now())
                .set(filesJson != null, MktServiceContract::getFiles, filesJson));
        requireUpdated(updated, id);
        auditService.log("contract.effect", BIZ_TYPE, id, "线下签署件上传");
        afterEffective(c);
    }

    /** 直签备案(§2.3a):云仓上传合同 → 园区审核通过 → 直接已生效。 */
    @Transactional
    public void effectDirect(Long id) {
        MktServiceContract c = require(id);
        if (c.getSignMode() == null || c.getSignMode() != SIGN_MODE_DIRECT) {
            throw new BizException("只有云仓直签合同可以备案生效");
        }
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, id)
                .in(MktServiceContract::getStatus, ST_DRAFT, ST_PENDING_AUDIT)
                .set(MktServiceContract::getStatus, ST_EFFECTIVE)
                .set(MktServiceContract::getEffectiveAt, LocalDateTime.now()));
        requireUpdated(updated, id);
        auditService.log("contract.effect.direct", BIZ_TYPE, id, "直签备案");
        afterEffective(c);
    }

    /** 首期款到账 → 履约中(由 PaymentReceived 监听或运营手动触发)。 */
    @Transactional
    public void startPerforming(Long id) {
        transition(id, ST_PERFORMING, "contract.perform", null, ST_EFFECTIVE);
    }

    @Transactional
    public void amend(Long id, String changeNote) {
        MktServiceContract c = require(id);
        transition(id, ST_AMENDING, "contract.amend", changeNote, ST_PERFORMING);
        snapshot(c, changeNote);
    }

    /** 变更完成:新版本生效,回到履约中。 */
    @Transactional
    public void amendDone(Long id) {
        MktServiceContract c = require(id);
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, id)
                .eq(MktServiceContract::getStatus, ST_AMENDING)
                .set(MktServiceContract::getStatus, ST_PERFORMING)
                .set(MktServiceContract::getContractVersion, c.getContractVersion() + 1));
        requireUpdated(updated, id);
        auditService.log("contract.amend.done", BIZ_TYPE, id, "版本 " + (c.getContractVersion() + 1));
    }

    @Transactional
    public void expire(Long id) {
        transition(id, ST_EXPIRED, "contract.expire", null, ST_PERFORMING);
    }

    /** 续签:到期 → 履约中,新起止 + 版本 +1。 */
    @Transactional
    public void renew(Long id, LocalDate newStart, LocalDate newEnd) {
        MktServiceContract c = require(id);
        if (newStart == null || newEnd == null || !newEnd.isAfter(newStart)) {
            throw new BizException("续签起止日期不合法");
        }
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, id)
                .eq(MktServiceContract::getStatus, ST_EXPIRED)
                .set(MktServiceContract::getStatus, ST_PERFORMING)
                .set(MktServiceContract::getStartDate, newStart)
                .set(MktServiceContract::getEndDate, newEnd)
                .set(MktServiceContract::getContractVersion, c.getContractVersion() + 1));
        requireUpdated(updated, id);
        snapshot(c, "续签");
        auditService.log("contract.renew", BIZ_TYPE, id, newStart + " ~ " + newEnd);
    }

    /** 终止(生效后任意阶段):未结算佣金作废;生效 clawback_days 内终止的已结算佣金扣回。 */
    @Transactional
    public void terminate(Long id, String reason, int clawbackDays) {
        if (!StringUtils.hasText(reason)) {
            throw new BizException("终止必须填写原因");
        }
        MktServiceContract c = require(id);
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, id)
                .in(MktServiceContract::getStatus, ST_EFFECTIVE, ST_PERFORMING, ST_AMENDING, ST_EXPIRED)
                .set(MktServiceContract::getStatus, ST_TERMINATED)
                .set(MktServiceContract::getTerminateReason, reason));
        requireUpdated(updated, id);
        auditService.log("contract.terminate", BIZ_TYPE, id, reason);
        boolean withinClawback = c.getEffectiveAt() != null
                && c.getEffectiveAt().plusDays(clawbackDays).isAfter(LocalDateTime.now());
        if (withinClawback) {
            commissionService.clawbackBySource(MktCommissionService.SOURCE_CONTRACT_BONUS, id, "合同终止:" + reason);
        }
    }

    /** 作废(待客户签阶段客户放弃 / 超期未签)。 */
    @Transactional
    public void voidContract(Long id, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BizException("作废必须填写原因");
        }
        transition(id, ST_VOID, "contract.void", reason, ST_DRAFT, ST_PENDING_AUDIT, ST_PENDING_SIGN);
    }

    // ---------------- 生效副作用 ----------------

    private void afterEffective(MktServiceContract c) {
        eventPublisher.publishEvent(new DomainEvent.ServiceContractEffective(
                c.getId(), c.getCustomerId(), c.getPartnerId(), c.getSignMode(), LocalDateTime.now()));
        createBonusCommission(c);
        if (c.getSignMode() != null && c.getSignMode() == SIGN_MODE_PARK) {
            createFirstBill(c);
        }
    }

    /** 路径 A 签约奖:评级 contract_bonus > 0 且有成交伙伴才生成(冻结,首期款到账解冻)。 */
    private void createBonusCommission(MktServiceContract c) {
        if (c.getPartnerId() == null || !StringUtils.hasText(c.getGrade())) {
            return;
        }
        MktCustomerGrade grade = gradeMapper.selectOne(new LambdaQueryWrapper<MktCustomerGrade>()
                .eq(MktCustomerGrade::getCode, c.getGrade()).last("limit 1"));
        if (grade == null || grade.getContractBonus() == null || grade.getContractBonus().signum() <= 0) {
            return;
        }
        BigDecimal bonus = grade.getContractBonus();
        commissionService.createAndSplit(new CommissionEvent(
                MktCommissionService.SOURCE_CONTRACT_BONUS, "BONUS-" + c.getId(), c.getId(),
                c.getCustomerId(), c.getPartnerId(), c.getGrade(), new BigDecimal("100"), bonus, bonus,
                LocalDateTime.now(), null, c.getProjectId(), c.getWarehouseId()));
    }

    /** 园区签首期应收:保证金 > 0 记保证金账单,否则记首月服务费(price_table.monthly,无则 0 元占位由财务改)。 */
    private void createFirstBill(MktServiceContract c) {
        BigDecimal amount = c.getDeposit() != null && c.getDeposit().signum() > 0 ? c.getDeposit() : BigDecimal.ZERO;
        Bill bill = new Bill();
        bill.setCode("MS-" + c.getContractNo());
        bill.setBillingKey("mkt_service:" + c.getId() + ":first");
        bill.setContractId(c.getId());
        bill.setProjectId(c.getProjectId());
        bill.setDirection(BILL_DIRECTION_RECEIVABLE);
        bill.setFeeType(BILL_FEE_TYPE);
        bill.setSource(BILL_SOURCE);
        bill.setStatus(BILL_STATUS_UNPAID);
        bill.setAmount(amount);
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setLateFee(BigDecimal.ZERO);
        bill.setPeriodStart(c.getStartDate());
        bill.setPeriodEnd(c.getStartDate());
        bill.setDueDate(c.getStartDate() == null ? LocalDate.now().plusDays(7) : c.getStartDate());
        bill.setRemark("云仓服务合同首期款(" + (amount.signum() > 0 ? "保证金" : "待财务补金额") + ")");
        try {
            billMapper.insert(bill);
        } catch (DuplicateKeyException dup) {
            log.info("[mkt] 合同 {} 首期账单已存在,跳过", c.getId());
        }
    }

    // ---------------- 工具 ----------------

    private void transition(Long id, int to, String action, String reason, Integer... from) {
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, id)
                .in(MktServiceContract::getStatus, (Object[]) from)
                .set(MktServiceContract::getStatus, to)
                .set(reason != null && to == ST_VOID, MktServiceContract::getTerminateReason, reason));
        requireUpdated(updated, id);
        auditService.log(action, BIZ_TYPE, id, reason);
    }

    private MktServiceContract require(Long id) {
        MktServiceContract c = contractMapper.selectById(id);
        if (c == null) {
            throw new BizException("服务合同不存在: " + id);
        }
        return c;
    }

    private static void requireUpdated(int updated, Long id) {
        if (updated == 0) {
            throw new BizException("合同 " + id + " 状态已变化,请刷新后重试");
        }
    }

    private void snapshot(MktServiceContract c, String note) {
        MktServiceContractVersion v = new MktServiceContractVersion();
        v.setContractId(c.getId());
        v.setVerNo(c.getContractVersion());
        v.setSnapshot(toJson(c));
        v.setChangedBy(MktAuditService.currentOperator());
        v.setChangeNote(note);
        v.setProjectId(c.getProjectId());
        versionMapper.insert(v);
    }

    private static String toJson(MktServiceContract c) {
        // 版本快照只需要人能看懂的关键字段,不引 ObjectMapper 以免与审计服务重复依赖
        return "{\"contractNo\":\"" + c.getContractNo() + "\",\"version\":" + c.getContractVersion()
                + ",\"startDate\":\"" + c.getStartDate() + "\",\"endDate\":\"" + c.getEndDate()
                + "\",\"priceTable\":" + (c.getPriceTable() == null ? "null" : c.getPriceTable())
                + ",\"deposit\":" + c.getDeposit() + ",\"warehouseId\":" + c.getWarehouseId() + "}";
    }
}
