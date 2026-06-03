package org.example.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilTest {

    @Test
    void generateSalt_returns32CharHexString() {
        String salt = PasswordUtil.generateSalt();
        assertNotNull(salt);
        assertEquals(32, salt.length());
        assertTrue(salt.matches("[0-9a-f]+"), "Salt should be lowercase hex");
    }

    @Test
    void generateSalt_isRandom() {
        String salt1 = PasswordUtil.generateSalt();
        String salt2 = PasswordUtil.generateSalt();
        assertNotEquals(salt1, salt2, "Two successive salts should differ");
    }

    @Test
    void hash_returns64CharHexString() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash("secret", salt);
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"), "Hash should be lowercase hex");
    }

    @Test
    void verify_correctPassword_returnsTrue() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash("myPassword", salt);
        assertTrue(PasswordUtil.verify("myPassword", salt, hash));
    }

    @Test
    void verify_wrongPassword_returnsFalse() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash("myPassword", salt);
        assertFalse(PasswordUtil.verify("wrongPassword", salt, hash));
    }

    @Test
    void verify_wrongSalt_returnsFalse() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash("myPassword", salt);
        String differentSalt = PasswordUtil.generateSalt();
        assertFalse(PasswordUtil.verify("myPassword", differentSalt, hash));
    }

    @Test
    void hash_isDeterministic() {
        String salt = PasswordUtil.generateSalt();
        String hash1 = PasswordUtil.hash("deterministic", salt);
        String hash2 = PasswordUtil.hash("deterministic", salt);
        assertEquals(hash1, hash2, "Hash must be deterministic for identical inputs");
    }
}
