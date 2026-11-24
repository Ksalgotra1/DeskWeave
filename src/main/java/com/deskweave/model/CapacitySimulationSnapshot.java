package com.deskweave.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Serializable what-if capacity simulation snapshot persisted via Java Object Streams (.dws).
 */
public class CapacitySimulationSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    // Transient runtime logger is excluded from binary serialization.
    private transient Logger logger = Logger.getLogger(CapacitySimulationSnapshot.class.getName());

    private final String snapshotName;
    private final LocalDateTime createdAt;
    private final LocalDate targetDate;
    private final WorkspaceType workspaceType;
    private final int physicalInventoryCount;
    private final int minRoomsNeeded;
    private final int roomDeficit;
    private final LocalTime peakConcurrencyTime;
    private final List<BookingRequest> allRequests;
    private final List<BookingRequest> flaggedRequests;
    private final Map<String, List<BookingRequest>> roomAssignments;

    public CapacitySimulationSnapshot(String snapshotName, LocalDateTime createdAt, LocalDate targetDate,
                                      WorkspaceType workspaceType, int physicalInventoryCount,
                                      int minRoomsNeeded, int roomDeficit, LocalTime peakConcurrencyTime,
                                      List<BookingRequest> allRequests, List<BookingRequest> flaggedRequests,
                                      Map<String, List<BookingRequest>> roomAssignments) {
        if (snapshotName == null || snapshotName.isBlank()) {
            throw new IllegalArgumentException("snapshotName must not be blank");
        }
        this.snapshotName = snapshotName.trim();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.targetDate = targetDate;
        this.workspaceType = Objects.requireNonNull(workspaceType, "workspaceType must not be null");
        this.physicalInventoryCount = physicalInventoryCount;
        this.minRoomsNeeded = minRoomsNeeded;
        this.roomDeficit = roomDeficit;
        this.peakConcurrencyTime = peakConcurrencyTime;
        this.allRequests = new ArrayList<>(Objects.requireNonNull(allRequests, "allRequests must not be null"));
        this.flaggedRequests = new ArrayList<>(Objects.requireNonNull(flaggedRequests, "flaggedRequests must not be null"));

        Map<String, List<BookingRequest>> copy = new LinkedHashMap<>();
        Objects.requireNonNull(roomAssignments, "roomAssignments must not be null")
                .forEach((k, v) -> copy.put(k, new ArrayList<>(v)));
        this.roomAssignments = copy;
    }

    public String getSnapshotName() {
        return snapshotName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public WorkspaceType getWorkspaceType() {
        return workspaceType;
    }

    public int getPhysicalInventoryCount() {
        return physicalInventoryCount;
    }

    public int getMinRoomsNeeded() {
        return minRoomsNeeded;
    }

    public int getRoomDeficit() {
        return roomDeficit;
    }

    public LocalTime getPeakConcurrencyTime() {
        return peakConcurrencyTime;
    }

    public List<BookingRequest> getAllRequests() {
        return Collections.unmodifiableList(allRequests);
    }

    public List<BookingRequest> getFlaggedRequests() {
        return Collections.unmodifiableList(flaggedRequests);
    }

    public Map<String, List<BookingRequest>> getRoomAssignments() {
        Map<String, List<BookingRequest>> unmodifiable = new LinkedHashMap<>();
        roomAssignments.forEach((k, v) -> unmodifiable.put(k, Collections.unmodifiableList(v)));
        return Collections.unmodifiableMap(unmodifiable);
    }

    public Logger getTransientLogger() {
        return logger;
    }
}
