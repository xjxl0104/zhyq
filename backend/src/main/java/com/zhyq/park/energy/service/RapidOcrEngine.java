package com.zhyq.park.energy.service;

import com.benjaminwan.ocrlibrary.OcrResult;
import com.benjaminwan.ocrlibrary.TextBlock;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 后端中文 OCR 引擎（RapidOCR / PaddleOCR PP-OCRv4 ONNX）。
 * 1) 懒加载初始化，不影响应用启动速度；
 * 2) Linux 下提前解压 libRapidOcr.so 并清除 ELF PT_GNU_STACK 的 PF_X（可执行栈）标志位：
 *    兼容 glibc 2.41+（如 Ubuntu 24.04 / eclipse-temurin:17-jre），避免 dlopen 报错
 *    "cannot enable executable stack as shared object requires: Invalid argument"；
 * 3) 全程加锁串行调用：规避 rapidocr 0.0.7 内部 isLibraryLoaded.compareAndSet(false,true) 在并发上传多张图时
 *    直接跳过同步块触发 nativeLoader=null 的并发竞态 bug，也避免底层 ONNX/JNI 单实例并发踩内存；
 * 4) 记录首次初始化真实根因异常，避免后续调用被掩盖为 nativeLoader is null。
 */
@Slf4j
@Component
public class RapidOcrEngine {

    public record OcrOutput(String text, String error) {}

    private static final int PT_GNU_STACK = 0x6474e551;
    private static final int PF_X = 0x1;

    private volatile InferenceEngine engine;
    private volatile String firstInitError;

    private InferenceEngine getEngine() {
        if (engine == null) {
            prepareLinuxNativeLibIfNeeded();
            engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V4);
        }
        return engine;
    }

    /**
     * 在 Linux 环境下预先把 classpath 中的 lib/libRapidOcr.so 解压到 RapidOCR 约定的
     * ${java.io.tmpdir}/ocrJava/onnx/libRapidOcr.so 并清除 ELF PT_GNU_STACK 的 PF_X 位（等效 execstack -c）。
     * 随后 JarFileUtil.copyFileFromJar 见目标文件已存在即直接 System.load，从而彻底兼容 glibc 2.41+。
     */
    private static void prepareLinuxNativeLibIfNeeded() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("linux")) return;
        String tmpDir = System.getProperty("java.io.tmpdir", "/tmp");
        Path target = Path.of(tmpDir, "ocrJava", "onnx", "libRapidOcr.so");
        try {
            Files.createDirectories(target.getParent());
            if (!Files.exists(target) || Files.size(target) == 0) {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                InputStream in = (cl != null) ? cl.getResourceAsStream("lib/libRapidOcr.so") : null;
                if (in == null) {
                    in = RapidOcrEngine.class.getClassLoader().getResourceAsStream("lib/libRapidOcr.so");
                }
                if (in == null) {
                    log.warn("classpath 未找到 lib/libRapidOcr.so，跳过 execstack 修补");
                    return;
                }
                try (InputStream stream = in) {
                    Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            boolean patched = clearElfExecStackFlag(target);
            if (patched) {
                log.info("已清除 {} 的 ELF PT_GNU_STACK 可执行栈标志位（兼容 glibc 2.41+）", target);
            }
        } catch (Exception e) {
            log.warn("预处理 Linux RapidOCR 原生库失败（将继续尝试默认加载）: {}", e.getMessage());
        }
    }

    /**
     * 就地清除 ELF64 小端共享库中 PT_GNU_STACK 段头的 PF_X 位。
     * @return 若检测到 PF_X 并成功清除返回 true，否则返回 false。
     */
    static boolean clearElfExecStackFlag(Path soPath) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(soPath.toFile(), "rw")) {
            if (raf.length() < 64) return false;
            byte[] header = new byte[64];
            raf.readFully(header);
            // ELF64 little-endian: 0x7F 'E' 'L' 'F', EI_CLASS=2 (64-bit), EI_DATA=1 (little-endian)
            if (header[0] != 0x7F || header[1] != 'E' || header[2] != 'L' || header[3] != 'F'
                    || header[4] != 2 || header[5] != 1) {
                return false;
            }
            ByteBuffer hb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            long ePhoff = hb.getLong(32);
            int ePhentsize = Short.toUnsignedInt(hb.getShort(54));
            int ePhnum = Short.toUnsignedInt(hb.getShort(56));
            if (ePhoff <= 0 || ePhentsize < 8 || ePhnum <= 0 || ePhoff + (long) ePhentsize * ePhnum > raf.length()) {
                return false;
            }
            byte[] ph = new byte[8];
            boolean patched = false;
            for (int i = 0; i < ePhnum; i++) {
                long off = ePhoff + (long) i * ePhentsize;
                raf.seek(off);
                raf.readFully(ph);
                ByteBuffer pb = ByteBuffer.wrap(ph).order(ByteOrder.LITTLE_ENDIAN);
                int pType = pb.getInt(0);
                int pFlags = pb.getInt(4);
                if (pType == PT_GNU_STACK && (pFlags & PF_X) != 0) {
                    int newFlags = pFlags & ~PF_X;
                    ByteBuffer out = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(newFlags);
                    raf.seek(off + 4);
                    raf.write(out.array());
                    patched = true;
                }
            }
            return patched;
        }
    }

    public String recognize(byte[] imageBytes, String ext) {
        return recognizeDetailed(imageBytes, ext).text();
    }

    public synchronized OcrOutput recognizeDetailed(byte[] imageBytes, String ext) {
        if (imageBytes == null || imageBytes.length == 0) return new OcrOutput("", "图片为空");
        String safeExt = (ext != null && ext.matches("[a-zA-Z0-9]{2,5}")) ? ext.toLowerCase() : "jpg";
        Path tmp = null;
        try {
            tmp = Files.createTempFile("zhyq-ocr-", "." + safeExt);
            Files.write(tmp, imageBytes);
            OcrResult result = getEngine().runOcr(tmp.toAbsolutePath().toString());
            firstInitError = null;
            if (result == null) return new OcrOutput("", "OCR 引擎返回空结果");
            List<TextBlock> blocks = result.getTextBlocks();
            String text = (blocks != null && !blocks.isEmpty())
                    ? formatBlocksByRows(blocks)
                    : (result.getStrRes() == null ? "" : result.getStrRes().trim());
            return new OcrOutput(text, text.isBlank() ? "图片未检测到文字" : null);
        } catch (Throwable t) {
            log.warn("RapidOCR recognize failed", t);
            String msg = describeThrowable(t);
            if (firstInitError == null) {
                firstInitError = msg;
            } else if (msg.contains("nativeLoader") && !firstInitError.equals(msg)) {
                msg = "首次初始化失败根因: " + firstInitError;
            }
            return new OcrOutput("", msg);
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
            }
        }
    }

    private static String describeThrowable(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 4) {
            if (depth > 0) sb.append(" -> ");
            sb.append(cur.getClass().getSimpleName());
            if (cur.getMessage() != null && !cur.getMessage().isBlank()) {
                sb.append(": ").append(cur.getMessage());
            }
            cur = cur.getCause();
            depth++;
        }
        return sb.toString();
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
