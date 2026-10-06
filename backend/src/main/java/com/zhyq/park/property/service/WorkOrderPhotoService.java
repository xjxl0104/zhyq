package com.zhyq.park.property.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.file.entity.SysFile;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
import com.zhyq.park.property.entity.WorkOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 处理照片先暂存，随处理完成事务关联工单；已提交的照片保留为验收凭据。 */
@Service
@RequiredArgsConstructor
public class WorkOrderPhotoService {
    public static final String BIZ_TYPE = "work_order_finish";
    public static final String VERIFY_BIZ_TYPE = "work_order_verify";
    private final SysFileMapper files;
    private final FileStorageService storage;

    public static String validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BizException("请选择处理现场照片");
        if (file.getSize() > 20L * 1024 * 1024) throw new BizException("处理照片不能超过20MB");
        String ext = FileStorageService.extOf(file.getOriginalFilename());
        if (!Set.of("jpg", "jpeg", "png").contains(ext)) throw new BizException("处理照片仅支持 JPG、PNG 格式");
        try (var source = file.getInputStream(); var input = ImageIO.createImageInputStream(source)) {
            if (input == null) throw new BizException("请上传有效的处理照片");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BizException("请上传有效的处理照片");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if (!(format.equals("png") && ext.equals("png"))
                        && !(format.equals("jpeg") && Set.of("jpg", "jpeg").contains(ext))) {
                    throw new BizException("照片内容与文件格式不一致");
                }
                long width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width * height > 50_000_000L)
                    throw new BizException("照片尺寸不能超过5000万像素");
                reader.read(0); // 拒绝仅有图片头、无法解码的损坏文件。
                return format.equals("png") ? "image/png" : "image/jpeg";
            } finally { reader.dispose(); }
        } catch (BizException e) { throw e;
        } catch (Exception e) { throw new BizException("照片无法读取，请重新拍照或上传 JPG、PNG 照片"); }
    }

    /** 调用者必须与工单状态更新处于同一事务。 */
    public void attachForFinish(WorkOrder order, String username, List<Long> photoIds) {
        if (photoIds == null || photoIds.isEmpty()) throw new BizException("请至少拍照或上传一张处理现场照片后再提交完成");
        attach(order, username, photoIds, BIZ_TYPE, "处理");
    }

    /** 验收照片可选，但提交的每一张必须由当前验收人上传并在同一事务内关联。 */
    public void attachForVerify(WorkOrder order, String username, List<Long> photoIds) {
        if (photoIds == null || photoIds.isEmpty()) return;
        attach(order, username, photoIds, VERIFY_BIZ_TYPE, "验收");
    }

    private void attach(WorkOrder order, String username, List<Long> photoIds, String bizType, String stage) {
        if (photoIds.size() > 20 || photoIds.stream().anyMatch(Objects::isNull))
            throw new BizException(stage + "照片最多20张");
        for (Long id : photoIds.stream().distinct().sorted().toList()) {
            SysFile file = files.selectById(id);
            if (file == null || !bizType.equals(file.getBizType()) || file.getBizId() != null
                    || !Objects.equals(order.getTenantId(), file.getTenantId())
                    || username == null || !username.equals(file.getCreateBy())
                    || !Set.of("image/jpeg", "image/png").contains(Objects.toString(file.getContentType(), ""))) {
                throw new BizException(stage + "照片不可用，请重新上传；不能使用其他工单的照片");
            }
            storage.resolveExisting(file.getStorePath());
            int changed = files.update(null, new LambdaUpdateWrapper<SysFile>()
                    .eq(SysFile::getId, id).eq(SysFile::getBizType, bizType)
                    .eq(SysFile::getCreateBy, username).isNull(SysFile::getBizId)
                    .set(SysFile::getBizId, order.getId()));
            if (changed != 1) throw new BizException("照片已被删除或提交，请刷新后重试");
        }
    }
}
