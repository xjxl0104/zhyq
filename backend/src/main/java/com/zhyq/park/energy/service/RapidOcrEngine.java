package com.zhyq.park.energy.service;

import com.benjaminwan.ocrlibrary.OcrResult;
import com.benjaminwan.ocrlibrary.TextBlock;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 后端中文 OCR 引擎（RapidOCR / PaddleOCR PP-OCRv4 ONNX）。
 * 懒加载初始化，不影响应用启动速度；按文本块 Y/X 坐标逐行重排，适配银行电子回单双栏表格与电子发票。
 */
@Slf4j
@Component
public class RapidOcrEngine {

    public record OcrOutput(String text, String error) {}

    private volatile InferenceEngine engine;

    private InferenceEngine getEngine() {
        if (engine == null) {
            synchronized (this) {
                if (engine == null) {
                    engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V4);
                }
            }
        }
        return engine;
    }

    public String recognize(byte[] imageBytes, String ext) {
        return recognizeDetailed(imageBytes, ext).text();
    }

    public OcrOutput recognizeDetailed(byte[] imageBytes, String ext) {
        if (imageBytes == null || imageBytes.length == 0) return new OcrOutput("", "图片为空");
        String safeExt = (ext != null && ext.matches("[a-zA-Z0-9]{2,5}")) ? ext.toLowerCase() : "jpg";
        Path tmp = null;
        try {
            tmp = Files.createTempFile("zhyq-ocr-", "." + safeExt);
            Files.write(tmp, imageBytes);
            OcrResult result = getEngine().runOcr(tmp.toAbsolutePath().toString());
            if (result == null) return new OcrOutput("", "OCR 引擎返回空结果");
            List<TextBlock> blocks = result.getTextBlocks();
            String text = (blocks != null && !blocks.isEmpty())
                    ? formatBlocksByRows(blocks)
                    : (result.getStrRes() == null ? "" : result.getStrRes().trim());
            return new OcrOutput(text, text.isBlank() ? "图片未检测到文字" : null);
        } catch (Throwable t) {
            log.warn("RapidOCR recognize failed", t);
            String msg = t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
            if (t.getCause() != null) {
                msg += " -> " + t.getCause().getClass().getSimpleName()
                        + (t.getCause().getMessage() != null ? ": " + t.getCause().getMessage() : "");
            }
            return new OcrOutput("", msg);
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
            }
        }
    }

    /** 按文本框中心 Y 坐标分行、同行内按 X 从左到右拼接（同一行不同列用 3 个空格分隔，便于双栏回单解析） */
    private static String formatBlocksByRows(List<TextBlock> blocks) {
        record Box(double cy, double x, double h, String text) {}
        List<Box> list = new ArrayList<>();
        for (TextBlock b : blocks) {
            if (b == null || b.getText() == null || b.getText().isBlank()) continue;
            var pts = b.getBoxPoint();
            if (pts == null || pts.size() < 4) {
                list.add(new Box(0, 0, 20, b.getText().trim()));
                continue;
            }
            double minY = pts.stream().mapToDouble(p -> p.getY()).min().orElse(0);
            double maxY = pts.stream().mapToDouble(p -> p.getY()).max().orElse(0);
            double minX = pts.stream().mapToDouble(p -> p.getX()).min().orElse(0);
            list.add(new Box((minY + maxY) / 2.0, minX, Math.max(10, maxY - minY), b.getText().trim()));
        }
        list.sort(Comparator.comparingDouble(Box::cy).thenComparingDouble(Box::x));

        List<List<Box>> rows = new ArrayList<>();
        for (Box box : list) {
            if (rows.isEmpty()) {
                List<Box> r = new ArrayList<>();
                r.add(box);
                rows.add(r);
            } else {
                List<Box> lastRow = rows.get(rows.size() - 1);
                double rowCy = lastRow.stream().mapToDouble(Box::cy).average().orElse(box.cy());
                double rowH = lastRow.stream().mapToDouble(Box::h).average().orElse(20);
                if (Math.abs(box.cy() - rowCy) <= Math.max(12.0, rowH * 0.65)) {
                    lastRow.add(box);
                } else {
                    List<Box> r = new ArrayList<>();
                    r.add(box);
                    rows.add(r);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (List<Box> row : rows) {
            row.sort(Comparator.comparingDouble(Box::x));
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) sb.append("   ");
                sb.append(row.get(i).text());
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }
}
