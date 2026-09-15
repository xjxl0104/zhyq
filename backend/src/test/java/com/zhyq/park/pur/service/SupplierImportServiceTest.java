package com.zhyq.park.pur.service;

import com.zhyq.park.pur.entity.Supplier;
import com.zhyq.park.pur.mapper.SupplierMapper;
import com.zhyq.park.system.entity.SysDictData;
import com.zhyq.park.system.mapper.SysDictDataMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SupplierImportServiceTest {

    @Test
    void mapsCategoryLabelAndSkipsExisting() {
        SupplierMapper mapper = mock(SupplierMapper.class);
        SysDictDataMapper dict = mock(SysDictDataMapper.class);
        SysDictData d = new SysDictData();
        d.setLabel("物业服务");
        d.setValue("property");
        when(dict.selectList(any())).thenReturn(List.of(d));
        when(mapper.nextCode()).thenReturn("GYS-0005");
        // 第一行不存在,第二行已存在
        when(mapper.selectCount(any())).thenReturn(0L, 1L);

        String csv = "供应商名称,类别,联系电话,状态\n甲物业,物业服务,020-1,正常\n乙公司,办公采购,,停用\n";
        var result = new SupplierImportService(mapper, dict)
                .importFile(new MockMultipartFile("file", "s.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(mapper).insert(captor.capture());
        Supplier s = captor.getValue();
        assertThat(s.getCategory()).isEqualTo("property");
        assertThat(s.getCode()).isEqualTo("GYS-0005");
        assertThat(s.getStatus()).isEqualTo(1);
    }
}
