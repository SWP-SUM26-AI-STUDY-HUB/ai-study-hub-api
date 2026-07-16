package vn.ai_study_hub_api.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Small hashing helpers shared across the codebase: document duplicate detection
 * (content fingerprint) and embedded-image deduplication in moderation.
 */
public final class HashUtil {

    private HashUtil() {
    }

    /**
     * Computes the SHA-256 digest of {@code data} and returns it as a lowercase hex string.
     *
     * @param data the bytes to hash
     * @return 64-char lowercase hex SHA-256 fingerprint
     */
    public static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory algorithm in every JDK; this is unreachable in practice.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
