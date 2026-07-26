package com.zhyq.park.file;

import com.zhyq.park.file.entity.SysFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 附件关联规则单测(纯逻辑,不依赖 Spring/DB):
 * 回填时只认「bizId 为空」的记录,已关联的不覆盖 —— 防越权改他人/他业务附件。
 */
class FileAttachRuleTest {

    @Test
    void 仅回填bizId为空的记录() {
        SysFile free = new SysFile();      // 未关联
        free.setId(1L);
        SysFile taken = new SysFile();     // 已属于别的业务
        taken.setId(2L);
        taken.setBizId(999L);
        taken.setBizType("contract");

        List<SysFile> candidates = List.of(free, taken);
        List<SysFile> toAttach = candidates.stream()
                .filter(FileAttachRule::canAttach)
                .toList();

        assertEquals(1, toAttach.size());
        assertEquals(1L, toAttach.get(0).getId());
    }

    @Test
    void 空bizId可关联_非空不可() {
        SysFile f = new SysFile();
        assertTrue(FileAttachRule.canAttach(f));
        f.setBizId(5L);
        assertFalse(FileAttachRule.canAttach(f));
    }
}
