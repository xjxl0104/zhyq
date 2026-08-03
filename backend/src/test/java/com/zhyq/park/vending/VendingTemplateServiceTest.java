package com.zhyq.park.vending;

import com.zhyq.park.vending.model.VendingImportData;
import com.zhyq.park.vending.model.VendingImportType;
import com.zhyq.park.vending.service.VendingImportParser;
import com.zhyq.park.vending.service.VendingTemplateService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VendingTemplateServiceTest {
    private final VendingTemplateService templateService = new VendingTemplateService();
    private final VendingImportParser parser = new VendingImportParser();

    @ParameterizedTest
    @EnumSource(VendingImportType.class)
    void generatedTemplateRoundTrips(VendingImportType type) throws Exception {
        byte[] bytes = templateService.template(type);
        VendingImportData data = parser.parse(type, bytes);

        assertEquals(type, data.type());
        assertEquals(type.headers(), data.headers());
        assertTrue(data.rows().isEmpty());

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertNotNull(sheet.getPaneInformation());
            assertEquals("填写说明", workbook.getSheetAt(1).getSheetName());
        }
    }
}
