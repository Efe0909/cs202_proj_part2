package org.example.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = org.example.BackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.config=classpath:logback-it.xml")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("It.6 multi-address + selected-address city rule over HTTP against seeded MySQL")
class AddressApiSeedTest {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate rest;
    @Autowired private DataSource dataSource;

    private final ObjectMapper mapper = new ObjectMapper();

    private String url(String path) { return "http://localhost:" + port + path; }

    private ResponseEntity<String> post(String path, Object body) {
        return rest.exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(body), String.class);
    }

    private ResponseEntity<String> put(String path, Object body) {
        return rest.exchange(url(path), HttpMethod.PUT,
                new HttpEntity<>(body), String.class);
    }

    private ResponseEntity<String> get(String path) {
        return rest.exchange(url(path), HttpMethod.GET,
                HttpEntity.EMPTY, String.class);
    }

    private ResponseEntity<String> delete(String path) {
        return rest.exchange(url(path), HttpMethod.DELETE,
                HttpEntity.EMPTY, String.class);
    }

    private JsonNode json(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("bad json: " + body, e);
        }
    }

    private int registerThrowaway(String tag, String city) {
        ResponseEntity<String> r = post("/api/auth/register", Map.of(
                "username", "qa_addr_" + tag,
                "password", "pw",
                "email", "qa_addr_" + tag + "@example.com",
                "fullName", "QA Addr " + tag,
                "role", "CUSTOMER",
                "city", city,
                "province", "Kadikoy"));
        assertEquals(HttpStatus.OK, r.getStatusCode(),
                "register throwaway must succeed, body=" + r.getBody());
        int id = json(r.getBody()).get("userId").asInt();
        assertTrue(id > 8, "throwaway user_id must be above the seed watermark, got " + id);
        try (java.sql.Connection c = dataSource.getConnection();
             java.sql.PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM UserAddress WHERE user_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
        return id;
    }

    private int addAddress(int userId, String city, String province) {
        ResponseEntity<String> r = post("/api/users/" + userId + "/addresses",
                Map.of("city", city, "province", province));
        assertEquals(HttpStatus.OK, r.getStatusCode(),
                "addAddress must succeed, body=" + r.getBody());
        return json(r.getBody()).get("addressId").asInt();
    }

    private void select(int userId, int addressId) {
        ResponseEntity<String> r = put("/api/users/" + userId + "/selected-address",
                Map.of("addressId", addressId));
        assertEquals(HttpStatus.OK, r.getStatusCode(),
                "setSelected must succeed, body=" + r.getBody());
    }

    @Test
    @DisplayName("GET addresses for seeded ece(4) -> two addresses, exactly one selected")
    void listSeededCustomer_hasAddressesWithSelectedFlag() {
        ResponseEntity<String> r = get("/api/users/4/addresses");
        assertEquals(HttpStatus.OK, r.getStatusCode(), "body=" + r.getBody());
        JsonNode arr = json(r.getBody());
        assertTrue(arr.isArray() && arr.size() >= 1, "ece must have >=1 address");
        int selectedCount = 0;
        for (JsonNode a : arr) {
            assertFalse(a.get("city").asText().isBlank(), "city present");
            assertFalse(a.get("province").asText().isBlank(), "province present");
            assertTrue(a.has("selected"), "selected flag present");
            if (a.get("selected").asBoolean()) selectedCount++;
        }
        assertEquals(1, selectedCount,
                "exactly one address must be flagged selected, body=" + r.getBody());
        JsonNode sel = null;
        for (JsonNode a : arr) if (a.get("selected").asBoolean()) sel = a;
        assertNotNull(sel);
        assertEquals("Istanbul", sel.get("city").asText(),
                "ece's seeded selected city is Istanbul");
    }

    @Test
    @DisplayName("add {city,province} appears in list; blank inputs rejected")
    void addAddress_thenListed_blankInputsRejected() {
        int uid = registerThrowaway("add", "Istanbul");

        int aId = addAddress(uid, "Ankara", "Ankara");
        ResponseEntity<String> list = get("/api/users/" + uid + "/addresses");
        boolean found = false;
        for (JsonNode a : json(list.getBody())) {
            if (a.get("addressId").asInt() == aId) {
                found = true;
                assertEquals("Ankara", a.get("city").asText());
            }
        }
        assertTrue(found, "added address must appear in list, body=" + list.getBody());

        assertEquals(HttpStatus.BAD_REQUEST,
                post("/api/users/" + uid + "/addresses",
                        Map.of("city", "  ", "province", "Ankara")).getStatusCode(),
                "blank city must be 400");
        assertEquals(HttpStatus.BAD_REQUEST,
                post("/api/users/" + uid + "/addresses",
                        Map.of("city", "Ankara", "province", "")).getStatusCode(),
                "blank province must be 400");
        assertEquals(HttpStatus.BAD_REQUEST,
                post("/api/users/" + uid + "/addresses",
                        Map.of("city", "Ankara")).getStatusCode(),
                "missing province key must be 400");
    }

    @Test
    @DisplayName("set selected to owned address works; set to another user's address is 4xx")
    void setSelected_ownership() {
        int uid = registerThrowaway("sel", "Istanbul");
        int aIst = addAddress(uid, "Istanbul", "Istanbul");
        int aAnk = addAddress(uid, "Ankara", "Ankara");

        select(uid, aAnk);
        JsonNode arr = json(get("/api/users/" + uid + "/addresses").getBody());
        for (JsonNode a : arr) {
            boolean sel = a.get("selected").asBoolean();
            if (a.get("addressId").asInt() == aAnk) assertTrue(sel, "Ankara now selected");
            if (a.get("addressId").asInt() == aIst) assertFalse(sel, "Istanbul deselected");
        }

        ResponseEntity<String> bad = put("/api/users/" + uid + "/selected-address",
                Map.of("addressId", 1));
        assertTrue(bad.getStatusCode().is4xxClientError(),
                "selecting another user's address must be 4xx, got "
                        + bad.getStatusCode() + " body=" + bad.getBody());

        JsonNode after = json(get("/api/users/" + uid + "/addresses").getBody());
        for (JsonNode a : after) {
            if (a.get("addressId").asInt() == aAnk)
                assertTrue(a.get("selected").asBoolean(),
                        "selection must be unchanged after rejected cross-user select");
        }
        assertEquals(1, seededSelectedAddressId(4),
                "cross-user select must not mutate the victim's selection");
    }

    @Test
    @DisplayName("delete an address you don't own -> 4xx; own delete succeeds; selected repoints")
    void deleteAddress_ownership_and_selectedRepoint() throws Exception {
        int uid = registerThrowaway("del", "Istanbul");
        int a1 = addAddress(uid, "Istanbul", "Istanbul");
        int a2 = addAddress(uid, "Ankara", "Ankara");

        ResponseEntity<String> notOwned =
                delete("/api/users/addresses/1?userId=" + uid);
        assertTrue(notOwned.getStatusCode().is4xxClientError(),
                "deleting another user's address must be 4xx, got "
                        + notOwned.getStatusCode());
        assertTrue(rowExists("SELECT 1 FROM UserAddress WHERE address_id=1"),
                "victim's seeded address row must survive a cross-user delete");

        ResponseEntity<String> ok = delete("/api/users/addresses/" + a2 + "?userId=" + uid);
        assertEquals(HttpStatus.OK, ok.getStatusCode(), "own delete must 200");
        assertFalse(rowExists("SELECT 1 FROM UserAddress WHERE address_id=" + a2),
                "deleted own address must be gone");

        ResponseEntity<String> okSel = delete("/api/users/addresses/" + a1 + "?userId=" + uid);
        assertEquals(HttpStatus.OK, okSel.getStatusCode(),
                "deleting the selected address must not 500, body=" + okSel.getBody());
        assertFalse(rowExists("SELECT 1 FROM UserAddress WHERE address_id=" + a1),
                "deleted selected address must be gone");
        Integer sel = seededSelectedAddressIdNullable(uid);
        if (sel != null) {
            assertTrue(rowExists("SELECT 1 FROM UserAddress WHERE address_id=" + sel),
                    "selected_address_id must reference a live row (FK integrity)");
        }
    }

    @Test
    @DisplayName("city rule end-to-end via selected address")
    void cityRule_endToEnd_throughSelectedAddress() {
        int noSel = registerThrowaway("nosel", "Istanbul");
        ResponseEntity<String> r0 = post("/api/orders", Map.of(
                "customerId", noSel, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 1))));
        assertEquals(HttpStatus.BAD_REQUEST, r0.getStatusCode(),
                "no selected address must be 400, body=" + r0.getBody());
        assertNotNull(r0.getBody());
        assertTrue(r0.getBody().toLowerCase().contains("select")
                        && r0.getBody().toLowerCase().contains("address"),
                "message must instruct to select a delivery address, got: " + r0.getBody());

        int uid = registerThrowaway("city", "Istanbul");
        int istAddr = addAddress(uid, "Istanbul", "Istanbul");
        int ankAddr = addAddress(uid, "Ankara", "Ankara");
        select(uid, istAddr);

        ResponseEntity<String> okOrder = post("/api/orders", Map.of(
                "customerId", uid, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 1))));
        assertTrue(okOrder.getStatusCode().is2xxSuccessful(),
                "same-city order must succeed, body=" + okOrder.getBody());

        select(uid, ankAddr);
        ResponseEntity<String> badOrder = post("/api/orders", Map.of(
                "customerId", uid, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 1))));
        assertEquals(HttpStatus.BAD_REQUEST, badOrder.getStatusCode(),
                "after switching selected city the order must 400, body=" + badOrder.getBody());
        assertTrue(badOrder.getBody() != null
                        && badOrder.getBody().toLowerCase().contains("own city"),
                "must be the own-city rejection, got: " + badOrder.getBody());

        select(uid, istAddr);
        ResponseEntity<String> okAgain = post("/api/orders", Map.of(
                "customerId", uid, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 1))));
        assertTrue(okAgain.getStatusCode().is2xxSuccessful(),
                "switching the selected address back must re-enable ordering, body="
                        + okAgain.getBody());
    }

    @Test
    @DisplayName("deleting selected address never 500s and leaves no dangling FK")
    void circularFk_deleteSelected_setsNullNotDangling() throws Exception {
        int uid = registerThrowaway("fk", "Ankara");
        int only = addAddress(uid, "Ankara", "Ankara");
        assertEquals(only, seededSelectedAddressId(uid),
                "sole address must be the selection");

        ResponseEntity<String> r = delete("/api/users/addresses/" + only + "?userId=" + uid);
        assertEquals(HttpStatus.OK, r.getStatusCode(),
                "deleting the sole (selected) address must not 500, body=" + r.getBody());
        assertNull(seededSelectedAddressIdNullable(uid),
                "with no addresses left, selected_address_id must be NULL");
        assertFalse(rowExists("SELECT 1 FROM UserAddress WHERE address_id=" + only),
                "the address row must be gone");
    }

    private boolean rowExists(String sql) throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next();
        }
    }

    private int seededSelectedAddressId(int userId) {
        Integer v = seededSelectedAddressIdNullable(userId);
        assertNotNull(v, "expected a non-null selected_address_id for user " + userId);
        return v;
    }

    private Integer seededSelectedAddressIdNullable(int userId) {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT selected_address_id FROM Users WHERE user_id=" + userId)) {
            if (rs.next()) {
                int v = rs.getInt(1);
                return rs.wasNull() ? null : v;
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterAll
    void restoreToWatermarks() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate(
                    "UPDATE Users SET selected_address_id=NULL WHERE user_id > 8");
            st.executeUpdate(
                    "DELETE oi FROM OrderItem oi "
                  + "JOIN `Order` o ON o.order_id = oi.order_id "
                  + "WHERE o.customer_id > 8");
            st.executeUpdate("DELETE FROM `Order` WHERE customer_id > 8");
            st.executeUpdate("DELETE FROM UserAddress WHERE user_id > 8");
            st.executeUpdate("DELETE FROM Users WHERE user_id > 8");
        }
    }
}
