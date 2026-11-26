package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktPublicSeaMeta;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktPublicSeaMetaMapper;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** 客户分派与合同共享一份业务归属；意向字段、ERP货主映射均不授予商家客户权限。 */
@Service
@RequiredArgsConstructor
public class MktCustomerAssignmentService {
    public static final int UNASSIGNED = 0;
    public static final int PENDING = 1;
    public static final int ACCEPTED = 2;
    public static final int REJECTED = 3;
    public static final String REBATE_BULK_5000 = "BULK_5000";
    public static final String REBATE_STANDARD = "STANDARD";
    public static final String REBATE_STRATEGIC = "STRATEGIC";
    public static final BigDecimal REBATE_PRICE_BULK_5000 = new BigDecimal("0.100");
    public static final BigDecimal REBATE_PRICE_STANDARD = new BigDecimal("0.150");
    private static final BigDecimal MAX_REBATE_AMOUNT = new BigDecimal("99999999999.999");
    private final CustomerMapper customers;
    private final MktWarehouseMapper warehouses;
    private final MktServiceContractMapper contracts;
    private final MktAuditService audit;
    private final MktCustomerProgressService progress;

    /** 非构造注入保持存量单测兼容；合同签约生效时同步完结公海状态释放专员在跟名额。 */
    @Autowired(required = false)
    private MktPublicSeaMetaMapper seaMetas;

    void setSeaMetas(MktPublicSeaMetaMapper seaMetas) {
        this.seaMetas = seaMetas;
    }

    public MktWarehouse requireAvailableWarehouse(Long warehouseId, Long projectId) {
        if (warehouseId == null || warehouseId <= 0) throw new BizException(400, "请选择承接云仓");
        MktWarehouse warehouse = warehouses.selectForUpdate(warehouseId);
        if (warehouse == null || !Integer.valueOf(MktWarehouseOnboardingService.JS_ONLINE).equals(warehouse.getJoinStatus())) {
            throw new BizException(409, "云仓不存在或尚未上线，不能接收客户");
        }
        if (!MktWarehouseOnboardingService.isYunshanMode(warehouse.getOrderMode()) && !Integer.valueOf(MktWarehouseOnboardingService.ERP_LIVE).equals(warehouse.getErpStatus())) {
            throw new BizException(409, "云仓尚未配置可用订单模式，请先选择人工导入或完成 ERP 接入");
        }
        if (projectId != null && warehouse.getProjectId() != null && !projectId.equals(warehouse.getProjectId())) {
            throw new BizException(403, "不能跨园区分派客户");
        }
        return warehouse;
    }

    @Transactional
    public void assign(Long customerId, Long warehouseId, String reason) {
        assign(customerId, warehouseId, reason, false, warehouseId == null ? null : REBATE_STANDARD, null);
    }

    @Transactional
    public void assign(Long customerId, Long warehouseId, String reason, boolean manualQuoteOverride) {
        assign(customerId, warehouseId, reason, manualQuoteOverride, warehouseId == null ? null : REBATE_STANDARD, null);
    }

