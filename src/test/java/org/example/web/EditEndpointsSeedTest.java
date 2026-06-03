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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = org.example.BackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.config=classpath:logback-it.xml")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Edit endpoints (PUT restaurants/{id}, PUT restaurants/menu/{itemId}) over HTTP")
class EditEndpointsSeedTest {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate rest;
    @Autowired private DataSource dataSource;

    private String url(String path) { return "http://localhost:" + port + path; }

    private ResponseEntity<String> put(String path, Object body) {
        return rest.exchange(url(path), HttpMethod.PUT,
                new HttpEntity<>(body), String.class);
    }

    @Test
    @DisplayName("PUT /api/restaurants/1 by owner persists every field including keywords")
    void updateRestaurant_owner_persistsAllFields() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("managerId",   1);
        payload.put("name",        "Bosphorus Kebab Edited");
        payload.put("cuisineType", "Turkish-Fusion");
        payload.put("address",     "New Addr 123");
        payload.put("city",        "Istanbul");
        payload.put("keywords",    List.of("kebab", "edited", "newkw"));

        ResponseEntity<String> r = put("/api/restaurants/1", payload);
        assertEquals(HttpStatus.OK, r.getStatusCode(),
                "owner update must be 200, body=" + r.getBody());

        ResponseEntity<org.example.model.Restaurant> after =
                rest.getForEntity(url("/api/restaurants/1"), org.example.model.Restaurant.class);
        assertEquals(HttpStatus.OK, after.getStatusCode());
        org.example.model.Restaurant body = after.getBody();
        assertNotNull(body);
        assertEquals("Bosphorus Kebab Edited", body.getName());
        assertEquals("Turkish-Fusion",         body.getCuisineType());
        assertEquals("New Addr 123",           body.getAddress());
        assertEquals("Istanbul",               body.getCity());
        assertNotNull(body.getKeywords(),
                "findById must populate keywords for the edit form to pre-fill");
        assertTrue(body.getKeywords().contains("edited"),
                "keywords must be replaced with the new set, got " + body.getKeywords());
        assertEquals(3, body.getKeywords().size(),
                "keyword count must match the submitted list, got " + body.getKeywords());
    }

    @Test
    @DisplayName("PUT /api/restaurants/1 by non-owner manager (2) is 4xx and does not mutate")
    void updateRestaurant_nonOwner_rejected() throws Exception {
        String beforeName = readRestaurantName(1);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("managerId",   2);
        payload.put("name",        "Hijacked!");
        payload.put("cuisineType", "X");
        payload.put("address",     "X");
        payload.put("city",        "Istanbul");
        payload.put("keywords",    List.of("h"));

        ResponseEntity<String> r = put("/api/restaurants/1", payload);
        assertTrue(r.getStatusCode().is4xxClientError(),
                "non-owner update must be 4xx, got " + r.getStatusCode()
                        + " body=" + r.getBody());

        assertEquals(beforeName, readRestaurantName(1),
                "non-owner must not have mutated restaurant 1's name");
    }

    @Test
    @DisplayName("PUT /api/restaurants/menu/1 by owner persists name+desc+price+image+category")
    void updateMenuItem_owner_persistsAllFields() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("managerId",   1);
        payload.put("categoryId",  1);
        payload.put("name",        "Mercimek Edit");
        payload.put("description", "Edited desc");
        payload.put("price",       55.00);
        payload.put("imagePath",   "img/lentil-edit.jpg");

        ResponseEntity<String> r = put("/api/restaurants/menu/1", payload);
        assertEquals(HttpStatus.OK, r.getStatusCode(),
                "owner menu-item update must be 200, body=" + r.getBody());

        ResponseEntity<org.example.model.MenuItem[]> menu = rest.getForEntity(
                url("/api/restaurants/1/menu"), org.example.model.MenuItem[].class);
        org.example.model.MenuItem updated = null;
        for (org.example.model.MenuItem m : menu.getBody()) {
            if (m.getItemId() == 1) { updated = m; break; }
        }
        assertNotNull(updated, "item id 1 must still exist after update");
        assertEquals("Mercimek Edit",        updated.getName());
        assertEquals("Edited desc",          updated.getDescription());
        assertEquals(55.00, updated.getPrice(), 0.001);
        assertEquals("img/lentil-edit.jpg",  updated.getImagePath());
        assertEquals(1, updated.getCategoryId());
    }

    @Test
    @DisplayName("PUT /api/restaurants/menu/1 by non-owner (manager 2) is 4xx and does not mutate")
    void updateMenuItem_nonOwner_rejected() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("managerId",   2);
        payload.put("categoryId",  1);
        payload.put("name",        "Hijacked Item");
        payload.put("description", "x");
        payload.put("price",       1.0);
        payload.put("imagePath",   "x");

        ResponseEntity<String> r = put("/api/restaurants/menu/1", payload);
        assertTrue(r.getStatusCode().is4xxClientError(),
                "non-owner item update must be 4xx, got " + r.getStatusCode()
                        + " body=" + r.getBody());
    }

    @Test
    @DisplayName("PUT /api/restaurants/menu/1 with price <= 0 is 400")
    void updateMenuItem_nonPositivePrice_is400() {
        Map<String, Object> zero = new LinkedHashMap<>();
        zero.put("managerId", 1);
        zero.put("categoryId", 1);
        zero.put("name", "Mercimek");
        zero.put("price", 0);
        ResponseEntity<String> r = put("/api/restaurants/menu/1", zero);
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "price 0 must be 400, body=" + r.getBody());

        Map<String, Object> neg = new LinkedHashMap<>(zero);
        neg.put("price", -5.0);
        ResponseEntity<String> r2 = put("/api/restaurants/menu/1", neg);
        assertEquals(HttpStatus.BAD_REQUEST, r2.getStatusCode(),
                "negative price must be 400, body=" + r2.getBody());
    }

    @Test
    @DisplayName("PUT /api/restaurants/menu/9999 (unknown item) is 4xx")
    void updateMenuItem_unknownItem_is4xx() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("managerId", 1);
        payload.put("categoryId", 1);
        payload.put("name", "Ghost");
        payload.put("price", 10.0);
        ResponseEntity<String> r = put("/api/restaurants/menu/9999", payload);
        assertTrue(r.getStatusCode().is4xxClientError(),
                "unknown itemId must be 4xx, got " + r.getStatusCode()
                        + " body=" + r.getBody());
    }

    private String readRestaurantName(int id) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name FROM Restaurant WHERE restaurant_id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "restaurant " + id + " must exist");
                return rs.getString(1);
            }
        }
    }

    @AfterAll
    void restoreSeedRows() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE Restaurant SET "
                    + "name='Bosphorus Kebab', "
                    + "cuisine_type='Turkish', "
                    + "address='Taksim Meydani No:5, Beyoglu', "
                    + "city='Istanbul' "
                    + "WHERE restaurant_id=1");
            st.executeUpdate("DELETE FROM RestaurantKeyword WHERE restaurant_id=1");
            st.executeUpdate("INSERT INTO RestaurantKeyword (restaurant_id, keyword) VALUES "
                    + "(1,'kebab'),(1,'turkish'),(1,'grilled'),(1,'meat')");

            st.executeUpdate("UPDATE MenuItem SET "
                    + "name='Mercimek Corbasi', "
                    + "description='Classic red lentil soup with lemon', "
                    + "price=45.00, "
                    + "image_path='img/lentil.jpg', "
                    + "category_id=1 "
                    + "WHERE item_id=1");
        }
    }
}
