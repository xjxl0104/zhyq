package com.zhyq.park.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.energy.entity.FeeNotice;
import com.zhyq.park.energy.entity.FeePeriod;
import com.zhyq.park.energy.entity.LeaseFeeNotice;
import com.zhyq.park.energy.entity.LeaseFeePeriod;
import com.zhyq.park.energy.mapper.FeeNoticeMapper;
import com.zhyq.park.energy.mapper.FeePeriodMapper;
import com.zhyq.park.energy.mapper.LeaseFeeNoticeMapper;
import com.zhyq.park.energy.mapper.LeaseFeePeriodMapper;
import com.zhyq.park.file.entity.SysFile;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 缴费汇总 AI 自动识别核准服务：
 * - 租户缴费回执（RECEIPT）：严格核对 支付单位名称、接收单位名称、接收单位账号、支付金额 与缴费通知单完全一致，
 *   全部一致且唯一命中时自动上传进对应通知单的「缴费」资料并更新缴费状态为「已缴费」；
 * - 发票（INVOICE）：严格核对 购买方名称、销售方名称（数智云仓/昌记）、发票含税金额（价税合计） 与缴费通知单完全一致，
 *   全部一致且唯一命中时自动上传进对应通知单的「发票」资料并更新开票状态为「已开票」；
 * - 任意项无法识别、存在差异或无法唯一匹配时，绝不写入任何通知单，返回详细核对差异提醒人工核对。
 */
@Service
@RequiredArgsConstructor
public class NoticeDocumentAiService {

    public static final String DOC_RECEIPT = "RECEIPT";
    public static final String DOC_INVOICE = "INVOICE";
    public static final String MODE_UTILITY = "utility";
    public static final String MODE_LEASE = "lease";

    private final FeePeriodMapper feePeriodMapper;
    private final FeeNoticeMapper feeNoticeMapper;
    private final LeaseFeePeriodMapper leasePeriodMapper;
    private final LeaseFeeNoticeMapper leaseNoticeMapper;
    private final FeeNoticePaymentService feeNoticePayments;
    private final LeaseFeePaymentService leaseFeePayments;
    private final ObjectMapper objectMapper;

    @Value("${zhyq.ai.vision-api-url:${ZHYQ_AI_VISION_URL:}}")
    private String visionApiUrl;
    @Value("${zhyq.ai.vision-api-key:${ZHYQ_AI_VISION_KEY:}}")
    private String visionApiKey;
    @Value("${zhyq.ai.vision-model:${ZHYQ_AI_VISION_MODEL:qwen-vl-max}}")
    private String visionModel;

    /** 复用系统已配置的 DeepSeek 模型（ZHYQ_QUOTE_AI_*），用于将 OCR/PDF 原始文本精准结构化提取 */
    @Value("${zhyq.ai.text-api-url:${ZHYQ_QUOTE_AI_URL:}}")
    private String deepseekApiUrl;
    @Value("${zhyq.ai.text-api-key:${ZHYQ_QUOTE_AI_KEY:}}")
    private String deepseekApiKey;
    @Value("${zhyq.ai.text-model:${ZHYQ_QUOTE_AI_MODEL:deepseek-flash}}")
    private String deepseekModel;

    public record ExtractedFields(
            String payerName,
            String payeeName,
            String payeeAccount,
            BigDecimal amount,
            String buyerName,
            String sellerName,
            BigDecimal taxInclusiveAmount,
            String invoiceNo,
            String sourceMethod
    ) {}

    public record CheckItem(String label, boolean passed, String expected, String actual) {}

    public record VerifyResult(
            String status, // AUTO_APPROVED | MANUAL_REVIEW
            String fileName,
            String docType,
            Long matchedNoticeId,
            String matchedNoticeNo,
            String matchedTenantName,
            String matchedCategory,
            Long fileId,
            String reason,
            ExtractedFields extracted,
            List<CheckItem> checks
    ) {}

    record NoticeCandidate(
            Long id,
            String noticeNo,
            String tenantName,
            String category,
            String expectedPayeeName,
            String expectedPaymentAccount,
            Set<String> expectedAccountsDigits,
            BigDecimal totalDue
    ) {}

    @Transactional(rollbackFor = Exception.class)
    public VerifyResult verifyAndUpload(MultipartFile file, String mode, String docType, Long periodId) {
        return verifyAndUpload(file, mode, docType, periodId, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public VerifyResult verifyAndUpload(MultipartFile file, String mode, String docType, Long periodId, String ocrText) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请上传有效的文件");
        }
        String normMode = MODE_LEASE.equalsIgnoreCase(mode) ? MODE_LEASE : MODE_UTILITY;
        String normDoc = DOC_INVOICE.equalsIgnoreCase(docType) ? DOC_INVOICE : DOC_RECEIPT;
        String fileName = file.getOriginalFilename() == null ? "未命名文件" : file.getOriginalFilename();

        List<NoticeCandidate> candidates = loadCandidates(normMode, periodId);
        if (candidates.isEmpty()) {
            return manualReview(fileName, normDoc, null, "当前账期尚未生成缴费通知单，请先生成通知单后再上传核准", List.of());
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BizException("读取上传文件失败");
        }
        MultipartFile reusableFile = new InMemoryMultipartFile(
                file.getName(), fileName, file.getContentType(), bytes);

        ExtractedFields extracted = extractFields(bytes, fileName, normDoc, ocrText);
        if (DOC_RECEIPT.equals(normDoc)) {
            return matchAndApplyReceipt(reusableFile, fileName, normMode, candidates, extracted);
        }
        return matchAndApplyInvoice(reusableFile, fileName, normMode, candidates, extracted);
    }

