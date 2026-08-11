package com.example.inventoryapp;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * PasswordUtils provides salted SHA-256 password hashing so that plain-text
 * passwords are never stored in or compared against the database.
 *
 * Each user gets a unique, cryptographically random salt. The salt is
 * combined with the password before hashing so that two users with the
 * same password never produce the same stored hash, and so that
 * precomputed rainbow-table attacks are not effective against the
 * database if it is ever read by an attacker.
 *
 * Deliberately uses only java.util.Base64 and java.security classes
 * (no android.* APIs) so this class can be exercised by fast local JUnit
 * tests without requiring an emulator or Robolectric.
 */
public final class PasswordUtils {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH_BYTES = 16;

    private PasswordUtils() {
        // Utility class; no instances.
    }

    /**
     * Generates a new, cryptographically random salt.
     *
     * @return a Base64-encoded salt string, safe to store in the database
     */
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] saltBytes = new byte[SALT_LENGTH_BYTES];
        random.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    /**
     * Hashes a plain-text password together with a salt using SHA-256.
     * Hashing is deterministic for a given password/salt pair, so the
     * same inputs always produce the same output hash.
     *
     * @param password plain-text password to hash
     * @param salt     Base64-encoded salt (as produced by generateSalt())
     * @return Base64-encoded hash string, safe to store in the database
     */
    public static String hashPassword(String password, String salt) {
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        if (salt == null) {
            throw new IllegalArgumentException("salt must not be null");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            digest.update(Base64.getDecoder().decode(salt));
            byte[] hashedBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every standard JVM/Android
            // runtime, so this should never happen in practice.
            throw new IllegalStateException("Required hash algorithm not available: " + HASH_ALGORITHM, e);
        }
    }

    /**
     * Verifies a plain-text password attempt against a previously stored
     * salted hash. This is what login should call instead of doing a
     * direct string comparison against a stored plain-text password.
     *
     * @param passwordAttempt plain-text password entered by the user
     * @param salt            Base64-encoded salt stored for this user
     * @param storedHash      Base64-encoded hash stored for this user
     * @return true if the password attempt matches, false otherwise
     */
    public static boolean verifyPassword(String passwordAttempt, String salt, String storedHash) {
        if (passwordAttempt == null || salt == null || storedHash == null) {
            return false;
        }
        String attemptHash = hashPassword(passwordAttempt, salt);
        return constantTimeEquals(attemptHash, storedHash);
    }

    /**
     * Compares two strings in constant time to avoid leaking information
     * about how many leading characters matched via response-time
     * differences (timing attacks).
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
