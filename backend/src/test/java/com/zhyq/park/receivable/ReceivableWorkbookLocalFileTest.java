package com.zhyq.park.receivable;

import com.zhyq.park.receivable.model.ReceivableWorkbookData;
import com.zhyq.park.receivable.service.ReceivableWorkbookParser;
import com.zhyq.park.receivable.service.ReceivableRuleParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceivableWorkbookLocalFileTest {
    @Test
    @EnabledIfSystemProperty(named = "receivableWorkbook", matches = ".+")
    void parsesUserWorkbookWithoutCommittingIt() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(System.getProperty("receivableWorkbook")));
        ReceivableWorkbookData data = new ReceivableWorkbookParser().parse(bytes);

        assertEquals(9, data.rows().size());
        assertEquals(new BigDecimal("167139857.679488"), data.totals().contractRentTotal());
        assertEquals(new BigDecimal("1383944"), data.totals().monthlyTotal());
        ReceivableRuleParser ruleParser = new ReceivableRuleParser();
        assertTrue(data.rows().stream().allMatch(row ->
                row.rentAccountRaw() == null || ruleParser.parseAccount(row.rentAccountRaw()).isPresent()));
        assertTrue(data.rows().stream().allMatch(row ->
                row.propertyAccountRaw() == null || ruleParser.parseAccount(row.propertyAccountRaw()).isPresent()));
    }
}
