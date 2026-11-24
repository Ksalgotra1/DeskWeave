package com.deskweave.model;

/**
 * Collaborative meeting room with projector and whiteboard attributes.
 */
public class MeetingRoom extends Workspace {

    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_GRACE_MINUTES = 10;

    private final boolean hasProjector;
    private final boolean hasWhiteboard;

    public MeetingRoom(int id, String code, String name, int floor, int capacity, boolean active,
                       boolean hasProjector, boolean hasWhiteboard) {
        super(id, code, name, floor, capacity, active);
        this.hasProjector = hasProjector;
        this.hasWhiteboard = hasWhiteboard;
    }

    @Override
    public WorkspaceType getWorkspaceType() {
        return WorkspaceType.ROOM;
    }

    @Override
    public int getDefaultGraceMinutes() {
        return DEFAULT_GRACE_MINUTES;
    }

    @Override
    public String getAmenitySummary() {
        if (hasProjector && hasWhiteboard) {
            return "Projector, Whiteboard";
        }
        if (hasProjector) {
            return "Projector";
        }
        if (hasWhiteboard) {
            return "Whiteboard";
        }
        return "Standard Room";
    }

    public boolean isHasProjector() {
        return hasProjector;
    }

    public boolean isHasWhiteboard() {
        return hasWhiteboard;
    }
}
