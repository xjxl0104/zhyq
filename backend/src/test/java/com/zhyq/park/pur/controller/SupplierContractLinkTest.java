package com.zhyq.park.pur.controller;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.pur.entity.Supplier;
import com.zhyq.park.pur.entity.SupplierContract;
import com.zhyq.park.pur.mapper.SupplierContractMapper;
import com.zhyq.park.pur.mapper.SupplierMapper;
import com.zhyq.park.pur.service.SupplierContractImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierContractLinkTest {
    @Mock private SupplierContractMapper contractMapper;
    @Mock private SupplierMapper supplierMapper;
    @Mock private SupplierContractImportService importService;

    private SupplierContractController controller() {
        return new SupplierContractController(contractMapper, supplierMapper, importService);
    }

    @Test
    void addendumMustReferenceMainContractOfSameSupplier() {
        SupplierContract parent = new SupplierContract();
        parent.setId(9L);
        parent.setSupplierId(2L);
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        when(supplierMapper.selectById(1L)).thenReturn(supplier);
        when(contractMapper.selectById(9L)).thenReturn(parent);

        SupplierContract child = new SupplierContract();
        child.setSupplierId(1L);
        child.setParentContractId(9L);
        child.setName("补充协议一");

        assertThatThrownBy(() -> controller().add(child))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("同一供应商");
        verify(contractMapper, never()).insert(any(SupplierContract.class));
    }

    @Test
    void addendumCannotReferenceAnotherAddendum() {
        SupplierContract parent = new SupplierContract();
        parent.setId(9L);
        parent.setSupplierId(1L);
        parent.setParentContractId(8L);
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        when(supplierMapper.selectById(1L)).thenReturn(supplier);
        when(contractMapper.selectById(9L)).thenReturn(parent);

        SupplierContract child = new SupplierContract();
        child.setSupplierId(1L);
        child.setParentContractId(9L);
        child.setName("补充协议二");

        assertThatThrownBy(() -> controller().add(child))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("主合同");
        verify(contractMapper, never()).insert(any(SupplierContract.class));
    }
}
