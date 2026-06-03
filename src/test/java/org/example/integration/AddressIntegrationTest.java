package org.example.integration;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = org.example.BackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.config=classpath:logback-it.xml")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("It.6 adversarial: multi-address + selected-address city rule (integration)")
class AddressIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private DataSource dataSource;

    private final ObjectMapper mapper = new ObjectMapper();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

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
            throw new RuntimeException("Cannot parse JSON: " + body, e);
        }
    }

    private boolean rowExists(String sql) {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next();
        } catch (Exception e) {
            throw new RuntimeException("rowExists query failed: " + sql, e);
        }
    }

    private Integer selectedAddressIdFor(int userId) {
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

    private int registerCustomer(String tag) {
        ResponseEntity<String> r = post("/api/auth/register", Map.of(
                "username", "qa_int_" + tag,
                "password", "pw",
                "email", "qa_int_" + tag + "@example.com",
                "fullName", "QA Integration " + tag,
                "role", "CUSTOMER",
                "city", "Istanbul",
                "province", "Kadikoy"));
        assertEquals(HttpStatus.OK, r.getStatusCode(),
                "register must succeed, body=" + r.getBody());
        int id = json(r.getBody()).get("userId").asInt();
        assertTrue(id > 8, "throwaway user_id must be above seed watermark, got " + id);
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

    private void setSelected(int userId, int addressId) {
        ResponseEntity<String> r = put("/api/users/" + userId + "/selected-address",
                Map.of("addressId", addressId));
        assertEquals(HttpStatus.OK, r.getStatusCode(),
                "setSelected must succeed, body=" + r.getBody());
    }

    @Test
    @DisplayName("Req1a: DELETE another user's seeded address -> 4xx, row untouched")
    void req1_deleteOwnershipEnforced_seededVictim() {
        int attacker = registerCustomer("own1a");

        ResponseEntity<String> r = delete("/api/users/addresses/3?userId=" + attacker);
        assertTrue(r.getStatusCode().is4xxClientError(),
                "cross-user delete must be 4xx, got " + r.getStatusCode()
                        + " body=" + r.getBody());

        assertTrue(rowExists("SELECT 1 FROM UserAddress WHERE address_id=3"),
                "victim's address row 3 must survive the unauthorized delete");
    }

    @Test
    @DisplayName("Req1b: DELETE a throwaway victim's address while being a different user -> 4xx")
    void req1_deleteOwnershipEnforced_throwawayVictim() {
        int victim   = registerCustomer("own1b_v");
        int attacker = registerCustomer("own1b_a");

        int victimAddr = addAddress(victim, "Izmir", "Izmir");

        ResponseEntity<String> r = delete("/api/users/addresses/" + victimAddr + "?userId=" + attacker);
        assertTrue(r.getStatusCode().is4xxClientError(),
                "attacker must not be able to delete victim's address, got "
                        + r.getStatusCode() + " body=" + r.getBody());

        assertTrue(rowExists("SELECT 1 FROM UserAddress WHERE address_id=" + victimAddr),
                "victim's address must survive unauthorized delete");
    }

    @Test
    @DisplayName("Req2a: PUT /selected-address pointing at another user's address -> 4xx")
    void req2_setSelectedOwnership_seededVictim() {
        int uid = registerCustomer("sel2a");
        addAddress(uid, "Istanbul", "Istanbul");

        ResponseEntity<String> bad = put("/api/users/" + uid + "/selected-address",
                Map.of("addressId", 3));
        assertTrue(bad.getStatusCode().is4xxClientError(),
                "selecting another user's address must be 4xx, got "
                        + bad.getStatusCode() + " body=" + bad.getBody());

        assertEquals(3, selectedAddressIdFor(5),
                "berk's selection must not have moved after the rejected cross-user select");
    }

    @Test
    @DisplayName("Req2b: attacker cannot claim victim's address as their selection")
    void req2_setSelectedOwnership_throwawayVictim() {
        int victim   = registerCustomer("sel2b_v");
        int attacker = registerCustomer("sel2b_a");

        int victimAddr   = addAddress(victim, "Ankara", "Ankara");
        int attackerAddr = addAddress(attacker, "Istanbul", "Istanbul");

        setSelected(attacker, attackerAddr);

        ResponseEntity<String> bad = put("/api/users/" + attacker + "/selected-address",
                Map.of("addressId", victimAddr));
        assertTrue(bad.getStatusCode().is4xxClientError(),
                "cross-user setSelected must be 4xx, got "
                        + bad.getStatusCode() + " body=" + bad.getBody());

        assertEquals(attackerAddr, selectedAddressIdFor(attacker),
                "attacker's selection must be unchanged after the rejected cross-user select");

        Integer victimSel = selectedAddressIdFor(victim);
        assertEquals(victimAddr, victimSel,
                "victim's selection must be unchanged after the rejected cross-user select");
    }

    @Test
    @DisplayName("Req3a: switch selected city, city rule flips accordingly")
    void req3_cityRuleViaSelectedAddress() {
        int uid = registerCustomer("city3a");
        int istAddr = addAddress(uid, "Istanbul", "Istanbul");
        int ankAddr = addAddress(uid, "Ankara", "Ankara");

        ResponseEntity<String> ok = post("/api/orders", Map.of(
                "customerId", uid, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 1))));
        assertTrue(ok.getStatusCode().is2xxSuccessful(),
                "same-city order must succeed, body=" + ok.getBody());

        setSelected(uid, ankAddr);
        ResponseEntity<String> bad = post("/api/orders", Map.of(
                "customerId", uid, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 1))));
        assertEquals(HttpStatus.BAD_REQUEST, bad.getStatusCode(),
                "cross-city order after switching selected address must be 400, body="
                        + bad.getBody());

        setSelected(uid, istAddr);
        ResponseEntity<String> okAgain = post("/api/orders", Map.of(
                "customerId", uid, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 1))));
        assertTrue(okAgain.getStatusCode().is2xxSuccessful(),
                "switching back must re-enable the order, body=" + okAgain.getBody());
    }

    @Test
    @DisplayName("Req3b: pinar(8) Ankara selected cannot order from restaurant 1 (Istanbul)")
    void req3_seededPinar_ankaraSelected_cannotOrderIstanbul() {
        ResponseEntity<String> r = post("/api/orders", Map.of(
                "customerId", 8, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 1))));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "pinar (Ankara selected) ordering r1 (Istanbul) must be 400, body="
                        + r.getBody());
    }

    @Test
    @DisplayName("Req4: no address -> placeOrder 400 'Select a delivery address'")
    void req4_noSelectedAddress_orderRejected() {
        int uid = registerCustomer("nosel4");

        ResponseEntity<String> r = post("/api/orders", Map.of(
                "customerId", uid, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 1))));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "no selected address must be 400, body=" + r.getBody());
        assertNotNull(r.getBody());
        String bodyLower = r.getBody().toLowerCase();
        assertTrue(bodyLower.contains("select") && bodyLower.contains("address"),
                "response must instruct user to select a delivery address, got: "
                        + r.getBody());
    }

    @Test
    @DisplayName("Req4b: add then delete all addresses -> order rejected again")
    void req4_addThenDeleteAllAddresses_orderRejectedAgain() {
        int uid = registerCustomer("nosel4b");
        int addrId = addAddress(uid, "Istanbul", "Istanbul");

        ResponseEntity<String> ok = post("/api/orders", Map.of(
                "customerId", uid, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 1))));
        assertTrue(ok.getStatusCode().is2xxSuccessful(),
                "order with selected address must succeed, body=" + ok.getBody());

        ResponseEntity<String> del = delete("/api/users/addresses/" + addrId + "?userId=" + uid);
        assertEquals(HttpStatus.OK, del.getStatusCode(),
                "delete own address must 200, body=" + del.getBody());

        assertNull(selectedAddressIdFor(uid),
                "selected_address_id must be NULL after deleting the only address");

        ResponseEntity<String> bad = post("/api/orders", Map.of(
                "customerId", uid, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 1))));
        assertEquals(HttpStatus.BAD_REQUEST, bad.getStatusCode(),
                "no selected address (after delete) must be 400, body=" + bad.getBody());
    }

    @Test
    @DisplayName("Req5a: 3 addresses listed; delete middle; others survive")
    void req5_multipleAddresses_listAndDelete() {
        int uid = registerCustomer("1n5a");

        int a1 = addAddress(uid, "Istanbul", "Istanbul");
        int a2 = addAddress(uid, "Ankara",   "Ankara");
        int a3 = addAddress(uid, "Izmir",    "Izmir");

        ResponseEntity<String> listResp = get("/api/users/" + uid + "/addresses");
        assertEquals(HttpStatus.OK, listResp.getStatusCode(),
                "list must succeed, body=" + listResp.getBody());
        JsonNode arr = json(listResp.getBody());
        assertTrue(arr.isArray(), "response must be an array");
        assertEquals(3, arr.size(),
                "must have exactly 3 addresses, got: " + listResp.getBody());

        List<Integer> ids = new ArrayList<>();
        for (JsonNode node : arr) ids.add(node.get("addressId").asInt());
        assertTrue(ids.contains(a1), "a1 must appear in list");
        assertTrue(ids.contains(a2), "a2 must appear in list");
        assertTrue(ids.contains(a3), "a3 must appear in list");

        ResponseEntity<String> del = delete("/api/users/addresses/" + a2 + "?userId=" + uid);
        assertEquals(HttpStatus.OK, del.getStatusCode(),
                "delete own address must 200, body=" + del.getBody());

        assertFalse(rowExists("SELECT 1 FROM UserAddress WHERE address_id=" + a2),
                "deleted a2 must not exist");
        assertTrue(rowExists("SELECT 1 FROM UserAddress WHERE address_id=" + a1),
                "a1 must survive the deletion of a2");
        assertTrue(rowExists("SELECT 1 FROM UserAddress WHERE address_id=" + a3),
                "a3 must survive the deletion of a2");

        ResponseEntity<String> listAfter = get("/api/users/" + uid + "/addresses");
        assertEquals(2, json(listAfter.getBody()).size(),
                "list after delete of a2 must have 2 items, body=" + listAfter.getBody());
    }

    @Test
    @DisplayName("Req5b: seeded ece(4) has 2 addresses; exactly one selected")
    void req5_seededEceHasTwoAddresses_integrity() {
        ResponseEntity<String> r = get("/api/users/4/addresses");
        assertEquals(HttpStatus.OK, r.getStatusCode(), "body=" + r.getBody());
        JsonNode arr = json(r.getBody());
        assertTrue(arr.isArray() && arr.size() >= 2,
                "ece must have at least 2 seeded addresses, got: " + r.getBody());

        int selectedCount = 0;
        for (JsonNode a : arr) {
            assertFalse(a.get("city").asText().isBlank(),
                    "city must not be blank for address " + a.get("addressId").asInt());
            if (a.get("selected").asBoolean()) selectedCount++;
        }
        assertEquals(1, selectedCount,
                "exactly one address must be flagged as selected, body=" + r.getBody());
    }

    @Test
    @DisplayName("Req6a: first address for new user is auto-selected")
    void req6_autoSelectOnFirstAddress() {
        int uid = registerCustomer("auto6a");

        assertEquals(null, selectedAddressIdFor(uid),
                "fresh user must have no selected address");

        ResponseEntity<String> r = post("/api/users/" + uid + "/addresses",
                Map.of("city", "Bursa", "province", "Bursa"));
        assertEquals(HttpStatus.OK, r.getStatusCode(), "addAddress must succeed, body=" + r.getBody());
        JsonNode created = json(r.getBody());
        int newAddrId = created.get("addressId").asInt();

        assertTrue(created.has("selected"),
                "response must include 'selected' flag, body=" + r.getBody());
        assertTrue(created.get("selected").asBoolean(),
                "first address must be returned as selected, body=" + r.getBody());

        assertEquals(newAddrId, selectedAddressIdFor(uid),
                "selected_address_id in Users must point to the new address");
    }

    @Test
    @DisplayName("Req6b: second address is NOT auto-selected when one already exists")
    void req6_secondAddressNotAutoSelected() {
        int uid = registerCustomer("auto6b");
        int first = addAddress(uid, "Istanbul", "Istanbul");

        assertEquals(first, selectedAddressIdFor(uid),
                "first address must be auto-selected");

        ResponseEntity<String> r = post("/api/users/" + uid + "/addresses",
                Map.of("city", "Ankara", "province", "Ankara"));
        assertEquals(HttpStatus.OK, r.getStatusCode(), "addAddress must succeed, body=" + r.getBody());
        JsonNode created = json(r.getBody());

        assertFalse(created.get("selected").asBoolean(),
                "second address must NOT steal the selection, body=" + r.getBody());

        assertEquals(first, selectedAddressIdFor(uid),
                "selection must remain on the first address after adding a second");
    }

    @Test
    @DisplayName("Req7a: deleting selected address repoints to remaining")
    void req7_deleteSelectedAddress_repointsToRemaining() {
        int uid = registerCustomer("repoint7a");
        int a1 = addAddress(uid, "Istanbul", "Istanbul");
        int a2 = addAddress(uid, "Ankara",   "Ankara");

        setSelected(uid, a1);
        assertEquals(a1, selectedAddressIdFor(uid), "a1 must be selected before delete");

        ResponseEntity<String> del = delete("/api/users/addresses/" + a1 + "?userId=" + uid);
        assertEquals(HttpStatus.OK, del.getStatusCode(),
                "deleting selected address must not 500, body=" + del.getBody());

        assertFalse(rowExists("SELECT 1 FROM UserAddress WHERE address_id=" + a1),
                "deleted address must be gone from UserAddress");

        Integer sel = selectedAddressIdFor(uid);
        if (sel != null) {
            assertTrue(rowExists("SELECT 1 FROM UserAddress WHERE address_id=" + sel),
                    "selected_address_id must reference a live row, not a dangling FK");
            assertEquals(a2, sel,
                    "selection must have been repointed to the remaining address a2");
        }
    }

    @Test
    @DisplayName("Req7b: deleting the sole selected address -> selected_address_id becomes NULL")
    void req7_deleteSoleSelectedAddress_becomesNull() {
        int uid = registerCustomer("repoint7b");
        int only = addAddress(uid, "Izmir", "Izmir");

        assertEquals(only, selectedAddressIdFor(uid),
                "sole address must be auto-selected");

        ResponseEntity<String> del = delete("/api/users/addresses/" + only + "?userId=" + uid);
        assertEquals(HttpStatus.OK, del.getStatusCode(),
                "deleting sole selected address must not 500, body=" + del.getBody());

        assertFalse(rowExists("SELECT 1 FROM UserAddress WHERE address_id=" + only),
                "deleted sole address must not exist in UserAddress");

        assertNull(selectedAddressIdFor(uid),
                "selected_address_id must be NULL when the only address is deleted");

        JsonNode arr = json(get("/api/users/" + uid + "/addresses").getBody());
        assertTrue(arr.isArray() && arr.size() == 0,
                "address list must be empty after deleting the sole address");
    }

    @Test
    @DisplayName("Req7c: deleting a non-selected address does not move the selection")
    void req7_deleteNonSelected_selectionUnchanged() {
        int uid = registerCustomer("repoint7c");
        int a1 = addAddress(uid, "Istanbul", "Istanbul");
        int a2 = addAddress(uid, "Ankara",   "Ankara");

        setSelected(uid, a1);
        assertEquals(a1, selectedAddressIdFor(uid), "a1 must be selected");

        ResponseEntity<String> del = delete("/api/users/addresses/" + a2 + "?userId=" + uid);
        assertEquals(HttpStatus.OK, del.getStatusCode(),
                "delete non-selected address must 200, body=" + del.getBody());

        assertFalse(rowExists("SELECT 1 FROM UserAddress WHERE address_id=" + a2),
                "a2 must be gone");
        assertEquals(a1, selectedAddressIdFor(uid),
                "selection must NOT have moved when a non-selected address was deleted");
    }

    @Test
    @DisplayName("Req8a: seeded ece(4) Istanbul selected can order from r1 (Istanbul)")
    void req8_regression_seededEceCanOrderIstanbul() {
        ResponseEntity<String> r = post("/api/orders", Map.of(
                "customerId", 4, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 1))));
        assertTrue(r.getStatusCode().is2xxSuccessful(),
                "seeded ece (Istanbul selected) must be able to order r1 (Istanbul), body="
                        + r.getBody());
    }

    @Test
    @DisplayName("Req8b: seeded pinar(8) Ankara selected cannot order from r1 (Istanbul)")
    void req8_regression_seededPinarCannotOrderIstanbul() {
        ResponseEntity<String> r = post("/api/orders", Map.of(
                "customerId", 8, "restaurantId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 1))));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode(),
                "pinar (Ankara selected) cannot order r1 (Istanbul), body=" + r.getBody());
    }

    @Test
    @DisplayName("Req8c: GET /api/restaurants/1 returns Bosphorus Kebab (seed intact)")
    void req8_regression_seedDataIntact() {
        ResponseEntity<String> r = get("/api/restaurants/1");
        assertEquals(HttpStatus.OK, r.getStatusCode(), "body=" + r.getBody());
        assertNotNull(r.getBody());
        assertTrue(r.getBody().contains("Bosphorus Kebab"),
                "seed restaurant 1 must be Bosphorus Kebab, body=" + r.getBody());
    }

    @Test
    @DisplayName("Req8d: ece(4) addr 1 Istanbul is selected, addr 2 Ankara is not")
    void req8_regression_seededEceSelectedFlagCorrect() {
        ResponseEntity<String> r = get("/api/users/4/addresses");
        assertEquals(HttpStatus.OK, r.getStatusCode(), "body=" + r.getBody());
        JsonNode arr = json(r.getBody());

        JsonNode addr1 = null, addr2 = null;
        for (JsonNode a : arr) {
            int id = a.get("addressId").asInt();
            if (id == 1) addr1 = a;
            if (id == 2) addr2 = a;
        }
        assertNotNull(addr1, "seeded addr 1 must appear in ece's list, body=" + r.getBody());
        assertNotNull(addr2, "seeded addr 2 must appear in ece's list, body=" + r.getBody());
        assertTrue(addr1.get("selected").asBoolean(),
                "addr 1 (Istanbul) must be flagged selected for ece");
        assertFalse(addr2.get("selected").asBoolean(),
                "addr 2 (Ankara) must NOT be flagged selected for ece");
    }

    @AfterAll
    void restoreToWatermarks() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate(
                    "UPDATE Users SET selected_address_id = NULL WHERE user_id > 8");
            st.executeUpdate("DELETE FROM Rating       WHERE rating_id  > 10");
            st.executeUpdate("DELETE FROM OrderItem    WHERE order_id   > 12");
            st.executeUpdate("DELETE FROM `Order`      WHERE order_id   > 12");
            st.executeUpdate("DELETE FROM UserAddress  WHERE user_id    > 8");
            st.executeUpdate("DELETE FROM Users        WHERE user_id    > 8");
        }
    }
}
