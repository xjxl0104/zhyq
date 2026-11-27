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
                10L, "DIPARK-202610-01", "广州云帕供应链管理有限公司", "水电/垃圾费", "2026-10",
                "广州数智云仓产业园运营管理有限公司",
                "户名：广州数智云仓产业园运营管理有限公司\n账号：44050147010100001234",
                Set.of("44050147010100001234"),
                new BigDecimal("34983.86")
        );
        List<CheckItem> okChecks = NoticeDocumentAiService.evaluateReceipt(hit, ex);
        assertTrue(okChecks.stream().allMatch(CheckItem::passed));

        // 金额差 0.01 元也必须拦截，交由人工核对
        NoticeCandidate wrongAmt = new NoticeCandidate(
                10L, "DIPARK-202610-01", "广州云帕供应链管理有限公司", "水电/垃圾费", "2026-10",
                "广州数智云仓产业园运营管理有限公司", "", Set.of("44050147010100001234"),
                new BigDecimal("34983.85")
        );
        assertFalse(NoticeDocumentAiService.evaluateReceipt(wrongAmt, ex).stream().allMatch(CheckItem::passed));

        // 收款账号不匹配必须拦截
        NoticeCandidate wrongAcc = new NoticeCandidate(
                10L, "DIPARK-202610-01", "广州云帕供应链管理有限公司", "水电/垃圾费", "2026-10",
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
                20L, "DIPARK-ZJ-202610-01", "广州云帕供应链管理有限公司", "租金", "2026-10",
                "广州昌记物流有限公司", "", Set.of(),
                new BigDecimal("99000.00")
        );
        assertTrue(NoticeDocumentAiService.evaluateInvoice(hit, ex).stream().allMatch(CheckItem::passed));

        // 非数智云仓/昌记开具的发票必须拦截
        ExtractedFields foreignSeller = new ExtractedFields(
                null, null, null, null,
                "广州云帕供应链管理有限公司", "其他第三方有限公司", new BigDecimal("99000.00"), "12345678",
                null, null, "PDF_TEXT", null
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

    @Test
    void clearElfExecStackFlagClearsPtGnuStackXBit() throws Exception {
        // 构造最小 ELF64 little-endian 头 + 1 个 PT_GNU_STACK(p_flags=R|W|X=0x7) 程序头
        byte[] elf = new byte[64 + 56];
        elf[0] = 0x7F; elf[1] = 'E'; elf[2] = 'L'; elf[3] = 'F';
        elf[4] = 2; // ELF64
        elf[5] = 1; // little-endian
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(elf).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.putLong(32, 64L);          // e_phoff
        bb.putShort(54, (short) 56);  // e_phentsize
        bb.putShort(56, (short) 1);   // e_phnum
        bb.putInt(64, 0x6474e551);    // PT_GNU_STACK
        bb.putInt(68, 0x7);           // PF_R | PF_W | PF_X

        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("elf-test-", ".so");
        try {
            java.nio.file.Files.write(tmp, elf);
            assertTrue(RapidOcrEngine.clearElfExecStackFlag(tmp));
            // 幂等：再次调用返回 false，且标志位已变为 PF_R | PF_W (0x6)
            assertFalse(RapidOcrEngine.clearElfExecStackFlag(tmp));
            byte[] after = java.nio.file.Files.readAllBytes(tmp);
            int flags = java.nio.ByteBuffer.wrap(after).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(68);
            assertEquals(0x6, flags);
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }

    @Test
    void cleanExtractedNameStripsTwoColumnVerticalHeaderNoiseWithoutCorruptingValidNames() {
        // 实测场景：双栏回单右侧竖排「收款人」的「收」字落到付款人户名同行末尾
        assertEquals("广州好易点数字服务有限公司",
                NoticeDocumentAiService.cleanExtractedName("广州好易点数字服务有限公司 收"));
        assertEquals("广州好易点数字服务有限公司",
                NoticeDocumentAiService.cleanExtractedName("广州好易点数字服务有限公司收"));
        assertEquals("广州好易点数字服务有限公司",
                NoticeDocumentAiService.cleanExtractedName("广州好易点数字服务有限公司 | 收款"));
        assertEquals("广州好易点数字服务有限公司",
                NoticeDocumentAiService.cleanExtractedName("广州好易点数字服务有限公司 付 款"));
        // 正常企业名称（含「收」字在中间、或正常分公司/服务中心后缀）绝不能被误截断
        assertEquals("广州海收贸易有限公司",
                NoticeDocumentAiService.cleanExtractedName("广州海收贸易有限公司"));
        assertEquals("广州数智云仓产业园运营有限公司",
                NoticeDocumentAiService.cleanExtractedName("广州数智云仓产业园运营有限公司"));
        assertEquals("广州数智云仓产业园运营有限公司第一分公司",
                NoticeDocumentAiService.cleanExtractedName("广州数智云仓产业园运营有限公司第一分公司"));

        // 完整双栏 OCR 文本端到端测试
        String ocr = """
                户名   广州好易点数字服务有限公司   收   户名   广州数智云仓产业园运营有限公司
                账号   4400123456789012345   款   账号   44050129162500000277
                金额   ¥370.67
                """;
        ExtractedFields ex = NoticeDocumentAiService.parseReceiptText(ocr, "RAPID_OCR");
        assertEquals("广州好易点数字服务有限公司", ex.payerName());
        assertEquals("广州数智云仓产业园运营有限公司", ex.payeeName());
        assertEquals("44050129162500000277", ex.payeeAccount());
        assertEquals(new BigDecimal("370.67"), ex.amount());
    }
}