    @Transactional
    public void assign(Long customerId, Long warehouseId, String reason, boolean manualQuoteOverride,
                       String rebatePolicyType, BigDecimal rebateUnitPrice) {
        reason = text(reason, "请填写分派或调整原因");
        Customer customer = requireLocked(customerId);
        if (Integer.valueOf(4).equals(customer.getServiceType())) throw new BizException("园区入驻客户不分派云仓");
        if (Integer.valueOf(3).equals(customer.getStatus())) throw new BizException("已流失客户须先恢复跟进后再分派");
        String resolvedPolicy = null;
        BigDecimal resolvedPrice = null;
        if (warehouseId != null) {
            ResolvedRebate rebate = resolveRebate(rebatePolicyType, rebateUnitPrice);
            resolvedPolicy = rebate.policyType();
            resolvedPrice = rebate.unitPrice();
        }
        boolean sameRebate = Objects.equals(resolvedPolicy, customer.getRebatePolicyType())
                && sameDecimal(resolvedPrice, customer.getRebateUnitPrice());
        if (Objects.equals(warehouseId, customer.getAssignedWarehouseId()) && sameRebate
                && (warehouseId == null || Integer.valueOf(PENDING).equals(customer.getWarehouseAssignmentStatus())
                || Integer.valueOf(ACCEPTED).equals(customer.getWarehouseAssignmentStatus()))) return;
        assertNoActiveContracts(customerId);
        MktWarehouse warehouse = warehouseId == null ? null : requireAvailableWarehouse(warehouseId, customer.getProjectId());
        if(warehouse!=null && !Objects.equals(warehouse.getTenantId(),customer.getTenantId()))
            throw new BizException(403,"不能跨运营主体分派客户");
        if(warehouse!=null && customer.getReferrerId()!=null && customer.getAssignedWarehouseId()==null) {
            String state=customers.initialQuoteState(customerId);
            if(state==null || "no_demand".equals(state))
                throw new BizException(409,"推荐客户请先完成详细报价需求并确认归属园区，再分配云仓");
            if("waiting".equals(state) || "failed".equals(state) || "partial".equals(state)) {
                if(!manualQuoteOverride) throw new BizException(409,"报价依据不足或 AI 评估失败，请确认人工分配并填写原因");
                String issue=switch(state){case "waiting"->"缺少可比报价"; case "partial"->"报价或产能待核实"; default->"AI 评估未完成";};
                reason="人工分配（"+issue+"）："+reason;
                if(reason.length()>500) throw new BizException(400,"请缩短人工分配原因");
            } else if(!"ready".equals(state)) {
                throw new BizException(409,"客户报价评估正在进行，请查看报告后再分配云仓");
            }
        }
        LambdaUpdateWrapper<Customer> update = versionGuard(customer)
                .set(Customer::getAssignedWarehouseId, warehouseId)
                .set(Customer::getWarehouseSalespersonId, null).set(Customer::getWarehouseSalesStage,0)
                .set(Customer::getWarehouseAssignmentStatus, warehouseId == null ? UNASSIGNED : PENDING)
                .set(Customer::getRebatePolicyType, resolvedPolicy)
                .set(Customer::getRebateUnitPrice, resolvedPrice)
                .set(Customer::getRebateConfirmedAt, null);
        if (customer.getProjectId() == null && warehouse != null && warehouse.getProjectId() != null) {
            update.set(Customer::getProjectId, warehouse.getProjectId());
        }
        requireUpdated(customers.update(null, update));
        String assignProgress = warehouseId == null ? "已取消云仓分派，等待重新对接"
                : "已分派云仓，等待商家确认承接（返点：" + resolvedPrice.toPlainString() + " 元/单）";
        progress.transition(customerId, warehouseId == null ? 0 : 30, assignProgress, "assigned");
        String auditReason = warehouseId == null ? reason
                : reason + "；平台服务费（返点）：" + rebatePolicyLabel(resolvedPolicy) + " " + resolvedPrice.toPlainString() + " 元/单";
        audit.log("customer.warehouse.assign", "customer", customerId, auditReason, customer.getAssignedWarehouseId(), warehouseId);
    }

