package org.example.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = org.example.BackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.config=classpath:logback-it.xml")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Phone management (GET/POST/DELETE /api/users/.../phones) over HTTP")
class PhoneApiSeedTest {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate rest;
    @Autowired private DataSource dataSource;

    private String url(String path) { return "http://localhost:" + port + path; }

    private ResponseEntity<String> get(String path) {
        return rest.exchange(url(path), HttpMethod.GET, HttpEntity.EMPTY, String.class);
    }

    private ResponseEntity<String> post(String path, Object body) {
        return rest.exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(body), String.class);
    }

    private ResponseEntity<String> delete(String path) {
        return rest.exchange(url(path), HttpMethod.DELETE,
                HttpEntity.EMPTY, String.class);
    }

    @Test
    @DisplayName("GET list returns at least the seeded phone for ece(4)")
    void list_seeded_returnsAtLeastOnePhone() {
        ResponseEntity<String> r = get("/api/users/4/phones");
        assertEquals(HttpStatus.OK, r.getStatusCode(), "body=" + r.getBody());
        assertNotNull(r.getBody());
        assertTrue(r.getBody().contains("phoneId") || r.getBody().contains("phone_id"),
                "list payload must carry phoneId, got " + r.getBody());
    }

    @Test
    @DisplayName("POST then GET roundtrip: phone shows up in subsequent list")
    void postThenGet_phonePersisted() throws Exception {
        ResponseEntity<String> created = post("/api/users/4/phones",
                Map.of("phone", "+90 532 000 0001"));
        assertEquals(HttpStatus.OK, created.getStatusCode(),
                "add must be 200, body=" + created.getBody());

        ResponseEntity<String> list = get("/api/users/4/phones");
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertNotNull(list.getBody());
        assertTrue(list.getBody().contains("+90 532 000 0001"),
                "added phone must appear in subsequent list, got " + list.getBody());
    }

    @Test
    @DisplayName("POST allows multiple phones for the same user (1:N)")
    void postMultiple_allPersist() {
        post("/api/users/5/phones", Map.of("phone", "+90 533 000 1001"));
        post("/api/users/5/phones", Map.of("phone", "+90 533 000 1002"));
        ResponseEntity<String> list = get("/api/users/5/phones");
        assertEquals(HttpStatus.OK, list.getStatusCode());
        String body = list.getBody();
        assertNotNull(body);
        assertTrue(body.contains("+90 533 000 1001"), "phone 1 must persist, body=" + body);
        assertTrue(body.contains("+90 533 000 1002"), "phone 2 must persist, body=" + body);
    }

    @Test
    @DisplayName("DELETE by owner removes the phone")
    void delete_byOwner_removes() throws Exception {
        ResponseEntity<String> created = post("/api/users/6/phones",
                Map.of("phone", "+90 534 000 2222"));
        assertEquals(HttpStatus.OK, created.getStatusCode());
        int phoneId = extractPhoneId(created.getBody());

        ResponseEntity<String> del = delete("/api/users/phones/" + phoneId + "?userId=6");
        assertEquals(HttpStatus.OK, del.getStatusCode(),
                "owner delete must be 200, body=" + del.getBody());

        ResponseEntity<String> list = get("/api/users/6/phones");
        assertNotNull(list.getBody());
        assertFalse(list.getBody().contains("+90 534 000 2222"),
                "deleted phone must not appear in list, body=" + list.getBody());
    }

    @Test
    @DisplayName("DELETE by non-owner is 4xx and leaves phone intact")
    void delete_byNonOwner_rejected() throws Exception {
        ResponseEntity<String> created = post("/api/users/7/phones",
                Map.of("phone", "+90 535 000 3333"));
        assertEquals(HttpStatus.OK, created.getStatusCode());
        int phoneId = extractPhoneId(created.getBody());

        ResponseEntity<String> bad = delete("/api/users/phones/" + phoneId + "?userId=4");
        assertTrue(bad.getStatusCode().is4xxClientError(),
                "non-owner delete must be 4xx, got " + bad.getStatusCode()
                        + " body=" + bad.getBody());

        ResponseEntity<String> list = get("/api/users/7/phones");
        assertNotNull(list.getBody());
        assertTrue(list.getBody().contains("+90 535 000 3333"),
                "phone must survive rejected delete, body=" + list.getBody());
    }

    @Test
    @DisplayName("DELETE unknown phoneId is 4xx (not 500)")
    void delete_unknownPhoneId_is4xx() {
        ResponseEntity<String> r = delete("/api/users/phones/99999?userId=4");
        assertTrue(r.getStatusCode().is4xxClientError(),
                "delete of unknown phoneId must be 4xx, got " + r.getStatusCode()
                        + " body=" + r.getBody());
    }

    @Test
    @DisplayName("POST with blank phone is 400")
    void post_blankPhone_is400() {
        ResponseEntity<String> r = post("/api/users/4/phones", Map.of("phone", "   "));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "blank phone must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("POST for unknown user is 4xx")
    void post_unknownUser_is4xx() {
        ResponseEntity<String> r = post("/api/users/99999/phones",
                Map.of("phone", "+90 500 000 0000"));
        assertTrue(r.getStatusCode().is4xxClientError(),
                "unknown user must be 4xx, got " + r.getStatusCode()
                        + " body=" + r.getBody());
    }

    private static int extractPhoneId(String json) {
        int i = json.indexOf("\"phoneId\"");
        if (i < 0) i = json.indexOf("\"phone_id\"");
        if (i < 0) throw new IllegalStateException("phoneId not in " + json);
        int colon = json.indexOf(":", i);
        int end = colon + 1;
        while (end < json.length() && !Character.isDigit(json.charAt(end))) end++;
        int start = end;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        return Integer.parseInt(json.substring(start, end));
    }

    @AfterAll
    void restoreWatermark() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM UserPhone WHERE phone_id > 5");
        }
    }
}
