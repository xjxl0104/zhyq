package com.zhyq.park.marketing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

/** Warehouse documents stay private and are referenced by persistent IDs. */
@Service
@RequiredArgsConstructor
public class MktWarehouseFileService {
    public static final String BIZ_TYPE = "mkt_warehouse";
    private static final Set<String> ALLOWED = Set.of("pdf", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png");
    private final SysFileMapper files;
    private final FileStorageService storage;
    private final ObjectMapper json;

    public Map<String, Object> upload(MultipartFile upload, Long warehouseId) {
        if (upload == null || upload.isEmpty()) throw new BizException("请选择文件");
        if (upload.getSize() > 20L * 1024 * 1024) throw new BizException("文件不能超过20MB");
        if (!ALLOWED.contains(FileStorageService.extOf(upload.getOriginalFilename())))
            throw new BizException("支持PDF、Word、Excel和JPG/PNG图片");
        var stored = storage.store(upload);
        SysFile f = new SysFile();
        f.setBizType(BIZ_TYPE); f.setBizId(warehouseId);
        f.setOriginalName(stored.originalName()); f.setStorePath(stored.storePath());
        f.setUrl(stored.url()); f.setFileSize(stored.size()); f.setExt(stored.ext()); f.setContentType(stored.contentType());
        try { files.insert(f); }
        catch (RuntimeException e) { storage.deletePhysical(stored.storePath()); throw e; }
        return view(f);
    }

    public SysFile requireOwned(Long fileId, Long warehouseId) {
        SysFile f = fileId == null ? null : files.selectById(fileId);
        if (f == null || !BIZ_TYPE.equals(f.getBizType()) || !Objects.equals(warehouseId, f.getBizId()))
            throw new BizException(403, "附件不存在或不属于此云仓，请重新上传");
        storage.resolveExisting(f.getStorePath());
        return f;
    }

    public SysFile requireReference(String reference, Long warehouseId) {
        if (reference == null || !reference.matches("file:[1-9][0-9]*"))
            throw new BizException("请上传文件后再提交，不能填写本机临时路径");
        try { return requireOwned(Long.valueOf(reference.substring(5)), warehouseId); }
        catch (NumberFormatException e) { throw new BizException("附件编号无效"); }
    }

    public String validateReferences(String references, Long warehouseId) {
        try {
            var nodes = json.readTree(references);
            if (nodes == null || !nodes.isArray() || nodes.size() > 10) throw new BizException("最多上传10份资料");
            List<Map<String, Object>> result = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (var node : nodes) {
                Long id = node.path("id").canConvertToLong() ? node.path("id").longValue() : null;
                SysFile f = requireOwned(id, warehouseId);
                if (seen.add(id)) result.add(view(f));
            }
            return json.writeValueAsString(result);
        } catch (BizException e) { throw e; }
        catch (Exception e) { throw new BizException("附件格式无效，请重新选择文件"); }
    }

    public static Map<String, Object> view(SysFile f) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", f.getId()); out.put("name", f.getOriginalName());
        out.put("size", f.getFileSize()); out.put("ext", f.getExt());
        return out;
    }
}
