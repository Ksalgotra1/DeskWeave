package com.deskweave.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Salted SHA-256 password hashing utility backed by {@link MessageDigest}.
 */
public final class PasswordHasher {

    private static final int SALT_BYTES = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private PasswordHasher() {
    }

    /**
     * Generates a random 16-byte salt and hashes {@code plainPassword} as {@code saltHex:digestHex}.
     */
    public static String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        return hashWithSalt(plainPassword, HEX.formatHex(salt));
    }

    /**
     * Computes {@code saltHex:SHA256(saltHex + ":" + plainPassword)}.
     */
    public static String hashWithSalt(String plainPassword, String saltHex) {
        Objects.requireNonNull(plainPassword, "plainPassword must not be null");
        if (saltHex == null || saltHex.isBlank() || saltHex.contains(":")) {
            throw new IllegalArgumentException("Invalid salt format");
        }
        String payload = saltHex + ":" + plainPassword;
        byte[] digest = sha256(payload.getBytes(StandardCharsets.UTF_8));
        return saltHex + ":" + HEX.formatHex(digest);
    }

    /**
     * Verifies a plaintext password against a stored {@code saltHex:digestHex} string in constant time.
     */
    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null) {
            return false;
        }
        int sep = storedHash.indexOf(':');
        if (sep <= 0 || sep == storedHash.length() - 1) {
            return false;
        }
        String saltHex = storedHash.substring(0, sep);
        String expected = hashWithSalt(plainPassword, saltHex);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }
}
