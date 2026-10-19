package com.zhyq.park.marketing.yunshan;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.crm.entity.Customer;
import com.zhyq.park.crm.mapper.CustomerMapper;
import com.zhyq.park.marketing.entity.*;
import com.zhyq.park.marketing.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

/** 下拉候选、保存和同步共用合同及本地仓库边界。 */
@Service
@RequiredArgsConstructor
public class YunshanCustomerScope {
    private static final Long TENANT = MyMetaObjectHandler.DEFAULT_TENANT_ID;
    private final CustomerMapper customers;
    private final MktCustomerOperationWarehouseMapper links;
    private final MktOperationWarehouseMapper sites;

    public LambdaQueryWrapper<Customer> signedCustomers(Long warehouseId) {
        if (warehouseId == null || warehouseId <= 0) throw new BizException("请选择当前加盟云仓");
        return new LambdaQueryWrapper<Customer>().eq(Customer::getTenantId, TENANT).apply("""
            EXISTS (SELECT 1 FROM crm_warehouse w WHERE w.id={0} AND w.tenant_id=crm_customer.tenant_id
              AND w.deleted=0 AND w.join_status NOT IN (7,8)) AND
            EXISTS (SELECT 1 FROM crm_service_contract sc
              WHERE sc.customer_id=crm_customer.id AND sc.tenant_id=crm_customer.tenant_id AND sc.deleted=0
                AND sc.status IN (4,5,6) AND sc.start_date <= {1} AND sc.end_date >= {1}
                AND (EXISTS (SELECT 1 FROM crm_contract_warehouse cw
                    WHERE cw.contract_id=sc.id AND cw.tenant_id=sc.tenant_id AND cw.deleted=0
                      AND cw.warehouse_id={0} AND cw.effective_from <= {1}
                      AND (cw.effective_to IS NULL OR cw.effective_to > {1}))
                  OR (sc.warehouse_id={0} AND NOT EXISTS (SELECT 1 FROM crm_contract_warehouse cw
                    WHERE cw.contract_id=sc.id AND cw.tenant_id=sc.tenant_id AND cw.deleted=0))))
            """, warehouseId, LocalDate.now(YunshanVolumeSyncService.SHANGHAI));
    }

    public void requireSigned(Long warehouseId, Long customerId) {
        if (customerId == null || customers.selectCount(signedCustomers(warehouseId).eq(Customer::getId, customerId)) == 0)
            throw new BizException("该客户与当前云仓没有有效的已签合同，请先完成合同签署或备案");
    }

    public List<MktOperationWarehouse> sites(Long warehouseId, Long customerId) {
        requireSigned(warehouseId, customerId);
        var rows = links.selectList(new LambdaQueryWrapper<MktCustomerOperationWarehouse>()
            .eq(MktCustomerOperationWarehouse::getTenantId, TENANT)
            .eq(MktCustomerOperationWarehouse::getWarehouseId, warehouseId)
            .eq(MktCustomerOperationWarehouse::getCustomerId, customerId).eq(MktCustomerOperationWarehouse::getStatus, 1));
        if (rows.isEmpty()) return List.of();
        return sites.selectList(new LambdaQueryWrapper<MktOperationWarehouse>()
            .eq(MktOperationWarehouse::getTenantId, TENANT).eq(MktOperationWarehouse::getWarehouseId, warehouseId)
            .eq(MktOperationWarehouse::getStatus, 1)
            .in(MktOperationWarehouse::getId, rows.stream().map(MktCustomerOperationWarehouse::getOperationWarehouseId).distinct().toList())
            .orderByAsc(MktOperationWarehouse::getId));
    }

    public MktOperationWarehouse requireSite(Long warehouseId, Long customerId, Long siteId) {
        return sites(warehouseId, customerId).stream().filter(s -> s.getId().equals(siteId)).findFirst()
            .orElseThrow(() -> new BizException("请先在客户与作业仓库中为该客户建立有效关联，再选择对应作业仓库"));
    }
}
