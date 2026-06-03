package org.example.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatisticsServiceTest {

    private DataSource dataSource;
    private Connection connection;
    private StatisticsService service;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        service = new StatisticsService(dataSource);
    }

    private void stubSingleValueRow(String sqlNeedle, Object value) throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(contains(sqlNeedle))).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        if (value instanceof Double) {
            when(rs.getDouble(1)).thenReturn((Double) value);
        } else if (value instanceof Integer) {
            when(rs.getDouble(1)).thenReturn(((Integer) value).doubleValue());
        } else {
            when(rs.getString(1)).thenReturn(String.valueOf(value));
        }
    }

    private void stubEmptyResult(String sqlNeedle) throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(contains(sqlNeedle))).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);
    }

    private void stubAllEmpty() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);
    }

    @Test
    void emptyRestaurant_scalarsAreZeroAndNoNpe() throws Exception {
        stubAllEmpty();

        Map<String, Object> summary = assertDoesNotThrow(
                () -> service.getMonthlySummary(5),
                "Empty restaurant must not throw (no NPE on missing rows)");

        assertEquals(0.0, (double) summary.get("totalRevenue"), 0.0001,
                "No accepted orders => totalRevenue 0.0, not null");
        assertEquals(0, (int) summary.get("totalOrders"),
                "No accepted orders => totalOrders 0");
        assertEquals(0.0, (double) summary.get("totalDiscount"), 0.0001,
                "No coupon orders => totalDiscount 0.0");
        assertNotNull(summary.get("itemRevenue"),
                "itemRevenue must be an (empty) map, not null");
        assertTrue(((Map<?, ?>) summary.get("itemRevenue")).isEmpty(),
                "No items => itemRevenue empty");
        assertEquals("N/A", summary.get("mostOrderedItem"));
        assertEquals("N/A", summary.get("topRevenueCategory"));
        assertNull(summary.get("topCustomerByOrders"),
                "Documented: no rows => topCustomerByOrders is null");
        assertNull(summary.get("topCustomerByValue"),
                "Documented: no rows => topCustomerByValue is null");
    }

    @Test
    void totalRevenue_usesPostCouponTotalPrice_acceptedOnly() throws Exception {
        stubAllEmpty();
        stubSingleValueRow("COALESCE(SUM(total_price), 0) FROM `Order`", 563.00);

        Map<String, Object> s = service.getMonthlySummary(1);

        assertEquals(563.00, (double) s.get("totalRevenue"), 0.0001,
                "Revenue must be the stored post-coupon total_price sum");

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce()).prepareStatement(sqlCap.capture());
        boolean hasAcceptedRevenueSql = sqlCap.getAllValues().stream()
                .anyMatch(q -> q.contains("SUM(total_price)")
                        && q.contains("status = 'ARRIVED'"));
        assertTrue(hasAcceptedRevenueSql,
                "Revenue SQL must filter status = 'ARRIVED'");
    }

    @Test
    void totalOrders_sqlFiltersArrivedAndCurdateMonthWindow() throws Exception {
        stubAllEmpty();
        stubSingleValueRow("SELECT COUNT(*) FROM `Order`", 3);

        int orders = (int) service.getMonthlySummary(1).get("totalOrders");
        assertEquals(3, orders, "restaurant 1 has 3 ARRIVED orders");

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce()).prepareStatement(sqlCap.capture());
        String countSql = sqlCap.getAllValues().stream()
                .filter(q -> q.contains("SELECT COUNT(*) FROM `Order`"))
                .findFirst().orElseThrow();
        assertTrue(countSql.contains("status = 'ARRIVED'"),
                "Count must exclude PREPARING/SENT via status='ARRIVED'");
        assertTrue(countSql.contains("CURDATE()"),
                "Count must be bounded by the CURDATE()-based month window");
    }

    @Test
    void totalDiscount_isCouponOnlyAndAcceptedOnly() throws Exception {
        stubAllEmpty();
        stubSingleValueRow("COALESCE(SUM(item_sum - o.total_price), 0)", 32.00);

        double discount = (double) service.getMonthlySummary(1).get("totalDiscount");
        assertEquals(32.00, discount, 0.0001,
                "restaurant 1 coupon discount = 20 (O1) + 12 (O6) = 32.00");

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce()).prepareStatement(sqlCap.capture());
        String discSql = sqlCap.getAllValues().stream()
                .filter(q -> q.contains("SUM(item_sum - o.total_price)"))
                .findFirst().orElseThrow();
        assertTrue(discSql.contains("o.coupon_id IS NOT NULL"),
                "Discount must only sum orders that actually used a coupon");
        assertTrue(discSql.contains("o.status = 'ARRIVED'"),
                "A coupon order whose status is NOT ARRIVED must be excluded");
        assertTrue(discSql.contains("SUM(quantity * unit_price) AS item_sum"),
                "Pre-coupon side must be SUM(quantity*unit_price)");
    }

    @Test
    void itemRevenue_usesQuantityTimesUnitPrice() throws Exception {
        stubAllEmpty();
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(contains("SUM(oi.quantity * oi.unit_price) AS total_revenue")))
                .thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString("name")).thenReturn("Adana Kebab");
        when(rs.getInt("total_qty")).thenReturn(2);
        when(rs.getDouble("total_revenue")).thenReturn(240.00);

        @SuppressWarnings("unchecked")
        Map<String, Object> itemRev =
                (Map<String, Object>) service.getMonthlySummary(1).get("itemRevenue");

        assertTrue(itemRev.containsKey("Adana Kebab"));
        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) itemRev.get("Adana Kebab");
        assertEquals(2, entry.get("qty"));
        assertEquals(240.00, (double) entry.get("revenue"), 0.0001);

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce()).prepareStatement(sqlCap.capture());
        String itemSql = sqlCap.getAllValues().stream()
                .filter(q -> q.contains("AS total_revenue"))
                .findFirst().orElseThrow();
        assertTrue(itemSql.contains("FROM OrderItem oi"),
                "Item revenue must be sourced from OrderItem, not Order.total_price");
        assertTrue(itemSql.contains("o.status = 'ARRIVED'"),
                "Item revenue must only count ARRIVED orders");
    }

    @Test
    void topRevenueCategory_usesQtyTimesUnitPriceAndAcceptedFilter() throws Exception {
        stubAllEmpty();
        stubSingleValueRow("SUM(oi.quantity * oi.unit_price) AS cat_revenue",
                "Main Dishes");

        String cat = (String) service.getMonthlySummary(1).get("topRevenueCategory");
        assertEquals("Main Dishes", cat);

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce()).prepareStatement(sqlCap.capture());
        String catSql = sqlCap.getAllValues().stream()
                .filter(q -> q.contains("AS cat_revenue"))
                .findFirst().orElseThrow();
        assertTrue(catSql.contains("o.status = 'ARRIVED'"));
        assertTrue(catSql.contains("JOIN MenuCategory mc"));
    }

    @Test
    void mostOrderedItem_returnsMappedNameWhenRowPresent() throws Exception {
        stubAllEmpty();
        stubSingleValueRow("ORDER BY total_qty DESC, mi.item_id ASC LIMIT 1",
                "Mercimek Corbasi");

        String item = (String) service.getMonthlySummary(1).get("mostOrderedItem");
        assertEquals("Mercimek Corbasi", item);
        assertNotEquals("N/A", item,
                "When OrderItem rows exist the sentinel must NOT be returned");
    }

    @Test
    void topCustomerByOrders_mappedFromRowAndNotNullWhenPresent() throws Exception {
        stubAllEmpty();
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(contains("AS order_count"))).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getInt("user_id")).thenReturn(4);
        when(rs.getString("full_name")).thenReturn("Ece Sahin");
        when(rs.getInt("order_count")).thenReturn(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> top =
                (Map<String, Object>) service.getMonthlySummary(1).get("topCustomerByOrders");
        assertNotNull(top, "With an accepted order present this must not be null");
        assertEquals(4, top.get("customerId"));
        assertEquals("Ece Sahin", top.get("fullName"));
        assertEquals(1, top.get("orderCount"));
    }
}