    private List<NoticeCandidate> loadCandidates(String mode, Long periodId) {
        List<NoticeCandidate> out = new ArrayList<>();
        if (MODE_LEASE.equals(mode)) {
            LeaseFeePeriod p = leasePeriodMapper.selectById(periodId);
            if (p == null) throw new BizException(404, "租金/物业费账期不存在");
            List<LeaseFeeNotice> notices = leaseNoticeMapper.selectList(new LambdaQueryWrapper<LeaseFeeNotice>()
                    .eq(LeaseFeeNotice::getLeasePeriodId, periodId)
                    .orderByAsc(LeaseFeeNotice::getSort).orderByAsc(LeaseFeeNotice::getId));
            for (LeaseFeeNotice n : notices) {
                boolean isRent = "RENT".equalsIgnoreCase(n.getFeeCategory());
                String payee = firstNonBlank(n.getPayeeName(), isRent ? p.getRentPayeeName() : p.getPropertyPayeeName());
                String acc = firstNonBlank(n.getPaymentAccount(), isRent ? p.getRentPaymentAccount() : p.getPropertyPaymentAccount());
                out.add(new NoticeCandidate(
                        n.getId(), n.getNoticeNo(), n.getTenantName(),
                        isRent ? "租金" : "物业管理费",
                        payee, acc, extractAccountNumbers(acc), scale2(n.getTotalDue())
                ));
            }
        } else {
            FeePeriod p = feePeriodMapper.selectById(periodId);
            if (p == null) throw new BizException(404, "水电/垃圾费账期不存在");
            List<FeeNotice> notices = feeNoticeMapper.selectList(new LambdaQueryWrapper<FeeNotice>()
                    .eq(FeeNotice::getFeePeriodId, periodId)
                    .orderByAsc(FeeNotice::getSort).orderByAsc(FeeNotice::getId));
            for (FeeNotice n : notices) {
                String ovAcc = readOverrideField(n.getHeaderOverrideJson(), "paymentAccount");
                String acc = firstNonBlank(ovAcc, p.getPaymentAccount());
                String payee = extractPayeeNameFromAccountText(acc);
                out.add(new NoticeCandidate(
                        n.getId(), n.getNoticeNo(), n.getTenantName(),
                        "水电/垃圾费",
                        payee, acc, extractAccountNumbers(acc), scale2(n.getTotalDue())
                ));
            }
        }
        return out;
    }

    private VerifyResult matchAndApplyReceipt(MultipartFile file, String fileName, String mode,
                                              List<NoticeCandidate> candidates, ExtractedFields ex) {
        if (ex == null || (!StringUtils.hasText(ex.payerName()) && !StringUtils.hasText(ex.payeeName())
                && !StringUtils.hasText(ex.payeeAccount()) && ex.amount() == null)) {
            return manualReview(fileName, DOC_RECEIPT, ex,
                    "无法从该文件提取清晰回执文字（建议上传银行电子回单 PDF，或由人工核对后手动上传至对应通知单）", List.of());
        }

        List<NoticeCandidate> fullMatches = new ArrayList<>();
        NoticeCandidate bestByTenant = null;
        List<CheckItem> bestChecks = List.of();
        int bestScore = -1;

        for (NoticeCandidate c : candidates) {
            List<CheckItem> checks = evaluateReceipt(c, ex);
            int passed = (int) checks.stream().filter(CheckItem::passed).count();
            boolean allPassed = passed == checks.size();
            if (allPassed) {
                fullMatches.add(c);
            }
            boolean tenantHit = nameMatches(c.tenantName(), ex.payerName());
            int score = passed + (tenantHit ? 2 : 0);
            if (score > bestScore) {
                bestScore = score;
                bestByTenant = c;
                bestChecks = checks;
            }
        }

        if (fullMatches.size() == 1) {
            NoticeCandidate hit = fullMatches.get(0);
            SysFile saved = MODE_LEASE.equals(mode)
                    ? leaseFeePayments.upload(file, hit.id())
                    : feeNoticePayments.upload(file, hit.id());
            if (MODE_LEASE.equals(mode)) {
                leaseFeePayments.updateStatus(hit.id(), 1);
            } else {
                feeNoticePayments.updateStatus(hit.id(), 1);
            }
            return new VerifyResult(
                    "AUTO_APPROVED", fileName, DOC_RECEIPT,
                    hit.id(), hit.noticeNo(), hit.tenantName(), hit.category(),
                    saved != null ? saved.getId() : null,
                    "AI 核对全部一致，已自动上传回执并标记为「已缴费」",
                    ex, evaluateReceipt(hit, ex)
            );
        }

        if (fullMatches.size() > 1) {
            return manualReview(fileName, DOC_RECEIPT, ex,
                    "命中多张金额与收付款信息相同的通知单，为避免归错账单，请人工核对后手动上传",
                    evaluateReceipt(fullMatches.get(0), ex));
        }

        String detail = buildMismatchSummary(bestByTenant, bestChecks, "回执信息与通知单不完全一致，请人工核对");
        return manualReview(fileName, DOC_RECEIPT, ex, detail, bestChecks);
    }

