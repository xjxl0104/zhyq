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

    /**
     * 可执行/脚本类扩展名黑名单。预算计划与采购申请要能上传「各种格式」的文件,
     * 原先 16 项白名单挡得太死(wps/et/csv/rar/ofd/mp4 都传不上),故改为「除危险类型外一律放行」。
     * 上传件只经 /file/download 以 attachment 方式下发、不被服务端执行,
     * 但仍拦下这些一旦被误点或误暴露即可执行的类型,避免把上传目录变成投毒面。
     */
    public static final Set<String> BLOCKED_EXT = Set.of(
        // 可执行与安装包
        "exe","msi","com","scr","cpl","dll","sys","app","dmg","pkg","deb","rpm","bin","elf",
        // 脚本与批处理
        "bat","cmd","ps1","psm1","vbs","vbe","js","mjs","jse","wsf","wsh","sh","bash","zsh","py","pl","rb",
        // 服务端可执行页面(万一被静态目录暴露)
        "jsp","jspx","php","php3","php4","php5","phtml","asp","aspx","cer","asa","cgi",
        // 可带字节码/可执行的容器
        "jar","war","ear","class","apk","ipa",
        // 内嵌脚本的网页与帮助文档
        "html","htm","xhtml","shtml","svg","hta","chm",
        // 快捷方式/系统配置
        "lnk","url","desktop","reg","inf","scf");

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy/MM");

    public static String extOf(String originalName) {
        if (originalName == null) return "";
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) return "";
        return originalName.substring(dot + 1).toLowerCase();
    }

    /**
     * 是否允许上传:除黑名单里的可执行/脚本类型外一律放行(办公、图纸、影音、压缩包等任意格式)。
     * 空扩展名同样拒绝 — 无扩展名既判不出类型,下载时也给不出正确的 Content-Type。
     */
    public static boolean isAllowed(String ext) {
        return ext != null && !ext.isBlank() && !BLOCKED_EXT.contains(ext);
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
            throw new com.zhyq.park.common.exception.BizException(
                ext.isBlank() ? "文件缺少扩展名,无法识别类型" : "出于安全考虑不允许上传该类型文件: " + ext);
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