    @Transactional
    public void accept(Long customerId, Long warehouseId) {
        Customer customer = requireAssigned(customerId, warehouseId);
        if (Integer.valueOf(3).equals(customer.getStatus())) throw new BizException("已流失客户不能承接");
        requireAvailableWarehouse(warehouseId, customer.getProjectId());
        if (Integer.valueOf(ACCEPTED).equals(customer.getWarehouseAssignmentStatus())) return;
        if (!Integer.valueOf(PENDING).equals(customer.getWarehouseAssignmentStatus())) throw new BizException("当前客户不在待承接状态");
        LocalDateTime confirmedAt = customer.getRebateUnitPrice() == null ? null : LocalDateTime.now();
        requireUpdated(customers.update(null, versionGuard(customer)
                .eq(Customer::getAssignedWarehouseId, warehouseId).eq(Customer::getWarehouseAssignmentStatus, PENDING)
                .set(Customer::getWarehouseAssignmentStatus, ACCEPTED)
                .set(Customer::getRebateConfirmedAt, confirmedAt)));
        progress.transition(customerId, 60, "云仓已确认承接，正在对接服务需求", "accepted");
        String auditNote = customer.getRebateUnitPrice() == null ? "云仓确认承接"
                : "云仓确认承接，已确认返点 " + customer.getRebateUnitPrice().toPlainString() + " 元/单（" + rebatePolicyLabel(customer.getRebatePolicyType()) + "）";
        audit.log("customer.warehouse.accept", "customer", customerId, auditNote);
    }

    @Transactional
    public void reject(Long customerId, Long warehouseId, String reason) {
        reason = text(reason, "请填写拒绝原因");
        Customer customer = requireAssigned(customerId, warehouseId);
        if (Integer.valueOf(REJECTED).equals(customer.getWarehouseAssignmentStatus())) return;
        if (!Integer.valueOf(PENDING).equals(customer.getWarehouseAssignmentStatus())) throw new BizException("只能拒绝待承接客户");
        assertNoActiveContracts(customerId);
        requireUpdated(customers.update(null, versionGuard(customer)
                .eq(Customer::getAssignedWarehouseId, warehouseId).eq(Customer::getWarehouseAssignmentStatus, PENDING)
                .set(Customer::getWarehouseAssignmentStatus, REJECTED)
                .set(Customer::getRebateConfirmedAt, null)));
        progress.transition(customerId, 0, "云仓未承接，等待重新分派", "rejected");
        audit.log("customer.warehouse.reject", "customer", customerId, reason);
    }

    @Transactional
    public void updateProgress(Long customerId, String summary) {
        updateProgress(customerId, summary, null);
    }
    @Transactional
    public void updateProgress(Long customerId, String summary, Integer percentage) {
        publishProgress(requireLocked(customerId), summary, percentage, "admin");
    }

    @Transactional
    public void updateWarehouseProgress(Long customerId, Long warehouseId, String summary) {
        updateWarehouseProgress(customerId, warehouseId, summary, null);
    }
    @Transactional
    public void updateWarehouseProgress(Long customerId, Long warehouseId, String summary, Integer percentage) {
        Customer customer = requireAssigned(customerId, warehouseId);
        if (!Integer.valueOf(ACCEPTED).equals(customer.getWarehouseAssignmentStatus())) throw new BizException(403, "仅已承接客户可以发布进度");
        publishProgress(customer, summary, percentage, "warehouse");
    }

    /** 调用者处于合同事务：锁定同一客户并核验当前承接，防止签署时分派已变化。 */
    @Transactional
    public Customer validateContractWarehouse(MktServiceContract contract) {
        Customer customer = requireLocked(contract.getCustomerId());
        if (Integer.valueOf(3).equals(customer.getStatus())) throw new BizException("客户已流失，不能签署服务合同");
        if (contract.getWarehouseId() == null) contract.setWarehouseId(customer.getAssignedWarehouseId());
        if (contract.getWarehouseId() == null || !Objects.equals(contract.getWarehouseId(), customer.getAssignedWarehouseId())
                || !Integer.valueOf(ACCEPTED).equals(customer.getWarehouseAssignmentStatus())) {
            throw new BizException("合同云仓必须是后台分派且已确认承接的云仓");
        }
        requireAvailableWarehouse(contract.getWarehouseId(), customer.getProjectId());
        if (contract.getProjectId() != null && customer.getProjectId() != null && !contract.getProjectId().equals(customer.getProjectId())) {
            throw new BizException(403, "合同与客户不属于同一园区");
        }
        return customer;
    }

