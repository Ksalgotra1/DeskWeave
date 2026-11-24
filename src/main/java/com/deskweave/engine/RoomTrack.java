package com.deskweave.engine;

import com.deskweave.model.BookingRequest;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single room/track timeline inside the Interval Partitioning min-heap.
 */
final class RoomTrack {

    private final int trackIndex;
    private LocalTime currentEndTime;
    private final List<BookingRequest> assignedRequests = new ArrayList<>();

    RoomTrack(int trackIndex, BookingRequest initialRequest) {
        this.trackIndex = trackIndex;
        assign(initialRequest);
    }

    void assign(BookingRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        assignedRequests.add(request);
        this.currentEndTime = request.getSlot().getEndTime();
    }

    int getTrackIndex() {
        return trackIndex;
    }

    LocalTime getCurrentEndTime() {
        return currentEndTime;
    }

    List<BookingRequest> getAssignedRequests() {
        return Collections.unmodifiableList(assignedRequests);
    }
}