    private VerifyResult matchAndApplyInvoice(MultipartFile file, String fileName, String mode,
                                              List<NoticeCandidate> candidates, ExtractedFields ex) {
        if (ex == null || (!StringUtils.hasText(ex.buyerName()) && !StringUtils.hasText(ex.sellerName())
                && ex.taxInclusiveAmount() == null)) {
            return manualReview(fileName, DOC_INVOICE, ex,
                    "无法从该文件提取清晰发票信息（建议上传数电票/电子发票 PDF 或 OFD，或由人工核对后手动上传）", List.of());
        }

        List<NoticeCandidate> fullMatches = new ArrayList<>();
        NoticeCandidate bestByBuyer = null;
        List<CheckItem> bestChecks = List.of();
        int bestScore = -1;

        for (NoticeCandidate c : candidates) {
            List<CheckItem> checks = evaluateInvoice(c, ex);
            int passed = (int) checks.stream().filter(CheckItem::passed).count();
            boolean allPassed = passed == checks.size();
            if (allPassed) {
                fullMatches.add(c);
            }
            boolean buyerHit = nameMatches(c.tenantName(), ex.buyerName());
            int score = passed + (buyerHit ? 2 : 0);
            if (score > bestScore) {
                bestScore = score;
                bestByBuyer = c;
                bestChecks = checks;
            }
        }

        if (fullMatches.size() == 1) {
            NoticeCandidate hit = fullMatches.get(0);
            SysFile saved = MODE_LEASE.equals(mode)
                    ? leaseFeePayments.uploadInvoice(file, hit.id())
                    : feeNoticePayments.uploadInvoice(file, hit.id());
            if (MODE_LEASE.equals(mode)) {
                leaseFeePayments.updateInvoiceStatus(hit.id(), 1);
            } else {
                feeNoticePayments.updateInvoiceStatus(hit.id(), 1);
            }
            return new VerifyResult(
                    "AUTO_APPROVED", fileName, DOC_INVOICE,
                    hit.id(), hit.noticeNo(), hit.tenantName(), hit.category(),
                    saved != null ? saved.getId() : null,
                    "AI 核对全部一致，已自动上传发票并标记为「已开票」",
                    ex, evaluateInvoice(hit, ex)
            );
        }

        if (fullMatches.size() > 1) {
            return manualReview(fileName, DOC_INVOICE, ex,
                    "命中多张金额与购销方相同的通知单，为避免归错发票，请人工核对后手动上传",
                    evaluateInvoice(fullMatches.get(0), ex));
        }

        String detail = buildMismatchSummary(bestByBuyer, bestChecks, "发票信息与通知单不完全一致，请人工核对");
        return manualReview(fileName, DOC_INVOICE, ex, detail, bestChecks);
    }

    static List<CheckItem> evaluateReceipt(NoticeCandidate c, ExtractedFields ex) {
        List<CheckItem> checks = new ArrayList<>();
        boolean payerOk = nameMatches(c.tenantName(), ex.payerName());
        checks.add(new CheckItem("支付单位名称", payerOk, c.tenantName(), nvl(ex.payerName())));

        boolean payeeOk = payeeNameMatches(c.expectedPayeeName(), ex.payeeName());
        checks.add(new CheckItem("接收单位名称", payeeOk, nvl(c.expectedPayeeName()), nvl(ex.payeeName())));

        String exAccDigits = digitsOnly(ex.payeeAccount());
        boolean accOk = StringUtils.hasText(exAccDigits) && exAccDigits.length() >= 8
                && c.expectedAccountsDigits().contains(exAccDigits);
        String expAccStr = c.expectedAccountsDigits().isEmpty() ? "(未配置收款账号)" : String.join(" / ", c.expectedAccountsDigits());
        checks.add(new CheckItem("接收单位账号", accOk, expAccStr, StringUtils.hasText(exAccDigits) ? exAccDigits : "(未识别)"));

        boolean amtOk = ex.amount() != null && c.totalDue() != null
                && scale2(ex.amount()).compareTo(scale2(c.totalDue())) == 0;
        checks.add(new CheckItem("支付金额", amtOk,
                c.totalDue() == null ? "-" : "¥" + scale2(c.totalDue()),
                ex.amount() == null ? "(未识别)" : "¥" + scale2(ex.amount())));
        return checks;
    }

