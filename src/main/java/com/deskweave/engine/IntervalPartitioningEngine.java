package com.deskweave.engine;

import com.deskweave.exception.InvalidTimeSlotException;
import com.deskweave.model.BookingRequest;
import com.deskweave.model.Workspace;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Greedy Interval Partitioning engine backed by a min-heap {@link PriorityQueue}.
 *
 * <p>Computes the minimum number of physical rooms/desks required to honor a set of
 * booking intervals in {@code O(n log n)} time, binds tracks to available inventory,
 * and flags requests that exceed capacity.
 */
public class IntervalPartitioningEngine {

    private static final Comparator<BookingRequest> REQUEST_ORDER = Comparator
            .comparing((BookingRequest r) -> r.getSlot().getStartTime())
            .thenComparing(r -> r.getSlot().getEndTime())
            .thenComparing(BookingRequest::getRequestId);

    private static final Comparator<RoomTrack> TRACK_MIN_HEAP_ORDER = Comparator
            .comparing(RoomTrack::getCurrentEndTime)
            .thenComparingInt(RoomTrack::getTrackIndex);

    /**
     * Partitions single-date booking requests onto the supplied workspaces.
     *
     * @throws InvalidTimeSlotException if requests span multiple dates
     */
    public PartitionResult partition(List<BookingRequest> requests, List<Workspace> availableWorkspaces)
            throws InvalidTimeSlotException {
        Objects.requireNonNull(requests, "requests must not be null");
        Objects.requireNonNull(availableWorkspaces, "availableWorkspaces must not be null");

        List<Workspace> activeWorkspaces = availableWorkspaces.stream()
                .filter(Objects::nonNull)
                .filter(Workspace::isActive)
                .sorted(Comparator.comparing(Workspace::getCode))
                .toList();

        if (requests.isEmpty()) {
            return new PartitionResult(
                    null,
                    0,
                    activeWorkspaces.size(),
                    0,
                    null,
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyList(),
                    Collections.emptySet(),
                    Collections.emptyList()
            );
        }

        LocalDate targetDate = requests.get(0).getSlot().getDate();
        for (BookingRequest request : requests) {
            Objects.requireNonNull(request, "BookingRequest element must not be null");
            if (!request.getSlot().getDate().equals(targetDate)) {
                throw new InvalidTimeSlotException(
                        "Single-date partition expects all requests on " + targetDate
                                + ", but found " + request.getSlot().getDate(),
                        request.getSlot().getStartTime(),
                        request.getSlot().getEndTime());
            }
        }

        List<BookingRequest> sortedRequests = new ArrayList<>(requests);
        sortedRequests.sort(REQUEST_ORDER);

        PriorityQueue<RoomTrack> minHeap = new PriorityQueue<>(TRACK_MIN_HEAP_ORDER);
        List<RoomTrack> allTracks = new ArrayList<>();
        LocalTime peakConcurrencyTime = null;

        for (BookingRequest request : sortedRequests) {
            RoomTrack earliest = minHeap.peek();
            // Reuse a room whenever its latest end time is <= the new request's start time.
            if (earliest != null && !earliest.getCurrentEndTime().isAfter(request.getSlot().getStartTime())) {
                minHeap.poll();
                earliest.assign(request);
                minHeap.offer(earliest);
            } else {
                RoomTrack newTrack = new RoomTrack(allTracks.size() + 1, request);
                allTracks.add(newTrack);
                minHeap.offer(newTrack);
                // Opening a new track increases maximum concurrent rooms reached so far.
                peakConcurrencyTime = request.getSlot().getStartTime();
            }
        }

        int minRoomsNeeded = allTracks.size();
        int actualInventoryCount = activeWorkspaces.size();
        int roomDeficit = Math.max(0, minRoomsNeeded - actualInventoryCount);

        Map<Workspace, List<BookingRequest>> honoredAssignments = new LinkedHashMap<>();
        Map<Integer, List<BookingRequest>> virtualTrackAssignments = new LinkedHashMap<>();
        List<BookingRequest> flaggedOverflowRequests = new ArrayList<>();
        Set<String> flaggedRequestIds = new HashSet<>();

        for (RoomTrack track : allTracks) {
            int trackIndex = track.getTrackIndex();
            List<BookingRequest> assigned = track.getAssignedRequests();
            virtualTrackAssignments.put(trackIndex, assigned);

            if (trackIndex <= actualInventoryCount) {
                Workspace physicalSpace = activeWorkspaces.get(trackIndex - 1);
                honoredAssignments.put(physicalSpace, assigned);
            } else {
                for (BookingRequest overflow : assigned) {
                    flaggedOverflowRequests.add(overflow);
                    flaggedRequestIds.add(overflow.getRequestId());
                }
            }
        }

        return new PartitionResult(
                targetDate,
                minRoomsNeeded,
                actualInventoryCount,
                roomDeficit,
                peakConcurrencyTime,
                honoredAssignments,
                virtualTrackAssignments,
                flaggedOverflowRequests,
                flaggedRequestIds,
                sortedRequests
        );
    }

    /**
     * Groups requests by {@link LocalDate} and executes Interval Partitioning independently per day.
     */
    public Map<LocalDate, PartitionResult> partitionByDate(List<BookingRequest> requests,
                                                           List<Workspace> availableWorkspaces)
            throws InvalidTimeSlotException {
        Objects.requireNonNull(requests, "requests must not be null");
        Objects.requireNonNull(availableWorkspaces, "availableWorkspaces must not be null");

        Map<LocalDate, List<BookingRequest>> groupedByDate = new HashMap<>();
        for (BookingRequest request : requests) {
            Objects.requireNonNull(request, "BookingRequest element must not be null");
            groupedByDate
                    .computeIfAbsent(request.getSlot().getDate(), ignored -> new ArrayList<>())
                    .add(request);
        }

        Map<LocalDate, PartitionResult> results = new LinkedHashMap<>();
        List<LocalDate> sortedDates = new ArrayList<>(groupedByDate.keySet());
        Collections.sort(sortedDates);
        for (LocalDate date : sortedDates) {
            results.put(date, partition(groupedByDate.get(date), availableWorkspaces));
        }
        return Collections.unmodifiableMap(results);
    }
}
