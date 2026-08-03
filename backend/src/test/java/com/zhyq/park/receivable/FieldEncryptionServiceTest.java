package com.zhyq.park.receivable;

import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FieldEncryptionServiceTest {
    @Test
    void encryptsWithRandomNonceAndDecrypts() {
        FieldEncryptionService service = new FieldEncryptionService(
                Base64.getEncoder().encodeToString(new byte[32]));

        String first = service.encrypt("660079339348");
        String second = service.encrypt("660079339348");

        assertNotEquals(first, second);
        assertEquals("660079339348", service.decrypt(first));
        assertEquals("6600****9348", service.mask("660079339348"));
    }

    @Test
    void rejectsMissingOrInvalidKeyWhenEncryptionIsRequired() {
        assertThrows(BizException.class, () -> new FieldEncryptionService("").encrypt("1234"));
        assertThrows(BizException.class, () -> new FieldEncryptionService(
                Base64.getEncoder().encodeToString(new byte[16])).encrypt("1234"));
    }

    @Test
    void rejectsUnknownCipherVersion() {
        FieldEncryptionService service = new FieldEncryptionService(
                Base64.getEncoder().encodeToString(new byte[32]));
        assertThrows(BizException.class, () -> service.decrypt("v2:abcd"));
    }
}
