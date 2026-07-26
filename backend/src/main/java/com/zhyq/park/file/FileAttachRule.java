package com.zhyq.park.file;

import com.zhyq.park.file.entity.SysFile;

/**
 * 附件关联规则:仅未关联(bizId 为空)的附件可被回填,防越权覆盖他人/他业务附件。
 */
public final class FileAttachRule {

    private FileAttachRule() {}

    /** 该附件是否可被关联到某业务:仅当 bizId 尚未设置。 */
    public static boolean canAttach(SysFile file) {
        return file != null && file.getBizId() == null;
    }
}
