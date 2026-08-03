package com.zhyq.park.importing.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class ImportFileHasher {
    private ImportFileHasher() {}

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String rowFingerprint(String bizType, List<String> keys) {
        StringBuilder value = new StringBuilder();
        append(value, bizType);
        for (String key : keys) {
            append(value, key == null ? "" : key.trim());
        }
        return sha256(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void append(StringBuilder out, String value) {
        out.append(value.length()).append(':').append(value).append('|');
    }
}
