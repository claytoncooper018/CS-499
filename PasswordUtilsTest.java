package com.example.inventoryapp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Local JUnit tests for PasswordUtils. These run on the host JVM (no
 * emulator or Robolectric needed) because PasswordUtils only uses
 * java.security / java.util.Base64, not android.* APIs.
 */
public class PasswordUtilsTest {

    @Test
    public void generateSalt_producesNonNullNonEmptySalt() {
        String salt = PasswordUtils.generateSalt();
        assertNotNull(salt);
        assertTrue(salt.length() > 0);
    }

    @Test
    public void generateSalt_producesDifferentSaltsEachCall() {
        String saltOne = PasswordUtils.generateSalt();
        String saltTwo = PasswordUtils.generateSalt();
        assertNotEquals(saltOne, saltTwo);
    }

    @Test
    public void hashPassword_isDeterministicForSamePasswordAndSalt() {
        String salt = PasswordUtils.generateSalt();
        String hashOne = PasswordUtils.hashPassword("MySecret123", salt);
        String hashTwo = PasswordUtils.hashPassword("MySecret123", salt);
        assertEquals(hashOne, hashTwo);
    }

    @Test
    public void hashPassword_sameSalt_differentPasswords_produceDifferentHashes() {
        String salt = PasswordUtils.generateSalt();
        String hashOne = PasswordUtils.hashPassword("MySecret123", salt);
        String hashTwo = PasswordUtils.hashPassword("DifferentPassword", salt);
        assertNotEquals(hashOne, hashTwo);
    }

    @Test
    public void hashPassword_samePassword_differentSalts_produceDifferentHashes() {
        // Two users with the same password should never end up with the
        // same stored hash, since each gets a unique salt.
        String saltForUserA = PasswordUtils.generateSalt();
        String saltForUserB = PasswordUtils.generateSalt();

        String hashForUserA = PasswordUtils.hashPassword("SamePassword!", saltForUserA);
        String hashForUserB = PasswordUtils.hashPassword("SamePassword!", saltForUserB);

        assertNotEquals(hashForUserA, hashForUserB);
    }

    @Test
    public void verifyPassword_acceptsCorrectPassword() {
        String salt = PasswordUtils.generateSalt();
        String storedHash = PasswordUtils.hashPassword("CorrectHorseBatteryStaple", salt);

        assertTrue(PasswordUtils.verifyPassword("CorrectHorseBatteryStaple", salt, storedHash));
    }

    @Test
    public void verifyPassword_rejectsIncorrectPassword() {
        String salt = PasswordUtils.generateSalt();
        String storedHash = PasswordUtils.hashPassword("CorrectHorseBatteryStaple", salt);

        assertFalse(PasswordUtils.verifyPassword("WrongPassword", salt, storedHash));
    }

    @Test
    public void verifyPassword_rejectsWhenSaltDoesNotMatch() {
        String correctSalt = PasswordUtils.generateSalt();
        String wrongSalt = PasswordUtils.generateSalt();
        String storedHash = PasswordUtils.hashPassword("CorrectHorseBatteryStaple", correctSalt);

        assertFalse(PasswordUtils.verifyPassword("CorrectHorseBatteryStaple", wrongSalt, storedHash));
    }
}
