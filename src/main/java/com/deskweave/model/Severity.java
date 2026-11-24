package com.deskweave.model;

/**
 * Priority severity levels for capacity recommendations.
 */
public enum Severity {
    HIGH(1),
    MEDIUM(2),
    LOW(3);

    private final int priorityRank;

    Severity(int priorityRank) {
        this.priorityRank = priorityRank;
    }

    public int getPriorityRank() {
        return priorityRank;
    }
}
