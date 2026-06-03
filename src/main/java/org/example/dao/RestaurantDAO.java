package org.example.dao;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Data-access object for the Restaurant table, including keyword and rating aggregation. */
@Repository
public class RestaurantDAO {

    private final DataSource dataSource;

    public RestaurantDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Returns all restaurants in a city, ranked by average rating (minimum 10 ratings to rank). */
    public List<org.example.model.Restaurant> findByCity(String city) {
        String sql = "SELECT r.*, " +
                "CASE WHEN COUNT(rt.rating_id) >= 10 THEN AVG(rt.score) ELSE 0 END AS avg_rating, " +
                "COUNT(rt.rating_id) AS rating_count " +
                "FROM Restaurant r " +
                "LEFT JOIN Rating rt ON r.restaurant_id = rt.restaurant_id " +
                "WHERE r.city = ? " +
                "GROUP BY r.restaurant_id " +
                "ORDER BY CASE WHEN COUNT(rt.rating_id) >= 10 THEN AVG(rt.score) ELSE 0 END DESC, r.name ASC";
        return queryRestaurantsBy(sql, city);
    }

    /** Searches restaurants by keyword (partial match) within a city, ranked by keyword hit count then rating. */
    public List<org.example.model.Restaurant> searchByKeywordAndCity(String keyword, String city) {
        String sql = "SELECT r.*, " +
                "CASE WHEN COUNT(DISTINCT rt.rating_id) >= 10 THEN AVG(rt.score) ELSE 0 END AS avg_rating, " +
                "COUNT(DISTINCT rt.rating_id) AS rating_count " +
                "FROM Restaurant r " +
                "JOIN RestaurantKeyword rk ON r.restaurant_id = rk.restaurant_id " +
                "LEFT JOIN Rating rt ON r.restaurant_id = rt.restaurant_id " +
                "WHERE rk.keyword LIKE ? AND r.city = ? " +
                "GROUP BY r.restaurant_id " +
                "ORDER BY COUNT(DISTINCT rk.keyword) DESC, CASE WHEN COUNT(DISTINCT rt.rating_id) >= 10 THEN AVG(rt.score) ELSE 0 END DESC, r.name ASC";
        List<org.example.model.Restaurant> list = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + keyword + "%");
            ps.setString(2, city);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(mapRowToRestaurant(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error searching restaurants", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return list;
    }

    /** Looks up a restaurant by primary key, including rating aggregate. */
    public Optional<org.example.model.Restaurant> findById(int restaurantId) {
        String sql = "SELECT r.*, " +
                "CASE WHEN COUNT(rt.rating_id) >= 10 THEN AVG(rt.score) ELSE 0 END AS avg_rating, " +
                "COUNT(rt.rating_id) AS rating_count " +
                "FROM Restaurant r " +
                "LEFT JOIN Rating rt ON r.restaurant_id = rt.restaurant_id " +
                "WHERE r.restaurant_id = ? " +
                "GROUP BY r.restaurant_id";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return Optional.of(mapRowToRestaurant(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding restaurant", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return Optional.empty();
    }

    /** Returns all restaurants managed by the given manager. */
    public List<org.example.model.Restaurant> findByManagerId(int managerId) {
        String sql = "SELECT r.*, " +
                "CASE WHEN COUNT(rt.rating_id) >= 10 THEN AVG(rt.score) ELSE 0 END AS avg_rating, " +
                "COUNT(rt.rating_id) AS rating_count " +
                "FROM Restaurant r " +
                "LEFT JOIN Rating rt ON r.restaurant_id = rt.restaurant_id " +
                "WHERE r.manager_id = ? " +
                "GROUP BY r.restaurant_id";
        return queryRestaurantsBy(sql, managerId);
    }

    /** Inserts a restaurant and returns the generated restaurant_id. */
    public int insert(org.example.model.Restaurant r) {
        String sql = "INSERT INTO Restaurant (manager_id, name, cuisine_type, address, city) VALUES (?, ?, ?, ?, ?)";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getManagerId());
            ps.setString(2, r.getName());
            ps.setString(3, r.getCuisineType());
            ps.setString(4, r.getAddress());
            ps.setString(5, r.getCity());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next())
                    return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting restaurant", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return -1;
    }

    /** Updates a restaurant only if the caller is the owning manager; returns rows affected. */
    public int update(org.example.model.Restaurant r, int managerId) {
        String sql = "UPDATE Restaurant SET name=?, cuisine_type=?, address=?, city=? WHERE restaurant_id=? AND manager_id=?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.getName());
            ps.setString(2, r.getCuisineType());
            ps.setString(3, r.getAddress());
            ps.setString(4, r.getCity());
            ps.setInt(5, r.getRestaurantId());
            ps.setInt(6, managerId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating restaurant", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Returns true if the restaurant exists and is owned by the given manager. */
    public boolean existsOwnedBy(int restaurantId, int managerId) {
        String sql = "SELECT COUNT(*) FROM Restaurant WHERE restaurant_id=? AND manager_id=?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, restaurantId);
            ps.setInt(2, managerId);
            try (ResultSet rs = ps.executeQuery()) {
                return (rs.next() && rs.getInt(1) > 0);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking restaurant ownership", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Adds a search keyword to a restaurant. */
    public void addKeyword(int restaurantId, String keyword) {
        String sql = "INSERT INTO RestaurantKeyword (restaurant_id, keyword) VALUES (?, ?)";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, restaurantId);
            ps.setString(2, keyword);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error adding keyword", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Removes all keywords for a restaurant (used before re-inserting an updated set). */
    public void deleteKeywords(int restaurantId) {
        String sql = "DELETE FROM RestaurantKeyword WHERE restaurant_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, restaurantId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting keywords", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Returns all search keywords for a restaurant. */
    public List<String> findKeywords(int restaurantId) {
        String sql = "SELECT keyword FROM RestaurantKeyword WHERE restaurant_id = ?";
        List<String> keywords = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    keywords.add(rs.getString("keyword"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching keywords", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return keywords;
    }

    private List<org.example.model.Restaurant> queryRestaurantsBy(String sql, Object param) {
        List<org.example.model.Restaurant> list = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param instanceof String)
                ps.setString(1, (String) param);
            else if (param instanceof Integer)
                ps.setInt(1, (Integer) param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(mapRowToRestaurant(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying restaurants", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return list;
    }

    private org.example.model.Restaurant mapRowToRestaurant(ResultSet rs) throws SQLException {
        org.example.model.Restaurant r = new org.example.model.Restaurant(
                rs.getInt("restaurant_id"),
                rs.getInt("manager_id"),
                rs.getString("name"),
                rs.getString("cuisine_type"),
                rs.getString("address"),
                rs.getString("city"));
        r.setAverageRating(rs.getDouble("avg_rating"));
        r.setRatingCount(rs.getInt("rating_count"));
        return r;
    }
}
