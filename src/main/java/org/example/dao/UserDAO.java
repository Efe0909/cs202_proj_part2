package org.example.dao;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Data-access object for Users and their associated addresses and phones. */
@Repository
public class UserDAO {

    private final DataSource dataSource;

    public UserDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Looks up a user by username. */
    public Optional<org.example.model.User> findByUsername(String username) {
        String sql = "SELECT * FROM Users WHERE username = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return Optional.of(mapRowToUser(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding user", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return Optional.empty();
    }

    /** Returns the hex-encoded salt for a username, or empty if user not found. */
    public Optional<String> findSaltByUsername(String username) {
        String sql = "SELECT salt FROM Users WHERE username = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return Optional.of(rs.getString("salt"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching salt", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return Optional.empty();
    }

    /** Looks up a user by primary key. */
    public Optional<org.example.model.User> findById(int userId) {
        String sql = "SELECT * FROM Users WHERE user_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return Optional.of(mapRowToUser(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding user by id", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return Optional.empty();
    }

    /** Inserts a user whose password field already contains the PBKDF2 hash. */
    public int insert(org.example.model.User user, String saltHex) {
        String sql = "INSERT INTO Users (username, password, salt, email, full_name, role) VALUES (?, ?, ?, ?, ?, ?)";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, saltHex);
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getFullName());
            ps.setString(6, user.getRole());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next())
                    return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting user", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return -1;
    }

    /** Returns true if the user exists and has the MANAGER role. */
    public boolean isManager(int userId) {
        String sql = "SELECT COUNT(*) FROM Users WHERE user_id = ? AND role = 'MANAGER'";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking manager role", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return false;
    }

    /** Returns true if a user with this username already exists. */
    public boolean usernameExists(String username) {
        String sql = "SELECT COUNT(*) FROM Users WHERE username = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking username", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return false;
    }

    /** Returns true if a user with this email already exists. */
    public boolean emailExists(String email) {
        String sql = "SELECT COUNT(*) FROM Users WHERE email = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking email", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return false;
    }

    /** Returns all addresses for a user, flagging which one is currently selected. */
    public List<org.example.model.UserAddress> findAddressesByUserId(int userId) {
        String sql = "SELECT a.address_id, a.user_id, a.city, a.province, " +
                "(u.selected_address_id = a.address_id) AS is_selected " +
                "FROM UserAddress a " +
                "JOIN Users u ON u.user_id = a.user_id " +
                "WHERE a.user_id = ? ORDER BY a.address_id";
        List<org.example.model.UserAddress> addresses = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    org.example.model.UserAddress a = new org.example.model.UserAddress(
                            rs.getInt("address_id"),
                            rs.getInt("user_id"),
                            rs.getString("city"),
                            rs.getString("province"));
                    a.setSelected(rs.getBoolean("is_selected"));
                    addresses.add(a);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching addresses", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return addresses;
    }

    /** Inserts an address for a user and returns the generated address_id. */
    public int addAddress(int userId, String city, String province) {
        String sql = "INSERT INTO UserAddress (user_id, city, province) VALUES (?, ?, ?)";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, toTitleCase(city));
            ps.setString(3, toTitleCase(province));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next())
                    return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error adding address", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return -1;
    }

    /** Returns true if the address belongs to the given user. */
    public boolean addressBelongsToUser(int addressId, int userId) {
        String sql = "SELECT 1 FROM UserAddress WHERE address_id = ? AND user_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, addressId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking address ownership", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Deletes an address; the FK ON DELETE SET NULL automatically clears selected_address_id if it was selected. */
    public int deleteAddress(int addressId) {
        String sql = "DELETE FROM UserAddress WHERE address_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, addressId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting address", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Returns the user's selected delivery address ID, or empty if none is set. */
    public Optional<Integer> findSelectedAddressId(int userId) {
        String sql = "SELECT selected_address_id FROM Users WHERE user_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("selected_address_id");
                    if (id != 0)
                        return Optional.of(id);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching selected address id", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return Optional.empty();
    }

    /** Returns the city of the user's selected delivery address, used for the order city-restriction check. */
    public Optional<String> findSelectedAddressCity(int userId) {
        String sql = "SELECT a.city FROM Users u "
                + "JOIN UserAddress a ON a.address_id = u.selected_address_id "
                + "WHERE u.user_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return Optional.ofNullable(rs.getString("city"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching selected address city", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return Optional.empty();
    }

    /** Sets the user's selected delivery address. */
    public int setSelectedAddress(int userId, int addressId) {
        String sql = "UPDATE Users SET selected_address_id = ? WHERE user_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, addressId);
            ps.setInt(2, userId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error setting selected address", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Returns all phone numbers for a user. */
    public List<org.example.model.UserPhone> findPhonesByUserId(int userId) {
        String sql = "SELECT phone_id, user_id, phone FROM UserPhone "
                + "WHERE user_id = ? ORDER BY phone_id";
        List<org.example.model.UserPhone> phones = new ArrayList<>();
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    phones.add(new org.example.model.UserPhone(
                            rs.getInt("phone_id"),
                            rs.getInt("user_id"),
                            rs.getString("phone")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching phones", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return phones;
    }

    /** Inserts a phone number for a user and returns the generated phone_id. */
    public int addPhone(int userId, String phone) {
        String sql = "INSERT INTO UserPhone (user_id, phone) VALUES (?, ?)";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, phone);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next())
                    return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error adding phone", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
        return -1;
    }

    /** Returns true if the phone number belongs to the given user. */
    public boolean phoneBelongsToUser(int phoneId, int userId) {
        String sql = "SELECT 1 FROM UserPhone WHERE phone_id = ? AND user_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, phoneId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking phone ownership", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /** Deletes a phone number by primary key. */
    public int deletePhone(int phoneId) {
        String sql = "DELETE FROM UserPhone WHERE phone_id = ?";
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, phoneId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting phone", e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    private org.example.model.User mapRowToUser(ResultSet rs) throws SQLException {
        org.example.model.User u = new org.example.model.User(
                rs.getInt("user_id"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("email"),
                rs.getString("full_name"),
                rs.getString("role"));
        int selId = rs.getInt("selected_address_id");
        if (!rs.wasNull()) {
            u.setSelectedAddressId(selId);
            String city = findSelectedAddressCity(u.getUserId()).orElse(null);
            if (city != null) {
                u.setSelectedCity(city);
            }
        }
        return u;
    }

    private static String toTitleCase(String s) {
        if (s == null || s.isBlank())
            return s;
        String t = s.trim();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1).toLowerCase();
    }
}
