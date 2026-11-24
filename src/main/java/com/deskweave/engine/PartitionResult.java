package com.deskweave.engine;

import com.deskweave.model.BookingRequest;
import com.deskweave.model.CapacitySimulationSnapshot;
import com.deskweave.model.Workspace;
import com.deskweave.model.WorkspaceType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable output produced by {@link IntervalPartitioningEngine}.
 */
public final class PartitionResult {

    private final LocalDate targetDate;
    private final int minRoomsNeeded;
    private final int actualInventoryCount;
    private final int roomDeficit;
    private final LocalTime peakConcurrencyTime;
    private final Map<Workspace, List<BookingRequest>> honoredAssignments;
    private final Map<Integer, List<BookingRequest>> virtualTrackAssignments;
    private final List<BookingRequest> flaggedOverflowRequests;
    private final Set<String> flaggedRequestIds;
    private final List<BookingRequest> sortedRequests;

    PartitionResult(LocalDate targetDate,
                    int minRoomsNeeded,
                    int actualInventoryCount,
                    int roomDeficit,
                    LocalTime peakConcurrencyTime,
                    Map<Workspace, List<BookingRequest>> honoredAssignments,
                    Map<Integer, List<BookingRequest>> virtualTrackAssignments,
                    List<BookingRequest> flaggedOverflowRequests,
                    Set<String> flaggedRequestIds,
                    List<BookingRequest> sortedRequests) {
        this.targetDate = targetDate;
        this.minRoomsNeeded = minRoomsNeeded;
        this.actualInventoryCount = actualInventoryCount;
        this.roomDeficit = roomDeficit;
        this.peakConcurrencyTime = peakConcurrencyTime;

        Map<Workspace, List<BookingRequest>> honoredCopy = new LinkedHashMap<>();
        honoredAssignments.forEach((ws, list) -> honoredCopy.put(ws, Collections.unmodifiableList(new ArrayList<>(list))));
        this.honoredAssignments = Collections.unmodifiableMap(honoredCopy);

        Map<Integer, List<BookingRequest>> trackCopy = new LinkedHashMap<>();
        virtualTrackAssignments.forEach((idx, list) -> trackCopy.put(idx, Collections.unmodifiableList(new ArrayList<>(list))));
        this.virtualTrackAssignments = Collections.unmodifiableMap(trackCopy);

        this.flaggedOverflowRequests = Collections.unmodifiableList(new ArrayList<>(flaggedOverflowRequests));
        this.flaggedRequestIds = Collections.unmodifiableSet(new HashSet<>(flaggedRequestIds));
        this.sortedRequests = Collections.unmodifiableList(new ArrayList<>(sortedRequests));
    }

    public Optional<LocalDate> getTargetDate() {
        return Optional.ofNullable(targetDate);
    }

    public int getMinRoomsNeeded() {
        return minRoomsNeeded;
    }

    public int getActualInventoryCount() {
        return actualInventoryCount;
    }

    public int getRoomDeficit() {
        return roomDeficit;
    }

    public Optional<LocalTime> getPeakConcurrencyTime() {
        return Optional.ofNullable(peakConcurrencyTime);
    }

    public Map<Workspace, List<BookingRequest>> getHonoredAssignments() {
        return honoredAssignments;
    }

    public Map<Integer, List<BookingRequest>> getVirtualTrackAssignments() {
        return virtualTrackAssignments;
    }

    public List<BookingRequest> getFlaggedOverflowRequests() {
        return flaggedOverflowRequests;
    }

    public Set<String> getFlaggedRequestIds() {
        return flaggedRequestIds;
    }

    public boolean hasOverflow() {
        return !flaggedOverflowRequests.isEmpty();
    }

    /**
     * Converts this partition result into a serializable snapshot for Unit 3 persistence.
     */
    public CapacitySimulationSnapshot toSnapshot(String snapshotName, WorkspaceType workspaceType) {
        Objects.requireNonNull(workspaceType, "workspaceType must not be null");
        Map<String, List<BookingRequest>> labeledAssignments = new LinkedHashMap<>();
        honoredAssignments.forEach((ws, reqs) -> labeledAssignments.put(ws.getDisplayName(), reqs));

        int trackIdx = 1;
        for (Map.Entry<Integer, List<BookingRequest>> entry : virtualTrackAssignments.entrySet()) {
            if (trackIdx > actualInventoryCount) {
                labeledAssignments.put("Overflow Track #" + entry.getKey(), entry.getValue());
            }
            trackIdx++;
        }

        return new CapacitySimulationSnapshot(
                snapshotName,
                LocalDateTime.now(),
                targetDate,
                workspaceType,
                actualInventoryCount,
                minRoomsNeeded,
                roomDeficit,
                peakConcurrencyTime,
                sortedRequests,
                flaggedOverflowRequests,
                labeledAssignments
        );
    }
}
