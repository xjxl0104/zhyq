package com.zhyq.park.crm;

import com.zhyq.park.crm.entity.Lead;
import com.zhyq.park.crm.mapper.LeadMapper;
import com.zhyq.park.crm.service.LeadImportService;
import com.zhyq.park.crm.service.LeadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登记表导入的解析。样例文件由真实工作簿裁剪而来（客户信息已替换为测试数据，
 * 真实手机号不进代码仓库），因此表头、合并方式、单元格类型与实际文件一致。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeadImportParseTest {

    @Mock private LeadMapper leadMapper;
    @Mock private LeadService leadService;

    private LeadImportService service;

    @BeforeEach
    void setUp() {
        service = new LeadImportService(leadMapper, leadService);
        when(leadMapper.selectCount(any())).thenReturn(0L); // 默认无重复
    }

    private MockMultipartFile sample() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/crm/lead-registry-sample.xlsx")) {
            assertNotNull(in, "样例文件缺失");
            return new MockMultipartFile("file", "登记表.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", in.readAllBytes());
        }
    }

    @Test
    void parsesEveryRegistryColumnByHeaderText() throws Exception {
        LeadImportService.ImportResult r = service.importWorkbook(sample());

        assertEquals(2, r.imported());
        assertEquals(0, r.skipped());
        assertTrue(r.errors().isEmpty(), () -> "不应有失败行: " + r.errors());

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadService, times(2)).create(captor.capture());
        Lead first = captor.getAllValues().get(0);

        assertEquals("测试甲", first.getContact());
        assertEquals("13900000001", first.getPhone());
        assertEquals("测试贸易", first.getCompany());
        assertEquals("电话咨询", first.getSource());
        assertEquals("电商", first.getCustomerType());
        assertEquals("仓库整租", first.getCoopMode());
        assertEquals("5000", first.getDemandArea());
        assertEquals("服装", first.getGoodsType());
        assertEquals("1万单", first.getOrderVolume());
        assertEquals("1年", first.getCoopPeriod());
        assertEquals("8元/㎡·月", first.getBudgetPrice());
        assertEquals("测试园区A", first.getIntentPark());
        assertEquals("广州市天河区", first.getRegion());
        assertEquals("A-高意向高价值", first.getGrade());
        assertEquals("测试员", first.getOwnerName());
        assertEquals("2026-07-20", String.valueOf(first.getRegisterDate()));
    }

    /** 登记表的「当前状态」是中文，要落成后端状态码 */
    @Test
    void mapsChineseStatusTextToCode() throws Exception {
        service.importWorkbook(sample());

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadService, times(2)).create(captor.capture());
        List<Lead> leads = captor.getAllValues();

        assertEquals(LeadService.ST_FOLLOWING, leads.get(0).getStatus());  // 跟进中
        assertEquals(LeadService.ST_QUOTED, leads.get(1).getStatus());     // 已报价/洽谈中
    }

    /** 同姓名+电话已存在则跳过：同一份文件重复导入不该产生副本 */
    @Test
    void skipsRowsAlreadyPresent() throws Exception {
        when(leadMapper.selectCount(any())).thenReturn(1L);

        LeadImportService.ImportResult r = service.importWorkbook(sample());

        assertEquals(0, r.imported());
        assertEquals(2, r.skipped());
        verify(leadService, never()).create(any());
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "x.xlsx", "application/vnd.ms-excel", new byte[0]);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.zhyq.park.common.exception.BizException.class, () -> service.importWorkbook(empty));
    }

    @Test
    void rejectsNonSpreadsheet() {
        MockMultipartFile txt = new MockMultipartFile("file", "a.xlsx", "text/plain", "not a workbook".getBytes());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.zhyq.park.common.exception.BizException.class, () -> service.importWorkbook(txt));
    }
}
