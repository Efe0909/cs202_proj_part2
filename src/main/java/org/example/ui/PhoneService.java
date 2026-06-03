package org.example.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper over the phone-management endpoints. Mirrors AddressService
 * but without a "selected" concept — phones are a flat 1:N list.
 *
 *   GET    /users/{userId}/phones          -> [{phoneId, phone}]
 *   POST   /users/{userId}/phones          body {phone}
 *   DELETE /users/phones/{phoneId}?userId=...
 */
final class PhoneService {

    private PhoneService() {}

    private static final ObjectMapper MAPPER = ApiClient.mapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static final class Phone {
        final int    id;
        final String phone;

        Phone(int id, String phone) {
            this.id    = id;
            this.phone = phone;
        }

        @Override
        public String toString() {
            return phone == null || phone.isBlank() ? "—" : phone;
        }
    }

    static List<Phone> list(int userId) throws Exception {
        String json = ApiClient.get("/users/" + userId + "/phones", String.class);
        List<Map<String, Object>> raw =
                MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        List<Phone> out = new ArrayList<>();
        if (raw == null) return out;
        for (Map<String, Object> m : raw) {
            out.add(new Phone(
                    asInt(first(m, "phoneId", "id", "phone_id")),
                    asStr(first(m, "phone", "number"))));
        }
        return out;
    }

    static void add(int userId, String phone) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", phone);
        ApiClient.post("/users/" + userId + "/phones", body);
    }

    static void delete(int userId, int phoneId) {
        ApiClient.delete("/users/phones/" + phoneId + "?userId=" + userId);
    }

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
}
