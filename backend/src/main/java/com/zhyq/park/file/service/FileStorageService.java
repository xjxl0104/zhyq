package com.zhyq.park.file.service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 文件存储:安全校验纯函数(static,可单测)。磁盘 IO 与持久化在 Task 4 补。
 */
public class FileStorageService {

    public static final Set<String> ALLOWED_EXT = Set.of(
        "jpg","jpeg","png","gif","webp",
        "pdf","doc","docx","xls","xlsx","ppt","pptx",
        "dwg","dxf","txt","zip");

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy/MM");

    public static String extOf(String originalName) {
        if (originalName == null) return "";
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) return "";
        return originalName.substring(dot + 1).toLowerCase();
    }

    public static boolean isAllowed(String ext) {
        return ext != null && ALLOWED_EXT.contains(ext);
    }

    public static String newStoredName(String originalName, String uuid) {
        String ext = extOf(originalName);
        return ext.isEmpty() ? uuid : uuid + "." + ext;
    }

    public static String buildRelativePath(LocalDate date, String storedName) {
        return date.format(YM) + "/" + storedName;
    }

    public static Path resolveSafely(Path root, String relative) {
        Path normalizedRoot = root.normalize();
        Path resolved = normalizedRoot.resolve(relative).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("非法路径: " + relative);
        }
        return resolved;
    }
}
