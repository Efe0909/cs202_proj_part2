package org.example.service;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Computes the current-calendar-month sales report for a restaurant; all metrics filter to ARRIVED orders only. */
@Service
public class StatisticsService {

    private final DataSource dataSource;

    public StatisticsService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static String monthWindow(String alias) {
        String a = alias.isEmpty() ? "" : alias + ".";
        return " AND YEAR(" + a + "created_at) = YEAR(CURDATE())" +
               " AND MONTH(" + a + "created_at) = MONTH(CURDATE()) ";
    }

    /** Returns the full monthly statistics map for a restaurant. */
    public Map<String, Object> getMonthlySummary(int restaurantId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRevenue",        getTotalRevenue(restaurantId));
        summary.put("totalOrders",         getTotalOrders(restaurantId));
        summary.put("itemRevenue",         getItemRevenue(restaurantId));
        summary.put("topCustomerByOrders", getTopCustomerByOrders(restaurantId));
        summary.put("topCustomerByValue",  getTopCustomerByValue(restaurantId));
        summary.put("mostOrderedItem",     getMostOrderedItem(restaurantId));
        summary.put("topRevenueCategory",  getTopRevenueCategory(restaurantId));
        summary.put("totalDiscount",       getTotalDiscount(restaurantId));
        return summary;
    }

    private double getTotalRevenue(int restaurantId) {
        String sql = "SELECT COALESCE(SUM(total_price), 0) FROM `Order` " +
                "WHERE restaurant_id = ? AND status = 'ARRIVED'" +
                monthWindow("");
        return queryDouble(sql, restaurantId);
    }

    private int getTotalOrders(int restaurantId) {
        String sql = "SELECT COUNT(*) FROM `Order` " +
                "WHERE restaurant_id = ? AND status = 'ARRIVED'" +
                monthWindow("");
        return (int) queryDouble(sql, restaurantId);
    }

