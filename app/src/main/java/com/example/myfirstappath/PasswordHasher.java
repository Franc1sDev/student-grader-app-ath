package com.example.myfirstappath;

import android.util.Base64;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/*
 Passwords are NEVER stored as typed. Each lecturer gets a random "salt", and the database stores
 PBKDF2(password + salt). At login the typed password is hashed the same way and the two hashes compared.
*/
public final class PasswordHasher {

    private PasswordHasher() { }

    private static final int ITERATIONS = 10_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;

    public static String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    public static String hash(String password, String salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(),
                Base64.decode(salt, Base64.NO_WRAP), ITERATIONS, KEY_LENGTH_BITS);
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is not available on this device", e);
        } finally {
            spec.clearPassword();
        }
    }

    public static boolean matches(String password, String salt, String expectedHash) {
        if (password == null || password.isEmpty()) return false;
        byte[] typed = hash(password, salt).getBytes();
        byte[] expected = expectedHash.getBytes();
        return MessageDigest.isEqual(typed, expected); // same time whatever the input (no timing hints)
    }
}
