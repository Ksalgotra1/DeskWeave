package com.deskweave.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/**
 * Lightweight demand interval used for booking creation, CSV import, and Interval Partitioning.
 */
public class BookingRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String requestId;
    private final String requesterName;
    private final WorkspaceType workspaceType;
    private final Integer preferredFloor;
    private final int attendeeCount;
    private final TimeSlot slot;
    private final String notes;

    public BookingRequest(String requestId, String requesterName, WorkspaceType workspaceType,
                          Integer preferredFloor, int attendeeCount, TimeSlot slot, String notes) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (requesterName == null || requesterName.isBlank()) {
            throw new IllegalArgumentException("requesterName must not be blank");
        }
        if (attendeeCount < 1) {
            throw new IllegalArgumentException("attendeeCount must be >= 1");
        }
        if (preferredFloor != null && preferredFloor < 1) {
            throw new IllegalArgumentException("preferredFloor must be >= 1");
        }
        this.requestId = requestId.trim();
        this.requesterName = requesterName.trim();
        this.workspaceType = Objects.requireNonNull(workspaceType, "workspaceType must not be null");
        this.preferredFloor = preferredFloor;
        this.attendeeCount = attendeeCount;
        this.slot = Objects.requireNonNull(slot, "slot must not be null");
        this.notes = notes == null ? "" : notes.trim();
    }

    public String getRequestId() {
        return requestId;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public WorkspaceType getWorkspaceType() {
        return workspaceType;
    }

    public Optional<Integer> getPreferredFloor() {
        return Optional.ofNullable(preferredFloor);
    }

    public int getAttendeeCount() {
        return attendeeCount;
    }

    public TimeSlot getSlot() {
        return slot;
    }

    public String getNotes() {
        return notes;
    }

    @Override
    public String toString() {
        return requestId + " (" + requesterName + ", " + workspaceType + ", " + slot + ")";
    }
}
