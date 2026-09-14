package com.zhyq.park.crm.service;

import com.zhyq.park.crm.entity.Channel;
import com.zhyq.park.crm.mapper.ChannelMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChannelImportServiceTest {

    private ChannelMapper mapper;
    private ChannelImportService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ChannelMapper.class);
        ChannelFollowService follow = mock(ChannelFollowService.class);
        when(follow.nextAgencyNo()).thenReturn("ZJ-0001");
        when(mapper.selectCount(any())).thenReturn(0L);
        service = new ChannelImportService(mapper, follow);
    }

    private List<Channel> inserted(int times) {
        ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
        verify(mapper, times(times)).insert(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void csvInGbkWithTitleRow() {
        String csv = "中介台账\r\n中介名称,联系人,电话,合作等级,佣金比例,合作协议,状态\r\n"
                + "甲地产,张三,13800000000,A,2%,已签,合作中\r\n,,,,,,\r\n乙经纪,李四,,C级,1.5,否,暂停合作\r\n";
        var file = new MockMultipartFile("file", "中介.csv", "text/csv", csv.getBytes(Charset.forName("GBK")));

        var result = service.importFile(file);

        assertThat(result.imported()).isEqualTo(2);
        List<Channel> list = inserted(2);
        assertThat(list.get(0).getName()).isEqualTo("甲地产");
        assertThat(list.get(0).getGrade()).isEqualTo("A-核心合作");
        assertThat(list.get(0).getCommissionRate()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(list.get(0).getAgreementSigned()).isEqualTo(1);
        assertThat(list.get(0).getStatus()).isEqualTo(1);
        assertThat(list.get(1).getStatus()).isEqualTo(0);
        assertThat(list.get(1).getAgreementSigned()).isEqualTo(0);
    }

    @Test
    void xlsx() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("中介");
            var h = sheet.createRow(0);
            h.createCell(0).setCellValue("公司名称");
            h.createCell(1).setCellValue("手机号");
            var r = sheet.createRow(1);
            r.createCell(0).setCellValue("丙中介");
            r.createCell(1).setCellValue(13900000000d);
            wb.write(out);
        }
        service.importFile(new MockMultipartFile("file", "a.xlsx", null, out.toByteArray()));
        Channel c = inserted(1).get(0);
        assertThat(c.getName()).isEqualTo("丙中介");
        assertThat(c.getPhone()).isEqualTo("13900000000");
        assertThat(c.getAgencyType()).isEqualTo("中介公司");
    }

    @Test
    void docxTable() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable t = doc.createTable(2, 2);
            t.getRow(0).getCell(0).setText("中介名称");
            t.getRow(0).getCell(1).setText("联系人");
            t.getRow(1).getCell(0).setText("丁经纪");
            t.getRow(1).getCell(1).setText("王五");
            doc.write(out);
        }
        service.importFile(new MockMultipartFile("file", "a.docx", null, out.toByteArray()));
        assertThat(inserted(1).get(0).getContact()).isEqualTo("王五");
    }
}
