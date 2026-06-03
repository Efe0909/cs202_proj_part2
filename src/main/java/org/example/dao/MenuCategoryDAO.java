package org.example.dao;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Data-access object for the MenuCategory table. */
@Repository
public class MenuCategoryDAO {

    private final DataSource dataSource;

    public MenuCategoryDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Returns all categories for a restaurant. */
    public List<org.example.model.MenuCategory> findByRestaurant(int restaurantId) {
        String sql = "SELECT * FROM MenuCategory WHERE restaurant_id = ?";
        List<org.example.model.MenuCategory> list = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRowToMenuCategory(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching menu categories", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return list;
    }

    /** Looks up a category by primary key. */
    public Optional<org.example.model.MenuCategory> findById(int categoryId) {
        String sql = "SELECT * FROM MenuCategory WHERE category_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRowToMenuCategory(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding category", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return Optional.empty();
    }

    /** Inserts a category and returns the generated category_id. */
    public int insert(org.example.model.MenuCategory cat) {
        String sql = "INSERT INTO MenuCategory (restaurant_id, name) VALUES (?, ?)";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, cat.getRestaurantId());
            ps.setString(2, cat.getName());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting category", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return -1;
    }

    /** Inserts a category only if the manager owns the restaurant; returns -1 if ownership check fails. */
    public int insertIfOwner(int restaurantId, String name, int managerId) {
        String sql = "INSERT INTO MenuCategory (restaurant_id, name) " +
                     "SELECT ?, ? FROM Restaurant WHERE restaurant_id = ? AND manager_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, restaurantId);
            ps.setString(2, name);
            ps.setInt(3, restaurantId);
            ps.setInt(4, managerId);
            int rows = ps.executeUpdate();
            if (rows == 0) return -1;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting category", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return -1;
    }

    /** Updates a category's name. */
    public void update(org.example.model.MenuCategory cat) {
        String sql = "UPDATE MenuCategory SET name=? WHERE category_id=?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cat.getName());
            ps.setInt(2, cat.getCategoryId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating category", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Updates a category only if the manager owns the restaurant; returns rows affected. */
    public int updateIfOwner(org.example.model.MenuCategory cat, int managerId) {
        String sql = "UPDATE MenuCategory SET name=? WHERE category_id=? " +
                     "AND restaurant_id IN (SELECT restaurant_id FROM Restaurant WHERE manager_id = ?)";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cat.getName());
            ps.setInt(2, cat.getCategoryId());
            ps.setInt(3, managerId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating category", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Deletes a category by primary key. */
    public void delete(int categoryId) {
        String sql = "DELETE FROM MenuCategory WHERE category_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting category", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Deletes a category only if the manager owns the restaurant; returns rows affected. */
    public int deleteIfOwner(int categoryId, int managerId) {
        String sql = "DELETE FROM MenuCategory WHERE category_id = ? " +
                     "AND restaurant_id IN (SELECT restaurant_id FROM Restaurant WHERE manager_id = ?)";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            ps.setInt(2, managerId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting category", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    private org.example.model.MenuCategory mapRowToMenuCategory(ResultSet rs) throws SQLException {
        return new org.example.model.MenuCategory(
                rs.getInt("category_id"),
                rs.getInt("restaurant_id"),
                rs.getString("name")
        );
    }
}
