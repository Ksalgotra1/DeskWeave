package com.deskweave.exception;

import com.deskweave.model.TimeSlot;

/**
 * Thrown when a requested booking overlaps an active reservation on the same workspace.
 */
public class BookingConflictException extends DeskWeaveException {

    private final int workspaceId;
    private final TimeSlot conflictingSlot;

    public BookingConflictException(String message, int workspaceId, TimeSlot conflictingSlot) {
        super(message);
        this.workspaceId = workspaceId;
        this.conflictingSlot = conflictingSlot;
    }

    public int getWorkspaceId() {
        return workspaceId;
    }

    public TimeSlot getConflictingSlot() {
        return conflictingSlot;
    }
}
