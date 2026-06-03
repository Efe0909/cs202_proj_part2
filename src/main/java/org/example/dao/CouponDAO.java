package org.example.dao;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Data-access object for the Coupon table. */
@Repository
public class CouponDAO {

    private final DataSource dataSource;

    public CouponDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Looks up a coupon by its code (case-insensitive). */
    public Optional<org.example.model.Coupon> findByCode(String code) {
        String sql = "SELECT * FROM Coupon WHERE code = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRowToCoupon(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding coupon", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return Optional.empty();
    }

    /** Looks up a coupon by primary key. */
    public Optional<org.example.model.Coupon> findById(int couponId) {
        String sql = "SELECT * FROM Coupon WHERE coupon_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, couponId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRowToCoupon(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding coupon", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return Optional.empty();
    }

    /** Returns all coupons for a restaurant. */
    public List<org.example.model.Coupon> findByRestaurant(int restaurantId) {
        String sql = "SELECT * FROM Coupon WHERE restaurant_id = ?";
        List<org.example.model.Coupon> list = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRowToCoupon(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing coupons", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return list;
    }

    /** Inserts a coupon and returns the generated coupon_id. */
    public int insert(org.example.model.Coupon coupon) {
        String sql = "INSERT INTO Coupon (restaurant_id, code, discount_type, discount_value, valid_from, valid_until, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, coupon.getRestaurantId());
            ps.setString(2, coupon.getCode().trim().toUpperCase());
            ps.setString(3, coupon.getDiscountType());
            ps.setDouble(4, coupon.getDiscountValue());
            ps.setDate(5, Date.valueOf(coupon.getValidFrom()));
            ps.setDate(6, Date.valueOf(coupon.getValidUntil()));
            ps.setBoolean(7, coupon.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting coupon", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return -1;
    }

    /** Atomically claims the coupon; returns false if another thread already claimed it (TOCTOU guard). */
    public boolean tryDeactivate(int couponId) {
        String sql = "UPDATE Coupon SET is_active = FALSE WHERE coupon_id = ? AND is_active = TRUE";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, couponId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error deactivating coupon", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    private org.example.model.Coupon mapRowToCoupon(ResultSet rs) throws SQLException {
        return new org.example.model.Coupon(
                rs.getInt("coupon_id"),
                rs.getInt("restaurant_id"),
                rs.getString("code"),
                rs.getString("discount_type"),
                rs.getDouble("discount_value"),
                rs.getDate("valid_from").toLocalDate(),
                rs.getDate("valid_until").toLocalDate(),
                rs.getBoolean("is_active")
        );
    }
}
