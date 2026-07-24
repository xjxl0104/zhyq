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
    void isAllowed_acceptsWhitelistRejectsExecutable() {
        assertTrue(FileStorageService.isAllowed("png"));
        assertTrue(FileStorageService.isAllowed("dwg"));
        assertFalse(FileStorageService.isAllowed("exe"));
        assertFalse(FileStorageService.isAllowed("jsp"));
        assertFalse(FileStorageService.isAllowed(""));
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