    @Transactional
    public void contractEffective(MktServiceContract contract) {
        Customer customer = validateContractWarehouse(contract);
        if (!Objects.equals(contract.getPartnerId(), customer.getReferrerId())) throw new BizException("合同推荐伙伴与客户当前归属不一致，请重新核对合同");
        markPublicSeaSigned(customer.getId());
        if (Integer.valueOf(2).equals(customer.getStatus())) {
            progress.transition(customer.getId(), 100, "新增服务合同已生效，继续为客户提供服务", "signed");
            return;
        }
        requireUpdated(customers.update(null, versionGuard(customer).eq(Customer::getStatus, 1).set(Customer::getStatus, 2)));
        progress.transition(customer.getId(), 100, "服务合同已签约生效，服务对接完成", "signed");
        audit.log("customer.contract.effective", "customer", customer.getId(), "服务合同生效");
    }

    private void markPublicSeaSigned(Long customerId) {
        if (seaMetas == null || customerId == null) return;
        seaMetas.update(null, new LambdaUpdateWrapper<MktPublicSeaMeta>()
                .eq(MktPublicSeaMeta::getCustomerId, customerId)
                .ne(MktPublicSeaMeta::getClaimStatus, "SIGNED")
                .set(MktPublicSeaMeta::getClaimStatus, "SIGNED")
                .set(MktPublicSeaMeta::getSlaDeadlineAt, null)
                .set(MktPublicSeaMeta::getUpdatedAt, LocalDateTime.now()));
    }

    /** 最后一份有效合同关闭时返回跟进中；有其他履约合同则保留已签约。返回是否已无有效合同。 */
    @Transactional
    public boolean contractClosed(Long customerId) {
        Customer customer = requireLocked(customerId);
        if (activeContractCount(customerId) > 0) {
            progress.transition(customerId, 100, "一份服务合同已结束，其他有效合同继续履约", "signed");
            return false;
        }
        boolean releaseAssignment = contracts.selectCount(new LambdaQueryWrapper<MktServiceContract>()
                .eq(MktServiceContract::getCustomerId, customerId).in(MktServiceContract::getStatus, 1, 2, 3)) == 0;
        boolean signed = Integer.valueOf(2).equals(customer.getStatus());
        if (signed || (releaseAssignment && customer.getAssignedWarehouseId() != null)) {
            LambdaUpdateWrapper<Customer> update = versionGuard(customer);
            if (signed) update.set(Customer::getStatus, 1);
            if (releaseAssignment) update.set(Customer::getAssignedWarehouseId, null)
                    .set(Customer::getWarehouseSalespersonId,null).set(Customer::getWarehouseSalesStage,0)
                    .set(Customer::getWarehouseAssignmentStatus, UNASSIGNED);
            requireUpdated(customers.update(null, update));
            int remainingStage = releaseAssignment ? 0 : Integer.valueOf(ACCEPTED).equals(customer.getWarehouseAssignmentStatus()) ? 60
                    : Integer.valueOf(PENDING).equals(customer.getWarehouseAssignmentStatus()) ? 30 : 0;
            progress.transition(customerId, remainingStage, "有效服务合同已结束，返回服务跟进；伙伴成交归属继续有效", "following");
            audit.log("customer.contract.closed", "customer", customerId, "有效服务合同已结束，返回跟进");
        }
        if (seaMetas != null && customer.getReferrerId() == null) {
            seaMetas.update(null, new LambdaUpdateWrapper<MktPublicSeaMeta>()
                    .eq(MktPublicSeaMeta::getCustomerId, customerId)
                    .eq(MktPublicSeaMeta::getClaimStatus, "SIGNED")
                    .set(MktPublicSeaMeta::getClaimStatus, "UNCLAIMED")
                    .set(MktPublicSeaMeta::getSeaSource, "EXPIRE_RETURN")
                    .set(MktPublicSeaMeta::getClaimHolderType, null)
                    .set(MktPublicSeaMeta::getClaimHolderId, null)
                    .set(MktPublicSeaMeta::getClaimHolderName, null)
                    .set(MktPublicSeaMeta::getSlaDeadlineAt, null)
                    .set(MktPublicSeaMeta::getVisitProtectedUntil, null)
                    .set(MktPublicSeaMeta::getFirstTouchedAt, null)
                    .set(MktPublicSeaMeta::getBlockNote, "历史服务合同已结束，回流内部公海待续签或换仓激活")
                    .set(MktPublicSeaMeta::getUpdatedAt, LocalDateTime.now()));
        }
        return true;
    }

