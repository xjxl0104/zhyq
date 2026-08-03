package com.zhyq.park.receivable;

import com.zhyq.park.receivable.model.ReceivableWorkbookData;
import com.zhyq.park.receivable.service.ReceivableWorkbookParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReceivableWorkbookParserTest {
    private final ReceivableWorkbookParser parser = new ReceivableWorkbookParser();

    @Test
    void parsesNineRowsAndExactTotals() throws Exception {
        ReceivableWorkbookData data = parser.parse(ReceivableWorkbookFixture.build());

        assertEquals("Sheet1", data.sheetName());
        assertEquals(3, data.headerRow());
        assertEquals(9, data.rows().size());
        assertEquals(new BigDecimal("84700"), data.totals().chargeArea());
        assertEquals(new BigDecimal("72501.6"), data.totals().actualArea());
        assertEquals(new BigDecimal("12198.4"), data.totals().sharedArea());
        assertEquals(new BigDecimal("167139857.679488"), data.totals().contractRentTotal());
        assertEquals(new BigDecimal("1214450"), data.totals().monthlyRent());
        assertEquals(new BigDecimal("169494"), data.totals().monthlyProperty());
        assertEquals(new BigDecimal("1383944"), data.totals().monthlyTotal());
        assertEquals(new BigDecimal("2712900"), data.totals().rentDeposit());
        assertEquals(new BigDecimal("301200"), data.totals().propertyDeposit());
    }

    @Test
    void preservesBlankAgreementAndFormulaSource() throws Exception {
        ReceivableWorkbookData data = parser.parse(ReceivableWorkbookFixture.build());

        assertNull(data.rows().get(0).agreementNoRaw());
        assertEquals("0.4*4700*30", data.rows().get(0).formulas().get("monthlyRent"));
        assertEquals(new BigDecimal("56400"), data.rows().get(0).monthlyRent());
    }

    @Test
    void rejectsWorkbookWithoutTheAuthoritativeHeaders() throws Exception {
        byte[] valid = ReceivableWorkbookFixture.build();
        valid[0] = 0;
        assertThrows(IllegalArgumentException.class, () -> parser.parse(valid));
    }
}
