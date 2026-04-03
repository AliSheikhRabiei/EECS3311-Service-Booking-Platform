package com.platform.auth;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Simple password hashing using SHA-256 + random salt.
 * Format stored in DB:  base64(salt) + ":" + base64(sha256(salt + password))
 *
 * Good enough for a course project. Production would use bcrypt.
 */
public class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Hashes a plaintext password and returns the storable string. */
    public static String hash(String plaintext) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = sha256(salt, plaintext);
        return Base64.getEncoder().encodeToString(salt) + ":"
             + Base64.getEncoder().encodeToString(hash);
    }

    /** Returns true if the plaintext matches the stored hash. */
    public static boolean verify(String plaintext, String stored) {
        if (stored == null || !stored.contains(":")) return false;
        String[] parts = stored.split(":", 2);
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expected = Base64.getDecoder().decode(parts[1]);
        byte[] actual   = sha256(salt, plaintext);
        // Constant-time comparison
        if (actual.length != expected.length) return false;
        int diff = 0;
        for (int i = 0; i < actual.length; i++) diff |= actual[i] ^ expected[i];
        return diff == 0;
    }

    private static byte[] sha256(byte[] salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            return md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
