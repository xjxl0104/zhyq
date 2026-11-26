package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktCustomerPricing;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.mapper.MktCustomerPricingMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
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
    /** 云仓业务员（P0）固定每单佣金：0.020 元/单。 */
    public static final BigDecimal SALESPERSON_FIXED_FEE = new BigDecimal("0.020");
    private final MktCustomerPricingMapper pricingMapper;
    private final CustomerMapper customerMapper;
    private final MktPromoterMapper promoterMapper;
    private final MktServiceContractMapper contractMapper;
    private final MktAuditService auditService;
    private final ObjectMapper objectMapper;
    private final MktPositionReviewService positionService;
    /** 非构造注入：晋升后调整上级时使用。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MktPromoterService promoterService;
    /** 非构造注入：解析客户分配的云仓业务员（P0）及其伙伴档案。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.zhyq.park.marketing.mapper.MktWarehouseContactMapper warehouseContactMapper;

    public record BeneficiaryInput(Long promoterId, BigDecimal amountPerOrder) {}
    public record PricingInput(BigDecimal totalPerOrder, BigDecimal ownerPerOrder, List<BeneficiaryInput> beneficiaries) {}
    public record Beneficiary(Long promoterId, String promoterName, String positionCode, BigDecimal amountPerOrder) {}
    /** 合同链路成员:从成交伙伴沿 parentId 向上(含成交伙伴),供佣金分配界面展示与选择。 */
    public record ContractChainMember(Long promoterId, String name, String positionCode, Integer status,
                                      boolean isOwner, BigDecimal amountPerOrder, boolean former) {}
    public record ContractPricingView(Long contractId, String contractNo, Long customerId, String customerName,
                                      Long partnerId, String partnerName, boolean configured, boolean canEdit,
                                      Long ownerPromoterId, String ownerName, BigDecimal totalPerOrder,
                                      BigDecimal ownerPerOrder, BigDecimal allocatedPerOrder, BigDecimal companyPerOrder,
                                      List<ContractChainMember> chain, List<Beneficiary> beneficiaries,
                                      LocalDateTime updatedAt, String readOnlyReason, List<Candidate> teamMembers,
                                      boolean totalPerOrderLocked, BigDecimal lockedTotalPerOrder,
                                      BigDecimal salespersonCommissionPerOrder, Long salespersonContactId,
                                      String salespersonName) {
        @com.fasterxml.jackson.annotation.JsonProperty public BigDecimal platformServiceRate() { return PLATFORM_RATE; }
        @com.fasterxml.jackson.annotation.JsonProperty public BigDecimal platformServiceFeePerOrder() { return serviceFee(totalPerOrder); }
        @com.fasterxml.jackson.annotation.JsonProperty public BigDecimal allocatablePerOrder() { return allocatable(totalPerOrder, salespersonCommissionPerOrder); }
        @com.fasterxml.jackson.annotation.JsonProperty public BigDecimal unallocatedPerOrder() { return unallocated(totalPerOrder, companyPerOrder, salespersonCommissionPerOrder); }
        @com.fasterxml.jackson.annotation.JsonProperty public boolean requiresReallocation() { return companyPerOrder != null && unallocatedPerOrder().signum() < 0; }
    }
    public record PricingView(Long customerId, String customerName, boolean configured, boolean canEdit,
                              Long ownerPromoterId, String ownerName, BigDecimal totalPerOrder,
                              BigDecimal ownerPerOrder, BigDecimal allocatedPerOrder, BigDecimal remainingPerOrder,
                              BigDecimal companyPerOrder, List<Beneficiary> beneficiaries,
                              LocalDateTime updatedAt, Long pricingId,
                              boolean totalPerOrderLocked, BigDecimal lockedTotalPerOrder,
                              BigDecimal salespersonCommissionPerOrder, Long salespersonContactId,
                              String salespersonName) {
        @com.fasterxml.jackson.annotation.JsonProperty public BigDecimal platformServiceRate() { return PLATFORM_RATE; }
        @com.fasterxml.jackson.annotation.JsonProperty public BigDecimal platformServiceFeePerOrder() { return serviceFee(totalPerOrder); }
        @com.fasterxml.jackson.annotation.JsonProperty public BigDecimal allocatablePerOrder() { return allocatable(totalPerOrder, salespersonCommissionPerOrder); }
        @com.fasterxml.jackson.annotation.JsonProperty public BigDecimal unallocatedPerOrder() { return unallocated(totalPerOrder, companyPerOrder, salespersonCommissionPerOrder); }
        @com.fasterxml.jackson.annotation.JsonProperty public boolean requiresReallocation() { return companyPerOrder != null && unallocatedPerOrder().signum() < 0; }
    }
    public record PricingCustomer(Long id, String name, String grade, Long referrerId, String referrerName,
                                  boolean configured, BigDecimal totalPerOrder, Long ownerPromoterId, String ownerName) {}
    /** 非 P4 仅接收本人分佣金额；不返回总佣金、他人分配、手机号或平台余额。 */
    public record AllocationItem(String key, Long contractId, String contractNo, Long customerId,
                                 String customerName, BigDecimal amountPerOrder, boolean configured,
                                 boolean canEdit, Integer contractStatus) {}
    /** phoneTail:手机号后 4 位,团队里有多位同名/未命名伙伴时用来区分。 */
    public record Candidate(Long id, String name, String positionCode, String phoneTail, boolean loginable) {
        static Candidate of(MktPromoter p) {
            String phone = p.getPhone();
            String tail = phone != null && phone.matches("^1\\d{10}$") ? phone.substring(7) : null;
            boolean loginable = (phone != null && !phone.startsWith("p:")) || (p.getOpenid() != null && !p.getOpenid().isBlank());
            return new Candidate(p.getId(), p.getName(), p.getPositionCode(), tail, loginable);
        }
    }
    public record PricingSnapshot(Long pricingId, Long ownerPromoterId, String ownerPositionCode,
                                  BigDecimal totalPerOrder, BigDecimal ownerPerOrder, BigDecimal companyPerOrder,
                                  List<Beneficiary> beneficiaries, BigDecimal salespersonPerOrder,
                                  Long salespersonContactId, Long salespersonPromoterId) {
        public PricingSnapshot(Long pricingId, Long ownerPromoterId, String ownerPositionCode,
                               BigDecimal totalPerOrder, BigDecimal ownerPerOrder, BigDecimal companyPerOrder,
                               List<Beneficiary> beneficiaries) {
            this(pricingId, ownerPromoterId, ownerPositionCode, totalPerOrder, ownerPerOrder, companyPerOrder,
                    beneficiaries, BigDecimal.ZERO, null, null);
        }
        public BigDecimal platformServiceFeePerOrder() { return serviceFee(totalPerOrder); }
        public BigDecimal unallocatedPerOrder() {
            BigDecimal allocated = ownerPerOrder.add(beneficiaries.stream()
                    .map(Beneficiary::amountPerOrder).reduce(BigDecimal.ZERO, BigDecimal::add));
            BigDecimal sp = salespersonPerOrder == null ? BigDecimal.ZERO : salespersonPerOrder;
            return totalPerOrder == null ? null : totalPerOrder.subtract(serviceFee(totalPerOrder)).subtract(sp).subtract(allocated);
        }
    }

    public List<AllocationItem> allocations(Long actorId) {
        MktPromoter actor = requireActivePartner(actorId);
        boolean p4 = "P4".equals(actor.getPositionCode());
        List<AllocationItem> result = new ArrayList<>();
        Set<Long> contractedCustomers = new HashSet<>();
        for (MktServiceContract contract : contractMapper.selectList(new LambdaQueryWrapper<MktServiceContract>()
                .in(MktServiceContract::getStatus, List.of(4, 5, 6, 7, 8)).orderByDesc(MktServiceContract::getId))) {
            if (!isSigned(contract)) continue;
            contractedCustomers.add(contract.getCustomerId());
            Customer customer = customerMapper.selectById(contract.getCustomerId());
            if (customer == null) continue;
            MktPromoter partner = promoterMapper.selectById(contract.getPartnerId());
            boolean managed = p4 && partner != null && inTeam(partner, actorId);
            MktCustomerPricing pricing = latestByContract(contract.getId());
            if (pricing == null) pricing = latest(customer.getId());
            BigDecimal ownAmount = ownAmount(pricing, actorId);
            if (!managed && (ownAmount == null || ownAmount.signum() <= 0)) continue;
            result.add(new AllocationItem("contract:" + contract.getId(), contract.getId(), contract.getContractNo(),
                    customer.getId(), customer.getName(), ownAmount, pricing != null,
                    managed && contract.getStatus() <= 6, contract.getStatus()));
        }
        // 兼容历史只登记客户已签约、尚未补录合同的客户定价，不重复展示已有合同的客户。
        for (Customer customer : customerMapper.selectList(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getStatus, 2).orderByDesc(Customer::getId))) {
            if (contractedCustomers.contains(customer.getId()) || !Integer.valueOf(2).equals(customer.getStatus())) continue;
            MktPromoter owner = ownerOf(customer);
            boolean managed = p4 && owner != null && actorId.equals(owner.getId());
            MktCustomerPricing pricing = latest(customer.getId());
            BigDecimal ownAmount = ownAmount(pricing, actorId);
            if (!managed && (ownAmount == null || ownAmount.signum() <= 0)) continue;
            result.add(new AllocationItem("customer:" + customer.getId(), null, null, customer.getId(),
                    customer.getName(), ownAmount, pricing != null, managed, null));
        }
        return result;
    }

    public ContractPricingView getContractForP4(Long actorId, Long contractId) {
        MktPromoter actor = requireP4(actorId);
        MktServiceContract contract = requireContract(contractId);
        MktPromoter partner = contract.getPartnerId() == null ? null : promoterMapper.selectById(contract.getPartnerId());
        if (!isSigned(contract) || partner == null || !inTeam(partner, actorId))
            throw new BizException(403, "该合同不在本人的已签约客户分佣范围内");
        return contractView(contractId, actor);
    }

    @Transactional
    public ContractPricingView saveContractForP4(Long actorId, Long contractId, PricingInput input) {
        getContractForP4(actorId, contractId);
        if (requireContract(contractId).getStatus() > 6) throw new BizException("合同已到期或终止，不能调整分佣");
        return saveContract(actorId, contractId, input);
    }

    private static boolean isSigned(MktServiceContract contract) {
        return contract.getStatus() != null && contract.getStatus() >= 4 && contract.getStatus() <= 8;
    }

    private BigDecimal ownAmount(MktCustomerPricing pricing, Long actorId) {
        if (pricing == null) return null;
        if (actorId.equals(pricing.getOwnerPromoterId())) return pricing.getOwnerPerOrder();
        return readBeneficiaries(pricing).stream().filter(b -> actorId.equals(b.promoterId()))
                .map(Beneficiary::amountPerOrder).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private MktPromoter requireActivePartner(Long actorId) {
        MktPromoter actor = actorId == null ? null : promoterMapper.selectById(actorId);
        if (actor == null || !Integer.valueOf(1).equals(actor.getStatus())) throw new BizException(403, "伙伴账号不可用");
        return actor;
    }

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
                .map(Candidate::of).toList();
    }

    public List<Candidate> team(Long actorId) {
        MktPromoter actor = requireP4(actorId);
        return members(actor).stream().filter(p -> !actorId.equals(p.getId()))
                .filter(p -> Integer.valueOf(1).equals(p.getStatus()) && inTeam(p, actorId))
                .map(Candidate::of).toList();
    }

    @Transactional
    public void setTeamPosition(Long actorId, Long promoterId, String code) {
        setTeamPositionInternal(actorId, promoterId, code, false);
    }
    /** 手机端仅管理 P1–P3；P4 的授予和调整保留给后台。 */
    @Transactional
    public void setMobileTeamPosition(Long actorId, Long promoterId, String code) {
        setTeamPositionInternal(actorId, promoterId, code, true);
    }
    private void setTeamPositionInternal(Long actorId, Long promoterId, String code, boolean mobile) {
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
        if (mobile && ("P4".equals(code) || "P4".equals(member.getPositionCode()))) {
            throw new BizException(403, "P4 称号只能在后台设置或调整");
        }
        if (mobile && (code == null || !Set.of("P1", "P2", "P3").contains(code))) {
            throw new BizException("手机端称号须为 P1、P2 或 P3");
        }
        if (code == null || !Set.of("P1", "P2", "P3", "P4").contains(code)) throw new BizException("角色须为 P1、P2、P3 或 P4");
        positionService.changeManually(promoterId, code, "P4 " + actorId + " 设置邀请体系成员角色");
        reparentAfterPromotion(member, code, actorId);
    }

    /**
     * 晋升后上级调整：成员晋升到某层级后，不再挂在同级或更低的原上级下，改为挂在其上级链里第一个岗位更高的上级
     * （找不到则挂到操作的 P4 下）。调整前客户的已保存分配仍保留原上级分成；调整后的新客户不再与原上级分成。
     */
    private void reparentAfterPromotion(MktPromoter member, String newCode, Long actorId) {
        if (promoterService == null) return;
        Long target = reparentTarget(member, newCode, actorId);
        if (target != null) {
            promoterService.changeParent(member.getId(), target, "晋升为 " + newCode + "，上级调整为岗位更高的最近上级");
        }
    }

    /** 晋升后的新上级 id；不需要调整（降级、晋升为 P4、上级已经更高）时返回 null。 */
    Long reparentTarget(MktPromoter member, String newCode, Long actorId) {
        int newRank = rankOf(newCode), oldRank = rankOf(member.getPositionCode());
        if (newRank <= oldRank || newRank >= 4) return null;
        Long target = actorId;
        MktPromoter cur = member.getParentId() == null ? null : promoterMapper.selectById(member.getParentId());
        Set<Long> seen = new HashSet<>();
        while (cur != null && seen.add(cur.getId()) && seen.size() <= 64) {
            if (rankOf(cur.getPositionCode()) > newRank) { target = cur.getId(); break; }
            cur = cur.getParentId() == null ? null : promoterMapper.selectById(cur.getParentId());
        }
        return target.equals(member.getParentId()) ? null : target;
    }

    public record PositionPreview(Long promoterId, String name, String fromCode, String toCode,
                                  Long currentParentId, String currentParentName,
                                  Long newParentId, String newParentName, boolean parentChanges,
                                  int contractsWithoutAllocation) {}

    /** 调整层级前的预览：新上级是谁；本人成交的合同里还有多少份没保存过分配（调整后这些合同会按新链路分成）。 */
    public PositionPreview previewPosition(Long actorId, Long promoterId, String code) {
        requireP4(actorId);
        MktPromoter member = promoterId == null ? null : promoterMapper.selectById(promoterId);
        if (member == null || actorId.equals(promoterId) || !Integer.valueOf(1).equals(member.getStatus()) || !inTeam(member, actorId)) {
            throw new BizException(403, "只能设置本人邀请体系内其他正常伙伴的角色");
        }
        if (code == null || !Set.of("P1", "P2", "P3", "P4").contains(code)) throw new BizException("角色须为 P1、P2、P3 或 P4");
        Long target = reparentTarget(member, code, actorId);
        MktPromoter current = member.getParentId() == null ? null : promoterMapper.selectById(member.getParentId());
        MktPromoter next = target == null ? current : promoterMapper.selectById(target);
        int missing = 0;
        if (target != null) {
            for (MktServiceContract contract : contractMapper.selectList(new LambdaQueryWrapper<MktServiceContract>()
                    .eq(MktServiceContract::getPartnerId, promoterId))) {
                if (latestByContract(contract.getId()) == null) missing++;
            }
        }
        return new PositionPreview(promoterId, member.getName(), member.getPositionCode(), code,
                current == null ? null : current.getId(), current == null ? null : current.getName(),
                next == null ? null : next.getId(), next == null ? null : next.getName(), target != null, missing);
    }

    private static int rankOf(String positionCode) {
        try { return positionCode == null ? 0 : Integer.parseInt(positionCode.substring(1)); }
        catch (RuntimeException e) { return 0; }
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
        enforceLockedTotal(input, lockedTotalPerOrder(customer, null));
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
        pricing.setSalespersonPerOrder(SALESPERSON_FIXED_FEE);
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
        return snapshotOf(pricing, owner, customer, true);
    }

    /** 计佣入口:合同分配版本(contract_id = contractId)优先,未配置则回退客户定价。 */
    public PricingSnapshot forAccrual(Long customerId, Long contractId) {
        if (contractId == null) return forAccrual(customerId);
        MktCustomerPricing pricing = latestByContract(contractId);
        if (pricing == null) return forAccrual(customerId);
        Customer customer = requireCustomer(customerId);
        if (!Objects.equals(pricing.getCustomerId(), customerId)) throw new BizException("合同分佣与客户不匹配，请核对合同");
        MktPromoter owner = requireP4(pricing.getOwnerPromoterId());
        return snapshotOf(pricing, owner, customer, true);
    }

    /** 只读尝试读取当前有效定价快照(供服务合同毛利校验优先使用,未配置时返回 empty)。 */
    public Optional<PricingSnapshot> findPricingSnapshot(Long customerId, Long contractId) {
        if (customerId == null) return Optional.empty();
        MktCustomerPricing pricing = contractId == null ? null : latestByContract(contractId);
        if (pricing == null) pricing = latest(customerId);
        if (pricing == null) return Optional.empty();
        MktPromoter owner = pricing.getOwnerPromoterId() == null ? null : promoterMapper.selectById(pricing.getOwnerPromoterId());
        if (owner == null || !"P4".equals(owner.getPositionCode()) || !Integer.valueOf(1).equals(owner.getStatus())) {
            return Optional.empty();
        }
        Customer customer = customerMapper.selectById(customerId);
        if (pricing.getContractId() == null) {
            MktPromoter currentOwner = customer == null ? null : ownerOf(customer);
            if (currentOwner == null || !owner.getId().equals(currentOwner.getId())) {
                return Optional.empty();
            }
        }
        try {
            return Optional.of(snapshotOf(pricing, owner, customer, false));
        } catch (BizException invalid) {
            return Optional.empty();
        }
    }

    /** 查找合同成交链路上的最近正常状态 P4(供超级管理员未绑私人伙伴手机号时代配分佣)。 */
    public MktPromoter resolveContractOwnerP4(Long contractId) {
        MktServiceContract contract = requireContract(contractId);
        Long startId = contract.getPartnerId();
        if (startId == null && contract.getCustomerId() != null) {
            Customer customer = customerMapper.selectById(contract.getCustomerId());
            if (customer != null) startId = customer.getReferrerId();
        }
        Set<Long> seen = new HashSet<>();
        Long id = startId;
        while (id != null && seen.add(id) && seen.size() <= 256) {
            MktPromoter p = promoterMapper.selectById(id);
            if (p == null) return null;
            if ("P4".equals(p.getPositionCode())) {
                return Integer.valueOf(1).equals(p.getStatus()) ? p : null;
            }
            id = p.getParentId();
        }
        return null;
    }

    /** 校验定价版本当前仍可执行(受益人有效、金额自洽),返回计佣快照;客户定价与合同分配共用。 */
    private PricingSnapshot snapshotOf(MktCustomerPricing pricing, MktPromoter owner, Customer customer, boolean createSalespersonPromoter) {
        List<Beneficiary> items = readBeneficiaries(pricing);
        Set<Long> ids = new HashSet<>();
        List<Beneficiary> current = new ArrayList<>();
        for (Beneficiary item : items) {
            if (item.promoterId() == null || owner.getId().equals(item.promoterId()) || !ids.add(item.promoterId())) {
                throw new BizException("分配配置受益人已失效，请 P4 重新配置");
            }
            MktPromoter recipient = promoterMapper.selectById(item.promoterId());
            requireRecipient(recipient, owner.getId());
            current.add(new Beneficiary(item.promoterId(), recipient.getName(), recipient.getPositionCode(), item.amountPerOrder()));
        }
        if (pricing.getOwnerPerOrder().signum() > 0 && Integer.valueOf(1).equals(owner.getIsInternal())) {
            throw new BizException("P4 已变更为内部人员，请重新配置本人金额");
        }
        BigDecimal allocated = current.stream().map(Beneficiary::amountPerOrder).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spFee = pricing.getSalespersonPerOrder() == null ? BigDecimal.ZERO : pricing.getSalespersonPerOrder();
        requirePlatformShare(pricing.getTotalPerOrder(), pricing.getOwnerPerOrder().add(allocated), spFee);
        SalespersonInfo sp = resolveSalesperson(customer, createSalespersonPromoter);
        Long spPromoterId = spFee.signum() > 0 && sp != null && sp.active() ? sp.promoterId() : null;
        BigDecimal paidToMembers = pricing.getOwnerPerOrder().add(allocated).add(spPromoterId != null ? spFee : BigDecimal.ZERO);
        BigDecimal companyRetained = pricing.getTotalPerOrder().subtract(paidToMembers);
        return new PricingSnapshot(pricing.getId(), owner.getId(), owner.getPositionCode(), pricing.getTotalPerOrder(),
                pricing.getOwnerPerOrder(), companyRetained, current, spFee,
                sp == null ? null : sp.contactId(), spPromoterId);
    }

    // ---------------- 按合同佣金分配(P4 在后台为每份合同配置链路成员每单金额) ----------------

    /** 合同分配只读视图（供后台非超管查看）。 */
    public ContractPricingView contractViewReadOnly(Long contractId, String customReadOnlyReason) {
        return contractViewInternal(contractId, null, true, customReadOnlyReason);
    }

    /** 合同分配视图:链路成员(成交伙伴向上)+当前配置;actor 为操作员关联的伙伴,可空。 */
    public ContractPricingView contractView(Long contractId, MktPromoter actor) {
        return contractViewInternal(contractId, actor, false, null);
    }

    private ContractPricingView contractViewInternal(Long contractId, MktPromoter actor, boolean forceReadOnly, String customReadOnlyReason) {
        MktServiceContract contract = requireContract(contractId);
        MktPromoter partner = contract.getPartnerId() == null ? null : promoterMapper.selectById(contract.getPartnerId());
        boolean canEdit = !forceReadOnly && actor != null && "P4".equals(actor.getPositionCode())
                && Integer.valueOf(1).equals(actor.getStatus())
                && partner != null && inTeam(partner, actor.getId())
                && (contract.getStatus() == null || contract.getStatus() <= 6);
        MktCustomerPricing pricing = latestByContract(contractId);
        if (pricing == null && contract.getCustomerId() != null) pricing = latest(contract.getCustomerId());
        boolean configured = pricing != null;
        Map<Long, BigDecimal> amounts = new HashMap<>();
        if (configured) {
            for (Beneficiary b : readBeneficiaries(pricing)) amounts.put(b.promoterId(), b.amountPerOrder());
        }
        List<ContractChainMember> chain = new ArrayList<>();
        MktPromoter cur = partner;
        Set<Long> seen = new HashSet<>();
        while (cur != null && seen.add(cur.getId()) && seen.size() <= 64) {
            boolean isOwner = configured && cur.getId().equals(pricing.getOwnerPromoterId());
            chain.add(new ContractChainMember(cur.getId(), cur.getName(), cur.getPositionCode(), cur.getStatus(),
                    isOwner, isOwner ? pricing.getOwnerPerOrder() : amounts.get(cur.getId()), false));
            cur = cur.getParentId() == null ? null : promoterMapper.selectById(cur.getParentId());
        }
        // 成员被调整层级后不再在当前链路上，但已保存的分配里仍有他：保留为“原链路成员”，调整前的客户继续按原分成。
        if (configured) {
            Set<Long> inChain = new HashSet<>();
            chain.forEach(member -> inChain.add(member.promoterId()));
            for (Beneficiary b : readBeneficiaries(pricing)) {
                if (b.promoterId() == null || inChain.contains(b.promoterId())) continue;
                MktPromoter former = promoterMapper.selectById(b.promoterId());
                if (former != null) chain.add(new ContractChainMember(former.getId(), former.getName(), former.getPositionCode(),
                        former.getStatus(), false, b.amountPerOrder(), true));
            }
        }
        Customer customer = contract.getCustomerId() == null ? null : customerMapper.selectById(contract.getCustomerId());
        BigDecimal lockedTotal = lockedTotalPerOrder(customer, contract);
        SalespersonInfo sp = resolveSalesperson(customer, false);
        BigDecimal allocated = BigDecimal.ZERO;
        if (configured) {
            allocated = pricing.getOwnerPerOrder().add(readBeneficiaries(pricing).stream()
                    .map(Beneficiary::amountPerOrder).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        BigDecimal effectiveTotal = lockedTotal != null ? lockedTotal : (configured ? pricing.getTotalPerOrder() : null);
        String reason = canEdit ? null : (customReadOnlyReason != null ? customReadOnlyReason : readOnlyReason(actor, partner));
        return new ContractPricingView(contractId, contract.getContractNo(), contract.getCustomerId(),
                customer == null ? null : customer.getName(), contract.getPartnerId(),
                partner == null ? null : partner.getName(), configured, canEdit,
                configured ? pricing.getOwnerPromoterId() : (canEdit ? actor.getId() : null),
                configured && promoterMapper.selectById(pricing.getOwnerPromoterId()) != null
                        ? promoterMapper.selectById(pricing.getOwnerPromoterId()).getName() : (canEdit ? actor.getName() : null),
                effectiveTotal,
                configured ? pricing.getOwnerPerOrder() : null,
                configured ? allocated : null,
                configured ? effectiveTotal.subtract(allocated) : null,
                chain, configured ? readBeneficiaries(pricing) : List.of(),
                configured ? pricing.getCreateTime() : null,
                reason,
                canEdit ? team(actor.getId()) : List.of(),
                lockedTotal != null, lockedTotal,
                SALESPERSON_FIXED_FEE,
                sp == null ? null : sp.contactId(),
                sp == null ? null : sp.name());
    }

    /** 说明为什么当前后台账号只能查看、不能设置合同分配，便于排查（尤其是后台账号未关联 P4 伙伴档案）。 */
    static String readOnlyReason(MktPromoter actor, MktPromoter partner) {
        if (actor == null) return "当前后台账号未关联伙伴档案：请在系统用户中把手机号设为与你的 P4 伙伴档案一致后重新登录（后台账号只按手机号关联伙伴）";
        if (!"P4".equals(actor.getPositionCode()))
            return "当前账号关联的伙伴「" + actor.getName() + "」岗位是 " + actor.getPositionCode() + "，仅 P4 可设置";
        if (!Integer.valueOf(1).equals(actor.getStatus()))
            return "当前账号关联的伙伴「" + actor.getName() + "」不是正常状态，不能设置";
        if (partner == null) return "该合同没有成交伙伴，无法设置分配";
        return "该合同的成交伙伴「" + partner.getName() + "」不在伙伴「" + actor.getName() + "」的邀请体系内";
    }

    /** 保存合同分配(仅 P4 或后台超管代负责 P4):生成不可变新版本,只影响之后产生的出库单,历史订单不重算。 */
    @Transactional
    public ContractPricingView saveContract(Long actorId, Long contractId, PricingInput input) {
        MktPromoter actor = requireP4(actorId);
        MktServiceContract contract = requireContract(contractId);
        MktPromoter partner = contract.getPartnerId() == null ? null : promoterMapper.selectById(contract.getPartnerId());
        if (partner == null || !inTeam(partner, actor.getId())) {
            throw new BizException(403, "该合同的成交伙伴不在本人邀请体系内");
        }
        Customer customer = contract.getCustomerId() == null ? null : customerMapper.selectById(contract.getCustomerId());
        enforceLockedTotal(input, lockedTotalPerOrder(customer, contract));
        validateContractAmounts(input);
        List<Beneficiary> beneficiaries = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (BeneficiaryInput item : input.beneficiaries()) {
            if (item.promoterId() == null || actorId.equals(item.promoterId()) || !ids.add(item.promoterId())) {
                throw new BizException("受益人不能重复或选择 P4 本人，请在本人金额中填写");
            }
            // 受益人可以是该合同链路上的成员，也可以是 P4 邀请体系内的其他正常成员（由 requireRecipient 校验）。
            MktPromoter recipient = promoterMapper.selectById(item.promoterId());
            requireRecipient(recipient, actorId);
            beneficiaries.add(new Beneficiary(recipient.getId(), recipient.getName(), recipient.getPositionCode(), item.amountPerOrder()));
        }
        if (input.ownerPerOrder().signum() > 0 && Integer.valueOf(1).equals(actor.getIsInternal())) {
            throw new BizException("内部人员不计个人佣金，请将本人金额设为 0");
        }
        MktCustomerPricing pricing = new MktCustomerPricing();
        pricing.setContractId(contractId);
        pricing.setCustomerId(contract.getCustomerId());
        pricing.setOwnerPromoterId(actorId);
        pricing.setTotalPerOrder(input.totalPerOrder());
        pricing.setOwnerPerOrder(input.ownerPerOrder());
        pricing.setSalespersonPerOrder(SALESPERSON_FIXED_FEE);
        pricing.setProjectId(contract.getProjectId());
        try { pricing.setBeneficiariesJson(objectMapper.writeValueAsString(beneficiaries)); }
        catch (Exception e) { throw new BizException("合同分配配置保存失败"); }
        pricingMapper.insert(pricing);
        auditService.log("pricing.contract.configure", "service_contract", contractId, "按合同每单金额分配", null, pricing);
        return contractView(contractId, actor);
    }

    /** 合同分配的链路成员可达 8 人,沿用金额规则但放宽 2 名受益人上限。 */
    static void validateContractAmounts(PricingInput input) {
        if (input == null) throw new BizException("请填写每单佣金配置");
        validateAmount(input.totalPerOrder(), "总佣金");
        validateAmount(input.ownerPerOrder(), "P4 本人金额");
        if (input.beneficiaries() == null) throw new BizException("请选择受益人；不分配时请提交空列表");
        if (input.beneficiaries().size() > 8) throw new BizException("受益人数量超出链路上限");
        BigDecimal allocated = input.ownerPerOrder();
        for (BeneficiaryInput item : input.beneficiaries()) {
            if (item == null) throw new BizException("受益人配置不能为空");
            validateAmount(item.amountPerOrder(), "受益人金额");
            allocated = allocated.add(item.amountPerOrder());
        }
        if (allocated.compareTo(input.totalPerOrder()) > 0) throw new BizException("本人金额与受益人金额合计不能超过总佣金");
        requirePlatformShare(input.totalPerOrder(), allocated, SALESPERSON_FIXED_FEE);
    }

    private MktServiceContract requireContract(Long id) {
        MktServiceContract c = id == null ? null : contractMapper.selectById(id);
        if (c == null) throw new BizException("服务合同不存在");
        return c;
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
        requirePlatformShare(input.totalPerOrder(), allocated, SALESPERSON_FIXED_FEE);
    }

    /** 平台服务费精确为总佣金的 10%；未分配余额单独展示，不能冒充服务费。 */
    static final BigDecimal PLATFORM_RATE = new BigDecimal("0.10");

    static BigDecimal serviceFee(BigDecimal total) {
        return total == null ? null : total.multiply(PLATFORM_RATE).setScale(4, java.math.RoundingMode.UNNECESSARY);
    }

    static BigDecimal allocatable(BigDecimal total) {
        return allocatable(total, SALESPERSON_FIXED_FEE);
    }

    static BigDecimal allocatable(BigDecimal total, BigDecimal salespersonFee) {
        if (total == null) return null;
        BigDecimal sp = salespersonFee == null ? BigDecimal.ZERO : salespersonFee;
        return total.subtract(serviceFee(total)).subtract(sp);
    }

    static BigDecimal unallocated(BigDecimal total, BigDecimal retained) {
        return unallocated(total, retained, SALESPERSON_FIXED_FEE);
    }

    static BigDecimal unallocated(BigDecimal total, BigDecimal retained, BigDecimal salespersonFee) {
        if (total == null || retained == null) return null;
        BigDecimal sp = salespersonFee == null ? BigDecimal.ZERO : salespersonFee;
        return retained.subtract(serviceFee(total)).subtract(sp);
    }

    static void requirePlatformShare(BigDecimal total, BigDecimal allocated) {
        requirePlatformShare(total, allocated, SALESPERSON_FIXED_FEE);
    }

    static void requirePlatformShare(BigDecimal total, BigDecimal allocated, BigDecimal salespersonFee) {
        BigDecimal sp = salespersonFee == null ? BigDecimal.ZERO : salespersonFee;
        BigDecimal afterPlatform = total.subtract(total.multiply(PLATFORM_RATE));
        BigDecimal maxAllocatable = afterPlatform.subtract(sp);
        if (maxAllocatable.signum() < 0) {
            throw new BizException("总佣金单价不足以覆盖 10% 平台服务费和 "
                    + sp.setScale(3, java.math.RoundingMode.HALF_UP).toPlainString() + " 元/单业务员佣金，最低须为 0.023 元/单");
        }
        if (allocated.compareTo(maxAllocatable) > 0) {
            throw new BizException("平台服务费为总佣金的 10%，业务员(P0)固定佣金 "
                    + sp.setScale(3, java.math.RoundingMode.HALF_UP).toPlainString()
                    + " 元/单，个人分配合计不能超过 "
                    + maxAllocatable.setScale(3, java.math.RoundingMode.DOWN).toPlainString() + " 元/单，请重新分配");
        }
    }

    private static void enforceLockedTotal(PricingInput input, BigDecimal lockedTotal) {
        if (lockedTotal == null) return;
        if (input == null || input.totalPerOrder() == null || input.totalPerOrder().compareTo(lockedTotal) != 0) {
            throw new BizException("总计佣单价已由云仓确认返点锁定为 "
                    + lockedTotal.setScale(3, java.math.RoundingMode.HALF_UP).toPlainString() + " 元/单，不能手动调整");
        }
    }

    static BigDecimal lockedTotalPerOrder(Customer customer, MktServiceContract contract) {
        if (contract != null && contract.getRebateUnitPrice() != null && contract.getRebateUnitPrice().signum() > 0) {
            return contract.getRebateUnitPrice();
        }
        if (customer != null && customer.getRebateUnitPrice() != null && customer.getRebateUnitPrice().signum() > 0
                && (customer.getRebateConfirmedAt() != null
                || Integer.valueOf(MktCustomerAssignmentService.ACCEPTED).equals(customer.getWarehouseAssignmentStatus()))) {
            return customer.getRebateUnitPrice();
        }
        return null;
    }

    private record SalespersonInfo(Long contactId, String name, Long promoterId, boolean active) {}

    private SalespersonInfo resolveSalesperson(Customer customer) {
        return resolveSalesperson(customer, false);
    }

    private SalespersonInfo resolveSalesperson(Customer customer, boolean createIfMissing) {
        if (customer == null || customer.getWarehouseSalespersonId() == null || warehouseContactMapper == null) return null;
        var contact = warehouseContactMapper.selectById(customer.getWarehouseSalespersonId());
        if (contact == null || (customer.getAssignedWarehouseId() != null && !Objects.equals(contact.getWarehouseId(), customer.getAssignedWarehouseId()))) {
            return null;
        }
        boolean active = Integer.valueOf(1).equals(contact.getStatus());
        Long promoterId = contact.getPromoterId();
        if (promoterId == null && contact.getPhone() != null && !contact.getPhone().isBlank()) {
            MktPromoter matched = promoterMapper.selectOne(new LambdaQueryWrapper<MktPromoter>()
                    .eq(MktPromoter::getPhone, contact.getPhone()).orderByDesc(MktPromoter::getId).last("limit 1"));
            if (matched != null) promoterId = matched.getId();
        }
        if (promoterId == null && createIfMissing && active && promoterService != null) {
            MktPromoter p = new MktPromoter();
            p.setName(contact.getName() == null || contact.getName().isBlank() ? "云仓业务员" : contact.getName());
            p.setPhone(contact.getPhone());
            p.setPositionCode("P0");
            p.setStatus(MktPromoterService.ST_NORMAL);
            p.setProjectId(contact.getProjectId() != null ? contact.getProjectId() : customer.getProjectId());
            p.setRemark("云仓业务员（自动建档）");
            MktPromoter created = promoterService.registerManaged(p, null);
            promoterId = created.getId();
            warehouseContactMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.zhyq.park.marketing.entity.MktWarehouseContact>()
                    .eq(com.zhyq.park.marketing.entity.MktWarehouseContact::getId, contact.getId())
                    .set(com.zhyq.park.marketing.entity.MktWarehouseContact::getPromoterId, promoterId));
        }
        if (active && promoterId != null) {
            MktPromoter p = promoterMapper.selectById(promoterId);
            if (p == null || !Integer.valueOf(1).equals(p.getStatus())) active = false;
        }
        return new SalespersonInfo(contact.getId(), contact.getName(), promoterId, active);
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
                .eq(MktCustomerPricing::getCustomerId, customerId)
                .isNull(MktCustomerPricing::getContractId)
                .orderByDesc(MktCustomerPricing::getId).last("limit 1"));
    }

    /** 合同分配版本:同一合同取最新一条不可变版本。 */
    private MktCustomerPricing latestByContract(Long contractId) {
        return pricingMapper.selectOne(new LambdaQueryWrapper<MktCustomerPricing>()
                .eq(MktCustomerPricing::getContractId, contractId)
                .orderByDesc(MktCustomerPricing::getId).last("limit 1"));
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
        BigDecimal lockedTotal = lockedTotalPerOrder(c, null);
        BigDecimal effectiveTotal = lockedTotal != null ? lockedTotal : (configured ? p.getTotalPerOrder() : null);
        BigDecimal allocated = configured ? items.stream().map(Beneficiary::amountPerOrder).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        BigDecimal company = configured ? effectiveTotal.subtract(p.getOwnerPerOrder()).subtract(allocated) : null;
        SalespersonInfo sp = resolveSalesperson(c);
        return new PricingView(c.getId(), c.getName(), configured, canEdit,
                owner == null ? null : owner.getId(), owner == null ? null : owner.getName(),
                effectiveTotal, configured ? p.getOwnerPerOrder() : null,
                allocated, company, company, items, configured ? p.getCreateTime() : null, configured ? p.getId() : null,
                lockedTotal != null, lockedTotal, SALESPERSON_FIXED_FEE,
                sp == null ? null : sp.contactId(), sp == null ? null : sp.name());
    }
}
