package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktCustomerPricing;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.mapper.MktCustomerPricingMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** 只由正常 P4 设置本人负责客户的每单金额，后台角色并不授予定价权。 */
@Service
@RequiredArgsConstructor
public class MktCustomerPricingService {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999.999");
    private final MktCustomerPricingMapper pricingMapper;
    private final CustomerMapper customerMapper;
    private final MktPromoterMapper promoterMapper;
    private final MktAuditService auditService;
    private final ObjectMapper objectMapper;
    private final MktPositionReviewService positionService;

    public record BeneficiaryInput(Long promoterId, BigDecimal amountPerOrder) {}
    public record PricingInput(BigDecimal totalPerOrder, BigDecimal ownerPerOrder, List<BeneficiaryInput> beneficiaries) {}
    public record Beneficiary(Long promoterId, String promoterName, String positionCode, BigDecimal amountPerOrder) {}
    public record PricingView(Long customerId, String customerName, boolean configured, boolean canEdit,
                              Long ownerPromoterId, String ownerName, BigDecimal totalPerOrder,
                              BigDecimal ownerPerOrder, BigDecimal allocatedPerOrder, BigDecimal remainingPerOrder,
                              BigDecimal companyPerOrder, List<Beneficiary> beneficiaries,
                              LocalDateTime updatedAt, Long pricingId) {}
    public record PricingCustomer(Long id, String name, String grade, Long referrerId, String referrerName,
                                  boolean configured, BigDecimal totalPerOrder, Long ownerPromoterId, String ownerName) {}
    public record Candidate(Long id, String name, String positionCode) {}
    public record PricingSnapshot(Long pricingId, Long ownerPromoterId, String ownerPositionCode,
                                  BigDecimal totalPerOrder, BigDecimal ownerPerOrder, BigDecimal companyPerOrder,
                                  List<Beneficiary> beneficiaries) {}

    public List<PricingCustomer> customers(Long actorId) {
        MktPromoter actor = requireP4(actorId);
        List<Long> ids = members(actor).stream().map(MktPromoter::getId).toList();
        if (ids.isEmpty()) return List.of();
        List<PricingCustomer> result = new ArrayList<>();
        for (Customer c : customerMapper.selectList(new LambdaQueryWrapper<Customer>()
                .in(Customer::getReferrerId, ids).orderByDesc(Customer::getId))) {
            MktPromoter owner = ownerOf(c);
            if (owner == null || !actorId.equals(owner.getId())) continue;
            MktCustomerPricing p = latest(c.getId());
            boolean configured = p != null && actorId.equals(p.getOwnerPromoterId());
            MktPromoter referrer = promoterMapper.selectById(c.getReferrerId());
            result.add(new PricingCustomer(c.getId(), c.getName(), c.getGrade(), c.getReferrerId(),
                    referrer == null ? null : referrer.getName(), configured,
                    configured ? p.getTotalPerOrder() : null, actorId, actor.getName()));
        }
        return result;
    }

    public List<Candidate> beneficiaries(Long actorId) {
        MktPromoter actor = requireP4(actorId);
        return members(actor).stream().filter(p -> !actorId.equals(p.getId()))
                .filter(p -> Integer.valueOf(1).equals(p.getStatus()) && !Integer.valueOf(1).equals(p.getIsInternal()))
                .filter(p -> inTeam(p, actorId))
                .map(p -> new Candidate(p.getId(), p.getName(), p.getPositionCode())).toList();
    }

    public List<Candidate> team(Long actorId) {
        MktPromoter actor = requireP4(actorId);
        return members(actor).stream().filter(p -> !actorId.equals(p.getId()))
                .filter(p -> Integer.valueOf(1).equals(p.getStatus()) && inTeam(p, actorId))
                .map(p -> new Candidate(p.getId(), p.getName(), p.getPositionCode())).toList();
    }

    @Transactional
    public void setTeamPosition(Long actorId, Long promoterId, String code) {
        if (actorId == null || promoterId == null || actorId.equals(promoterId)) {
            throw new BizException(403, "只能设置本人邀请体系内其他正常伙伴的角色");
        }
        // 固定顺序锁定操作者和目标；后台/其他 P4 的并发调岗不能穿过本次权限检查。
        Map<Long, MktPromoter> locked = new HashMap<>();
        for (Long id : java.util.stream.Stream.of(actorId, promoterId).sorted().toList()) locked.put(id, promoterMapper.selectForUpdate(id));
        requireP4State(locked.get(actorId));
        MktPromoter member = locked.get(promoterId);
        if (actorId.equals(promoterId) || member == null || !Integer.valueOf(1).equals(member.getStatus()) || !inTeam(member, actorId)) {
            throw new BizException(403, "只能设置本人邀请体系内其他正常伙伴的角色");
        }
        if (code == null || !Set.of("P1", "P2", "P3", "P4").contains(code)) throw new BizException("角色须为 P1、P2、P3 或 P4");
        positionService.changeManually(promoterId, code, "P4 " + actorId + " 设置邀请体系成员角色");
    }

