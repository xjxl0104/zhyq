package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.MktServiceContract;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.mapper.MktServiceContractMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final CustomerMapper customers;
    private final MktWarehouseMapper warehouses;
    private final MktServiceContractMapper contracts;
    private final MktAuditService audit;

    public MktWarehouse requireAvailableWarehouse(Long warehouseId, Long projectId) {
        if (warehouseId == null || warehouseId <= 0) throw new BizException("请选择承接云仓");
        MktWarehouse warehouse = warehouses.selectById(warehouseId);
        if (warehouse == null || !Integer.valueOf(MktWarehouseOnboardingService.JS_ONLINE).equals(warehouse.getJoinStatus())) {
            throw new BizException("云仓不存在或尚未上线，不能接收客户");
        }
        if (!"manual".equals(warehouse.getOrderMode()) && !Integer.valueOf(MktWarehouseOnboardingService.ERP_LIVE).equals(warehouse.getErpStatus())) {
            throw new BizException("云仓尚未配置可用订单模式，请先选择人工导入或完成 ERP 接入");
        }
        if (projectId != null && warehouse.getProjectId() != null && !projectId.equals(warehouse.getProjectId())) {
            throw new BizException(403, "不能跨园区分派客户");
        }
        return warehouse;
    }

    @Transactional
    public void assign(Long customerId, Long warehouseId, String reason) {
        reason = text(reason, "请填写分派或调整原因");
        Customer customer = requireLocked(customerId);
        if (Integer.valueOf(4).equals(customer.getServiceType())) throw new BizException("园区入驻客户不分派云仓");
        if (Integer.valueOf(3).equals(customer.getStatus())) throw new BizException("已流失客户须先恢复跟进后再分派");
        if (Objects.equals(warehouseId, customer.getAssignedWarehouseId())
                && (warehouseId == null || Integer.valueOf(PENDING).equals(customer.getWarehouseAssignmentStatus())
                || Integer.valueOf(ACCEPTED).equals(customer.getWarehouseAssignmentStatus()))) return;
        assertNoActiveContracts(customerId);
        MktWarehouse warehouse = warehouseId == null ? null : requireAvailableWarehouse(warehouseId, customer.getProjectId());
        LambdaUpdateWrapper<Customer> update = versionGuard(customer)
                .set(Customer::getAssignedWarehouseId, warehouseId)
                .set(Customer::getWarehouseAssignmentStatus, warehouseId == null ? UNASSIGNED : PENDING);
        if (customer.getProjectId() == null && warehouse != null && warehouse.getProjectId() != null) {
            update.set(Customer::getProjectId, warehouse.getProjectId());
        }
        requireUpdated(customers.update(null, update));
        audit.log("customer.warehouse.assign", "customer", customerId, reason, customer.getAssignedWarehouseId(), warehouseId);
    }

    @Transactional
    public void accept(Long customerId, Long warehouseId) {
        Customer customer = requireAssigned(customerId, warehouseId);
        if (Integer.valueOf(3).equals(customer.getStatus())) throw new BizException("已流失客户不能承接");
        requireAvailableWarehouse(warehouseId, customer.getProjectId());
        if (Integer.valueOf(ACCEPTED).equals(customer.getWarehouseAssignmentStatus())) return;
        if (!Integer.valueOf(PENDING).equals(customer.getWarehouseAssignmentStatus())) throw new BizException("当前客户不在待承接状态");
        requireUpdated(customers.update(null, versionGuard(customer)
                .eq(Customer::getAssignedWarehouseId, warehouseId).eq(Customer::getWarehouseAssignmentStatus, PENDING)
                .set(Customer::getWarehouseAssignmentStatus, ACCEPTED)));
        audit.log("customer.warehouse.accept", "customer", customerId, "云仓确认承接");
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
                .set(Customer::getWarehouseAssignmentStatus, REJECTED)));
        audit.log("customer.warehouse.reject", "customer", customerId, reason);
    }

    @Transactional
    public void updateProgress(Long customerId, String summary) {
        publishProgress(requireLocked(customerId), summary);
    }

    @Transactional
    public void updateWarehouseProgress(Long customerId, Long warehouseId, String summary) {
        Customer customer = requireAssigned(customerId, warehouseId);
        if (!Integer.valueOf(ACCEPTED).equals(customer.getWarehouseAssignmentStatus())) throw new BizException(403, "仅已承接客户可以发布进度");
        publishProgress(customer, summary);
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
        if (Integer.valueOf(2).equals(customer.getStatus())) return;
        requireUpdated(customers.update(null, versionGuard(customer).eq(Customer::getStatus, 1).set(Customer::getStatus, 2)));
        audit.log("customer.contract.effective", "customer", customer.getId(), "服务合同生效");
    }

    /** 最后一份有效合同关闭时返回跟进中；有其他履约合同则保留已签约。返回是否已无有效合同。 */
    @Transactional
    public boolean contractClosed(Long customerId) {
        Customer customer = requireLocked(customerId);
        if (activeContractCount(customerId) > 0) return false;
        if (Integer.valueOf(2).equals(customer.getStatus())) {
            requireUpdated(customers.update(null, versionGuard(customer).eq(Customer::getStatus, 2).set(Customer::getStatus, 1)));
            audit.log("customer.contract.closed", "customer", customerId, "有效服务合同已结束，返回跟进");
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
        out.put("publicProgress", customer.getPublicProgress());
        out.put("progressUpdatedAt", customer.getProgressUpdatedAt());
        return out;
    }

    public Map<String, Object> warehouseView(Customer customer) {
        Map<String, Object> out = new HashMap<>();
        out.put("id", customer.getId()); out.put("name", customer.getName()); out.put("industry", customer.getIndustry());
        out.put("serviceType", customer.getServiceType()); out.put("status", customer.getStatus()); out.put("grade", customer.getGrade());
        out.put("warehouseAssignmentStatus", customer.getWarehouseAssignmentStatus());
        out.put("publicProgress", customer.getPublicProgress()); out.put("progressUpdatedAt", customer.getProgressUpdatedAt());
        if (Integer.valueOf(PENDING).equals(customer.getWarehouseAssignmentStatus()) || Integer.valueOf(ACCEPTED).equals(customer.getWarehouseAssignmentStatus())) {
            out.put("contact", customer.getContact());
            String phone = customer.getPhone();
            out.put("phone", phone == null || phone.length() < 7 ? null : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4));
        }
        return out;
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

    private void publishProgress(Customer customer, String summary) {
        summary = text(summary, "请填写可向伙伴公开的跟进进度");
        requireUpdated(customers.update(null, versionGuard(customer).set(Customer::getPublicProgress, summary)
                .set(Customer::getProgressUpdatedAt, LocalDateTime.now())));
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
