package org.example.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;

/** PBKDF2-HMAC-SHA256 password hashing and constant-time verification. */
public class PasswordUtil {

    private static final int ITERATIONS  = 100_000;
    private static final int KEY_BITS    = 256;
    private static final int SALT_BYTES  = 16;

    /** Generates a cryptographically random 16-byte salt encoded as hex. */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    /** Hashes a password with PBKDF2-HMAC-SHA256 and returns a hex-encoded hash. */
    public static String hash(String password, String saltHex) {
        try {
            byte[] salt = HexFormat.of().parseHex(saltHex);
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    /** Verifies a password in constant time to prevent timing-based side-channel attacks. */
    public static boolean verify(String password, String saltHex, String storedHashHex) {
        String candidate = hash(password, saltHex);
        if (candidate.length() != storedHashHex.length()) return false;
        int diff = 0;
        for (int i = 0; i < candidate.length(); i++) {
            diff |= candidate.charAt(i) ^ storedHashHex.charAt(i);
        }
        return diff == 0;
    }
}
