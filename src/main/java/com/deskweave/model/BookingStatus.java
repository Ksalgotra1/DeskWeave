package com.deskweave.model;

/**
 * Lifecycle states for a workspace booking.
 */
public enum BookingStatus {
    CONFIRMED,
    CHECKED_IN,
    EXPIRED,
    CANCELLED,
    COMPLETED;

    /**
     * Returns true when the booking actively holds its workspace time slot.
     */
    public boolean isActiveSlot() {
        return this == CONFIRMED || this == CHECKED_IN;
    }
}
