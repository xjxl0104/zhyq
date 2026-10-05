package com.zhyq.park.building.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.building.entity.Floor;
import com.zhyq.park.building.mapper.FloorMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Floor plans are versioned attachments; existing orders keep their original file ID. */
@Service
@RequiredArgsConstructor
public class FloorPlanService {
    public static final String BIZ_TYPE = "floor_plan";
    private final FloorMapper floors;
    private final SysFileMapper files;
    private final FileStorageService storage;

    public SysFile current(Long floorId) {
        requireFloor(floorId);
        List<SysFile> found = files.selectList(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getBizType, BIZ_TYPE).eq(SysFile::getBizId, floorId)
                .orderByDesc(SysFile::getId).last("LIMIT 1"));
        return found.isEmpty() ? null : found.get(0);
    }

    public SysFile upload(Long floorId, MultipartFile upload) {
        Floor floor = requireFloor(floorId);
        String mime = validateImage(upload);
        FileStorageService.StoredResult stored = storage.store(upload);
        SysFile file = new SysFile();
        file.setBizType(BIZ_TYPE);
        file.setBizId(floorId);
        file.setTenantId(floor.getTenantId());
        file.setOriginalName(stored.originalName());
        file.setStorePath(stored.storePath());
        file.setUrl(stored.url());
        file.setExt(stored.ext());
        file.setContentType(mime);
        file.setFileSize(stored.size());
        try {
            files.insert(file);
        } catch (RuntimeException e) {
            storage.deletePhysical(stored.storePath());
            throw e;
        }
        return file;
    }

    private Floor requireFloor(Long id) {
        Floor floor = floors.selectById(id);
        if (floor == null) throw new BizException("楼层不存在或已删除");
        return floor;
    }

    static String validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BizException("请选择平面图");
        if (file.getSize() > 20L * 1024 * 1024) throw new BizException("平面图不能超过 20MB");
        String ext = FileStorageService.extOf(file.getOriginalFilename());
        if (!Set.of("jpg", "jpeg", "png").contains(ext)) throw new BizException("请上传 JPG 或 PNG 平面图；PDF/CAD 请先导出为图片");
        try (java.io.InputStream source = file.getInputStream();
             ImageInputStream input = ImageIO.createImageInputStream(source)) {
            if (input == null) throw new BizException("无法读取平面图");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BizException("文件不是有效图片");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase();
                if (!(format.equals("png") && ext.equals("png"))
                        && !(format.equals("jpeg") && Set.of("jpg", "jpeg").contains(ext))) {
                    throw new BizException("图片内容与文件扩展名不一致");
                }
                long width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width * height > 50_000_000L) {
                    throw new BizException("平面图尺寸过大，请缩小到 5000 万像素以内");
                }
                return format.equals("png") ? "image/png" : "image/jpeg";
            } finally { reader.dispose(); }
        } catch (BizException e) { throw e;
        } catch (Exception e) { throw new BizException("无法读取平面图，请重新导出为 JPG 或 PNG"); }
    }
}
