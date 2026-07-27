package com.zhyq.park.file.service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 文件存储:安全校验纯函数(static,可单测) + 磁盘 IO 实例方法。
 */
@org.springframework.stereotype.Service
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

    @org.springframework.beans.factory.annotation.Value("${zhyq.upload.path:./uploads}")
    private String uploadPath;

    @org.springframework.beans.factory.annotation.Value("${zhyq.upload.url-prefix:/uploads}")
    private String urlPrefix;

    // 20MB,与 application.yml 二次校验一致
    private static final long MAX_SIZE = 20L * 1024 * 1024;

    public record StoredResult(String storePath, String url, String ext,
                               String contentType, long size, String originalName) {}

    public StoredResult store(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new com.zhyq.park.common.exception.BizException("文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new com.zhyq.park.common.exception.BizException("文件超过20MB上限");
        }
        String original = org.springframework.util.StringUtils.cleanPath(
            file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String ext = extOf(original);
        if (!isAllowed(ext)) {
            throw new com.zhyq.park.common.exception.BizException("不允许的文件类型: " + ext);
        }
        String uuid = java.util.UUID.randomUUID().toString().replace("-", "");
        String stored = newStoredName(original, uuid);
        String relative = buildRelativePath(java.time.LocalDate.now(), stored);
        java.nio.file.Path root = java.nio.file.Paths.get(uploadPath).toAbsolutePath();
        java.nio.file.Path target = resolveSafely(root, relative);
        try {
            java.nio.file.Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
        } catch (java.io.IOException e) {
            throw new com.zhyq.park.common.exception.BizException("文件保存失败");
        }
        String url = urlPrefix + "/" + relative;
        return new StoredResult(relative, url, ext, file.getContentType(), file.getSize(), original);
    }

    public void deletePhysical(String storePath) {
        if (storePath == null || storePath.isBlank()) return;
        try {
            java.nio.file.Path root = java.nio.file.Paths.get(uploadPath).toAbsolutePath();
            java.nio.file.Path target = resolveSafely(root, storePath);
            java.nio.file.Files.deleteIfExists(target);
        } catch (Exception ignore) {
            // 物理文件删除失败不阻断逻辑删除
        }
    }

    /** 鉴权下载用:按库中相对路径定位磁盘文件(含路径穿越防护),不存在抛业务异常 */
    public Path resolveExisting(String storePath) {
        if (storePath == null || storePath.isBlank()) {
            throw new com.zhyq.park.common.exception.BizException("附件路径为空");
        }
        Path root = java.nio.file.Paths.get(uploadPath).toAbsolutePath();
        Path target = resolveSafely(root, storePath);
        if (!java.nio.file.Files.isRegularFile(target)) {
            throw new com.zhyq.park.common.exception.BizException("附件文件不存在或已被清理");
        }
        return target;
    }
}
