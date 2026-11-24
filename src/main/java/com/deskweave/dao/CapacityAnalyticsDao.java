package com.deskweave.dao;

import com.deskweave.exception.DataAccessException;
import com.deskweave.model.CapacityRecommendation;
import com.deskweave.model.RecommendationCategory;
import com.deskweave.model.Severity;
import com.deskweave.model.WorkspaceType;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JDBC analytics queries and recommendation persistence for the Capacity Planning module.
 */
public class CapacityAnalyticsDao {

    public record WorkspaceUsageStat(
            int workspaceId,
            String code,
            String name,
            WorkspaceType type,
            int floor,
            double bookedHours,
            int bookingCount
    ) {
    }

    public record NoShowStat(
            int floor,
            WorkspaceType type,
            int checkedInCount,
            int expiredCount
    ) {
    }

    public record SizeMismatchStat(
            int workspaceId,
            String code,
            String name,
            int floor,
            int capacity,
            int totalBookings,
            int smallGroupBookings
    ) {
    }

    private final DatabaseManager db;

    public CapacityAnalyticsDao(DatabaseManager db) {
        this.db = Objects.requireNonNull(db, "db must not be null");
    }

    /**
     * Computes total booked hours per active workspace across {@code [from, to]} (including zero-usage spaces).
     */
    public List<WorkspaceUsageStat> fetchWorkspaceBookedHours(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        String sql = "SELECT w.workspace_id, w.code, w.name, w.workspace_type, w.floor, b.start_time, b.end_time "
                + "FROM workspaces w "
                + "LEFT JOIN bookings b ON b.workspace_id = w.workspace_id "
                + "AND b.booking_date BETWEEN ? AND ? "
                + "AND b.status IN ('CONFIRMED', 'CHECKED_IN', 'COMPLETED') "
                + "WHERE w.is_active = TRUE "
                + "ORDER BY w.floor ASC, w.code ASC";

        Map<Integer, UsageAccumulator> grouped = new LinkedHashMap<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int wsId = rs.getInt("workspace_id");
                    UsageAccumulator acc = grouped.computeIfAbsent(wsId, ignored -> {
                        try {
                            return new UsageAccumulator(
                                    wsId,
                                    rs.getString("code"),
                                    rs.getString("name"),
                                    WorkspaceType.valueOf(rs.getString("workspace_type")),
                                    rs.getInt("floor"));
                        } catch (SQLException ex) {
                            throw new DataAccessException("Failed reading workspace metadata", ex);
                        }
                    });
                    Time start = rs.getTime("start_time");
                    Time end = rs.getTime("end_time");
                    if (start != null && end != null) {
                        double hours = Duration.between(start.toLocalTime(), end.toLocalTime()).toMinutes() / 60.0;
                        acc.addBooking(hours);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to fetch workspace booked hours", ex);
        }

        List<WorkspaceUsageStat> stats = new ArrayList<>(grouped.size());
        for (UsageAccumulator acc : grouped.values()) {
            stats.add(acc.toStat());
        }
        return stats;
    }