    static List<CheckItem> evaluateInvoice(NoticeCandidate c, ExtractedFields ex) {
        List<CheckItem> checks = new ArrayList<>();
        boolean buyerOk = nameMatches(c.tenantName(), ex.buyerName());
        checks.add(new CheckItem("购买方名称", buyerOk, c.tenantName(), nvl(ex.buyerName())));

        boolean sellerEntityOk = isAllowedIssuer(ex.sellerName());
        boolean sellerMatchesNotice = StringUtils.hasText(c.expectedPayeeName())
                ? payeeNameMatches(c.expectedPayeeName(), ex.sellerName())
                : sellerEntityOk;
        boolean sellerOk = sellerEntityOk && sellerMatchesNotice;
        checks.add(new CheckItem("销售方名称", sellerOk,
                StringUtils.hasText(c.expectedPayeeName()) ? c.expectedPayeeName() : "数智云仓 / 昌记",
                nvl(ex.sellerName())));

        boolean amtOk = ex.taxInclusiveAmount() != null && c.totalDue() != null
                && scale2(ex.taxInclusiveAmount()).compareTo(scale2(c.totalDue())) == 0;
        checks.add(new CheckItem("发票含税金额", amtOk,
                c.totalDue() == null ? "-" : "¥" + scale2(c.totalDue()),
                ex.taxInclusiveAmount() == null ? "(未识别)" : "¥" + scale2(ex.taxInclusiveAmount())));
        return checks;
    }

    private String buildMismatchSummary(NoticeCandidate best, List<CheckItem> checks, String fallback) {
        if (checks == null || checks.isEmpty()) return fallback;
        List<String> failed = checks.stream()
                .filter(i -> !i.passed())
                .map(i -> i.label() + "不一致（识别：" + i.actual() + "，通知单：" + i.expected() + "）")
                .toList();
        if (failed.isEmpty()) return fallback;
        String prefix = best != null && StringUtils.hasText(best.noticeNo())
                ? "核对参考通知单 " + best.noticeNo() + "（" + best.tenantName() + "）："
                : "";
        return prefix + String.join("；", failed) + "。请人工核对后手动上传。";
    }

    private VerifyResult manualReview(String fileName, String docType, ExtractedFields ex, String reason, List<CheckItem> checks) {
        return new VerifyResult("MANUAL_REVIEW", fileName, docType, null, null, null, null, null, reason, ex, checks);
    }

    // ------------------------------------------------------------------ 文本 & 视觉提取

    private ExtractedFields extractFields(byte[] bytes, String fileName, String docType, String ocrText) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        String text = "";
        String method = "TEXT";
        if (lower.endsWith(".pdf")) {
            text = extractPdfText(bytes);
            method = "PDF_TEXT";
        } else if (lower.endsWith(".ofd")) {
            text = extractOfdText(bytes);
            method = "OFD_XML";
        }

        ExtractedFields parsed = null;
        if (StringUtils.hasText(text) && text.trim().length() >= 15) {
            parsed = DOC_RECEIPT.equals(docType)
                    ? parseReceiptText(text, method)
                    : parseInvoiceText(text, method);
            if (isComplete(parsed, docType)) {
                return parsed;
            }
        }

        // 优先使用前端/本地 OCR 识别出的文字（支持 JPG/PNG/WEBP 银行回执与发票图片）
        String combinedText = StringUtils.hasText(text) ? text : (ocrText == null ? "" : ocrText);
        if (StringUtils.hasText(ocrText) && ocrText.trim().length() >= 10) {
            ExtractedFields fromOcr = DOC_RECEIPT.equals(docType)
                    ? parseReceiptText(ocrText, "OCR")
                    : parseInvoiceText(ocrText, "OCR");
            parsed = mergeExtracted(parsed, fromOcr);
            if (isComplete(parsed, docType)) {
                return parsed;
            }
        }

        // 若有 OCR/PDF/OFD 原文但正则未完整抽出字段，调用系统已接入的 DeepSeek 模型做结构化提取
        if (StringUtils.hasText(combinedText) && combinedText.trim().length() >= 10
                && StringUtils.hasText(deepseekApiUrl) && StringUtils.hasText(deepseekApiKey)) {
            ExtractedFields ds = callDeepSeekExtract(combinedText, docType);
            if (ds != null) {
                parsed = mergeExtracted(parsed, ds);
                if (isComplete(parsed, docType)) {
                    return parsed;
                }
            }
        }

