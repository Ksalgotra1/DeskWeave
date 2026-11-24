package com.deskweave.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable audit trail record for booking lifecycle transitions.
 */
public class AuditLogEntry {

    private final int auditId;
    private final int bookingId;
    private final int actorUserId;
    private final String action;
    private final String details;
    private final LocalDateTime createdAt;

    public AuditLogEntry(int auditId, int bookingId, int actorUserId,
                         String action, String details, LocalDateTime createdAt) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Audit action must not be blank");
        }
        this.auditId = auditId;
        this.bookingId = bookingId;
        this.actorUserId = actorUserId;
        this.action = action.trim();
        this.details = details == null ? "" : details.trim();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public int getAuditId() {
        return auditId;
    }

    public int getBookingId() {
        return bookingId;
    }

    public int getActorUserId() {
        return actorUserId;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "[" + createdAt + "] " + action + " booking#" + bookingId + " (" + details + ")";
    }
}