    public PricingView getForP4(Long actorId, Long customerId) {
        MktPromoter actor = requireP4(actorId);
        Customer customer = requireCustomer(customerId);
        requireOwnership(customer, actorId);
        return view(customer, actor, true);
    }

    public PricingView getForAdmin(Long customerId) {
        Customer customer = requireCustomer(customerId);
        return view(customer, ownerOf(customer), false);
    }

    @Transactional
    public PricingView save(Long actorId, Long customerId, PricingInput input) {
        MktPromoter actor = requireP4(actorId);
        Customer customer = requireCustomer(customerId);
        requireOwnership(customer, actorId);
        validateAmounts(input);
        List<Beneficiary> beneficiaries = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (BeneficiaryInput item : input.beneficiaries()) {
            if (item.promoterId() == null || actorId.equals(item.promoterId()) || !ids.add(item.promoterId())) {
                throw new BizException("受益人不能重复或选择 P4 本人，请在本人金额中填写");
            }
            MktPromoter recipient = promoterMapper.selectById(item.promoterId());
            requireRecipient(recipient, actorId);
            beneficiaries.add(new Beneficiary(recipient.getId(), recipient.getName(), recipient.getPositionCode(), item.amountPerOrder()));
        }
        if (input.ownerPerOrder().signum() > 0 && Integer.valueOf(1).equals(actor.getIsInternal())) {
            throw new BizException("内部人员不计个人佣金，请将本人金额设为 0");
        }
        MktCustomerPricing pricing = new MktCustomerPricing();
        pricing.setCustomerId(customerId);
        pricing.setOwnerPromoterId(actorId);
        pricing.setTotalPerOrder(input.totalPerOrder());
        pricing.setOwnerPerOrder(input.ownerPerOrder());
        pricing.setProjectId(customer.getProjectId());
        try { pricing.setBeneficiariesJson(objectMapper.writeValueAsString(beneficiaries)); }
        catch (Exception e) { throw new BizException("分配配置保存失败"); }
        // 每次新增不可变版本。计佣读取整行，不能观察到半份更新，也不重算已有订单。
        pricingMapper.insert(pricing);
        auditService.log("pricing.configure", "customer", customerId, "P4 每单自定义金额", null, pricing);
        return viewOf(customer, actor, true, pricing);
    }

    /** 新出库单必须配置；从不回退评级/岗位固定比例。历史订单由计佣幂等先返回，不重算。 */
    public PricingSnapshot forAccrual(Long customerId) {
        Customer customer = requireCustomer(customerId);
        MktCustomerPricing pricing = latest(customerId);
        if (pricing == null) throw new BizException("客户尚未设置 P4 每单佣金，请配置后重试该出库单");
        MktPromoter owner = requireP4(pricing.getOwnerPromoterId());
        requireOwnership(customer, owner.getId());
        List<Beneficiary> items = readBeneficiaries(pricing);
        validateAmounts(new PricingInput(pricing.getTotalPerOrder(), pricing.getOwnerPerOrder(), items.stream()
                .map(b -> new BeneficiaryInput(b.promoterId(), b.amountPerOrder())).toList()));
        Set<Long> ids = new HashSet<>();
        List<Beneficiary> current = new ArrayList<>();
        for (Beneficiary item : items) {
            if (item.promoterId() == null || owner.getId().equals(item.promoterId()) || !ids.add(item.promoterId())) {
                throw new BizException("客户定价受益人已失效，请 P4 重新配置");
            }
            MktPromoter recipient = promoterMapper.selectById(item.promoterId());
            requireRecipient(recipient, owner.getId());
            current.add(new Beneficiary(item.promoterId(), recipient.getName(), recipient.getPositionCode(), item.amountPerOrder()));
        }
        if (pricing.getOwnerPerOrder().signum() > 0 && Integer.valueOf(1).equals(owner.getIsInternal())) {
            throw new BizException("P4 已变更为内部人员，请重新配置本人金额");
        }
        BigDecimal allocated = current.stream().map(Beneficiary::amountPerOrder).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PricingSnapshot(pricing.getId(), owner.getId(), owner.getPositionCode(), pricing.getTotalPerOrder(),
                pricing.getOwnerPerOrder(), pricing.getTotalPerOrder().subtract(pricing.getOwnerPerOrder()).subtract(allocated), current);
    }

    static void validateAmounts(PricingInput input) {
        if (input == null) throw new BizException("请填写每单佣金配置");
        validateAmount(input.totalPerOrder(), "总佣金");
        validateAmount(input.ownerPerOrder(), "P4 本人金额");
        if (input.beneficiaries() == null) throw new BizException("请选择受益人；不分配时请提交空列表");
        if (input.beneficiaries().size() > 2) throw new BizException("每个客户最多分配给 2 名受益人");
        BigDecimal allocated = input.ownerPerOrder();
        for (BeneficiaryInput item : input.beneficiaries()) {
            if (item == null) throw new BizException("受益人配置不能为空");
            validateAmount(item.amountPerOrder(), "受益人金额");
            allocated = allocated.add(item.amountPerOrder());
        }
        if (allocated.compareTo(input.totalPerOrder()) > 0) throw new BizException("本人金额与受益人金额合计不能超过总佣金");
    }