    /**
     * Computes checked-in vs. expired booking counts grouped by floor and workspace type.
     */
    public List<NoShowStat> fetchNoShowStatsByFloorAndType(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        String sql = "SELECT w.floor, w.workspace_type, "
                + "SUM(CASE WHEN b.checked_in_at IS NOT NULL THEN 1 ELSE 0 END) AS checked_in_count, "
                + "SUM(CASE WHEN b.status = 'EXPIRED' THEN 1 ELSE 0 END) AS expired_count "
                + "FROM bookings b "
                + "JOIN workspaces w ON w.workspace_id = b.workspace_id "
                + "WHERE b.booking_date BETWEEN ? AND ? "
                + "AND (b.checked_in_at IS NOT NULL OR b.status = 'EXPIRED') "
                + "GROUP BY w.floor, w.workspace_type "
                + "ORDER BY w.floor ASC, w.workspace_type ASC";

        List<NoShowStat> stats = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stats.add(new NoShowStat(
                            rs.getInt("floor"),
                            WorkspaceType.valueOf(rs.getString("workspace_type")),
                            rs.getInt("checked_in_count"),
                            rs.getInt("expired_count")
                    ));
                }
            }
            return stats;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to fetch no-show statistics", ex);
        }
    }

    /**
     * Detects large meeting rooms frequently reserved by small groups.
     */
    public List<SizeMismatchStat> fetchLargeRoomMismatchStats(LocalDate from,
                                                              LocalDate to,
                                                              int largeRoomMinCapacity,
                                                              int smallGroupMaxAttendees) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        String sql = "SELECT w.workspace_id, w.code, w.name, w.floor, w.capacity, "
                + "COUNT(b.booking_id) AS total_bookings, "
                + "SUM(CASE WHEN b.attendee_count <= ? THEN 1 ELSE 0 END) AS small_group_bookings "
                + "FROM workspaces w "
                + "JOIN bookings b ON b.workspace_id = w.workspace_id "
                + "WHERE w.is_active = TRUE AND w.workspace_type = 'ROOM' AND w.capacity >= ? "
                + "AND b.booking_date BETWEEN ? AND ? "
                + "AND b.status IN ('CONFIRMED', 'CHECKED_IN', 'COMPLETED') "
                + "GROUP BY w.workspace_id, w.code, w.name, w.floor, w.capacity "
                + "ORDER BY w.floor ASC, w.code ASC";

        List<SizeMismatchStat> stats = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, smallGroupMaxAttendees);
            ps.setInt(2, largeRoomMinCapacity);
            ps.setDate(3, Date.valueOf(from));
            ps.setDate(4, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stats.add(new SizeMismatchStat(
                            rs.getInt("workspace_id"),
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getInt("floor"),
                            rs.getInt("capacity"),
                            rs.getInt("total_bookings"),
                            rs.getInt("small_group_bookings")
                    ));
                }
            }
            return stats;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to fetch large room mismatch statistics", ex);
        }
    }

    /**
     * Persists generated capacity recommendations atomically in a single batch transaction.
     */
    public void saveRecommendations(List<CapacityRecommendation> recommendations) {
        Objects.requireNonNull(recommendations, "recommendations must not be null");
        if (recommendations.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO capacity_recommendations "
                + "(generated_at, category, severity, floor, workspace_type, metric_value, summary, suggested_action) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        db.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (CapacityRecommendation rec : recommendations) {
                    ps.setTimestamp(1, Timestamp.valueOf(rec.getGeneratedAt()));
                    ps.setString(2, rec.getCategory().name());
                    ps.setString(3, rec.getSeverity().name());
                    ps.setInt(4, rec.getFloor());
                    ps.setString(5, rec.getWorkspaceType().name());
                    ps.setDouble(6, rec.getMetricValue());
                    ps.setString(7, rec.getSummary());
                    ps.setString(8, rec.getSuggestedAction());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        });
    }

    public List<CapacityRecommendation> findLatestRecommendations(int limit) {
        int safeLimit = Math.max(1, limit);
        String sql = "SELECT rec_id, generated_at, category, severity, floor, workspace_type, "
                + "metric_value, summary, suggested_action "
                + "FROM capacity_recommendations ORDER BY generated_at DESC, rec_id DESC LIMIT ?";
        List<CapacityRecommendation> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("generated_at");
                    list.add(new CapacityRecommendation(
                            rs.getInt("rec_id"),
                            ts != null ? ts.toLocalDateTime() : LocalDateTime.now(),
                            RecommendationCategory.valueOf(rs.getString("category")),
                            Severity.valueOf(rs.getString("severity")),
                            rs.getInt("floor"),
                            WorkspaceType.valueOf(rs.getString("workspace_type")),
                            rs.getDouble("metric_value"),
                            rs.getString("summary"),
                            rs.getString("suggested_action")
                    ));
                }
            }
            return list;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to fetch capacity recommendations", ex);
        }
    }

    private static final class UsageAccumulator {
        private final int workspaceId;
        private final String code;
        private final String name;
        private final WorkspaceType type;
        private final int floor;
        private double hours;
        private int count;

        private UsageAccumulator(int workspaceId, String code, String name, WorkspaceType type, int floor) {
            this.workspaceId = workspaceId;
            this.code = code;
            this.name = name;
            this.type = type;
            this.floor = floor;
        }

        private void addBooking(double bookingHours) {
            this.hours += bookingHours;
            this.count++;
        }

        private WorkspaceUsageStat toStat() {
            return new WorkspaceUsageStat(workspaceId, code, name, type, floor, hours, count);
        }
    }
}
