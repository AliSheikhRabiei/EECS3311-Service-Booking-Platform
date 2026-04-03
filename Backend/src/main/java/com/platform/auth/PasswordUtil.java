package com.platform.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing helpers.
 *
 * New hashes use PBKDF2-HMAC-SHA256 and are stored as:
 *   pbkdf2$iterations$base64(salt)$base64(hash)
 *
 * Legacy hashes in the older salt:sha256 format are still accepted so existing
 * accounts remain usable during migration.
 */
public class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int ITERATIONS = 150_000;

    /** Hashes a plaintext password and returns the storable string. */
    public static String hash(String plaintext) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(plaintext, salt, ITERATIONS, KEY_LENGTH_BITS);
        return "pbkdf2$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    /** Returns true if the plaintext matches the stored hash. */
    public static boolean verify(String plaintext, String stored) {
        if (stored == null || stored.isBlank()) return false;

        try {
            if (stored.startsWith("pbkdf2$")) {
                return verifyPbkdf2(plaintext, stored);
            }
            if (stored.contains(":")) {
                return verifyLegacySha256(plaintext, stored);
            }
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean verifyPbkdf2(String plaintext, String stored) {
        String[] parts = stored.split("\\$", 4);
        if (parts.length != 4) return false;

        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        byte[] actual = pbkdf2(plaintext, salt, iterations, expected.length * 8);
        return MessageDigest.isEqual(actual, expected);
    }

    private static boolean verifyLegacySha256(String plaintext, String stored) {
        String[] parts = stored.split(":", 2);
        if (parts.length != 2) return false;

        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expected = Base64.getDecoder().decode(parts[1]);
        byte[] actual = sha256(salt, plaintext);
        return MessageDigest.isEqual(actual, expected);
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations, int keyLengthBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("PBKDF2 password hashing not available", e);
        }
    }

    private static byte[] sha256(byte[] salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            return md.digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