    private static void validateAmount(BigDecimal amount, String label) {
        if (amount == null || amount.signum() < 0 || amount.compareTo(MAX_AMOUNT) > 0 || amount.stripTrailingZeros().scale() > 3) {
            throw new BizException(label + "须为非负金额，最多 3 位小数（0.001 元/单）");
        }
    }

    private MktPromoter requireP4(Long id) {
        MktPromoter p = id == null ? null : promoterMapper.selectById(id);
        return requireP4State(p);
    }

    private MktPromoter requireP4State(MktPromoter p) {
        if (p == null || !"P4".equals(p.getPositionCode()) || !Integer.valueOf(1).equals(p.getStatus())) {
            throw new BizException(403, "仅正常状态的 P4 拥有客户定价权");
        }
        return p;
    }

    private Customer requireCustomer(Long id) {
        Customer c = id == null ? null : customerMapper.selectById(id);
        if (c == null) throw new BizException("客户不存在");
        return c;
    }

    private void requireOwnership(Customer c, Long actorId) {
        MktPromoter owner = ownerOf(c);
        if (owner == null || !actorId.equals(owner.getId())) throw new BizException(403, "该客户不在本人负责的邀请体系内");
    }

    /** 最近的 P4 负责客户，冻结的 P4 不会使定价权悄悄转给更上一级。 */
    private MktPromoter ownerOf(Customer c) {
        Long id = c.getReferrerId();
        Set<Long> seen = new HashSet<>();
        while (id != null && seen.add(id) && seen.size() <= 256) {
            MktPromoter p = promoterMapper.selectById(id);
            if (p == null) return null;
            if ("P4".equals(p.getPositionCode())) return p;
            id = p.getParentId();
        }
        return null;
    }

    private boolean inTeam(MktPromoter p, Long ownerId) {
        Set<Long> seen = new HashSet<>();
        while (p != null && seen.add(p.getId()) && seen.size() <= 256) {
            if (ownerId.equals(p.getId())) return true;
            p = p.getParentId() == null ? null : promoterMapper.selectById(p.getParentId());
        }
        return false;
    }

    private void requireRecipient(MktPromoter p, Long actorId) {
        if (p == null || !Integer.valueOf(1).equals(p.getStatus()) || Integer.valueOf(1).equals(p.getIsInternal()) || !inTeam(p, actorId)) {
            throw new BizException("受益人须为本人邀请体系内的正常非内部伙伴");
        }
    }

    private List<MktPromoter> members(MktPromoter actor) {
        // path 为已有邀请体系索引；最终修改和计佣仍逐级验证 parentId，避免仅凭路径放行。
        String path = actor.getPath();
        if (path == null || path.isBlank()) return List.of(actor);
        return promoterMapper.selectList(new LambdaQueryWrapper<MktPromoter>().likeRight(MktPromoter::getPath, path));
    }

    private MktCustomerPricing latest(Long customerId) {
        return pricingMapper.selectOne(new LambdaQueryWrapper<MktCustomerPricing>()
                .eq(MktCustomerPricing::getCustomerId, customerId).orderByDesc(MktCustomerPricing::getId).last("limit 1"));
    }

    private List<Beneficiary> readBeneficiaries(MktCustomerPricing p) {
        try { return objectMapper.readValue(p.getBeneficiariesJson(), new TypeReference<List<Beneficiary>>() {}); }
        catch (Exception e) { throw new BizException("客户分配配置不可读，请联系管理员检查"); }
    }

    private PricingView view(Customer c, MktPromoter owner, boolean canEdit) {
        return viewOf(c, owner, canEdit, latest(c.getId()));
    }

    private PricingView viewOf(Customer c, MktPromoter owner, boolean canEdit, MktCustomerPricing p) {
        boolean configured = p != null && owner != null && owner.getId().equals(p.getOwnerPromoterId());
        List<Beneficiary> items = configured ? readBeneficiaries(p) : List.of();
        BigDecimal allocated = configured ? items.stream().map(Beneficiary::amountPerOrder).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        BigDecimal company = configured ? p.getTotalPerOrder().subtract(p.getOwnerPerOrder()).subtract(allocated) : null;
        return new PricingView(c.getId(), c.getName(), configured, canEdit,
                owner == null ? null : owner.getId(), owner == null ? null : owner.getName(),
                configured ? p.getTotalPerOrder() : null, configured ? p.getOwnerPerOrder() : null,
                allocated, company, company, items, configured ? p.getCreateTime() : null, configured ? p.getId() : null);
    }
}