    public void assertNoActiveContracts(Long customerId) {
        if (activeContractCount(customerId) > 0) throw new BizException("客户有生效或履约中的合同，不能直接调整云仓或归属；请先完成合同变更或终止");
    }

    public Map<String, Object> assignmentView(Customer customer) {
        Map<String, Object> out = new HashMap<>();
        out.put("intendedWarehouseId", customer.getIntendedWarehouseId());
        out.put("intendedWarehouseName", warehouseName(customer.getIntendedWarehouseId()));
        out.put("assignedWarehouseId", customer.getAssignedWarehouseId());
        out.put("assignedWarehouseName", warehouseName(customer.getAssignedWarehouseId()));
        out.put("warehouseAssignmentStatus", customer.getWarehouseAssignmentStatus() == null ? UNASSIGNED : customer.getWarehouseAssignmentStatus());
        out.put("rebatePolicyType", customer.getRebatePolicyType());
        out.put("rebatePolicyLabel", rebatePolicyLabel(customer.getRebatePolicyType()));
        out.put("rebateUnitPrice", customer.getRebateUnitPrice());
        out.put("rebateConfirmedAt", customer.getRebateConfirmedAt());
        out.put("publicProgress", customer.getPublicProgress());
        out.put("progressUpdatedAt", customer.getProgressUpdatedAt());
        out.putAll(progress.view(customer));
        return out;
    }

