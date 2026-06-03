package org.example.controller;

import java.util.List;
import java.util.Map;

/** Type-safe extraction helpers for loosely-typed JSON request bodies; throws IllegalArgumentException (HTTP 400) on bad input. */
final class ControllerInputs {

    private ControllerInputs() {}

    /** Required integer. Accepts JSON numbers and numeric strings. */
    static int requireInt(Map<String, ?> body, String key) {
        Object v = required(body, key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) { /* fall through */ }
        }
        throw new IllegalArgumentException("'" + key + "' must be an integer.");
    }

    /** Required double. Accepts JSON numbers and numeric strings. */
    static double requireDouble(Map<String, ?> body, String key) {
        Object v = required(body, key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) { /* fall through */ }
        }
        throw new IllegalArgumentException("'" + key + "' must be a number.");
    }

    /** Required non-blank string. */
    static String requireString(Map<String, ?> body, String key) {
        Object v = required(body, key);
        String s = v.toString();
        if (s.isBlank()) {
            throw new IllegalArgumentException("'" + key + "' must not be blank.");
        }
        return s;
    }

    /**
     * Optional string: returns {@code def} when the key is absent or null.
     * If present it must be a string-ish value (never throws on type).
     */
    static String optString(Map<String, ?> body, String key, String def) {
        if (body == null) return def;
        Object v = body.get(key);
        return v == null ? def : v.toString();
    }

    /** Required, non-empty list. */
    @SuppressWarnings("unchecked")
    static <T> List<T> requireList(Map<String, ?> body, String key) {
        Object v = required(body, key);
        if (!(v instanceof List<?> list)) {
            throw new IllegalArgumentException("'" + key + "' must be a list.");
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("'" + key + "' must not be empty.");
        }
        return (List<T>) list;
    }

    /**
     * Optional list: returns an empty list when the key is absent or null.
     * If present it must be a JSON list, otherwise throws
     * {@link IllegalArgumentException} (HTTP 400) rather than a 500.
     */
    @SuppressWarnings("unchecked")
    static <T> List<T> optStringList(Map<String, ?> body, String key) {
        if (body == null) return List.of();
        Object v = body.get(key);
        if (v == null) return List.of();
        if (!(v instanceof List<?> list)) {
            throw new IllegalArgumentException("'" + key + "' must be a list.");
        }
        return (List<T>) list;
    }

    private static Object required(Map<String, ?> body, String key) {
        if (body == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        Object v = body.get(key);
        if (v == null) {
            throw new IllegalArgumentException("'" + key + "' is required.");
        }
        return v;
    }
}
