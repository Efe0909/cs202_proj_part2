package org.example.dao;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Data-access object for the MenuItem table. */
@Repository
public class MenuItemDAO {

    private final DataSource dataSource;

    public MenuItemDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Returns all menu items for a restaurant. */
    public List<org.example.model.MenuItem> findByRestaurant(int restaurantId) {
        String sql = "SELECT * FROM MenuItem WHERE restaurant_id = ?";
        return queryItemsBy(sql, restaurantId);
    }

    /** Returns all menu items in a category. */
    public List<org.example.model.MenuItem> findByCategory(int categoryId) {
        String sql = "SELECT * FROM MenuItem WHERE category_id = ?";
        return queryItemsBy(sql, categoryId);
    }

    /** Looks up a menu item by primary key. */
    public Optional<org.example.model.MenuItem> findById(int itemId) {
        String sql = "SELECT * FROM MenuItem WHERE item_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToMenuItem(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding menu item", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return Optional.empty();
    }

    /** Inserts a menu item and returns the generated item_id. */
    public int insert(org.example.model.MenuItem item) {
        String sql = "INSERT INTO MenuItem (restaurant_id, category_id, name, description, price, image_path) VALUES (?, ?, ?, ?, ?, ?)";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, item.getRestaurantId());
            ps.setInt(2, item.getCategoryId());
            ps.setString(3, item.getName());
            ps.setString(4, item.getDescription());
            ps.setDouble(5, item.getPrice());
            ps.setString(6, item.getImagePath());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next())
                    return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting menu item", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return -1;
    }

    /** Updates all mutable fields of a menu item. */
    public void update(org.example.model.MenuItem item) {
        String sql = "UPDATE MenuItem SET category_id=?, name=?, description=?, price=?, image_path=? WHERE item_id=?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, item.getCategoryId());
            ps.setString(2, item.getName());
            ps.setString(3, item.getDescription());
            ps.setDouble(4, item.getPrice());
            ps.setString(5, item.getImagePath());
            ps.setInt(6, item.getItemId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating menu item", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Updates a menu item only if the manager owns the restaurant; returns rows affected. */
    public int updateIfOwner(org.example.model.MenuItem item, int managerId) {
        String sql = "UPDATE MenuItem SET category_id=?, name=?, description=?, price=?, image_path=? " +
                "WHERE item_id=? " +
                "AND restaurant_id IN (SELECT restaurant_id FROM Restaurant WHERE manager_id = ?)";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, item.getCategoryId());
            ps.setString(2, item.getName());
            ps.setString(3, item.getDescription());
            ps.setDouble(4, item.getPrice());
            ps.setString(5, item.getImagePath());
            ps.setInt(6, item.getItemId());
            ps.setInt(7, managerId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating menu item", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Deletes a menu item by primary key. */
    public void delete(int itemId) {
        String sql = "DELETE FROM MenuItem WHERE item_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting menu item", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Deletes a menu item only if the manager owns the restaurant; returns rows affected. */
    public int deleteIfOwner(int itemId, int managerId) {
        String sql = "DELETE FROM MenuItem WHERE item_id = ? " +
                "AND restaurant_id IN (SELECT restaurant_id FROM Restaurant WHERE manager_id = ?)";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.setInt(2, managerId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting menu item", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    private List<org.example.model.MenuItem> queryItemsBy(String sql, int param) {
        List<org.example.model.MenuItem> list = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    list.add(mapRowToMenuItem(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying menu items", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return list;
    }

    private org.example.model.MenuItem mapRowToMenuItem(ResultSet rs) throws SQLException {
        return new org.example.model.MenuItem(
                rs.getInt("item_id"),
                rs.getInt("restaurant_id"),
                rs.getInt("category_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getDouble("price"),
                rs.getString("image_path"));
    }
}