    public Map<String, Object> warehouseView(Customer customer) {
        Map<String, Object> out = new HashMap<>();
        out.put("id", customer.getId()); out.put("name", customer.getName()); out.put("industry", customer.getIndustry());
        out.put("serviceType", customer.getServiceType()); out.put("status", customer.getStatus()); out.put("grade", customer.getGrade());
        out.put("warehouseAssignmentStatus", customer.getWarehouseAssignmentStatus());
        out.put("rebatePolicyType", customer.getRebatePolicyType());
        out.put("rebatePolicyLabel", rebatePolicyLabel(customer.getRebatePolicyType()));
        out.put("rebateUnitPrice", customer.getRebateUnitPrice());
        out.put("rebateConfirmedAt", customer.getRebateConfirmedAt());
        out.put("publicProgress", customer.getPublicProgress()); out.put("progressUpdatedAt", customer.getProgressUpdatedAt());
        out.putAll(progress.view(customer));
        if (Integer.valueOf(PENDING).equals(customer.getWarehouseAssignmentStatus()) || Integer.valueOf(ACCEPTED).equals(customer.getWarehouseAssignmentStatus())) {
            out.put("contact", customer.getContact());
            out.put("brandName", customer.getBrandName()); out.put("demand", customer.getDemandArea());
            if (customer.getIntakeRemark() != null) out.put("remark", customer.getIntakeRemark());
            out.put("requirements", MktReferralRequirements.parse(customer.getFulfillmentRequirements()));
            String phone = customer.getPhone();
            out.put("phone", phone == null || phone.length() < 7 ? null : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4));
        }
        return out;
    }

    public record ResolvedRebate(String policyType, BigDecimal unitPrice) {}

    public static ResolvedRebate resolveRebate(String policyType, BigDecimal customPrice) {
        if (!StringUtils.hasText(policyType)) throw new BizException(400, "请选择平台服务费（返点）政策");
        String normalized = policyType.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case REBATE_BULK_5000 -> new ResolvedRebate(REBATE_BULK_5000, REBATE_PRICE_BULK_5000);
            case REBATE_STANDARD -> new ResolvedRebate(REBATE_STANDARD, REBATE_PRICE_STANDARD);
            case REBATE_STRATEGIC -> {
                if (customPrice == null || customPrice.signum() <= 0 || customPrice.compareTo(MAX_REBATE_AMOUNT) > 0
                        || customPrice.stripTrailingZeros().scale() > 3) {
                    throw new BizException(400, "战略客户请填写大于 0 且最多 3 位小数的返点单价（元/单）");
                }
                BigDecimal afterPlatform = customPrice.subtract(MktCustomerPricingService.serviceFee(customPrice));
                if (afterPlatform.compareTo(MktCustomerPricingService.SALESPERSON_FIXED_FEE) < 0) {
                    throw new BizException(400, "战略客户返点单价须足以覆盖 10% 平台服务费和 0.020 元/单业务员佣金（最低 0.023 元/单）");
                }
                yield new ResolvedRebate(REBATE_STRATEGIC, customPrice.setScale(3, java.math.RoundingMode.UNNECESSARY));
            }
            default -> throw new BizException(400, "不支持的平台服务费（返点）政策：" + policyType);
        };
    }

    public static String rebatePolicyLabel(String policyType) {
        if (policyType == null) return null;
        return switch (policyType) {
            case REBATE_BULK_5000 -> "日均 5000 单及以上（大批量订单）";
            case REBATE_STANDARD -> "日均 5000 单以下（非批量订单）";
            case REBATE_STRATEGIC -> "战略客户（一客一议）";
            default -> policyType;
        };
    }

    private static boolean sameDecimal(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private Customer requireAssigned(Long customerId, Long warehouseId) {
        Customer customer = requireLocked(customerId);
        if (warehouseId == null || !warehouseId.equals(customer.getAssignedWarehouseId())) throw new BizException(403, "无权访问该客户");
        return customer;
    }

    private Customer requireLocked(Long id) {
        Customer customer = id == null ? null : customers.selectForUpdate(id);
        if (customer == null) throw new BizException("客户不存在");
        return customer;
    }

    private long activeContractCount(Long customerId) {
        return contracts.selectCount(new LambdaQueryWrapper<MktServiceContract>().eq(MktServiceContract::getCustomerId, customerId)
                .in(MktServiceContract::getStatus, MktServiceContractService.ST_EFFECTIVE, MktServiceContractService.ST_PERFORMING, MktServiceContractService.ST_AMENDING));
    }

    private void publishProgress(Customer customer, String summary, Integer percentage, String source) {
        summary = text(summary, "请填写可向伙伴公开的跟进进度");
        progress.publish(customer.getId(), summary, percentage, source);
        audit.log("customer.progress.publish", "customer", customer.getId(), "发布伙伴可见进度", customer.getPublicProgress(), summary);
    }

    private String warehouseName(Long id) {
        MktWarehouse warehouse = id == null ? null : warehouses.selectById(id);
        return warehouse == null ? null : warehouse.getName();
    }

    private static LambdaUpdateWrapper<Customer> versionGuard(Customer customer) {
        return new LambdaUpdateWrapper<Customer>().eq(Customer::getId, customer.getId())
                .eq(customer.getVersion() != null, Customer::getVersion, customer.getVersion()).setSql("version = version + 1");
    }

    private static String text(String value, String error) {
        if (!StringUtils.hasText(value) || value.trim().length() > 500) throw new BizException(error + "（1–500 字）");
        return value.trim();
    }

    private static void requireUpdated(int updated) {
        if (updated != 1) throw new BizException(409, "客户状态已变化，请刷新后重试");
    }
}
