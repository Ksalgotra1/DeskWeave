package com.deskweave.model;

/**
 * Individual workstation desk with monitor and standing-desk attributes.
 */
public class Desk extends Workspace {

    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_GRACE_MINUTES = 15;

    private final boolean hasMonitor;
    private final boolean standingDesk;

    public Desk(int id, String code, String name, int floor, int capacity, boolean active,
                boolean hasMonitor, boolean standingDesk) {
        super(id, code, name, floor, capacity, active);
        this.hasMonitor = hasMonitor;
        this.standingDesk = standingDesk;
    }

    @Override
    public WorkspaceType getWorkspaceType() {
        return WorkspaceType.DESK;
    }

    @Override
    public int getDefaultGraceMinutes() {
        return DEFAULT_GRACE_MINUTES;
    }

    @Override
    public String getAmenitySummary() {
        if (hasMonitor && standingDesk) {
            return "Monitor, Standing Desk";
        }
        if (hasMonitor) {
            return "Monitor";
        }
        if (standingDesk) {
            return "Standing Desk";
        }
        return "Standard Desk";
    }

    public boolean isHasMonitor() {
        return hasMonitor;
    }

    public boolean isStandingDesk() {
        return standingDesk;
    }
}