        // 扫描件/图片或文本不完整时，若配置了视觉 AI 模型则调用多模态识别兜底
        if (StringUtils.hasText(visionApiUrl) && StringUtils.hasText(visionApiKey)) {
            byte[] imgBytes = null;
            String mime = "image/png";
            if (lower.endsWith(".png")) { imgBytes = bytes; mime = "image/png"; }
            else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) { imgBytes = bytes; mime = "image/jpeg"; }
            else if (lower.endsWith(".webp")) { imgBytes = bytes; mime = "image/webp"; }
            else if (lower.endsWith(".pdf")) { imgBytes = renderPdfFirstPagePng(bytes); mime = "image/png"; }
            if (imgBytes != null && imgBytes.length > 0) {
                ExtractedFields ai = callVisionAi(imgBytes, mime, docType);
                if (ai != null) return mergeExtracted(parsed, ai);
            }
        }
        return parsed;
    }

    private boolean isComplete(ExtractedFields ex, String docType) {
        if (ex == null) return false;
        if (DOC_RECEIPT.equals(docType)) {
            return StringUtils.hasText(ex.payerName()) && StringUtils.hasText(ex.payeeName())
                    && StringUtils.hasText(ex.payeeAccount()) && ex.amount() != null;
        }
        return StringUtils.hasText(ex.buyerName()) && StringUtils.hasText(ex.sellerName())
                && ex.taxInclusiveAmount() != null;
    }

    private ExtractedFields mergeExtracted(ExtractedFields a, ExtractedFields b) {
        if (a == null) return b;
        if (b == null) return a;
        return new ExtractedFields(
                firstNonBlank(b.payerName(), a.payerName()),
                firstNonBlank(b.payeeName(), a.payeeName()),
                firstNonBlank(b.payeeAccount(), a.payeeAccount()),
                b.amount() != null ? b.amount() : a.amount(),
                firstNonBlank(b.buyerName(), a.buyerName()),
                firstNonBlank(b.sellerName(), a.sellerName()),
                b.taxInclusiveAmount() != null ? b.taxInclusiveAmount() : a.taxInclusiveAmount(),
                firstNonBlank(b.invoiceNo(), a.invoiceNo()),
                "AI_VISION"
        );
    }

    static String extractPdfText(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(doc.getNumberOfPages(), 3));
            return stripper.getText(doc);
        } catch (Exception e) {
            return "";
        }
    }

    static String extractOfdText(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (!entry.isDirectory() && name.endsWith(".xml")) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    String stripped = xml
                            .replaceAll("<[^>]+>", " ")
                            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                            .replace("&quot;", "\"").replace("&apos;", "'")
                            .replaceAll("[ \\t\\x0B\\f]+", " ");
                    sb.append(stripped).append('\n');
                }
            }
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    private byte[] renderPdfFirstPagePng(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            if (doc.getNumberOfPages() == 0) return null;
            BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 150);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private ExtractedFields callDeepSeekExtract(String rawText, String docType) {
        try {
            String trimmed = normalizeOcrText(rawText);
            if (trimmed.length() > 6000) trimmed = trimmed.substring(0, 6000);
            String sysPrompt = DOC_RECEIPT.equals(docType)
                    ? "你是严格的财务回执结构化抽取助手。请从下面的银行电子回单 OCR/PDF 原文中抽取字段，只返回纯 JSON 对象（不要 markdown 代码块）："
                      + "{\"payerName\":\"付款人/支付单位全称\",\"payeeName\":\"收款人/接收单位全称\",\"payeeAccount\":\"收款人银行账号纯数字\",\"amount\":\"支付金额数字(如99000.00)\"}。"
                      + "只能抽取原文中明确存在的信息，不得猜测或编造，原文没有的填 null。"
                    : "你是严格的财务发票结构化抽取助手。请从下面的电子发票 OCR/PDF 原文中抽取字段，只返回纯 JSON 对象（不要 markdown 代码块）："
                      + "{\"buyerName\":\"购买方名称全称\",\"sellerName\":\"销售方名称全称\",\"taxInclusiveAmount\":\"价税合计(小写)含税金额数字(如99000.00)\",\"invoiceNo\":\"发票号码\"}。"
                      + "只能抽取原文中明确存在的信息，不得猜测或编造，原文没有的填 null。";
            String modelName = StringUtils.hasText(deepseekModel) ? deepseekModel.trim() : "deepseek-flash";
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelName);
            body.put("temperature", 0);
            body.put("max_tokens", "deepseek-flash".equals(modelName) ? 4096 : 768);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", sysPrompt),
                    Map.of("role", "user", "content", trimmed)
            ));
            String reqJson = objectMapper.writeValueAsString(body);
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(deepseekApiUrl.trim()))
                    .timeout(Duration.ofSeconds(40))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + deepseekApiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(reqJson, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) return null;
            JsonNode root = objectMapper.readTree(resp.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            int l = content.indexOf('{'), r = content.lastIndexOf('}');
            if (l < 0 || r <= l) return null;
            JsonNode j = objectMapper.readTree(content.substring(l, r + 1));
            return new ExtractedFields(
                    jsonStr(j, "payerName"),
                    jsonStr(j, "payeeName"),
                    digitsOnly(jsonStr(j, "payeeAccount")),
                    parseDecimal(jsonStr(j, "amount")),
                    jsonStr(j, "buyerName"),
                    jsonStr(j, "sellerName"),
                    parseDecimal(jsonStr(j, "taxInclusiveAmount")),
                    jsonStr(j, "invoiceNo"),
                    "OCR+DEEPSEEK"
            );
        } catch (Exception e) {
            return null;
        }
    }

    private ExtractedFields callVisionAi(byte[] imgBytes, String mime, String docType) {
        try {
            String dataUri = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(imgBytes);
            String prompt = DOC_RECEIPT.equals(docType)
                    ? "你是财务审核助手。请从这张银行缴费回执图片中严格提取以下字段并只返回纯JSON（不要markdown代码块）："
                      + "{\"payerName\":\"付款人/支付单位全称\",\"payeeName\":\"收款人/接收单位全称\",\"payeeAccount\":\"收款人银行账号纯数字\",\"amount\":\"支付金额数字(如34983.86)\"}。看不清的字段填null。"
                    : "你是财务审核助手。请从这张发票图片中严格提取以下字段并只返回纯JSON（不要markdown代码块）："
                      + "{\"buyerName\":\"购买方名称全称\",\"sellerName\":\"销售方名称全称\",\"taxInclusiveAmount\":\"价税合计(小写)含税金额数字(如34983.86)\",\"invoiceNo\":\"发票号码\"}。看不清的字段填null。";

            Map<String, Object> body = Map.of(
                    "model", visionModel,
                    "temperature", 0,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(
                                    Map.of("type", "text", "text", prompt),
                                    Map.of("type", "image_url", "image_url", Map.of("url", dataUri))
                            )
                    ))
            );
            String reqJson = objectMapper.writeValueAsString(body);
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(visionApiUrl))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + visionApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(reqJson, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) return null;
            JsonNode root = objectMapper.readTree(resp.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            int l = content.indexOf('{'), r = content.lastIndexOf('}');
            if (l < 0 || r <= l) return null;
            JsonNode j = objectMapper.readTree(content.substring(l, r + 1));
            return new ExtractedFields(
                    jsonStr(j, "payerName"),
                    jsonStr(j, "payeeName"),
                    digitsOnly(jsonStr(j, "payeeAccount")),
                    parseDecimal(jsonStr(j, "amount")),
                    jsonStr(j, "buyerName"),
                    jsonStr(j, "sellerName"),
                    parseDecimal(jsonStr(j, "taxInclusiveAmount")),
                    jsonStr(j, "invoiceNo"),
                    "AI_VISION"
            );
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 结构化正则解析（纯函数）

    private static final Pattern PAYER_NAME_PAT = Pattern.compile(
            "(?:(?:付款人名称|付款户名|付款方名称|付款人全称|付款账户名称|付款人户名|付方户名|付方名称|付款单位|转出户名|借方户名)[:： \\t]+|付款人[:：])([^\\r\\n]+)");
    private static final Pattern PAYEE_NAME_PAT = Pattern.compile(
            "(?:(?:收款人名称|收款户名|收款方名称|收款人全称|收款账户名称|收款人户名|收方户名|收方名称|收款单位|转入户名|贷方户名|对方户名)[:： \\t]+|收款人[:：])([^\\r\\n]+)");
    private static final Pattern PAYEE_ACC_PAT = Pattern.compile(
            "(?:收款人账号|收款账号|收款方账号|收方账号|转入账号|贷方账号|对方账号|收款卡号)[:：\\s]+([0-9\\s\\-]{10,36})");
    private static final Pattern GENERIC_NAME_PAT = Pattern.compile(
            "(?:户名|账户名称|全称|名称)[:：\\s]+([^\\r\\n]+)");
    private static final Pattern GENERIC_ACC_PAT = Pattern.compile(
            "(?:账号|银行账号|卡号)[:：\\s]+([0-9\\s\\-]{10,36})");
    private static final Pattern RECEIPT_AMT_PAT = Pattern.compile(
            "(?:交易金额|汇款金额|付款金额|转账金额|本次付款金额|实付金额|小写金额|金额[（(]小写[）)]|小写|金\\s*额)[:：\\s]*(?:RMB|CNY|人民币)?\\s*[¥￥]?\\s*([0-9,]+\\.[0-9]{2})");
    private static final Pattern YEN_AMT_PAT = Pattern.compile("(?:[¥￥]|RMB|CNY)\\s*([0-9,]+\\.[0-9]{2})");

    private static final Pattern INVOICE_BUYER_PAT = Pattern.compile(
            "购\\s*买\\s*方[\\s\\S]{0,80}?名\\s*称[:：\\s]+([^\\r\\n]+)");
    private static final Pattern INVOICE_SELLER_PAT = Pattern.compile(
            "销\\s*售\\s*方[\\s\\S]{0,80}?名\\s*称[:：\\s]+([^\\r\\n]+)");
    private static final Pattern INVOICE_NAME_LINE_PAT = Pattern.compile(
            "名\\s*称[:：\\s]+([^\\r\\n]+)");
    private static final Pattern INVOICE_TOTAL_PAT = Pattern.compile(
            "(?:价税合计|（小写）|\\(小写\\)|小写)[^\\r\\n0-9]{0,30}[¥￥]?\\s*([0-9,]+\\.[0-9]{2})");
    private static final Pattern INVOICE_NO_PAT = Pattern.compile(
            "(?:发票号码|号码)[:：\\s]*(\\d{8,20})");

    /** 清理 OCR 文本中中文字符之间的单个多余空格，保留换行与双栏多空格分隔 */
    static String normalizeOcrText(String raw) {
        if (raw == null) return "";
        String s = raw.replace('\u00A0', ' ').replace('　', ' ');
        // 仅折叠相邻汉字间的单个空格（绝不跨换行，也保留 2+ 空格作为左右双栏分隔）
        s = s.replaceAll("([\\u4e00-\\u9fa5]) ([\\u4e00-\\u9fa5])", "$1$2");
        s = s.replaceAll("([\\u4e00-\\u9fa5]) ([\\u4e00-\\u9fa5])", "$1$2");
        // 折叠汉字与中文括号/冒号间的单个水平空格
        s = s.replaceAll("([\\u4e00-\\u9fa5]) ([（()）:：])", "$1$2");
        s = s.replaceAll("([（(]) ([\\u4e00-\\u9fa5])", "$1$2");
        s = s.replaceAll("([:：])[ \\t]+", "$1");
        return s;
    }

    static ExtractedFields parseReceiptText(String rawText, String method) {
        String text = normalizeOcrText(rawText);
        String payer = cleanExtractedName(firstGroup(PAYER_NAME_PAT, text));
        String payee = cleanExtractedName(firstGroup(PAYEE_NAME_PAT, text));
        String payeeAcc = digitsOnly(firstGroup(PAYEE_ACC_PAT, text));

        // 兜底：国内主流网银回单常采用左右双栏排版（「付款人 | 收款人」下各有一行「户名：...」「账号：...」）
        if (!StringUtils.hasText(payer) || !StringUtils.hasText(payee)) {
            List<String> genericNames = new ArrayList<>();
            Matcher m = GENERIC_NAME_PAT.matcher(text);
            while (m.find()) {
                String seg = m.group(1);
                for (String part : seg.split("(?:户名|账户名称|全称|名称)[:：]")) {
                    String c = cleanExtractedName(part);
                    if (StringUtils.hasText(c) && c.length() >= 4) {
                        genericNames.add(c);
                    }
                }
            }
            if (genericNames.size() >= 2) {
                if (!StringUtils.hasText(payer)) payer = genericNames.get(0);
                if (!StringUtils.hasText(payee)) payee = genericNames.get(1);
            }
        }
        if (!StringUtils.hasText(payeeAcc)) {
            List<String> genericAccs = new ArrayList<>();
            Matcher m = GENERIC_ACC_PAT.matcher(text);
            while (m.find()) {
                String d = digitsOnly(m.group(1));
                if (d.length() >= 10 && d.length() <= 30) {
                    genericAccs.add(d);
                }
            }
            if (genericAccs.size() >= 2) {
                payeeAcc = genericAccs.get(1);
            } else if (genericAccs.size() == 1) {
                payeeAcc = genericAccs.get(0);
            }
        }

        BigDecimal amt = parseDecimal(firstGroup(RECEIPT_AMT_PAT, text));
        if (amt == null) {
            amt = parseDecimal(firstGroup(YEN_AMT_PAT, text));
        }
        return new ExtractedFields(payer, payee, payeeAcc, amt, null, null, null, null, method);
    }

    static ExtractedFields parseInvoiceText(String rawText, String method) {
        String text = normalizeOcrText(rawText);
        String buyer = null;
        String seller = null;

        // 处理数电票/专票常见的双栏同行排版：「名称：购买方公司   名称：销售方公司」
        Matcher nm = INVOICE_NAME_LINE_PAT.matcher(text);
        List<String> names = new ArrayList<>();
        while (nm.find()) {
            String segment = nm.group(1);
            String[] parts = segment.split("名\\s*称[:：\\s]+");
            for (String p : parts) {
                String cleaned = cleanExtractedName(p);
                if (StringUtils.hasText(cleaned) && cleaned.length() >= 3) {
                    names.add(cleaned);
                }
            }
        }

        String byBuyerBlock = cleanExtractedName(firstGroup(INVOICE_BUYER_PAT, text));
        String bySellerBlock = cleanExtractedName(firstGroup(INVOICE_SELLER_PAT, text));

        if (StringUtils.hasText(byBuyerBlock) && StringUtils.hasText(bySellerBlock)
                && !normName(byBuyerBlock).equals(normName(bySellerBlock))) {
            buyer = byBuyerBlock;
            seller = bySellerBlock;
        } else if (names.size() >= 2) {
            buyer = names.get(0);
            seller = names.get(1);
        } else {
            buyer = firstNonBlank(byBuyerBlock, names.isEmpty() ? null : names.get(0));
            seller = bySellerBlock;
        }

        BigDecimal total = parseDecimal(firstGroup(INVOICE_TOTAL_PAT, text));
        if (total == null) {
            // 兜底：取文档中最大的 ¥ 金额（价税合计通常为整张发票最大含税总额）
            Matcher ym = YEN_AMT_PAT.matcher(text);
            while (ym.find()) {
                BigDecimal v = parseDecimal(ym.group(1));
                if (v != null && (total == null || v.compareTo(total) > 0)) {
                    total = v;
                }
            }
        }
        String invoiceNo = firstGroup(INVOICE_NO_PAT, text);
        return new ExtractedFields(null, null, null, null, buyer, seller, total, invoiceNo, method);
    }

    private static String cleanExtractedName(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String s = raw.trim();
        // 截断同一行后续标签（如「收款人名称：」「户名：」「销售方信息」「纳税人识别号：」「账号：」「开户行：」）
        s = s.replaceFirst("[ \\t]+(?:收款人|付款人|收款方|付款方|收方|付方|户名|账户名称|纳税人识别号|统一社会信用代码|账号|开户行|开户银行|银行账号|销售方|购买方|名称|税号|金额|币种).*$", "");
        s = s.replaceFirst("(?:收款人名称|收款户名|户名|销售方信息|销售方|购买方信息|购买方|纳税人识别号|统一社会信用代码|开户行|账号)[:：]?.*$", "");
        s = s.trim();
        if (s.length() < 3 || "收款人".equals(s) || "付款人".equals(s)) return null;
        return s;
    }

    static String extractPayeeNameFromAccountText(String accountText) {
        if (!StringUtils.hasText(accountText)) return "";
        Matcher m = Pattern.compile("(?:户名|收款单位|账户名称|开户名称|单位名称)[:：\\s]+([^\\r\\n/，,]+)").matcher(accountText);
        if (m.find()) {
            return cleanExtractedName(m.group(1));
        }
        for (String line : accountText.split("[\\r\\n/]+")) {
            String t = line.trim();
            if (t.contains("有限公司") || t.contains("昌记") || t.contains("数智云仓")) {
                return cleanExtractedName(t);
            }
        }
        return "";
    }

    static Set<String> extractAccountNumbers(String text) {
        Set<String> set = new LinkedHashSet<>();
        if (!StringUtils.hasText(text)) return set;
        Matcher m = Pattern.compile("(?<!\\d)(?:\\d[\\s\\-]?){10,30}(?!\\d)").matcher(text);
        while (m.find()) {
            String d = digitsOnly(m.group());
            if (d.length() >= 10 && d.length() <= 30) {
                set.add(d);
            }
        }
        return set;
    }

    static boolean nameMatches(String expected, String actual) {
        String e = normName(expected);
        String a = normName(actual);
        return !e.isEmpty() && e.equals(a);
    }

    static boolean payeeNameMatches(String expected, String actual) {
        String e = normName(expected);
        String a = normName(actual);
        if (e.isEmpty() || a.isEmpty()) return false;
        return e.equals(a);
    }

    static boolean isAllowedIssuer(String sellerName) {
        String s = normName(sellerName);
        if (s.isEmpty()) return false;
        return s.contains("数智云仓") || s.contains("昌记");
    }

    static String normName(String s) {
        if (s == null) return "";
        return s.trim()
                .replace('（', '(')
                .replace('）', ')')
                .replaceAll("[\\s　]+", "");
    }

    static String digitsOnly(String s) {
        return s == null ? "" : s.replaceAll("\\D+", "");
    }

    private static BigDecimal parseDecimal(String s) {
        if (!StringUtils.hasText(s)) return null;
        try {
            return new BigDecimal(s.replace(",", "").trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal scale2(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String firstGroup(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String firstNonBlank(String... arr) {
        for (String s : arr) {
            if (StringUtils.hasText(s)) return s.trim();
        }
        return "";
    }

    private static String nvl(String s) {
        return StringUtils.hasText(s) ? s.trim() : "(未识别)";
    }

    private static String jsonStr(JsonNode j, String key) {
        JsonNode n = j.path(key);
        if (n.isMissingNode() || n.isNull()) return null;
        String v = n.asText("").trim();
        return v.isEmpty() || "null".equalsIgnoreCase(v) ? null : v;
    }

    private String readOverrideField(String json, String key) {
        if (!StringUtils.hasText(json)) return "";
        try {
            JsonNode n = objectMapper.readTree(json).path(key);
            return n.isMissingNode() || n.isNull() ? "" : n.asText("");
        } catch (Exception e) {
            return "";
        }
    }

    /** 基于已读取字节数组的可重复落盘 MultipartFile，避免部分容器上 getBytes() 后 transferTo() 流耗尽报错 */
    private static final class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        private InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name == null ? "file" : name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content == null ? new byte[0] : content;
        }

        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public java.io.InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) throws java.io.IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
