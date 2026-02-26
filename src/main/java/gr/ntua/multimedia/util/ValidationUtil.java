package gr.ntua.multimedia.util;

import java.util.Objects;

public final class ValidationUtil {
    private ValidationUtil() {}

    public static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
    }

    public static void requireNonNull(Object obj, String fieldName) {
        Objects.requireNonNull(obj, fieldName + " cannot be null");
    }

    public static String normalizeParagraphText(String text) {
        if (text == null) return "";
        String t = text.replace("\r\n", "\n").replace("\r", "\n");
        t = t.replace("\t", "    ");
        return t;
    }
}