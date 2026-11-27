package com.zhyq.park.energy.service;

import com.zhyq.park.energy.service.NoticeDocumentAiService.CheckItem;
import com.zhyq.park.energy.service.NoticeDocumentAiService.ExtractedFields;
import com.zhyq.park.energy.service.NoticeDocumentAiService.NoticeCandidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NoticeDocumentAiServiceTest {

    @Test
    void receiptParseAndStrictMatchPassesOnlyWhenAllFourFieldsMatch() {
        String bankText = """
                中国工商银行网上银行电子回单
                付款人名称：广州云帕供应链管理有限公司
                收款人名称：广州数智云仓产业园运营管理有限公司
                收款人账号：4405 0147 0101 0000 1234
                交易金额：RMB 34,983.86
                """;
        ExtractedFields ex = NoticeDocumentAiService.parseReceiptText(bankText, "PDF_TEXT");
        assertEquals("广州云帕供应链管理有限公司", ex.payerName());
        assertEquals("广州数智云仓产业园运营管理有限公司", ex.payeeName());
        assertEquals("44050147010100001234", ex.payeeAccount());
        assertEquals(new BigDecimal("34983.86"), ex.amount());

        NoticeCandidate hit = new NoticeCandidate(
                10L, "DIPARK-202610-01", "广州云帕供应链管理有限公司", "水电/垃圾费",
                "广州数智云仓产业园运营管理有限公司",
                "户名：广州数智云仓产业园运营管理有限公司\n账号：44050147010100001234",
                Set.of("44050147010100001234"),
                new BigDecimal("34983.86")
        );
        List<CheckItem> okChecks = NoticeDocumentAiService.evaluateReceipt(hit, ex);
        assertTrue(okChecks.stream().allMatch(CheckItem::passed));

        // 金额差 0.01 元也必须拦截，交由人工核对
        NoticeCandidate wrongAmt = new NoticeCandidate(
                10L, "DIPARK-202610-01", "广州云帕供应链管理有限公司", "水电/垃圾费",
                "广州数智云仓产业园运营管理有限公司", "", Set.of("44050147010100001234"),
                new BigDecimal("34983.85")
        );
        assertFalse(NoticeDocumentAiService.evaluateReceipt(wrongAmt, ex).stream().allMatch(CheckItem::passed));

        // 收款账号不匹配必须拦截
        NoticeCandidate wrongAcc = new NoticeCandidate(
                10L, "DIPARK-202610-01", "广州云帕供应链管理有限公司", "水电/垃圾费",
                "广州数智云仓产业园运营管理有限公司", "", Set.of("44050147010100009999"),
                new BigDecimal("34983.86")
        );
        assertFalse(NoticeDocumentAiService.evaluateReceipt(wrongAcc, ex).stream().allMatch(CheckItem::passed));
    }

    @Test
    void invoiceParseAndStrictMatchWorksForShuzhiAndChangji() {
        String invoiceText = """
                电子发票（普通发票）
                发票号码：26442000000123456789
                购买方信息 名称：广州云帕供应链管理有限公司 销售方信息 名称：广州昌记物流有限公司
                价税合计（大写）玖万玖仟元整 （小写）¥99,000.00
                """;
        ExtractedFields ex = NoticeDocumentAiService.parseInvoiceText(invoiceText, "PDF_TEXT");
        assertEquals("广州云帕供应链管理有限公司", ex.buyerName());
        assertEquals("广州昌记物流有限公司", ex.sellerName());
        assertEquals(new BigDecimal("99000.00"), ex.taxInclusiveAmount());
        assertEquals("26442000000123456789", ex.invoiceNo());

        NoticeCandidate hit = new NoticeCandidate(
                20L, "DIPARK-ZJ-202610-01", "广州云帕供应链管理有限公司", "租金",
                "广州昌记物流有限公司", "", Set.of(),
                new BigDecimal("99000.00")
        );
        assertTrue(NoticeDocumentAiService.evaluateInvoice(hit, ex).stream().allMatch(CheckItem::passed));

        // 非数智云仓/昌记开具的发票必须拦截
        ExtractedFields foreignSeller = new ExtractedFields(
                null, null, null, null,
                "广州云帕供应链管理有限公司", "其他第三方有限公司", new BigDecimal("99000.00"), "12345678", "PDF_TEXT"
        );
        assertFalse(NoticeDocumentAiService.evaluateInvoice(hit, foreignSeller).stream().allMatch(CheckItem::passed));
    }

    @Test
    void ocrSpacedTwoColumnBankReceiptParsesCleanly() {
        String ocrText = """
                银 行 电 子 回 单
                付 款 人                           收 款 人
                户 名 ： 广 州 云 帕 供 应 链 管 理 有 限 公 司   户 名 ： 广 州 市 昌 记 供 应 链 有 限 公 司
                账 号 ： 4400 1234 5678 9012                 账 号 ： 6600 7523 9348
                金 额 ： ¥ 99,000.00
                """;
        ExtractedFields ex = NoticeDocumentAiService.parseReceiptText(ocrText, "OCR");
        assertEquals("广州云帕供应链管理有限公司", ex.payerName());
        assertEquals("广州市昌记供应链有限公司", ex.payeeName());
        assertEquals("660075239348", ex.payeeAccount());
        assertEquals(new BigDecimal("99000.00"), ex.amount());
    }

    @Test
    void rapidOcrRecognizesRealIcbcReceiptImageWhenPresent() throws Exception {
        java.nio.file.Path sample = java.nio.file.Path.of(
                "C:/Users/27896/.gemini/antigravity/brain/b8db1729-839e-4646-97e6-1a92bfb60dc1/.user_uploaded/media_1795750657006_f2cc25c4.jpg");
        if (!java.nio.file.Files.isRegularFile(sample)) return;
        RapidOcrEngine engine = new RapidOcrEngine();
        String ocrText = engine.recognize(java.nio.file.Files.readAllBytes(sample), "jpg");
        System.out.println("=== RAPID OCR REAL ICBC OUTPUT ===\n" + ocrText + "\n=== END ===");
        ExtractedFields ex = NoticeDocumentAiService.parseReceiptText(ocrText, "RAPID_OCR");
        System.out.println("EXTRACTED = " + ex);
        assertEquals("广州昌泰供应链科技有限公司", ex.payerName());
        assertEquals("广州数智云仓产业园运营有限公司", ex.payeeName());
        assertEquals("44050129162500000277", ex.payeeAccount());
        assertEquals(new BigDecimal("9400.00"), ex.amount());
    }
}
