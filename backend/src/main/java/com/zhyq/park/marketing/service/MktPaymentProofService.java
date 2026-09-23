package com.zhyq.park.marketing.service;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.file.mapper.SysFileMapper;
import com.zhyq.park.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;

@Service @RequiredArgsConstructor
public class MktPaymentProofService {
    private final SysFileMapper files;
    private final FileStorageService storage;
    public String require(String reference, String bizType, Long bizId) {
        if (reference == null || !reference.matches("file:[1-9][0-9]*")) throw new BizException("请上传本单据的真实付款凭证");
        Long id;
        try { id = Long.valueOf(reference.substring(5)); } catch (NumberFormatException e) { throw new BizException("凭证编号无效"); }
        var file = files.selectById(id);
        if (file == null || !Objects.equals(bizType, file.getBizType()) || !Objects.equals(bizId, file.getBizId()))
            throw new BizException("付款凭证不存在或不属于该单据");
        storage.resolveExisting(file.getStorePath());
        return reference;
    }
}
