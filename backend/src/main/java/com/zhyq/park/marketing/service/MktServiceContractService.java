package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.zhyq.park.marketing.finance.MktFixedFeeBillingService;
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
import java.util.Objects;
import java.util.Set;

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
    static final String BILL_FEE_TYPE_RENT = "租金";
    static final String BILL_FEE_TYPE_DEPOSIT = "保证金";
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
    private final MktLockService lockService;
    private final MktAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final MktCustomerAssignmentService assignmentService;
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final Set<String> PRICE_KEYS = Set.of("perOrder", "perItem", "storage", "monthly");

    /** 新建草稿:自动编号 CS-yyyyMM-xxxx,sign_mode 取客户设置(空则园区签),评级/推荐人从客户快照。 */
    @Transactional
    public MktServiceContract createDraft(MktServiceContract draft) {
        Customer customer = assignmentService.validateContractWarehouse(draft);
        if (customer == null) {
            throw new BizException("客户不存在: " + draft.getCustomerId());
        }
        draft.setContractNo("CS-" + LocalDate.now().format(NO_MONTH) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        draft.setStatus(ST_DRAFT);
        draft.setContractVersion(1);
        draft.setTermsEffectiveFrom(null);
        if (draft.getSignMode() == null) {
            draft.setSignMode(customer.getSignMode() == null ? SIGN_MODE_PARK : customer.getSignMode());
        }
        if (draft.getAutoRenew() == null) draft.setAutoRenew(0);
        validateTerms(draft);
        if (StringUtils.hasText(draft.getFiles())) validateFiles(draft.getFiles(), false);
        draft.setId(null);
        draft.setProjectId(customer.getProjectId());
        draft.setEffectiveAt(null); draft.setSignedAt(null); draft.setSignMethod(null);
        draft.setAuditReason(null); draft.setTerminateReason(null);
        // 佣金归属与比例一律以客户档案为准,不接受调用方传入(防越权指定收款伙伴 / 抬评级)
        draft.setGrade(customer.getGrade());
        draft.setPartnerId(customer.getReferrerId());
        validateEconomics(draft);
        contractMapper.insert(draft);
        auditService.log("contract.create", BIZ_TYPE, draft.getId(), null);
        return draft;
    }

    /** 草稿编辑只允许改商业条款和附件，不允许通过 PUT 改合同归属或状态。 */
    @Transactional
    public void updateDraft(MktServiceContract draft) {
        MktServiceContract current = require(draft.getId());
        if (!Integer.valueOf(ST_DRAFT).equals(current.getStatus())) throw new BizException("仅草稿合同可编辑");
        if (!Objects.equals(current.getCustomerId(), draft.getCustomerId())
                || !Objects.equals(current.getWarehouseId(), draft.getWarehouseId())
                || !Objects.equals(current.getSignMode(), draft.getSignMode())) {
            throw new BizException("客户、承接云仓和签约方式不可在草稿编辑中更换，请重新建合同");
        }
        assignmentService.validateContractWarehouse(current);
        if (draft.getAutoRenew() == null) draft.setAutoRenew(current.getAutoRenew() == null ? 0 : current.getAutoRenew());
        validateTerms(draft);
        draft.setGrade(current.getGrade());
        validateEconomics(draft);
        if (StringUtils.hasText(draft.getFiles())) validateFiles(draft.getFiles(), false);
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, current.getId()).eq(MktServiceContract::getStatus, ST_DRAFT)
                .set(MktServiceContract::getServiceType, draft.getServiceType()).set(MktServiceContract::getFeeModel, draft.getFeeModel())
                .set(MktServiceContract::getPriceTable, draft.getPriceTable()).set(MktServiceContract::getDeposit, draft.getDeposit())
                .set(MktServiceContract::getStartDate, draft.getStartDate()).set(MktServiceContract::getEndDate, draft.getEndDate())
                .set(MktServiceContract::getPayCycle, draft.getPayCycle()).set(MktServiceContract::getAutoRenew, draft.getAutoRenew())
                .set(MktServiceContract::getTemplateId, draft.getTemplateId()).set(MktServiceContract::getFiles, draft.getFiles())
                .set(MktServiceContract::getRemark, draft.getRemark()));
        requireUpdated(updated, current.getId());
        auditService.log("contract.draft.update", BIZ_TYPE, current.getId(), null);
    }

    @Transactional
    public void submit(Long id) {
        MktServiceContract c = require(id);
        if (Integer.valueOf(ST_PENDING_AUDIT).equals(c.getStatus())) return;
        assignmentService.validateContractWarehouse(c);
        validateTerms(c);
        validateEconomics(c);
        if (Integer.valueOf(SIGN_MODE_DIRECT).equals(c.getSignMode())) validateFiles(c.getFiles(), true);
        transition(id, ST_PENDING_AUDIT, "contract.submit", null, ST_DRAFT);
    }

    @Transactional
    public void audit(Long id, boolean pass, String reason) {
        if (pass) {
            MktServiceContract c = require(id);
            if (Integer.valueOf(SIGN_MODE_DIRECT).equals(c.getSignMode())) { effectDirect(id); return; }
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
        validateFiles(filesJson, true);
        if (isActive(c)) return;
        assignmentService.validateContractWarehouse(c);
        validateTerms(c);
        validateEconomics(c);
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, id)
                .eq(MktServiceContract::getStatus, ST_PENDING_SIGN)
                .set(MktServiceContract::getStatus, ST_EFFECTIVE)
                .set(MktServiceContract::getSignMethod, SIGN_METHOD_OFFLINE)
                .set(MktServiceContract::getSignedAt, LocalDateTime.now())
                .set(MktServiceContract::getEffectiveAt, LocalDateTime.now())
                .set(filesJson != null, MktServiceContract::getFiles, filesJson));
        requireUpdated(updated, id);
        c.setFiles(filesJson);
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
        if (isActive(c)) return;
        if (!Integer.valueOf(ST_PENDING_AUDIT).equals(c.getStatus())) throw new BizException("直签合同须先提交审核，才能备案生效");
        assignmentService.validateContractWarehouse(c);
        validateTerms(c);
        validateEconomics(c);
        validateFiles(c.getFiles(), true);
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, id)
                .eq(MktServiceContract::getStatus, ST_PENDING_AUDIT)
                .set(MktServiceContract::getStatus, ST_EFFECTIVE)
                .set(MktServiceContract::getEffectiveAt, LocalDateTime.now()));
        requireUpdated(updated, id);
        auditService.log("contract.effect.direct", BIZ_TYPE, id, "直签备案");
        afterEffective(c);
    }

    /** 首期款到账 → 履约中(由 PaymentReceived 监听或运营手动触发)。 */
    @Transactional
    public void startPerforming(Long id) {
        MktServiceContract current = require(id);
        // 结清历史欠款不得复活到期/终止合同，重复到账也不应回滚真实收款。
        if (current.getStatus() != null && Set.of(ST_PERFORMING, ST_AMENDING, ST_EXPIRED, ST_TERMINATED, ST_VOID).contains(current.getStatus())) return;
        transition(id, ST_PERFORMING, "contract.perform", null, ST_EFFECTIVE);
    }

    @Transactional
    public void amend(Long id, String changeNote) {
        MktServiceContract c = require(id);
        if (!StringUtils.hasText(changeNote)) throw new BizException("请填写合同变更原因");
        if (c.getTermsEffectiveFrom() != null && c.getTermsEffectiveFrom().isAfter(LocalDate.now()))
            throw new BizException("上一份变更条款尚未生效，请在生效后再变更");
        transition(id, ST_AMENDING, "contract.amend", changeNote, ST_PERFORMING);
    }

    /** 变更完成:新版本生效,回到履约中。 */
    @Transactional
    public void amendDone(Long id) { throw new BizException("请填写变更条款、生效日期并上传签署件"); }

    public record Amendment(String priceTable, LocalDate endDate, Integer payCycle, String files,
                            String remark, LocalDate effectiveDate) {}

    @Transactional
    public void amendDone(Long id, Amendment amendment) {
        // 与固定月费出账共享合同锁，防止校验后另一个事务按旧条款生成新账单。
        MktServiceContract c = contractMapper.selectForUpdate(id);
        if (c == null) throw new BizException("合同不存在");
        if (!Integer.valueOf(ST_AMENDING).equals(c.getStatus())) throw new BizException("仅变更中的合同可完成条款变更");
        if (amendment == null || amendment.effectiveDate() == null || amendment.effectiveDate().isBefore(LocalDate.now()))
            throw new BizException("变更生效日期不得早于今天");
        if (amendment.effectiveDate().isBefore(c.getStartDate()) || amendment.endDate() == null
                || amendment.endDate().isBefore(amendment.effectiveDate())) throw new BizException("生效日期须位于新的合同期限内");
        MktServiceContract next = new MktServiceContract();
        org.springframework.beans.BeanUtils.copyProperties(c, next);
        next.setPriceTable(amendment.priceTable()); next.setEndDate(amendment.endDate());
        next.setPayCycle(amendment.payCycle()); next.setFiles(amendment.files()); next.setRemark(amendment.remark());
        next.setTermsEffectiveFrom(amendment.effectiveDate());
        validateTerms(next); validateEconomics(next); validateFiles(next.getFiles(), true);
        if (Integer.valueOf(1).equals(c.getSignMode()) && (Integer.valueOf(1).equals(c.getFeeModel()) || Integer.valueOf(4).equals(c.getFeeModel()))) {
            long months = java.time.temporal.ChronoUnit.MONTHS.between(java.time.YearMonth.from(c.getStartDate()), java.time.YearMonth.from(amendment.effectiveDate()));
            if (months < 0 || !c.getStartDate().plusMonths(months).equals(amendment.effectiveDate()))
                throw new BizException("固定月费变更须从合同起始日对应的月计费周期开始生效");
        }
        if (billMapper.selectCount(new LambdaQueryWrapper<Bill>().eq(Bill::getContractId, id).eq(Bill::getSource, BILL_SOURCE)
                .and(q -> q.ge(Bill::getPeriodEnd, amendment.effectiveDate()).or().gt(Bill::getPeriodEnd, next.getEndDate()))) > 0)
            throw new BizException("生效日期或新期限涉及已生成账单，请选择下一个未出账周期");
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, id)
                .eq(MktServiceContract::getStatus, ST_AMENDING)
                .eq(MktServiceContract::getContractVersion, c.getContractVersion())
                .set(MktServiceContract::getStatus, ST_PERFORMING)
                .set(MktServiceContract::getPriceTable, next.getPriceTable()).set(MktServiceContract::getEndDate, next.getEndDate())
                .set(MktServiceContract::getPayCycle, next.getPayCycle()).set(MktServiceContract::getFiles, next.getFiles())
                .set(MktServiceContract::getRemark, next.getRemark()).set(MktServiceContract::getTermsEffectiveFrom, next.getTermsEffectiveFrom())
                .set(MktServiceContract::getContractVersion, c.getContractVersion() + 1));
        requireUpdated(updated, id);
        snapshot(c, "条款变更，生效日 " + amendment.effectiveDate());
        auditService.log("contract.amend.done", BIZ_TYPE, id, "版本 " + (c.getContractVersion() + 1)
                + "，生效日 " + amendment.effectiveDate(), toJson(c), toJson(next));
    }

    @Transactional
    public void cancelAmend(Long id, String reason) {
        if (!StringUtils.hasText(reason)) throw new BizException("请填写取消变更原因");
        transition(id, ST_PERFORMING, "contract.amend.cancel", reason, ST_AMENDING);
    }

    @Transactional
    public void expire(Long id) {
        MktServiceContract c = require(id);
        if (Integer.valueOf(ST_EXPIRED).equals(c.getStatus())) return;
        lockCustomer(c);
        if (c.getEndDate() == null || !c.getEndDate().isBefore(LocalDate.now())) throw new BizException("合同尚未到期");
        transition(id, ST_EXPIRED, "contract.expire", null, ST_EFFECTIVE, ST_PERFORMING, ST_AMENDING);
        closeCustomer(c, "合同到期");
    }

    /** 续签:到期 → 履约中,新起止 + 版本 +1。 */
    @Transactional
    public void renew(Long id, LocalDate newStart, LocalDate newEnd) {
        MktServiceContract c = require(id);
        if (newStart == null || newEnd == null || !newEnd.isAfter(newStart)) {
            throw new BizException("续签起止日期不合法");
        }
        if (Integer.valueOf(ST_PERFORMING).equals(c.getStatus()) && newStart.equals(c.getStartDate()) && newEnd.equals(c.getEndDate())) return;
        assignmentService.validateContractWarehouse(c);
        if (c.getEndDate() != null && !newStart.isAfter(c.getEndDate())) throw new BizException("续签开始日期须晚于原合同结束日期");
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, id)
                .eq(MktServiceContract::getStatus, ST_EXPIRED)
                .set(MktServiceContract::getStatus, ST_PERFORMING)
                .set(MktServiceContract::getStartDate, newStart)
                .set(MktServiceContract::getTermsEffectiveFrom, newStart)
                .set(MktServiceContract::getEndDate, newEnd)
                .set(MktServiceContract::getContractVersion, c.getContractVersion() + 1));
        requireUpdated(updated, id);
        assignmentService.contractEffective(c);
        lockService.markDeal(c.getCustomerId(), c.getPartnerId());
        snapshot(c, "续签");
        auditService.log("contract.renew", BIZ_TYPE, id, newStart + " ~ " + newEnd);
    }

    /** 终止(生效后任意阶段):未结算佣金作废;生效 clawback_days 内终止的已结算佣金扣回。 */
    @Transactional
    public void terminate(Long id, String reason, int clawbackDays) {
        if (!StringUtils.hasText(reason)) {
            throw new BizException("终止必须填写原因");
        }
        if (clawbackDays < 0) throw new BizException("扣回期限不能为负数");
        MktServiceContract c = require(id);
        if (Integer.valueOf(ST_TERMINATED).equals(c.getStatus())) return;
        lockCustomer(c);
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<MktServiceContract>()
                .eq(MktServiceContract::getId, id)
                .in(MktServiceContract::getStatus, ST_EFFECTIVE, ST_PERFORMING, ST_AMENDING)
                .set(MktServiceContract::getStatus, ST_TERMINATED)
                .set(MktServiceContract::getTerminateReason, reason));
        requireUpdated(updated, id);
        closeCustomer(c, "合同终止");
        auditService.log("contract.terminate", BIZ_TYPE, id, reason);
        commissionService.voidUnsettledBySource(MktCommissionService.SOURCE_CONTRACT_BONUS, id, "合同终止:" + reason);
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
        transition(id, ST_VOID, "contract.void", reason, ST_PENDING_SIGN);
    }

    // ---------------- 生效副作用 ----------------

    private void afterEffective(MktServiceContract c) {
        // 合同生效与锁客成交必须在同一事务内完成；锁客更新失败时合同生效一起回滚。
        assignmentService.contractEffective(c);
        lockService.markDeal(c.getCustomerId(), c.getPartnerId());
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
        commissionService.createAndSplitInTransaction(new CommissionEvent(
                MktCommissionService.SOURCE_CONTRACT_BONUS, "BONUS-" + c.getId(), c.getId(),
                c.getCustomerId(), c.getPartnerId(), c.getGrade(), new BigDecimal("100"), bonus, bonus,
                LocalDateTime.now(), null, c.getProjectId(), c.getWarehouseId()));
    }

    /** 园区签首期应收:保证金优先；无保证金只对明确固定月费生成账单，按量计费等实际业务发生。 */
    private void createFirstBill(MktServiceContract c) {
        boolean deposit = c.getDeposit() != null && c.getDeposit().signum() > 0;
        BigDecimal amount = deposit ? c.getDeposit() : fixedFirstAmount(c);
        if (amount.signum() <= 0) return;
        Bill bill = new Bill();
        bill.setCode("MS-" + c.getContractNo());
        bill.setBillingKey("mkt_service:" + c.getId() + (deposit ? ":first" : ":rent:" + c.getStartDate()));
        bill.setContractId(c.getId());
        bill.setProjectId(c.getProjectId());
        bill.setDirection(BILL_DIRECTION_RECEIVABLE);
        bill.setFeeType(deposit ? BILL_FEE_TYPE_DEPOSIT : BILL_FEE_TYPE_RENT);
        bill.setSource(BILL_SOURCE);
        bill.setStatus(BILL_STATUS_UNPAID);
        bill.setAmount(amount);
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setLateFee(BigDecimal.ZERO);
        bill.setPeriodStart(c.getStartDate());
        bill.setPeriodEnd(deposit ? c.getStartDate() : MktFixedFeeBillingService.periodEnd(c, c.getStartDate().plusMonths(1)));
        bill.setDueDate(c.getStartDate() == null ? LocalDate.now().plusDays(7) : c.getStartDate());
        bill.setRemark("云仓服务合同首期款(" + (deposit ? "保证金" : "首月固定服务费") + ")");
        try {
            billMapper.insert(bill);
        } catch (DuplicateKeyException dup) {
            log.info("[mkt] 合同 {} 首期账单已存在,跳过", c.getId());
        }
    }

    private static BigDecimal fixedFirstAmount(MktServiceContract c) {
        if (!Integer.valueOf(1).equals(c.getFeeModel()) && !Integer.valueOf(4).equals(c.getFeeModel())) {
            return BigDecimal.ZERO;
        }
        return MktFixedFeeBillingService.fixedAmount(c, c.getStartDate(), c.getStartDate().plusMonths(1));
    }

    /** 按量报价必须覆盖承接成本及佣金；出库时仍按实际件数复核，避免报价后配置变化。 */
    private void validateEconomics(MktServiceContract c) {
        if (!Integer.valueOf(SIGN_MODE_PARK).equals(c.getSignMode())
                || !(Integer.valueOf(2).equals(c.getFeeModel()) || Integer.valueOf(3).equals(c.getFeeModel()))) return;
        var warehouse = assignmentService.requireAvailableWarehouse(c.getWarehouseId(), c.getProjectId());
        JsonNode costs = parseJson(warehouse.getFeeModel(), "请先配置承接云仓的单票/按件成本价");
        if (!costs.isObject()) throw new BizException("云仓成本价格式无效");
        String gradeCode = StringUtils.hasText(c.getGrade()) ? c.getGrade() : "D";
        MktCustomerGrade grade = gradeMapper.selectOne(new LambdaQueryWrapper<MktCustomerGrade>()
                .eq(MktCustomerGrade::getCode, gradeCode).last("limit 1"));
        if (grade == null || grade.getErpTotalRate() == null || grade.getErpTotalRate().signum() < 0
                || grade.getErpTotalRate().compareTo(new BigDecimal("100")) > 0) throw new BizException("请先配置客户评级佣金比例");
        BigDecimal retained = BigDecimal.ONE.subtract(grade.getErpTotalRate().movePointLeft(2));
        JsonNode prices = parseJson(c.getPriceTable(), "单价表不是合法 JSON");
        BigDecimal totalCost = BigDecimal.ZERO;
        for (String key : java.util.List.of("perOrder", "perItem")) {
            JsonNode costNode = costs.get(key);
            if (costNode != null && (!costNode.isNumber() || costNode.decimalValue().signum() < 0)) throw new BizException("云仓成本价不合法");
            BigDecimal cost = costNode == null ? BigDecimal.ZERO : costNode.decimalValue();
            totalCost = totalCost.add(cost);
            BigDecimal price = prices.has(key) ? prices.get(key).decimalValue() : BigDecimal.ZERO;
            if (price.multiply(retained).compareTo(cost) < 0) throw new BizException("报价不足以覆盖云仓成本与佣金，请调整合同单价：" + key);
        }
        if (totalCost.signum() <= 0) throw new BizException("请先配置承接云仓的单票/按件成本价");
    }

    private void lockCustomer(MktServiceContract c) {
        if (customerMapper.selectForUpdate(c.getCustomerId()) == null) throw new BizException("合同客户不存在");
    }

    private void closeCustomer(MktServiceContract c, String reason) {
        if (assignmentService.contractClosed(c.getCustomerId())) lockService.releaseDeal(c.getCustomerId(), reason);
    }

    private static boolean isActive(MktServiceContract c) {
        return Integer.valueOf(ST_EFFECTIVE).equals(c.getStatus()) || Integer.valueOf(ST_PERFORMING).equals(c.getStatus())
                || Integer.valueOf(ST_AMENDING).equals(c.getStatus());
    }

    /** 两个入口共用同一份条款校验；调用方不能通过草稿编辑绕开签约校验。 */
    public static void validateTerms(MktServiceContract c) {
        if (c.getCustomerId() == null || c.getWarehouseId() == null) throw new BizException("请选择客户和承接云仓");
        if (c.getSignMode() == null || c.getSignMode() < 1 || c.getSignMode() > 2) throw new BizException("签约方式不合法");
        if (c.getServiceType() == null || c.getServiceType() < 1 || c.getServiceType() > 3) throw new BizException("服务类型不合法");
        if (c.getFeeModel() == null || c.getFeeModel() < 1 || c.getFeeModel() > 4) throw new BizException("计费模式不合法");
        if (c.getStartDate() == null || c.getEndDate() == null || !c.getEndDate().isAfter(c.getStartDate())) throw new BizException("合同起止日期不合法");
        if (c.getPayCycle() == null || c.getPayCycle() < 1 || c.getPayCycle() > 3) throw new BizException("结算周期不合法");
        if (c.getAutoRenew() != null && c.getAutoRenew() != 0 && c.getAutoRenew() != 1) throw new BizException("自动续签设置不合法");
        if (c.getDeposit() == null) c.setDeposit(BigDecimal.ZERO);
        if (c.getDeposit().signum() < 0 || c.getDeposit().stripTrailingZeros().scale() > 2 || c.getDeposit().compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new BizException("保证金须为非负金额，最多两位小数");
        }
        JsonNode prices = parseJson(c.getPriceTable(), "单价表不是合法 JSON");
        if (!prices.isObject() || prices.isEmpty()) throw new BizException("单价表须为非空 JSON 对象");
        var fields = prices.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            JsonNode value = entry.getValue();
            if (!PRICE_KEYS.contains(entry.getKey()) || !value.isNumber() || value.decimalValue().signum() < 0
                    || value.decimalValue().stripTrailingZeros().scale() > 4 || value.decimalValue().compareTo(new BigDecimal("99999999.99")) > 0) {
                throw new BizException("单价仅支持 perOrder、perItem、storage、monthly 的非负金额，最多四位小数");
            }
        }
        String required = switch (c.getFeeModel()) { case 1 -> "storage"; case 2 -> "perOrder"; case 3 -> "perItem"; default -> "monthly"; };
        if (!prices.has(required) || prices.get(required).decimalValue().signum() <= 0) throw new BizException("当前计费模式必须填写正数单价：" + required);
    }

    private static void validateFiles(String value, boolean required) {
        if (!StringUtils.hasText(value)) { if (required) throw new BizException("请先上传已签署合同附件"); return; }
        JsonNode files = parseJson(value, "合同附件格式不合法");
        if (!files.isArray() || (required && files.isEmpty()) || files.size() > 20) throw new BizException("合同附件须为 1–20 个文件引用");
        for (JsonNode file : files) {
            JsonNode id = file.isObject() ? file.get("id") : file;
            if (id == null || !id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0) throw new BizException("合同附件缺少有效文件编号");
        }
    }

    private static JsonNode parseJson(String value, String error) {
        try {
            JsonNode json = value == null ? null : JSON.readTree(value);
            if (json == null || json.isNull()) throw new BizException(error);
            return json;
        } catch (java.io.IOException ex) { throw new BizException(error); }
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
        // 兼容旧版已在“发起变更”时保存的同一版本，历史签署快照不可覆盖。
        if (versionMapper.selectCount(new LambdaQueryWrapper<MktServiceContractVersion>()
                .eq(MktServiceContractVersion::getContractId, c.getId()).eq(MktServiceContractVersion::getVerNo, c.getContractVersion())) > 0) return;
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
        try { return JSON.writeValueAsString(c); }
        catch (java.io.IOException error) { throw new BizException("合同版本快照生成失败"); }
    }
}
