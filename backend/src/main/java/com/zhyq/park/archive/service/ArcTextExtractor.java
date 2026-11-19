package com.zhyq.park.archive.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 从文件里尽力提取一段正文(PDF 文本层 / Word / Excel / 文本文件),供自动归类与全文检索。
 * 提取不了(扫描件、图片、CAD、过大文件、损坏文件)一律返回空串,调用方退回到文件名判断;绝不抛异常。
 */
public final class ArcTextExtractor {
    private static final Logger log = LoggerFactory.getLogger(ArcTextExtractor.class);
    /** 超过这个大小的文件不尝试解析,避免占用过多内存 */
    private static final long MAX_PARSE_BYTES = 40L * 1024 * 1024;
    private static final int MAX_CHARS = 20000;
    private static final int PDF_PAGES = 5;
    private static final int SHEETS = 3;
    private static final int ROWS = 40;

    private ArcTextExtractor() {}

    public static String extract(Path file, String ext) {
        if (file == null || ext == null) return "";
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_PARSE_BYTES) return "";
            String text = switch (ext.toLowerCase(Locale.ROOT)) {
                case "pdf" -> pdf(file);
                case "docx" -> docx(file);
                case "doc" -> doc(file);
                case "xlsx", "xls" -> excel(file);
                case "txt", "csv", "md" -> plain(file);
                default -> "";
            };
            text = text == null ? "" : text.replaceAll("[\s\u00a0]+", " ").trim();
            return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
        } catch (Throwable e) {
            log.debug("资料正文提取失败 {}: {}", file.getFileName(), e.toString());
            return "";
        }
    }

    private static String pdf(Path file) throws Exception {
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            if (doc.isEncrypted()) return "";
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(PDF_PAGES, doc.getNumberOfPages()));
            return stripper.getText(doc);
        }
    }

    private static String docx(Path file) throws Exception {
        try (InputStream in = new FileInputStream(file.toFile());
             XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }

    private static String doc(Path file) throws Exception {
        try (InputStream in = new FileInputStream(file.toFile());
             WordExtractor ex = new WordExtractor(in)) {
            return ex.getText();
        }
    }

    private static String excel(Path file) throws Exception {
        StringBuilder sb = new StringBuilder();
        DataFormatter fmt = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(file.toFile(), null, true)) {
            int sheets = Math.min(SHEETS, wb.getNumberOfSheets());
            for (int i = 0; i < sheets && sb.length() < MAX_CHARS; i++) {
                Sheet sheet = wb.getSheetAt(i);
                sb.append(sheet.getSheetName()).append(' ');
                int rows = 0;
                for (Row row : sheet) {
                    if (rows++ >= ROWS || sb.length() >= MAX_CHARS) break;
                    for (Cell cell : row) {
                        String v = fmt.formatCellValue(cell);
                        if (!v.isBlank()) sb.append(v).append(' ');
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String plain(Path file) throws Exception {
        byte[] head = Files.readAllBytes(file);
        if (head.length > 60000) head = java.util.Arrays.copyOf(head, 60000);
        String utf8 = new String(head, StandardCharsets.UTF_8);
        // 含替换字符说明不是 UTF-8,按 GBK 重解
        return utf8.indexOf('\uFFFD') >= 0 ? new String(head, Charset.forName("GBK")) : utf8;
    }
}
