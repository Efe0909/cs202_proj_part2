package org.example.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ControllerInputs defensive extraction boundaries")
class ControllerInputsTest {

    private Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    @DisplayName("requireInt accepts a JSON number")
    void requireInt_number() {
        assertEquals(7, ControllerInputs.requireInt(map("k", 7), "k"));
    }

    @Test
    @DisplayName("requireInt accepts a numeric string (the documented coercion)")
    void requireInt_numericString() {
        assertEquals(42, ControllerInputs.requireInt(map("k", " 42 "), "k"));
    }

    @Test
    @DisplayName("requireInt truncates a Double via Number.intValue()")
    void requireInt_doubleNarrows() {
        assertEquals(3, ControllerInputs.requireInt(map("k", 3.9), "k"));
    }

    @Test
    @DisplayName("requireInt missing key -> IllegalArgumentException naming the field")
    void requireInt_missing() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ControllerInputs.requireInt(map(), "customerId"));
        assertTrue(ex.getMessage().contains("customerId"));
    }

    @Test
    @DisplayName("requireInt null value -> IllegalArgumentException (not NPE)")
    void requireInt_nullValue() {
        Map<String, Object> m = new HashMap<>();
        m.put("k", null);
        assertThrows(IllegalArgumentException.class,
                () -> ControllerInputs.requireInt(m, "k"));
    }

    @Test
    @DisplayName("requireInt non-numeric string -> IllegalArgumentException (not NumberFormat)")
    void requireInt_garbageString() {
        assertThrows(IllegalArgumentException.class,
                () -> ControllerInputs.requireInt(map("k", "abc"), "k"));
    }

    @Test
    @DisplayName("requireInt wrong type (Map) -> IllegalArgumentException (not ClassCast)")
    void requireInt_wrongType() {
        assertThrows(IllegalArgumentException.class,
                () -> ControllerInputs.requireInt(map("k", Map.of("x", 1)), "k"));
    }

    @Test
    @DisplayName("requireInt blank string -> IllegalArgumentException")
    void requireInt_blankString() {
        assertThrows(IllegalArgumentException.class,
                () -> ControllerInputs.requireInt(map("k", "   "), "k"));
    }

    @Test
    @DisplayName("requireDouble accepts number and numeric string")
    void requireDouble_ok() {
        assertEquals(2.5, ControllerInputs.requireDouble(map("k", 2.5), "k"), 1e-9);
        assertEquals(3.0, ControllerInputs.requireDouble(map("k", "3"), "k"), 1e-9);
    }

    @Test
    @DisplayName("requireDouble garbage / missing -> IllegalArgumentException")
    void requireDouble_bad() {
        assertThrows(IllegalArgumentException.class,
                () -> ControllerInputs.requireDouble(map("k", "NaNotNumber"), "k"));
        assertThrows(IllegalArgumentException.class,
                () -> ControllerInputs.requireDouble(map(), "price"));
    }

    @Test
    @DisplayName("requireString blank -> IllegalArgumentException")
    void requireString_blank() {
        assertThrows(IllegalArgumentException.class,
                () -> ControllerInputs.requireString(map("k", "   "), "k"));
    }

    @Test
    @DisplayName("requireString missing -> IllegalArgumentException")
    void requireString_missing() {
        assertThrows(IllegalArgumentException.class,
                () -> ControllerInputs.requireString(map(), "username"));
    }

    @Test
    @DisplayName("requireString returns the value when present")
    void requireString_ok() {
        assertEquals("hi", ControllerInputs.requireString(map("k", "hi"), "k"));
    }

    @Test
    @DisplayName("optString returns default for missing/null, value otherwise")
    void optString_behaviour() {
        assertEquals("def", ControllerInputs.optString(map(), "k", "def"));
        Map<String, Object> m = new HashMap<>();
        m.put("k", null);
        assertEquals("def", ControllerInputs.optString(m, "k", "def"));
        assertEquals("v", ControllerInputs.optString(map("k", "v"), "k", "def"));
        assertNull(ControllerInputs.optString(null, "k", null));
    }

    @Test
    @DisplayName("requireList rejects missing, empty, and wrong-type")
    void requireList_guards() {
        assertThrows(IllegalArgumentException.class,
                () -> ControllerInputs.requireList(map(), "items"));
        assertThrows(IllegalArgumentException.class,
                () -> ControllerInputs.requireList(map("items", List.of()), "items"));
        assertThrows(IllegalArgumentException.class,
                () -> ControllerInputs.requireList(map("items", "notalist"), "items"));
    }

    @Test
    @DisplayName("requireList returns a non-empty list")
    void requireList_ok() {
        List<Object> l = ControllerInputs.requireList(
                map("items", List.of(1, 2)), "items");
        assertEquals(2, l.size());
    }

    @Test
    @DisplayName("null body -> IllegalArgumentException 'Request body is required.'")
    void nullBody() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ControllerInputs.requireInt(null, "k"));
        assertTrue(ex.getMessage().toLowerCase().contains("body"));
    }
}
