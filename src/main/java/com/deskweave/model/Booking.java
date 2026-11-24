package com.deskweave.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Persisted workspace reservation entity.
 */
public class Booking {

    private final int bookingId;
    private final int userId;
    private final int workspaceId;
    private final TimeSlot slot;
    private final int attendeeCount;
    private final BookingStatus status;
    private final LocalDateTime checkedInAt;
    private final String notes;
    private final LocalDateTime createdAt;

    public Booking(int bookingId, int userId, int workspaceId, TimeSlot slot,
                   int attendeeCount, BookingStatus status, LocalDateTime checkedInAt,
                   String notes, LocalDateTime createdAt) {
        if (attendeeCount < 1) {
            throw new IllegalArgumentException("attendeeCount must be >= 1");
        }
        this.bookingId = bookingId;
        this.userId = userId;
        this.workspaceId = workspaceId;
        this.slot = Objects.requireNonNull(slot, "slot must not be null");
        this.attendeeCount = attendeeCount;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.checkedInAt = checkedInAt;
        this.notes = notes == null ? "" : notes.trim();
        this.createdAt = createdAt;
    }

    public int getBookingId() {
        return bookingId;
    }

    public int getUserId() {
        return userId;
    }

    public int getWorkspaceId() {
        return workspaceId;
    }

    public TimeSlot getSlot() {
        return slot;
    }

    public int getAttendeeCount() {
        return attendeeCount;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Optional<LocalDateTime> getCheckedInAt() {
        return Optional.ofNullable(checkedInAt);
    }

    public String getNotes() {
        return notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "Booking#" + bookingId + " [ws=" + workspaceId + ", " + slot + ", " + status + "]";
    }
}
