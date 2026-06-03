package org.example.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = org.example.BackendApplication.class,
        properties = "logging.config=classpath:logback-it.xml")
@DisplayName("StatisticsService against the It.0 seeded MySQL")
class StatisticsServiceSeedIntegrationTest {

    @Autowired private StatisticsService statisticsService;
    @Autowired private DataSource dataSource;

    private double d(Object o) { return ((Number) o).doubleValue(); }
    private int    i(Object o) { return ((Number) o).intValue(); }

    private void assertRestaurant(int id, double revenue, int orders, double discount) {
        Map<String, Object> s = statisticsService.getMonthlySummary(id);
        assertEquals(revenue, d(s.get("totalRevenue")), 0.001,
                "restaurant " + id + " totalRevenue");
        assertEquals(orders, i(s.get("totalOrders")),
                "restaurant " + id + " totalOrders");
        assertEquals(discount, d(s.get("totalDiscount")), 0.001,
                "restaurant " + id + " totalDiscount");
    }

    @Test
    @DisplayName("r1: 563.00 / 3 / 32.00 (O12 PREPARING excluded)")
    void restaurant1_matchesSeedGroundTruth() {
        assertRestaurant(1, 563.00, 3, 32.00);
    }

    @Test
    @DisplayName("r2: 495.00 / 3 / 20.00")
    void restaurant2_matchesSeedGroundTruth() {
        assertRestaurant(2, 495.00, 3, 20.00);
    }

    @Test
    @DisplayName("r3: 348.00 / 2 / 42.00 (O11 SENT excluded from all metrics)")
    void restaurant3_matchesSeedGroundTruth() {
        assertRestaurant(3, 348.00, 2, 42.00);
    }

    @Test
    @DisplayName("r4: 385.00 / 2 / 5.00")
    void restaurant4_matchesSeedGroundTruth() {
        assertRestaurant(4, 385.00, 2, 5.00);
    }

    @Test
    @DisplayName("non-ACCEPTED seed orders exist but are excluded from revenue")
    void nonAcceptedOrdersExistInSeedButAreExcluded() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM `Order` WHERE status <> 'ARRIVED'")) {
                rs.next();
                assertEquals(2, rs.getInt(1),
                        "Seed must contain exactly the O11 SENT + O12 PREPARING orders");
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COALESCE(SUM(total_price),0) FROM `Order` "
                            + "WHERE restaurant_id = 1 AND status = 'ARRIVED'")) {
                rs.next();
                assertEquals(563.00, rs.getDouble(1), 0.001);
            }
        }
        assertEquals(563.00,
                d(statisticsService.getMonthlySummary(1).get("totalRevenue")),
                0.001, "PREPARING order must not inflate revenue");
    }

    @Test
    @DisplayName("r1 collection metrics non-null and internally consistent")
    void restaurant1_collectionMetricsInternallyConsistent() {
        Map<String, Object> s = statisticsService.getMonthlySummary(1);

        assertEquals("Mercimek Corbasi", s.get("mostOrderedItem"));
        assertEquals("Main Dishes", s.get("topRevenueCategory"));

        @SuppressWarnings("unchecked")
        Map<String, Object> topByOrders =
                (Map<String, Object>) s.get("topCustomerByOrders");
        assertNotNull(topByOrders);
        assertEquals(4, i(topByOrders.get("customerId")));
        assertEquals("Ece Sahin", topByOrders.get("fullName"));
        assertEquals(1, i(topByOrders.get("orderCount")));

        @SuppressWarnings("unchecked")
        Map<String, Object> topByValue =
                (Map<String, Object>) s.get("topCustomerByValue");
        assertNotNull(topByValue);
        assertEquals(275.00, d(topByValue.get("orderTotal")), 0.001);
        assertEquals("Berk Ozturk", topByValue.get("fullName"));
        assertNotNull(topByValue.get("items"));
        assertFalse(((java.util.List<?>) topByValue.get("items")).isEmpty());

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> itemRevenue =
                (Map<String, Map<String, Object>>) s.get("itemRevenue");
        assertFalse(itemRevenue.isEmpty());
        double itemRevSum = itemRevenue.values().stream()
                .mapToDouble(e -> ((Number) e.get("revenue")).doubleValue())
                .sum();
        assertEquals(595.00, itemRevSum, 0.001,
                "Per-item revenue is pre-coupon qty*unit_price summed (200+275+120=595)");
        assertEquals(d(s.get("totalRevenue")),
                itemRevSum - d(s.get("totalDiscount")), 0.001,
                "post-coupon revenue == pre-coupon item sum - coupon discount");
    }
}
