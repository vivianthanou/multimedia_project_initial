package gr.ntua.multimedia.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class PasswordHasher {
    private static final String SALT = "MEDIALAB_STATIC_SALT_2025";

    private PasswordHasher() {}

    public static String hash(String plain) {
        ValidationUtil.requireNonBlank(plain, "plain");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest((SALT + plain).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static boolean matches(String plain, String hash) {
        ValidationUtil.requireNonBlank(hash, "hash");
        return hash(plain).equals(hash);
    }
}