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
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = org.example.BackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.config=classpath:logback-it.xml")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Adversarial edge cases over HTTP (auth, rating, coupon, order, ownership)")
class AdversarialEdgeCasesSeedTest {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate rest;
    @Autowired private DataSource dataSource;

    private String url(String path) { return "http://localhost:" + port + path; }

    private ResponseEntity<String> post(String path, Object body) {
        return rest.exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(body), String.class);
    }

    private ResponseEntity<String> put(String path, Object body) {
        return rest.exchange(url(path), HttpMethod.PUT,
                new HttpEntity<>(body), String.class);
    }

    private ResponseEntity<String> delete(String path) {
        return rest.exchange(url(path), HttpMethod.DELETE,
                HttpEntity.EMPTY, String.class);
    }

    @Test
    @DisplayName("login with wrong password -> 401, no leakage of valid credentials")
    void login_wrongPassword_is401() throws Exception {
        int status = loginStatus("manager_ali", "definitely_wrong");
        assertEquals(401, status, "wrong password must be 401");
    }

    @Test
    @DisplayName("login with unknown username -> 401 (same as wrong password)")
    void login_unknownUsername_is401() throws Exception {
        int status = loginStatus("ghost_user_99", "anything");
        assertEquals(401, status,
                "unknown user must be 401 (not 404) to avoid enumeration");
    }

    private int loginStatus(String username, String password) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url("/api/auth/login")))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .build();
        return client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    @DisplayName("register with existing username -> 400 with clear message")
    void register_duplicateUsername_is400() {
        ResponseEntity<String> r = post("/api/auth/register", Map.of(
                "username", "manager_ali", "password", "x",
                "email", "fresh@x.com", "fullName", "X",
                "city", "Istanbul", "province", "Kadikoy"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "duplicate username must be 400, body=" + r.getBody());
        assertNotNull(r.getBody());
        assertTrue(r.getBody().toLowerCase().contains("username"),
                "error body should mention 'username', got " + r.getBody());
    }

    @Test
    @DisplayName("register with existing email -> 400 (not 500 from UNIQUE violation)")
    void register_duplicateEmail_is400() {
        ResponseEntity<String> r = post("/api/auth/register", Map.of(
                "username", "fresh_user_qa", "password", "x",
                "email", "ali@example.com", "fullName", "X",
                "city", "Istanbul", "province", "Kadikoy"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "duplicate email must be 400 not 500, body=" + r.getBody());
        assertNotNull(r.getBody());
        assertTrue(r.getBody().toLowerCase().contains("email"),
                "error body should mention 'email', got " + r.getBody());
    }

    @Test
    @DisplayName("rate after 24h since arrivedAt -> 409 ('rating window has expired')")
    void rate_afterWindow_is409() throws Exception {
        ResponseEntity<String> placed = post("/api/orders", Map.of(
                "customerId", 4, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 1))));
        assertTrue(placed.getStatusCode().is2xxSuccessful(),
                "setup placeOrder must succeed: " + placed.getBody());
        int orderId = extractOrderId(placed.getBody());

        assertTrue(put("/api/orders/" + orderId + "/accept",
                Map.of("managerId", 1)).getStatusCode().is2xxSuccessful());
        assertTrue(put("/api/orders/" + orderId + "/arrive",
                Map.of("managerId", 1)).getStatusCode().is2xxSuccessful());

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE `Order` SET arrived_at = NOW() - INTERVAL 25 HOUR WHERE order_id = ?")) {
            ps.setInt(1, orderId);
            int updated = ps.executeUpdate();
            assertEquals(1, updated, "test setup: must back-date exactly one row");
        }

        ResponseEntity<String> r = post("/api/orders/" + orderId + "/rate", Map.of(
                "customerId", 4, "restaurantId", 1, "score", 5, "comment", "late"));
        assertEquals(HttpStatus.CONFLICT, r.getStatusCode(),
                "rating beyond 24h must be 409, body=" + r.getBody());
        assertTrue(r.getBody() != null && r.getBody().toLowerCase().contains("24"),
                "body should mention the 24h window, got " + r.getBody());
    }

    @Test
    @DisplayName("rate a non-ARRIVED order (O11 SENT @ r3) -> 409")
    void rate_notArrived_is409() {
        ResponseEntity<String> r = post("/api/orders/11/rate", Map.of(
                "customerId", 8, "restaurantId", 3, "score", 5));
        assertEquals(HttpStatus.CONFLICT, r.getStatusCode(),
                "rating a non-ARRIVED order must be 409, body=" + r.getBody());
    }

    @Test
    @DisplayName("placeOrder with an expired coupon -> 400 ('Invalid or expired coupon')")
    void placeOrder_expiredCoupon_is400() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate(
                    "UPDATE Coupon SET valid_from='2026-01-01', valid_until='2026-01-02' "
                            + "WHERE coupon_id=3");
        }

        ResponseEntity<String> r = post("/api/orders", Map.of(
                "customerId", 6, "restaurantId", 3,
                "items", List.of(Map.of("itemId", 9, "quantity", 1)),
                "couponCode", "PASTA15"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "expired coupon must be 400, body=" + r.getBody());
        assertTrue(r.getBody() != null && r.getBody().toLowerCase().contains("coupon"),
                "body should mention the coupon, got " + r.getBody());
    }

    @Test
    @DisplayName("placeOrder with a deactivated coupon -> 400")
    void placeOrder_deactivatedCoupon_is400() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE Coupon SET is_active=0 WHERE coupon_id=4");
        }

        ResponseEntity<String> r = post("/api/orders", Map.of(
                "customerId", 5, "restaurantId", 4,
                "items", List.of(Map.of("itemId", 12, "quantity", 1)),
                "couponCode", "BURGER5"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "inactive coupon must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("placeOrder with itemId from a different restaurant -> 4xx")
    void placeOrder_itemFromWrongRestaurant_is4xx() {
        ResponseEntity<String> r = post("/api/orders", Map.of(
                "customerId", 4, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 9, "quantity", 1))));
        assertTrue(r.getStatusCode().is4xxClientError(),
                "item from wrong restaurant must be 4xx, got " + r.getStatusCode()
                        + " body=" + r.getBody());
    }

    @Test
    @DisplayName("addMenuItem missing description -> 400")
    void addMenuItem_missingDescription_is400() {
        ResponseEntity<String> r = post("/api/restaurants/1/menu", Map.of(
                "managerId", 1, "categoryId", 1,
                "name", "QA_NO_DESC", "imagePath", "img/x.jpg", "price", 10.0));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "missing description must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("addMenuItem missing image path -> 400")
    void addMenuItem_missingImage_is400() {
        ResponseEntity<String> r = post("/api/restaurants/1/menu", Map.of(
                "managerId", 1, "categoryId", 1,
                "name", "QA_NO_IMG", "description", "x", "price", 10.0));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "missing imagePath must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("updateMenuItem with blank description -> 400 (item 1 untouched)")
    void updateMenuItem_blankDescription_is400() {
        ResponseEntity<String> r = put("/api/restaurants/menu/1", Map.of(
                "managerId", 1, "categoryId", 1,
                "name", "Mercimek Corbasi",
                "description", "",
                "imagePath", "img/lentil.jpg",
                "price", 45.0));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "blank description must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("updateMenuItem with blank image -> 400 (item 1 untouched)")
    void updateMenuItem_blankImage_is400() {
        ResponseEntity<String> r = put("/api/restaurants/menu/1", Map.of(
                "managerId", 1, "categoryId", 1,
                "name", "Mercimek Corbasi",
                "description", "Classic red lentil soup with lemon",
                "imagePath", "",
                "price", 45.0));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "blank imagePath must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("manager 2 deleting category 1 (owned by manager 1) -> 4xx, category untouched")
    void deleteCategory_nonOwner_rejected() throws Exception {
        ResponseEntity<String> r = delete("/api/restaurants/categories/1?managerId=2");
        assertTrue(r.getStatusCode().is4xxClientError(),
                "non-owner delete must be 4xx, got " + r.getStatusCode()
                        + " body=" + r.getBody());
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM MenuCategory WHERE category_id=1")) {
            rs.next();
            assertEquals(1, rs.getInt(1),
                    "category 1 must still exist after rejected non-owner delete");
        }
    }

    @Test
    @DisplayName("manager 3 deleting menu item 1 (owned by manager 1) -> 4xx, item untouched")
    void deleteMenuItem_nonOwner_rejected() throws Exception {
        ResponseEntity<String> r = delete("/api/restaurants/menu/1?managerId=3");
        assertTrue(r.getStatusCode().is4xxClientError(),
                "non-owner menu delete must be 4xx, got " + r.getStatusCode()
                        + " body=" + r.getBody());
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM MenuItem WHERE item_id=1")) {
            rs.next();
            assertEquals(1, rs.getInt(1),
                    "item 1 must still exist after rejected non-owner delete");
        }
    }

    private static int extractOrderId(String json) {
        int i = json.indexOf("\"orderId\"");
        if (i < 0) throw new IllegalStateException("orderId not in " + json);
        int colon = json.indexOf(":", i);
        int end = colon + 1;
        while (end < json.length() && !Character.isDigit(json.charAt(end))) end++;
        int start = end;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        return Integer.parseInt(json.substring(start, end));
    }

    @AfterAll
    void restoreSeedRows() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate(
                    "UPDATE Coupon SET valid_from='2026-04-01', valid_until='2026-05-20' "
                            + "WHERE coupon_id=3");
            st.executeUpdate("UPDATE Coupon SET is_active=1 WHERE coupon_id=4");

            st.executeUpdate("DELETE FROM Rating       WHERE rating_id   > 10");
            st.executeUpdate("DELETE FROM OrderItem    WHERE order_id    > 12");
            st.executeUpdate("DELETE FROM `Order`      WHERE order_id    > 12");
        }
    }
}
