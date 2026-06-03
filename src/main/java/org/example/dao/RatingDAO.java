package org.example.dao;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Data-access object for the Rating table. */
@Repository
public class RatingDAO {

    private final DataSource dataSource;

    public RatingDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Inserts a new rating. */
    public void insert(org.example.model.Rating rating) {
        String sql = "INSERT INTO Rating (customer_id, restaurant_id, order_id, score, comment) VALUES (?, ?, ?, ?, ?)";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rating.getCustomerId());
            ps.setInt(2, rating.getRestaurantId());
            ps.setInt(3, rating.getOrderId());
            ps.setInt(4, rating.getScore());
            ps.setString(5, rating.getComment());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting rating", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Returns true if a rating already exists for the given order (one rating per order). */
    public boolean existsForOrder(int orderId) {
        String sql = "SELECT COUNT(*) FROM Rating WHERE order_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking rating existence", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return false;
    }

    /** Returns ratings for a restaurant, newest first; rating_id is the tiebreaker for identical timestamps. */
    public List<org.example.model.Rating> findByRestaurant(int restaurantId) {
        String sql = "SELECT * FROM Rating WHERE restaurant_id = ? ORDER BY created_at DESC, rating_id DESC";
        List<org.example.model.Rating> list = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRowToRating(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching ratings", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return list;
    }

    private org.example.model.Rating mapRowToRating(ResultSet rs) throws SQLException {
        org.example.model.Rating r = new org.example.model.Rating(
                rs.getInt("customer_id"),
                rs.getInt("restaurant_id"),
                rs.getInt("order_id"),
                rs.getInt("score"),
                rs.getString("comment")
        );
        r.setRatingId(rs.getInt("rating_id"));
        r.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return r;
    }
}