    private Map<String, Object> getItemRevenue(int restaurantId) {
        String sql = "SELECT mi.name, SUM(oi.quantity) AS total_qty, " +
                "SUM(oi.quantity * oi.unit_price) AS total_revenue " +
                "FROM OrderItem oi " +
                "JOIN MenuItem mi ON oi.item_id = mi.item_id " +
                "JOIN `Order` o ON oi.order_id = o.order_id " +
                "WHERE o.restaurant_id = ? AND o.status = 'ARRIVED'" +
                monthWindow("o") +
                "GROUP BY mi.item_id, mi.name ORDER BY total_revenue DESC";
        Map<String, Object> result = new LinkedHashMap<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindRestaurant(ps, restaurantId, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("qty", rs.getInt("total_qty"));
                    entry.put("revenue", rs.getDouble("total_revenue"));
                    result.put(rs.getString("name"), entry);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching item revenue", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return result;
    }

    private Map<String, Object> getTopCustomerByOrders(int restaurantId) {
        String sql = "SELECT u.user_id, u.full_name, COUNT(*) AS order_count " +
                "FROM `Order` o JOIN Users u ON o.customer_id = u.user_id " +
                "WHERE o.restaurant_id = ? AND o.status = 'ARRIVED'" +
                monthWindow("o") +
                "GROUP BY o.customer_id, u.user_id, u.full_name " +
                "ORDER BY order_count DESC, u.user_id ASC LIMIT 1";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindRestaurant(ps, restaurantId, 1);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("customerId", rs.getInt("user_id"));
                    m.put("fullName", rs.getString("full_name"));
                    m.put("orderCount", rs.getInt("order_count"));
                    return m;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching top customer by orders", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return null;
    }

    private Map<String, Object> getTopCustomerByValue(int restaurantId) {
        String sql = "SELECT u.user_id, u.full_name, o.order_id, o.total_price, " +
                "o.created_at FROM `Order` o JOIN Users u ON o.customer_id = u.user_id " +
                "WHERE o.restaurant_id = ? AND o.status = 'ARRIVED'" +
                monthWindow("o") +
                "ORDER BY o.total_price DESC, o.order_id ASC LIMIT 1";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindRestaurant(ps, restaurantId, 1);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("customerId", rs.getInt("user_id"));
                    m.put("fullName", rs.getString("full_name"));
                    m.put("orderId", rs.getInt("order_id"));
                    m.put("orderTotal", rs.getDouble("total_price"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    m.put("orderDate", ts == null ? null : ts.toString());
                    m.put("items", getOrderItems(rs.getInt("order_id")));
                    return m;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching top customer by value", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return null;
    }

    /** Line items of one order (for the highest-value order's details). */
    private List<Map<String, Object>> getOrderItems(int orderId) {
        String sql = "SELECT mi.name, oi.quantity, oi.unit_price " +
                "FROM OrderItem oi JOIN MenuItem mi ON oi.item_id = mi.item_id " +
                "WHERE oi.order_id = ? ORDER BY oi.order_item_id ASC";
        List<Map<String, Object>> items = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> it = new LinkedHashMap<>();
                    it.put("name", rs.getString("name"));
                    it.put("quantity", rs.getInt("quantity"));
                    it.put("unitPrice", rs.getDouble("unit_price"));
                    items.add(it);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching order items", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return items;
    }

    private String getMostOrderedItem(int restaurantId) {
        String sql = "SELECT mi.name, SUM(oi.quantity) AS total_qty " +
                "FROM OrderItem oi " +
                "JOIN MenuItem mi ON oi.item_id = mi.item_id " +
                "JOIN `Order` o ON oi.order_id = o.order_id " +
                "WHERE o.restaurant_id = ? AND o.status = 'ARRIVED'" +
                monthWindow("o") +
                "GROUP BY mi.item_id, mi.name ORDER BY total_qty DESC, mi.item_id ASC LIMIT 1";
        return queryString(sql, restaurantId, 1);
    }

    private String getTopRevenueCategory(int restaurantId) {
        String sql = "SELECT mc.name, SUM(oi.quantity * oi.unit_price) AS cat_revenue " +
                "FROM OrderItem oi " +
                "JOIN MenuItem mi ON oi.item_id = mi.item_id " +
                "JOIN MenuCategory mc ON mi.category_id = mc.category_id " +
                "JOIN `Order` o ON oi.order_id = o.order_id " +
                "WHERE o.restaurant_id = ? AND o.status = 'ARRIVED'" +
                monthWindow("o") +
                "GROUP BY mc.category_id, mc.name ORDER BY cat_revenue DESC, mc.category_id ASC LIMIT 1";
        return queryString(sql, restaurantId, 1);
    }

    /** Sums (pre-coupon item total − stored total_price) over ARRIVED coupon orders. */
    private double getTotalDiscount(int restaurantId) {
        String sql = "SELECT COALESCE(SUM(item_sum - o.total_price), 0) " +
                "FROM `Order` o " +
                "JOIN (SELECT order_id, SUM(quantity * unit_price) AS item_sum " +
                "      FROM OrderItem GROUP BY order_id) s ON s.order_id = o.order_id " +
                "WHERE o.restaurant_id = ? AND o.status = 'ARRIVED' " +
                "AND o.coupon_id IS NOT NULL" +
                monthWindow("o");
        return queryDouble(sql, restaurantId);
    }

    private void bindRestaurant(PreparedStatement ps, int restaurantId, int totalBinds)
            throws SQLException {
        for (int i = 1; i <= totalBinds; i++) ps.setInt(i, restaurantId);
    }

    private double queryDouble(String sql, int restaurantId) {
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindRestaurant(ps, restaurantId, 1);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Statistics query error", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return 0;
    }

    private String queryString(String sql, int restaurantId, int totalBinds) {
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindRestaurant(ps, restaurantId, totalBinds);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Statistics query error", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return "N/A";
    }
}
