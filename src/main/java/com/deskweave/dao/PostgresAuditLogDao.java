package com.deskweave.dao;

import com.deskweave.exception.DataAccessException;
import com.deskweave.model.AuditLogEntry;

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

/**
 * JDBC implementation of {@link AuditLogDao} supporting standalone and transactional inserts.
 */
public class PostgresAuditLogDao implements AuditLogDao {

    private static final String SELECT_COLS =
            "SELECT audit_id, booking_id, actor_user_id, action, details, created_at FROM booking_audit_log";

    private final DatabaseManager db;

    public PostgresAuditLogDao(DatabaseManager db) {
        this.db = Objects.requireNonNull(db, "db must not be null");
    }

    @Override
    public AuditLogEntry save(AuditLogEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        try (Connection conn = db.getConnection()) {
            return insert(conn, entry);
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to save audit log entry", ex);
        }
    }

    @Override
    public AuditLogEntry insert(Connection conn, AuditLogEntry entry) throws SQLException {
        Objects.requireNonNull(conn, "conn must not be null");
        Objects.requireNonNull(entry, "entry must not be null");
        String sql = "INSERT INTO booking_audit_log (booking_id, actor_user_id, action, details, created_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        LocalDateTime createdAt = entry.getCreatedAt() != null ? entry.getCreatedAt() : LocalDateTime.now();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, entry.getBookingId());
            ps.setInt(2, entry.getActorUserId());
            ps.setString(3, entry.getAction());
            ps.setString(4, entry.getDetails());
            ps.setTimestamp(5, Timestamp.valueOf(createdAt));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated audit_id returned on insert");
                }
                return new AuditLogEntry(keys.getInt(1), entry.getBookingId(), entry.getActorUserId(),
                        entry.getAction(), entry.getDetails(), createdAt);
            }
        }
    }

    @Override
    public List<AuditLogEntry> findByBookingId(int bookingId) {
        String sql = SELECT_COLS + " WHERE booking_id = ? ORDER BY created_at ASC, audit_id ASC";
        List<AuditLogEntry> entries = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapRow(rs));
                }
            }
            return entries;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to load audit log for bookingId=" + bookingId, ex);
        }
    }

    @Override
    public List<AuditLogEntry> findRecent(int limit) {
        int safeLimit = Math.max(1, limit);
        String sql = SELECT_COLS + " ORDER BY created_at DESC, audit_id DESC LIMIT ?";
        List<AuditLogEntry> entries = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapRow(rs));
                }
            }
            return entries;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to fetch recent audit logs", ex);
        }
    }

    private static AuditLogEntry mapRow(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        return new AuditLogEntry(
                rs.getInt("audit_id"),
                rs.getInt("booking_id"),
                rs.getInt("actor_user_id"),
                rs.getString("action"),
                rs.getString("details"),
                ts != null ? ts.toLocalDateTime() : LocalDateTime.now()
        );
    }
}
