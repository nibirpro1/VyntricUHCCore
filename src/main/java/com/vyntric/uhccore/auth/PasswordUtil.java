package com.vyntric.uhccore.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Small helper for salted password hashing. We don't store plaintext passwords anywhere -
 * only a random salt + the salted/stretched SHA-256 hash go into auth.yml.
 */
public final class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_ROUNDS = 10_000;

    private PasswordUtil() {
    }

    public static String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String hash(String password, String saltBase64) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] salt = Base64.getDecoder().decode(saltBase64);

            byte[] result = password.getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < HASH_ROUNDS; i++) {
                digest.reset();
                digest.update(salt);
                digest.update(result);
                result = digest.digest();
            }
            return Base64.getEncoder().encodeToString(result);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every JVM, this should never happen.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static boolean matches(String password, String saltBase64, String expectedHash) {
        String actual = hash(password, saltBase64);
        return constantTimeEquals(actual, expectedHash);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) return false;

        int diff = 0;
        for (int i = 0; i < aBytes.length; i++) {
            diff |= aBytes[i] ^ bBytes[i];
        }
        return diff == 0;
    }
}
