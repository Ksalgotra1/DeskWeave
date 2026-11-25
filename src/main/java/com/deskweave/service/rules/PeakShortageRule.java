package com.deskweave.service.rules;

import com.deskweave.engine.PartitionResult;
import com.deskweave.exception.InvalidTimeSlotException;
import com.deskweave.model.Booking;
import com.deskweave.model.BookingRequest;
import com.deskweave.model.BookingStatus;
import com.deskweave.model.CapacityRecommendation;
import com.deskweave.model.RecommendationCategory;
import com.deskweave.model.Severity;
import com.deskweave.model.Workspace;
import com.deskweave.model.WorkspaceType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs Interval Partitioning across historical demand to detect recurring peak capacity bottlenecks.
 */
public class PeakShortageRule implements CapacityRule {

    private static final int MIN_CONGESTED_DAYS = 2;

    private record GroupKey(int floor, WorkspaceType type) {
    }

    @Override
    public String getRuleName() {
        return "Peak Shortage Detector";
    }

    @Override
    public List<CapacityRecommendation> evaluate(CapacityAnalysisContext context) {
        Map<Integer, Workspace> wsById = new HashMap<>();
        Map<GroupKey, List<Workspace>> workspacesByGroup = new HashMap<>();
        for (Workspace ws : context.activeWorkspaces()) {
            wsById.put(ws.getId(), ws);
            workspacesByGroup
                    .computeIfAbsent(new GroupKey(ws.getFloor(), ws.getWorkspaceType()), ignored -> new ArrayList<>())
                    .add(ws);
        }

        Map<GroupKey, List<BookingRequest>> requestsByGroup = new HashMap<>();
        for (Booking booking : context.bookingsInRange()) {
            if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.EXPIRED) {
                continue;
            }
            Workspace ws = wsById.get(booking.getWorkspaceId());
            if (ws == null) {
                continue;
            }
            GroupKey key = new GroupKey(ws.getFloor(), ws.getWorkspaceType());
            requestsByGroup
                    .computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new BookingRequest(
                            "HIST-" + booking.getBookingId(),
                            "User#" + booking.getUserId(),
                            ws.getWorkspaceType(),
                            ws.getFloor(),
                            booking.getAttendeeCount(),
                            booking.getSlot(),
                            booking.getNotes()
                    ));
        }

        double highThreshold = context.config().getCapacityHighThreshold();
        List<CapacityRecommendation> recommendations = new ArrayList<>();

        for (Map.Entry<GroupKey, List<Workspace>> entry : workspacesByGroup.entrySet()) {
            GroupKey key = entry.getKey();
            List<Workspace> inventoryList = entry.getValue();
            int inventory = inventoryList.size();
            List<BookingRequest> groupRequests = requestsByGroup.getOrDefault(key, List.of());
            if (inventory == 0 || groupRequests.isEmpty()) {
                continue;
            }

            Map<LocalDate, PartitionResult> byDate;
            try {
                byDate = context.partitioningEngine().partitionByDate(groupRequests, inventoryList);
            } catch (InvalidTimeSlotException ex) {
                continue;
            }

            int congestedDays = 0;
            int maxRoomsNeeded = 0;
            int maxDeficit = 0;
            LocalTime samplePeakTime = null;

            for (PartitionResult dayResult : byDate.values()) {
                double ratio = (double) dayResult.getMinRoomsNeeded() / inventory;
                if (dayResult.getRoomDeficit() > 0 || ratio >= highThreshold) {
                    congestedDays++;
                    if (dayResult.getPeakConcurrencyTime().isPresent()) {
                        samplePeakTime = dayResult.getPeakConcurrencyTime().get();
                    }
                }
                maxRoomsNeeded = Math.max(maxRoomsNeeded, dayResult.getMinRoomsNeeded());
                maxDeficit = Math.max(maxDeficit, dayResult.getRoomDeficit());
            }

            if (congestedDays >= MIN_CONGESTED_DAYS) {
                double peakRatio = (double) maxRoomsNeeded / inventory;
                int peakPct = (int) Math.round(peakRatio * 100.0);
                int addCount = maxDeficit > 0 ? maxDeficit : Math.max(1, (int) Math.ceil(inventory * 0.25));
                String spaceNoun = key.type() == WorkspaceType.ROOM ? "room(s)" : "desk(s)";
                String peakWindowNote = samplePeakTime != null ? " around " + samplePeakTime : "";

                recommendations.add(new CapacityRecommendation(
                        0,
                        LocalDateTime.now(),
                        RecommendationCategory.SHORTAGE,
                        Severity.HIGH,
                        key.floor(),
                        key.type(),
                        peakRatio,
                        "Floor " + key.floor() + " " + key.type() + " hit " + peakPct
                                + "% peak concurrency across " + congestedDays + " day(s)" + peakWindowNote,
                        "Add " + addCount + " " + spaceNoun + " on Floor " + key.floor()
                                + " or stagger peak-hour reservations"
                ));
            }
        }
        return recommendations;
    }
}
