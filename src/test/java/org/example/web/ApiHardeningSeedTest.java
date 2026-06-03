package org.example.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = org.example.BackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.config=classpath:logback-it.xml")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("API hardening (validation / authz / lifecycle) over HTTP against seeded MySQL")
class ApiHardeningSeedTest {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate rest;
    @Autowired private DataSource dataSource;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> post(String path, Object body) {
        return rest.postForEntity(url(path), body, String.class);
    }

    private ResponseEntity<String> put(String path, Object body) {
        return rest.exchange(url(path),
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(body),
                String.class);
    }

    private ResponseEntity<String> delete(String path) {
        return rest.exchange(url(path),
                org.springframework.http.HttpMethod.DELETE,
                org.springframework.http.HttpEntity.EMPTY,
                String.class);
    }

    private int countWhere(String sql) throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    @DisplayName("POST /api/orders with no 'items' key -> 400")
    void placeOrder_missingItems_is400() {
        ResponseEntity<String> r = post("/api/orders",
                Map.of("customerId", 4, "restaurantId", 1));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "missing required 'items' must be a clean 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("POST /api/orders with empty items list -> 400")
    void placeOrder_emptyItems_is400() {
        ResponseEntity<String> r = post("/api/orders",
                Map.of("customerId", 4, "restaurantId", 1, "items", List.of()));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "empty items must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("POST /api/orders with quantity <= 0 -> 400")
    void placeOrder_nonPositiveQuantity_is400() {
        ResponseEntity<String> r = post("/api/orders", Map.of(
                "customerId", 4, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 0))));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "quantity 0 must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("POST /api/orders with a String where int expected (customerId='abc') -> 400 not 500")
    void placeOrder_stringWhereIntExpected_is400() {
        ResponseEntity<String> r = post("/api/orders", Map.of(
                "customerId", "abc", "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 1))));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "non-numeric customerId must be 400 (not a 500 ClassCast), body=" + r.getBody());
    }

    @Test
    @DisplayName("POST /api/orders with itemId as object instead of number -> 400 not 500")
    void placeOrder_itemIdWrongType_is400() {
        ResponseEntity<String> r = post("/api/orders", Map.of(
                "customerId", 4, "restaurantId", 1,
                "items", List.of(Map.of("itemId", Map.of("x", 1), "quantity", 1))));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "object itemId must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("POST /api/auth/register missing required fields -> 400")
    void register_missingFields_is400() {
        ResponseEntity<String> r = post("/api/auth/register",
                Map.of("username", "qa_user"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "register missing fields must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("POST coupon with missing managerId -> 400")
    void createCoupon_missingManagerId_is400() {
        ResponseEntity<String> r = post("/api/restaurants/1/coupons", Map.of(
                "code", "QATEST1", "discountType", "PERCENTAGE",
                "discountValue", 10, "validFrom", "2026-06-01",
                "validUntil", "2026-06-30"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "coupon without managerId must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("PUT /api/restaurants/{id} missing managerId -> 400")
    void updateRestaurant_missingManagerId_is400() {
        ResponseEntity<String> r = put("/api/restaurants/1", Map.of(
                "name", "Hacked Name", "cuisineType", "Turkish",
                "address", "x", "city", "Istanbul"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "PUT restaurant without managerId must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("POST /api/restaurants/{id}/categories missing managerId -> 400")
    void addCategory_missingManagerId_is400() {
        ResponseEntity<String> r = post("/api/restaurants/1/categories",
                Map.of("name", "Sneaky Category"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "add category without managerId must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("POST /api/restaurants/{id}/menu missing managerId -> 400")
    void addMenuItem_missingManagerId_is400() {
        ResponseEntity<String> r = post("/api/restaurants/1/menu", Map.of(
                "categoryId", 1, "name", "Sneaky Item", "price", 10.0));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "add menu item without managerId must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("DELETE /api/restaurants/categories/{id} without ?managerId -> 400")
    void deleteCategory_missingManagerIdParam_is400() {
        ResponseEntity<String> r = delete("/api/restaurants/categories/9999");
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "delete category without managerId must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("DELETE /api/restaurants/menu/{id} without ?managerId -> 400")
    void deleteMenuItem_missingManagerIdParam_is400() {
        ResponseEntity<String> r = delete("/api/restaurants/menu/9999");
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "delete menu item without managerId must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("DELETE /api/restaurants/coupons/{id} without ?managerId -> 400")
    void deleteCoupon_missingManagerIdParam_is400() {
        ResponseEntity<String> r = delete("/api/restaurants/coupons/9999");
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "delete coupon without managerId must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("PUT /api/restaurants/1 by non-owner (manager 2) rejected and unchanged; owner (1) succeeds")
    void updateRestaurant_ownershipEnforced() throws Exception {
        ResponseEntity<String> bad = put("/api/restaurants/1", Map.of(
                "managerId", 2, "name", "STOLEN", "cuisineType", "X",
                "address", "X", "city", "Istanbul"));
        assertTrue(bad.getStatusCode().is4xxClientError(),
                "non-owner update must be 4xx, got " + bad.getStatusCode()
                        + " body=" + bad.getBody());
        assertEquals(0, countWhere(
                "SELECT COUNT(*) FROM Restaurant WHERE restaurant_id=1 AND name='STOLEN'"),
                "non-owner must NOT have mutated restaurant 1");
        assertEquals(1, countWhere(
                "SELECT COUNT(*) FROM Restaurant WHERE restaurant_id=1 "
                        + "AND name='Bosphorus Kebab'"),
                "restaurant 1 name must be intact after rejected takeover");

        ResponseEntity<String> ok = put("/api/restaurants/1", Map.of(
                "managerId", 1, "name", "Bosphorus Kebab",
                "cuisineType", "Turkish",
                "address", "Taksim Meydani No:5, Beyoglu", "city", "Istanbul"));
        assertTrue(ok.getStatusCode().is2xxSuccessful(),
                "legitimate owner update must succeed, got " + ok.getStatusCode()
                        + " body=" + ok.getBody());
    }

    @Test
    @DisplayName("POST add menu item to r1 by non-owner (manager 2) rejected and not inserted")
    void addMenuItem_ownershipEnforced() throws Exception {
        int before = countWhere(
                "SELECT COUNT(*) FROM MenuItem WHERE name='QA_OWNERSHIP_PROBE'");
        ResponseEntity<String> bad = post("/api/restaurants/1/menu", Map.of(
                "managerId", 2, "categoryId", 1,
                "name", "QA_OWNERSHIP_PROBE",
                "description", "probe", "imagePath", "img/probe.jpg",
                "price", 10.0));
        assertTrue(bad.getStatusCode().is4xxClientError(),
                "non-owner add menu item must be 4xx, got " + bad.getStatusCode()
                        + " body=" + bad.getBody());
        assertEquals(before, countWhere(
                "SELECT COUNT(*) FROM MenuItem WHERE name='QA_OWNERSHIP_PROBE'"),
                "non-owner must NOT have inserted a menu item");
    }

    @Test
    @DisplayName("POST add category to r1 by non-owner (manager 3) rejected and not inserted")
    void addCategory_ownershipEnforced() throws Exception {
        int before = countWhere(
                "SELECT COUNT(*) FROM MenuCategory WHERE name='QA_CAT_PROBE'");
        ResponseEntity<String> bad = post("/api/restaurants/1/categories", Map.of(
                "managerId", 3, "name", "QA_CAT_PROBE"));
        assertTrue(bad.getStatusCode().is4xxClientError(),
                "non-owner add category must be 4xx, got " + bad.getStatusCode()
                        + " body=" + bad.getBody());
        assertEquals(before, countWhere(
                "SELECT COUNT(*) FROM MenuCategory WHERE name='QA_CAT_PROBE'"),
                "non-owner must NOT have inserted a category");
    }

    @Test
    @DisplayName("POST create coupon on r1 by non-owner (manager 2) rejected and not inserted")
    void createCoupon_ownershipEnforced() throws Exception {
        int before = countWhere(
                "SELECT COUNT(*) FROM Coupon WHERE code='QA_OWN_CPN'");
        ResponseEntity<String> bad = post("/api/restaurants/1/coupons", Map.of(
                "managerId", 2, "code", "QA_OWN_CPN",
                "discountType", "PERCENTAGE", "discountValue", 10,
                "validFrom", "2026-06-01", "validUntil", "2026-06-30"));
        assertTrue(bad.getStatusCode().is4xxClientError(),
                "non-owner create coupon must be 4xx, got " + bad.getStatusCode()
                        + " body=" + bad.getBody());
        assertEquals(before, countWhere(
                "SELECT COUNT(*) FROM Coupon WHERE code='QA_OWN_CPN'"),
                "non-owner must NOT have inserted a coupon");
    }

    @Test
    @DisplayName("DELETE coupon 1 by non-owner (manager 2) rejected; coupon stays active")
    void deleteCoupon_ownershipEnforced() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE Coupon SET is_active = TRUE WHERE coupon_id = 1");
        }
        ResponseEntity<String> bad =
                delete("/api/restaurants/coupons/1?managerId=2");
        assertTrue(bad.getStatusCode().is4xxClientError(),
                "non-owner delete coupon must be 4xx, got " + bad.getStatusCode()
                        + " body=" + bad.getBody());
        assertEquals(1, countWhere(
                "SELECT COUNT(*) FROM Coupon WHERE coupon_id=1 AND is_active=1"),
                "coupon 1 must remain active after rejected non-owner delete");
    }

    @Test
    @DisplayName("Add menu item with price 0 -> 400; price negative -> 400 (owner)")
    void menuItemPriceMustBePositive() {
        ResponseEntity<String> zero = post("/api/restaurants/1/menu", Map.of(
                "managerId", 1, "categoryId", 1,
                "name", "QA_ZERO_PRICE",
                "description", "x", "imagePath", "img/x.jpg",
                "price", 0));
        assertEquals(HttpStatus.BAD_REQUEST, zero.getStatusCode(),
                "price 0 must be 400, body=" + zero.getBody());

        ResponseEntity<String> neg = post("/api/restaurants/1/menu", Map.of(
                "managerId", 1, "categoryId", 1,
                "name", "QA_NEG_PRICE",
                "description", "x", "imagePath", "img/x.jpg",
                "price", -5.0));
        assertEquals(HttpStatus.BAD_REQUEST, neg.getStatusCode(),
                "negative price must be 400, body=" + neg.getBody());
    }

    @Test
    @DisplayName("Rate order with score 0 -> 4xx; score 6 -> 4xx")
    void ratingScoreBounds() {
        ResponseEntity<String> zero = post("/api/orders/1/rate", Map.of(
                "customerId", 4, "restaurantId", 1, "score", 0));
        assertEquals(HttpStatus.BAD_REQUEST, zero.getStatusCode(),
                "score 0 must be 400, body=" + zero.getBody());

        ResponseEntity<String> six = post("/api/orders/1/rate", Map.of(
                "customerId", 4, "restaurantId", 1, "score", 6));
        assertEquals(HttpStatus.BAD_REQUEST, six.getStatusCode(),
                "score 6 must be 400, body=" + six.getBody());
    }

    @Test
    @DisplayName("Cross-city order (customer 4 Istanbul -> restaurant 3 Ankara) -> 400")
    void crossCityOrderRejected() {
        ResponseEntity<String> r = post("/api/orders", Map.of(
                "customerId", 4, "restaurantId", 3,
                "items", List.of(Map.of("itemId", 9, "quantity", 1))));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "cross-city order must be 400, body=" + r.getBody());
    }

    @Test
    @DisplayName("Valid same-city order (customer 4 -> restaurant 1) -> 2xx")
    void sameCityOrderSucceeds() {
        ResponseEntity<String> r = post("/api/orders", Map.of(
                "customerId", 4, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 2))));
        assertTrue(r.getStatusCode().is2xxSuccessful(),
                "valid same-city order must succeed, got " + r.getStatusCode()
                        + " body=" + r.getBody());
    }

    @Test
    @DisplayName("Arrive a SENT order (O11, not PREPARING) -> 409")
    void arriveSentOrder_is409() {
        ResponseEntity<String> r = put("/api/orders/11/arrive", null);
        assertEquals(HttpStatus.CONFLICT, r.getStatusCode(),
                "arriving a SENT order must be 409, body=" + r.getBody());
    }

    @Test
    @DisplayName("Arrive an already-ARRIVED order (O1) -> 409")
    void arriveAlreadyArrivedOrder_is409() {
        ResponseEntity<String> r = put("/api/orders/1/arrive", null);
        assertEquals(HttpStatus.CONFLICT, r.getStatusCode(),
                "arriving an already-ARRIVED order must be 409, body=" + r.getBody());
    }

    @Test
    @DisplayName("Accept an already-ARRIVED order (O1) -> 409")
    void acceptAlreadyArrivedOrder_is409() {
        ResponseEntity<String> r = put("/api/orders/1/accept", null);
        assertEquals(HttpStatus.CONFLICT, r.getStatusCode(),
                "accepting an already-ARRIVED order must be 409, body=" + r.getBody());
    }

    @Test
    @DisplayName("Two concurrent placeOrder calls both succeed with correct independent totals")
    void concurrentPlaceOrderNoCorruption() throws Exception {
        Callable<ResponseEntity<String>> task = () -> post("/api/orders", Map.of(
                "customerId", 4, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 2))));

        ExecutorService ex = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<String>> f1 = ex.submit(task);
            Future<ResponseEntity<String>> f2 = ex.submit(task);
            ResponseEntity<String> r1 = f1.get();
            ResponseEntity<String> r2 = f2.get();

            assertTrue(r1.getStatusCode().is2xxSuccessful(),
                    "concurrent order 1 must succeed: " + r1.getStatusCode()
                            + " " + r1.getBody());
            assertTrue(r2.getStatusCode().is2xxSuccessful(),
                    "concurrent order 2 must succeed: " + r2.getStatusCode()
                            + " " + r2.getBody());
            assertTrue(r1.getBody() != null && r1.getBody().contains("90.0"),
                    "order 1 total must be 90.00 (2 x 45.00), body=" + r1.getBody());
            assertTrue(r2.getBody() != null && r2.getBody().contains("90.0"),
                    "order 2 total must be 90.00 (2 x 45.00), body=" + r2.getBody());
        } finally {
            ex.shutdownNow();
        }
    }

    @Test
    @DisplayName("Sanity: seeded restaurant 1 reachable and is Bosphorus Kebab")
    void seedSanity() {
        ResponseEntity<org.example.model.Restaurant> r =
                rest.getForEntity(url("/api/restaurants/1"), org.example.model.Restaurant.class);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertNotNull(r.getBody());
        assertEquals("Bosphorus Kebab", r.getBody().getName(),
                "seed must be loaded for the rest of the suite to be meaningful");
    }

    @AfterAll
    void resetToSeedWatermarks() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM Rating          WHERE rating_id     > 10");
            st.executeUpdate("DELETE FROM OrderItem       WHERE order_id      > 14");
            st.executeUpdate("DELETE FROM `Order`         WHERE order_id      > 14");
            st.executeUpdate("DELETE FROM MenuItem        WHERE item_id       > 18");
            st.executeUpdate("DELETE FROM MenuCategory    WHERE category_id   > 9");
            st.executeUpdate("DELETE FROM Coupon          WHERE coupon_id     > 5");
            st.executeUpdate("UPDATE Coupon SET is_active = TRUE WHERE coupon_id <= 5");
            st.executeUpdate("DELETE FROM RestaurantKeyword WHERE restaurant_id > 5");
            st.executeUpdate("DELETE FROM Restaurant      WHERE restaurant_id > 5");
            st.executeUpdate("DELETE FROM UserAddress     WHERE user_id       > 8");
            st.executeUpdate("DELETE FROM UserPhone       WHERE user_id       > 8");
            st.executeUpdate("DELETE FROM Users           WHERE user_id       > 8");
        }
    }
}
