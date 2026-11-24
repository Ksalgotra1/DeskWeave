package com.deskweave.dao;

import com.deskweave.exception.DataAccessException;
import com.deskweave.model.Desk;
import com.deskweave.model.MeetingRoom;
import com.deskweave.model.Workspace;
import com.deskweave.model.WorkspaceType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC implementation of {@link WorkspaceDao} mapping rows polymorphically to {@link Desk} and {@link MeetingRoom}.
 */
public class PostgresWorkspaceDao implements WorkspaceDao {

    private static final String SELECT_COLS =
            "SELECT workspace_id, code, name, workspace_type, floor, capacity, "
                    + "has_monitor, is_standing_desk, has_projector, has_whiteboard, is_active FROM workspaces";

    private final DatabaseManager db;

    public PostgresWorkspaceDao(DatabaseManager db) {
        this.db = Objects.requireNonNull(db, "db must not be null");
    }

    @Override
    public Optional<Workspace> findById(Integer id) {
        Objects.requireNonNull(id, "id must not be null");
        String sql = SELECT_COLS + " WHERE workspace_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to find workspace id=" + id, ex);
        }
    }

    @Override
    public Optional<Workspace> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String sql = SELECT_COLS + " WHERE UPPER(code) = UPPER(?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to find workspace code=" + code, ex);
        }
    }

    @Override
    public List<Workspace> findAll() {
        return queryList(SELECT_COLS + " ORDER BY floor ASC, code ASC", List.of());
    }

    @Override
    public List<Workspace> findActive() {
        return queryList(SELECT_COLS + " WHERE is_active = TRUE ORDER BY floor ASC, code ASC", List.of());
    }

    @Override
    public List<Workspace> findByFilter(WorkspaceType type,
                                        Integer floor,
                                        int minCapacity,
                                        boolean requireMonitor,
                                        boolean requireStanding,
                                        boolean requireProjector,
                                        boolean requireWhiteboard) {
        StringBuilder sql = new StringBuilder(SELECT_COLS).append(" WHERE is_active = TRUE");
        List<Object> params = new ArrayList<>();

        if (type != null) {
            sql.append(" AND workspace_type = ?");
            params.add(type.name());
        }
        if (floor != null) {
            sql.append(" AND floor = ?");
            params.add(floor);
        }
        if (minCapacity > 0) {
            sql.append(" AND capacity >= ?");
            params.add(minCapacity);
        }
        if (requireMonitor) {
            sql.append(" AND has_monitor = TRUE");
        }
        if (requireStanding) {
            sql.append(" AND is_standing_desk = TRUE");
        }
        if (requireProjector) {
            sql.append(" AND has_projector = TRUE");
        }
        if (requireWhiteboard) {
            sql.append(" AND has_whiteboard = TRUE");
        }
        sql.append(" ORDER BY floor ASC, code ASC");
        return queryList(sql.toString(), params);
    }

    @Override
    public Workspace save(Workspace workspace) {
        Objects.requireNonNull(workspace, "workspace must not be null");
        String sql = "INSERT INTO workspaces (code, name, workspace_type, floor, capacity, "
                + "has_monitor, is_standing_desk, has_projector, has_whiteboard, is_active) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindWorkspaceParams(ps, workspace);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("No generated workspace_id returned on insert");
                }
                int id = keys.getInt(1);
                return copyWithId(id, workspace);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to save workspace: " + workspace.getCode(), ex);
        }
    }

    @Override
    public boolean update(Workspace workspace) {
        Objects.requireNonNull(workspace, "workspace must not be null");
        String sql = "UPDATE workspaces SET code = ?, name = ?, workspace_type = ?, floor = ?, capacity = ?, "
                + "has_monitor = ?, is_standing_desk = ?, has_projector = ?, has_whiteboard = ?, is_active = ? "
                + "WHERE workspace_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindWorkspaceParams(ps, workspace);
            ps.setInt(11, workspace.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to update workspace id=" + workspace.getId(), ex);
        }
    }

    @Override
    public boolean deleteById(Integer id) {
        Objects.requireNonNull(id, "id must not be null");
        String sql = "DELETE FROM workspaces WHERE workspace_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to delete workspace id=" + id, ex);
        }
    }

    private List<Workspace> queryList(String sql, List<Object> params) {
        List<Workspace> results = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
            return results;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to query workspaces", ex);
        }
    }

    private static void bindWorkspaceParams(PreparedStatement ps, Workspace ws) throws SQLException {
        ps.setString(1, ws.getCode());
        ps.setString(2, ws.getName());
        ps.setString(3, ws.getWorkspaceType().name());
        ps.setInt(4, ws.getFloor());
        ps.setInt(5, ws.getCapacity());
        if (ws instanceof Desk desk) {
            ps.setBoolean(6, desk.isHasMonitor());
            ps.setBoolean(7, desk.isStandingDesk());
            ps.setBoolean(8, false);
            ps.setBoolean(9, false);
        } else if (ws instanceof MeetingRoom room) {
            ps.setBoolean(6, false);
            ps.setBoolean(7, false);
            ps.setBoolean(8, room.isHasProjector());
            ps.setBoolean(9, room.isHasWhiteboard());
        } else {
            ps.setBoolean(6, false);
            ps.setBoolean(7, false);
            ps.setBoolean(8, false);
            ps.setBoolean(9, false);
        }
        ps.setBoolean(10, ws.isActive());
    }

    private static Workspace copyWithId(int id, Workspace ws) {
        if (ws instanceof Desk desk) {
            return new Desk(id, desk.getCode(), desk.getName(), desk.getFloor(), desk.getCapacity(),
                    desk.isActive(), desk.isHasMonitor(), desk.isStandingDesk());
        }
        if (ws instanceof MeetingRoom room) {
            return new MeetingRoom(id, room.getCode(), room.getName(), room.getFloor(), room.getCapacity(),
                    room.isActive(), room.isHasProjector(), room.isHasWhiteboard());
        }
        throw new IllegalArgumentException("Unsupported Workspace subclass: " + ws.getClass().getName());
    }

    private static Workspace mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("workspace_id");
        String code = rs.getString("code");
        String name = rs.getString("name");
        WorkspaceType type = WorkspaceType.valueOf(rs.getString("workspace_type"));
        int floor = rs.getInt("floor");
        int capacity = rs.getInt("capacity");
        boolean active = rs.getBoolean("is_active");

        if (type == WorkspaceType.DESK) {
            return new Desk(id, code, name, floor, capacity, active,
                    rs.getBoolean("has_monitor"), rs.getBoolean("is_standing_desk"));
        }
        return new MeetingRoom(id, code, name, floor, capacity, active,
                rs.getBoolean("has_projector"), rs.getBoolean("has_whiteboard"));
    }
}
