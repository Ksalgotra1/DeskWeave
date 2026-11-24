package com.deskweave.dao;

import com.deskweave.exception.BookingConflictException;
import com.deskweave.exception.DataAccessException;
import com.deskweave.exception.InvalidTimeSlotException;
import com.deskweave.model.AuditLogEntry;
import com.deskweave.model.Booking;
import com.deskweave.model.BookingStatus;
import com.deskweave.model.TimeSlot;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC implementation of {@link BookingDao} featuring explicit transactions and conflict locking.
 */
public class PostgresBookingDao implements BookingDao {

    private static final String SELECT_COLS =
            "SELECT booking_id, user_id, workspace_id, booking_date, start_time, end_time, "
                    + "attendee_count, status, checked_in_at, notes, created_at FROM bookings";

    private final DatabaseManager db;
    private final AuditLogDao auditLogDao;

    public PostgresBookingDao(DatabaseManager db, AuditLogDao auditLogDao) {
        this.db = Objects.requireNonNull(db, "db must not be null");
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao must not be null");
    }

    @Override
    public Optional<Booking> findById(Integer id) {
        Objects.requireNonNull(id, "id must not be null");
        String sql = SELECT_COLS + " WHERE booking_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to find booking id=" + id, ex);
        }
    }

    @Override
    public List<Booking> findAll() {
        return queryBookings(SELECT_COLS + " ORDER BY booking_date DESC, start_time ASC", List.of());
    }

    @Override
    public Booking save(Booking booking) {
        Objects.requireNonNull(booking, "booking must not be null");
        try (Connection conn = db.getConnection()) {
            return insertBooking(conn, booking);
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to save booking", ex);
        }
    }

    @Override
    public Booking createWithAudit(Booking booking, int actorUserId, String auditDetails)
            throws BookingConflictException {
        Objects.requireNonNull(booking, "booking must not be null");
        String lockWorkspaceSql = "SELECT workspace_id FROM workspaces WHERE workspace_id = ? FOR UPDATE";

        try (Connection conn = db.getConnection()) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement lockPs = conn.prepareStatement(lockWorkspaceSql)) {
                    lockPs.setInt(1, booking.getWorkspaceId());
                    try (ResultSet rs = lockPs.executeQuery()) {
                        if (!rs.next()) {
                            throw new DataAccessException("Workspace not found for id=" + booking.getWorkspaceId());
                        }
                    }
                }

                if (hasOverlap(conn, booking.getWorkspaceId(), booking.getSlot())) {
                    conn.rollback();
                    throw new BookingConflictException(
                            "Workspace #" + booking.getWorkspaceId()
                                    + " already has an active booking overlapping " + booking.getSlot(),
                            booking.getWorkspaceId(),
                            booking.getSlot());
                }

                Booking saved = insertBooking(conn, booking);
                auditLogDao.insert(conn, new AuditLogEntry(
                        0,
                        saved.getBookingId(),
                        actorUserId,
                        "CREATED",
                        auditDetails == null ? "" : auditDetails,
                        LocalDateTime.now()
                ));
                conn.commit();
                return saved;
            } catch (BookingConflictException ex) {
                throw ex;
            } catch (Throwable ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                if (ex instanceof RuntimeException runtimeEx) {
                    throw runtimeEx;
                }
                throw new DataAccessException("Failed transactional createWithAudit", ex);
            } finally {
                try {
                    conn.setAutoCommit(prevAutoCommit);
                } catch (SQLException ignored) {
                    // Connection is closing.
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Database connection error during createWithAudit", ex);
        }
    }

    @Override
    public boolean updateStatusWithAudit(int bookingId,
                                         BookingStatus newStatus,
                                         LocalDateTime checkedInAt,
                                         int actorUserId,
                                         String auditAction,
                                         String details) {
        Objects.requireNonNull(newStatus, "newStatus must not be null");
        String sql = "UPDATE bookings SET status = ?, checked_in_at = ? WHERE booking_id = ?";
        return db.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newStatus.name());
                if (checkedInAt != null) {
                    ps.setTimestamp(2, Timestamp.valueOf(checkedInAt));
                } else {
                    ps.setNull(2, Types.TIMESTAMP);
                }
                ps.setInt(3, bookingId);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    return false;
                }
                auditLogDao.insert(conn, new AuditLogEntry(
                        0,
                        bookingId,
                        actorUserId,
                        auditAction,
                        details == null ? "" : details,
                        LocalDateTime.now()
                ));
                return true;
            }
        });
    }

    @Override
    public List<Booking> expireOverdueBookings(LocalDateTime cutoffDateTime, int systemActorUserId) {
        Objects.requireNonNull(cutoffDateTime, "cutoffDateTime must not be null");
        String selectSql = SELECT_COLS
                + " WHERE status = 'CONFIRMED' AND (booking_date < ? OR (booking_date = ? AND start_time <= ?)) "
                + "ORDER BY booking_date ASC, start_time ASC";
        String updateSql = "UPDATE bookings SET status = 'EXPIRED' WHERE booking_id = ? AND status = 'CONFIRMED'";

        Date cutoffDate = Date.valueOf(cutoffDateTime.toLocalDate());
        Time cutoffTime = Time.valueOf(cutoffDateTime.toLocalTime());

        return db.inTransaction(conn -> {
            List<Booking> candidates = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setDate(1, cutoffDate);
                ps.setDate(2, cutoffDate);
                ps.setTime(3, cutoffTime);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        candidates.add(mapRow(rs));
                    }
                }
            }

            List<Booking> expired = new ArrayList<>();
            try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                for (Booking candidate : candidates) {
                    updatePs.setInt(1, candidate.getBookingId());
                    if (updatePs.executeUpdate() > 0) {
                        Booking updatedBooking = new Booking(
                                candidate.getBookingId(),
                                candidate.getUserId(),
                                candidate.getWorkspaceId(),
                                candidate.getSlot(),
                                candidate.getAttendeeCount(),
                                BookingStatus.EXPIRED,
                                candidate.getCheckedInAt().orElse(null),
                                candidate.getNotes(),
                                candidate.getCreatedAt()
                        );
                        expired.add(updatedBooking);
                        auditLogDao.insert(conn, new AuditLogEntry(
                                0,
                                candidate.getBookingId(),
                                systemActorUserId,
                                "EXPIRED",
                                "Auto-expired unclaimed booking for slot " + candidate.getSlot(),
                                LocalDateTime.now()
                        ));
                    }
                }
            }
            return expired;
        });
    }

    @Override
    public boolean hasOverlappingActiveBooking(int workspaceId, TimeSlot slot) {
        Objects.requireNonNull(slot, "slot must not be null");
        try (Connection conn = db.getConnection()) {
            return hasOverlap(conn, workspaceId, slot);
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to check booking overlap", ex);
        }
    }

    @Override
    public List<Booking> findByUserId(int userId) {
        String sql = SELECT_COLS + " WHERE user_id = ? ORDER BY booking_date DESC, start_time ASC";
        return queryBookings(sql, List.of(userId));
    }

    @Override
    public List<Booking> findByDateRange(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from date must not be null");
        Objects.requireNonNull(to, "to date must not be null");
        String sql = SELECT_COLS + " WHERE booking_date BETWEEN ? AND ? ORDER BY booking_date ASC, start_time ASC";
        return queryBookings(sql, List.of(Date.valueOf(from), Date.valueOf(to)));
    }

    @Override
    public List<Booking> findActiveByWorkspaceAndDate(int workspaceId, LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        String sql = SELECT_COLS
                + " WHERE workspace_id = ? AND booking_date = ? AND status IN ('CONFIRMED', 'CHECKED_IN') "
                + "ORDER BY start_time ASC";
        return queryBookings(sql, List.of(workspaceId, Date.valueOf(date)));
    }

    @Override
    public boolean update(Booking booking) {
        Objects.requireNonNull(booking, "booking must not be null");
        String sql = "UPDATE bookings SET user_id = ?, workspace_id = ?, booking_date = ?, start_time = ?, "
                + "end_time = ?, attendee_count = ?, status = ?, checked_in_at = ?, notes = ? "
                + "WHERE booking_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, booking.getUserId());
            ps.setInt(2, booking.getWorkspaceId());
            ps.setDate(3, Date.valueOf(booking.getSlot().getDate()));
            ps.setTime(4, Time.valueOf(booking.getSlot().getStartTime()));
            ps.setTime(5, Time.valueOf(booking.getSlot().getEndTime()));
            ps.setInt(6, booking.getAttendeeCount());
            ps.setString(7, booking.getStatus().name());
            if (booking.getCheckedInAt().isPresent()) {
                ps.setTimestamp(8, Timestamp.valueOf(booking.getCheckedInAt().get()));
            } else {
                ps.setNull(8, Types.TIMESTAMP);
            }
            ps.setString(9, booking.getNotes());
            ps.setInt(10, booking.getBookingId());
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to update booking id=" + booking.getBookingId(), ex);
        }
    }

    @Override
    public boolean deleteById(Integer id) {
        Objects.requireNonNull(id, "id must not be null");
        String sql = "DELETE FROM bookings WHERE booking_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to delete booking id=" + id, ex);
        }
    }

    private boolean hasOverlap(Connection conn, int workspaceId, TimeSlot slot) throws SQLException {
        String sql = "SELECT COUNT(*) FROM bookings "
                + "WHERE workspace_id = ? AND booking_date = ? "
                + "AND status IN ('CONFIRMED', 'CHECKED_IN') "
                + "AND start_time < ? AND end_time > ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, workspaceId);
            ps.setDate(2, Date.valueOf(slot.getDate()));
            ps.setTime(3, Time.valueOf(slot.getEndTime()));
            ps.setTime(4, Time.valueOf(slot.getStartTime()));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private Booking insertBooking(Connection conn, Booking booking) throws SQLException {
        String sql = "INSERT INTO bookings (user_id, workspace_id, booking_date, start_time, end_time, "
                + "attendee_count, status, checked_in_at, notes, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        LocalDateTime createdAt = booking.getCreatedAt() != null ? booking.getCreatedAt() : LocalDateTime.now();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, booking.getUserId());
            ps.setInt(2, booking.getWorkspaceId());
            ps.setDate(3, Date.valueOf(booking.getSlot().getDate()));
            ps.setTime(4, Time.valueOf(booking.getSlot().getStartTime()));
            ps.setTime(5, Time.valueOf(booking.getSlot().getEndTime()));
            ps.setInt(6, booking.getAttendeeCount());
            ps.setString(7, booking.getStatus().name());
            if (booking.getCheckedInAt().isPresent()) {
                ps.setTimestamp(8, Timestamp.valueOf(booking.getCheckedInAt().get()));
            } else {
                ps.setNull(8, Types.TIMESTAMP);
            }
            ps.setString(9, booking.getNotes());
            ps.setTimestamp(10, Timestamp.valueOf(createdAt));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated booking_id returned on insert");
                }
                int id = keys.getInt(1);
                return new Booking(
                        id,
                        booking.getUserId(),
                        booking.getWorkspaceId(),
                        booking.getSlot(),
                        booking.getAttendeeCount(),
                        booking.getStatus(),
                        booking.getCheckedInAt().orElse(null),
                        booking.getNotes(),
                        createdAt
                );
            }
        }
    }

    private List<Booking> queryBookings(String sql, List<Object> params) {
        List<Booking> bookings = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    bookings.add(mapRow(rs));
                }
            }
            return bookings;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to query bookings", ex);
        }
    }

    private static Booking mapRow(ResultSet rs) throws SQLException {
        LocalDate date = rs.getDate("booking_date").toLocalDate();
        Time start = rs.getTime("start_time");
        Time end = rs.getTime("end_time");
        TimeSlot slot;
        try {
            slot = TimeSlot.of(date, start.toLocalTime(), end.toLocalTime());
        } catch (InvalidTimeSlotException ex) {
            throw new DataAccessException("Corrupt booking time slot in database for booking_id="
                    + rs.getInt("booking_id"), ex);
        }
        Timestamp checkedInTs = rs.getTimestamp("checked_in_at");
        Timestamp createdTs = rs.getTimestamp("created_at");
        return new Booking(
                rs.getInt("booking_id"),
                rs.getInt("user_id"),
                rs.getInt("workspace_id"),
                slot,
                rs.getInt("attendee_count"),
                BookingStatus.valueOf(rs.getString("status")),
                checkedInTs != null ? checkedInTs.toLocalDateTime() : null,
                rs.getString("notes"),
                createdTs != null ? createdTs.toLocalDateTime() : LocalDateTime.now()
        );
    }
}
