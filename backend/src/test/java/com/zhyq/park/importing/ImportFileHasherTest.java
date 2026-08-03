package com.zhyq.park.importing;

import com.zhyq.park.importing.service.ImportFileHasher;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ImportFileHasherTest {

    @Test
    void sha256IsStable() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ImportFileHasher.sha256("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rowFingerprintUsesLengthDelimitedKeys() {
        assertNotEquals(
                ImportFileHasher.rowFingerprint("rent", List.of("ab", "c")),
                ImportFileHasher.rowFingerprint("rent", List.of("a", "bc")));
    }
}
