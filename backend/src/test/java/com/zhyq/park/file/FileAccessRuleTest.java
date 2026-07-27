package com.zhyq.park.file;

import com.zhyq.park.file.entity.SysFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileAccessRuleTest {

    private static SysFile fileUploadedBy(String username) {
        SysFile f = new SysFile();
        f.setCreateBy(username);
        return f;
    }

    @Test
    void 上传者本人_可删() {
        assertTrue(FileAccessRule.canDelete(fileUploadedBy("zhangsan"), "zhangsan", false));
    }

    @Test
    void 他人_不可删() {
        assertFalse(FileAccessRule.canDelete(fileUploadedBy("zhangsan"), "lisi", false));
    }

    @Test
    void admin_可删任意() {
        assertTrue(FileAccessRule.canDelete(fileUploadedBy("zhangsan"), "admin", true));
    }

    @Test
    void 记录为空_不可删() {
        assertFalse(FileAccessRule.canDelete(null, "zhangsan", false));
    }

    @Test
    void 用户名空白_不可删() {
        assertFalse(FileAccessRule.canDelete(fileUploadedBy("zhangsan"), " ", false));
        assertFalse(FileAccessRule.canDelete(fileUploadedBy(null), null, false));
    }
}
