package org.example.dao;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Data-access object for the Order and OrderItem tables. */
@Repository
public class OrderDAO {

    private final DataSource dataSource;

    public OrderDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Inserts an order and returns the generated order_id. */
    public int insert(org.example.model.Order order) {
        String sql = "INSERT INTO `Order` (customer_id, restaurant_id, coupon_id, status, total_price) VALUES (?, ?, ?, ?, ?)";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, order.getCustomerId());
            ps.setInt(2, order.getRestaurantId());
            if (order.getCouponId() != null)
                ps.setInt(3, order.getCouponId());
            else
                ps.setNull(3, Types.INTEGER);
            ps.setString(4, order.getStatus());
            ps.setDouble(5, order.getTotalPrice());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next())
                    return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting order", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return -1;
    }

    /** Inserts a single order line item. */
    public void insertItem(org.example.model.OrderItem item) {
        String sql = "INSERT INTO OrderItem (order_id, item_id, quantity, unit_price) VALUES (?, ?, ?, ?)";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, item.getOrderId());
            ps.setInt(2, item.getItemId());
            ps.setInt(3, item.getQuantity());
            ps.setDouble(4, item.getUnitPrice());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting order item", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Transitions SENT → PREPARING and stamps preparing_at; returns 0 if the order is not in SENT state. */
    public int markPreparing(int orderId) {
        String sql = "UPDATE `Order` SET status='PREPARING', preparing_at=NOW() " +
                "WHERE order_id=? AND status='SENT'";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error marking order preparing", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Transitions PREPARING → ARRIVED and stamps arrived_at; returns 0 if the order is not in PREPARING state. */
    public int markArrived(int orderId) {
        String sql = "UPDATE `Order` SET status='ARRIVED', arrived_at=NOW() " +
                "WHERE order_id=? AND status='PREPARING'";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error marking order arrived", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Looks up an order by primary key. */
    public Optional<org.example.model.Order> findById(int orderId) {
        String sql = "SELECT * FROM `Order` WHERE order_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return Optional.of(mapRowToOrder(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding order", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return Optional.empty();
    }

    /** Returns all orders for a customer, newest first. */
    public List<org.example.model.Order> findByCustomer(int customerId) {
        String sql = "SELECT * FROM `Order` WHERE customer_id = ? ORDER BY created_at DESC";
        return queryOrdersBy(sql, customerId);
    }

    /** Returns orders for a customer filtered by status, newest first. */
    public List<org.example.model.Order> findByCustomerAndStatus(int customerId, String status) {
        String sql = "SELECT * FROM `Order` WHERE customer_id = ? AND status = ? ORDER BY created_at DESC";
        List<org.example.model.Order> list = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setString(2, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(mapRowToOrder(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying orders by status", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return list;
    }

    /** Returns all orders for a restaurant, newest first. */
    public List<org.example.model.Order> findByRestaurant(int restaurantId) {
        String sql = "SELECT * FROM `Order` WHERE restaurant_id = ? ORDER BY created_at DESC";
        return queryOrdersBy(sql, restaurantId);
    }

    /** Returns SENT and PREPARING orders for a restaurant, oldest first (kitchen queue order). */
    public List<org.example.model.Order> findPendingByRestaurant(int restaurantId) {
        String sql = "SELECT * FROM `Order` WHERE restaurant_id = ? AND status IN ('SENT','PREPARING') ORDER BY created_at ASC";
        return queryOrdersBy(sql, restaurantId);
    }

    /** Returns all line items for an order, joined with the item name from MenuItem. */
    public List<org.example.model.OrderItem> findItemsByOrder(int orderId) {
        String sql = "SELECT oi.*, mi.name AS item_name FROM OrderItem oi " +
                "JOIN MenuItem mi ON oi.item_id = mi.item_id " +
                "WHERE oi.order_id = ?";
        List<org.example.model.OrderItem> items = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    org.example.model.OrderItem item = new org.example.model.OrderItem(
                            rs.getInt("order_id"),
                            rs.getInt("item_id"),
                            rs.getInt("quantity"),
                            rs.getDouble("unit_price"));
                    item.setOrderItemId(rs.getInt("order_item_id"));
                    item.setItemName(rs.getString("item_name"));
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching order items", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return items;
    }

    private List<org.example.model.Order> queryOrdersBy(String sql, int param) {
        List<org.example.model.Order> list = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(mapRowToOrder(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying orders", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return list;
    }

    private org.example.model.Order mapRowToOrder(ResultSet rs) throws SQLException {
        org.example.model.Order o = new org.example.model.Order();
        o.setOrderId(rs.getInt("order_id"));
        o.setCustomerId(rs.getInt("customer_id"));
        o.setRestaurantId(rs.getInt("restaurant_id"));
        int couponId = rs.getInt("coupon_id");
        o.setCouponId(rs.wasNull() ? null : couponId);
        o.setStatus(rs.getString("status"));
        o.setTotalPrice(rs.getDouble("total_price"));
        o.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        o.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        Timestamp preparingAt = rs.getTimestamp("preparing_at");
        o.setPreparingAt(preparingAt == null ? null : preparingAt.toLocalDateTime());
        Timestamp arrivedAt = rs.getTimestamp("arrived_at");
        o.setArrivedAt(arrivedAt == null ? null : arrivedAt.toLocalDateTime());
        return o;
    }
}
