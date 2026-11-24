package com.deskweave.dao;

import com.deskweave.exception.DataAccessException;
import com.deskweave.model.User;
import com.deskweave.model.UserRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC implementation of {@link UserDao} for PostgreSQL and H2-PostgreSQL mode.
 */
public class PostgresUserDao implements UserDao {

    private static final String SELECT_COLS =
            "SELECT user_id, full_name, email, password_hash, role, department, created_at FROM users";

    private final DatabaseManager db;

    public PostgresUserDao(DatabaseManager db) {
        this.db = Objects.requireNonNull(db, "db must not be null");
    }

    @Override
    public Optional<User> findById(Integer id) {
        Objects.requireNonNull(id, "id must not be null");
        String sql = SELECT_COLS + " WHERE user_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to find user by id=" + id, ex);
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String sql = SELECT_COLS + " WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to find user by email=" + email, ex);
        }
    }

    @Override
    public List<User> findAll() {
        String sql = SELECT_COLS + " ORDER BY user_id ASC";
        List<User> users = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
            return users;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to list users", ex);
        }
    }

    @Override
    public User save(User user) {
        Objects.requireNonNull(user, "user must not be null");
        String sql = "INSERT INTO users (full_name, email, password_hash, role, department, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        LocalDateTime createdAt = user.getCreatedAt() != null ? user.getCreatedAt() : LocalDateTime.now();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole().name());
            ps.setString(5, user.getDepartment());
            ps.setTimestamp(6, Timestamp.valueOf(createdAt));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("No generated user_id returned on insert");
                }
                int id = keys.getInt(1);
                return new User(id, user.getFullName(), user.getEmail(), user.getPasswordHash(),
                        user.getRole(), user.getDepartment(), createdAt);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to save user: " + user.getEmail(), ex);
        }
    }

    @Override
    public boolean update(User user) {
        Objects.requireNonNull(user, "user must not be null");
        String sql = "UPDATE users SET full_name = ?, email = ?, password_hash = ?, role = ?, department = ? "
                + "WHERE user_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole().name());
            ps.setString(5, user.getDepartment());
            ps.setInt(6, user.getUserId());
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to update user id=" + user.getUserId(), ex);
        }
    }

    @Override
    public boolean deleteById(Integer id) {
        Objects.requireNonNull(id, "id must not be null");
        String sql = "DELETE FROM users WHERE user_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to delete user id=" + id, ex);
        }
    }

    private static User mapRow(ResultSet rs) throws SQLException {
        Timestamp createdTs = rs.getTimestamp("created_at");
        return new User(
                rs.getInt("user_id"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("password_hash"),
                UserRole.valueOf(rs.getString("role")),
                rs.getString("department"),
                createdTs != null ? createdTs.toLocalDateTime() : LocalDateTime.now()
        );
    }
}
