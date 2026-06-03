package org.example.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin, DEFENSIVE wrapper over the (parallel-developed) multi-address backend
 * contract. All key-name access is forgiving so the UI degrades gracefully if
 * the backend uses slightly different field names or the feature is not yet
 * deployed.
 *
 * Assumed contract:
 *   GET    /users/{userId}/addresses          -> [{addressId, city, province, selected}]
 *   POST   /users/{userId}/addresses          body {city, province}
 *   DELETE /users/addresses/{addressId}?userId=...
 *   PUT    /users/{userId}/selected-address   body {addressId}
 */
final class AddressService {

    private AddressService() {}

    private static final ObjectMapper MAPPER = ApiClient.mapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** A single address row, tolerant of backend key-name variance. */
    static final class Address {
        final int     id;
        final String  city;
        final String  province;
        final boolean selected;

        Address(int id, String city, String province, boolean selected) {
            this.id       = id;
            this.city     = city;
            this.province = province;
            this.selected = selected;
        }

        @Override
        public String toString() {
            String c = city == null || city.isBlank() ? "—" : city;
            String p = province == null || province.isBlank() ? "" : ", " + province;
            return c + p;
        }
    }

    /** Loads all addresses for a user. Throws on transport/HTTP failure. */
    static List<Address> list(int userId) throws Exception {
        String json = ApiClient.get("/users/" + userId + "/addresses", String.class);
        List<Map<String, Object>> raw =
                MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        List<Address> out = new ArrayList<>();
        if (raw == null) return out;
        for (Map<String, Object> m : raw) {
            out.add(new Address(
                    asInt(first(m, "addressId", "id", "address_id")),
                    asStr(first(m, "city")),
                    asStr(first(m, "province", "state", "region")),
                    asBool(first(m, "selected", "isSelected", "active", "default"))));
        }
        return out;
    }

    /** Returns the selected address, or the first one, or null if none. */
    static Address selectedOrFirst(List<Address> addresses) {
        if (addresses == null || addresses.isEmpty()) return null;
        for (Address a : addresses) {
            if (a.selected) return a;
        }
        return addresses.get(0);
    }

    static void add(int userId, String city, String province) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("city", city);
        body.put("province", province);
        ApiClient.post("/users/" + userId + "/addresses", body);
    }

    static void delete(int userId, int addressId) {
        ApiClient.delete("/users/addresses/" + addressId + "?userId=" + userId);
    }

    static void select(int userId, int addressId) {
        ApiClient.put("/users/" + userId + "/selected-address",
                Map.of("addressId", addressId));
    }

    /**
     * The city the customer should browse in: their selected address's
     * city. Users.city no longer exists — the User entity is normalised so
     * the city is on UserAddress. Returns null when no selected address.
     */
    static String resolveBrowsingCity(org.example.model.User user) {
        try {
            Address sel = selectedOrFirst(list(user.getUserId()));
            if (sel != null && sel.city != null && !sel.city.isBlank()) {
                return sel.city;
            }
        } catch (Exception ignored) {
            // Backend unreachable — caller renders an empty state.
        }
        return null;
    }

    // ── tiny defensive helpers ──────────────────────────────────────────────

    private static Object first(Map<String, Object> m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) return v;
        }
        return null;
    }

    private static int asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return -1; }
    }

    private static String asStr(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static boolean asBool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(o)) || "1".equals(String.valueOf(o));
    }

    static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
