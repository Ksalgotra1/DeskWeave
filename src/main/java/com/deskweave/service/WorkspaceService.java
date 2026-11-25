package com.deskweave.service;

import com.deskweave.config.AppLogger;
import com.deskweave.dao.WorkspaceDao;
import com.deskweave.exception.DeskWeaveException;
import com.deskweave.exception.ResourceNotFoundException;
import com.deskweave.model.Desk;
import com.deskweave.model.MeetingRoom;
import com.deskweave.model.Workspace;
import com.deskweave.model.WorkspaceType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Manages workspace inventory, multi-criteria search, and admin CRUD operations.
 */
public class WorkspaceService {

    private static final Logger LOGGER = AppLogger.getLogger(WorkspaceService.class);

    private final WorkspaceDao workspaceDao;

    public WorkspaceService(WorkspaceDao workspaceDao) {
        this.workspaceDao = Objects.requireNonNull(workspaceDao, "workspaceDao must not be null");
    }

    public List<Workspace> listAll() {
        return workspaceDao.findAll();
    }

    public List<Workspace> listActive() {
        return workspaceDao.findActive();
    }

    public Workspace getById(int workspaceId) throws ResourceNotFoundException {
        return workspaceDao.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
    }

    public List<Workspace> searchAvailable(WorkspaceType type,
                                           Integer floor,
                                           int minCapacity,
                                           boolean requireMonitor,
                                           boolean requireStanding,
                                           boolean requireProjector,
                                           boolean requireWhiteboard) {
        return workspaceDao.findByFilter(
                type, floor, minCapacity,
                requireMonitor, requireStanding, requireProjector, requireWhiteboard);
    }

    public Workspace createWorkspace(Workspace workspace) throws DeskWeaveException {
        Objects.requireNonNull(workspace, "workspace must not be null");
        if (workspaceDao.findByCode(workspace.getCode()).isPresent()) {
            throw new DeskWeaveException("Workspace code already exists: " + workspace.getCode());
        }
        Workspace saved = workspaceDao.save(workspace);
        LOGGER.info("Created workspace " + saved.getCode() + " (" + saved.getWorkspaceType() + ")");
        return saved;
    }

    public Workspace updateWorkspace(Workspace workspace) throws ResourceNotFoundException {
        Objects.requireNonNull(workspace, "workspace must not be null");
        getById(workspace.getId());
        if (!workspaceDao.update(workspace)) {
            throw new ResourceNotFoundException("Workspace", workspace.getId());
        }
        LOGGER.info("Updated workspace " + workspace.getCode());
        return getById(workspace.getId());
    }

    public Workspace setWorkspaceActive(int workspaceId, boolean active) throws ResourceNotFoundException {
        Workspace existing = getById(workspaceId);
        Workspace updated;
        if (existing instanceof Desk desk) {
            updated = new Desk(desk.getId(), desk.getCode(), desk.getName(), desk.getFloor(),
                    desk.getCapacity(), active, desk.isHasMonitor(), desk.isStandingDesk());
        } else if (existing instanceof MeetingRoom room) {
            updated = new MeetingRoom(room.getId(), room.getCode(), room.getName(), room.getFloor(),
                    room.getCapacity(), active, room.isHasProjector(), room.isHasWhiteboard());
        } else {
            throw new IllegalStateException("Unsupported workspace type: " + existing.getClass().getName());
        }
        workspaceDao.update(updated);
        LOGGER.info("Set workspace " + updated.getCode() + " active=" + active);
        return updated;
    }

    /**
     * Builds an O(1) ID-indexed map of all workspaces for UI table rendering and analytics.
     */
    public Map<Integer, Workspace> indexById() {
        Map<Integer, Workspace> index = new HashMap<>();
        for (Workspace ws : workspaceDao.findAll()) {
            index.put(ws.getId(), ws);
        }
        return index;
    }
}
