package com.zhyq.park.file;

import com.zhyq.park.file.service.FileStorageService;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    @Test
    void extOf_lowercasesAndStripsDot() {
        assertEquals("jpg", FileStorageService.extOf("Photo.JPG"));
        assertEquals("pdf", FileStorageService.extOf("a.b.pdf"));
        assertEquals("", FileStorageService.extOf("noext"));
    }

    @Test
    void isAllowed_acceptsAnyFormatRejectsExecutable() {
        assertTrue(FileStorageService.isAllowed("png"));
        assertTrue(FileStorageService.isAllowed("dwg"));
        assertFalse(FileStorageService.isAllowed("exe"));
        assertFalse(FileStorageService.isAllowed("jsp"));
        assertFalse(FileStorageService.isAllowed(""));
    }

    /** 预算/采购申请要能传「各种格式」:原白名单外的常见办公与归档格式现在都应放行。 */
    @Test
    void isAllowed_acceptsFormatsOutsideOldWhitelist() {
        assertTrue(FileStorageService.isAllowed("csv"));
        assertTrue(FileStorageService.isAllowed("wps"));
        assertTrue(FileStorageService.isAllowed("et"));
        assertTrue(FileStorageService.isAllowed("ofd"));
        assertTrue(FileStorageService.isAllowed("rar"));
        assertTrue(FileStorageService.isAllowed("7z"));
        assertTrue(FileStorageService.isAllowed("mp4"));
        assertTrue(FileStorageService.isAllowed("md"));
    }

    /** 放行归放行,可执行与内嵌脚本类型仍必须拦下。 */
    @Test
    void isAllowed_stillBlocksScriptAndMarkupTypes() {
        assertFalse(FileStorageService.isAllowed("bat"));
        assertFalse(FileStorageService.isAllowed("sh"));
        assertFalse(FileStorageService.isAllowed("js"));
        assertFalse(FileStorageService.isAllowed("php"));
        assertFalse(FileStorageService.isAllowed("jar"));
        assertFalse(FileStorageService.isAllowed("html"));
        assertFalse(FileStorageService.isAllowed("svg"));
        assertFalse(FileStorageService.isAllowed("lnk"));
    }

    @Test
    void newStoredName_usesUuidPlusExt() {
        assertEquals("abc123.jpg", FileStorageService.newStoredName("x.JPG", "abc123"));
        assertEquals("abc123", FileStorageService.newStoredName("noext", "abc123"));
    }

    @Test
    void buildRelativePath_yearMonthPrefixed() {
        assertEquals("2026/07/abc.png",
            FileStorageService.buildRelativePath(LocalDate.of(2026, 7, 24), "abc.png"));
    }

    @Test
    void resolveSafely_allowsInsideRoot() {
        Path root = Paths.get("/data/uploads");
        Path p = FileStorageService.resolveSafely(root, "2026/07/abc.png");
        assertTrue(p.normalize().startsWith(root.normalize()));
    }

    @Test
    void resolveSafely_rejectsTraversal() {
        Path root = Paths.get("/data/uploads");
        assertThrows(IllegalArgumentException.class,
            () -> FileStorageService.resolveSafely(root, "../../etc/passwd"));
    }
}
