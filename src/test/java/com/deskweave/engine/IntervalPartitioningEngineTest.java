package com.deskweave.engine;

import com.deskweave.exception.InvalidTimeSlotException;
import com.deskweave.model.BookingRequest;
import com.deskweave.model.CapacitySimulationSnapshot;
import com.deskweave.model.MeetingRoom;
import com.deskweave.model.TimeSlot;
import com.deskweave.model.Workspace;
import com.deskweave.model.WorkspaceType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntervalPartitioningEngineTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 25);
    private final IntervalPartitioningEngine engine = new IntervalPartitioningEngine();

    private static BookingRequest req(String id, LocalDate date, int startH, int startM, int endH, int endM)
            throws InvalidTimeSlotException {
        TimeSlot slot = TimeSlot.of(date, LocalTime.of(startH, startM), LocalTime.of(endH, endM));
        return new BookingRequest(id, "User-" + id, WorkspaceType.ROOM, 2, 4, slot, "");
    }

    private static Workspace room(int id, String code, boolean active) {
        return new MeetingRoom(id, code, "Room " + code, 2, 8, active, true, true);
    }

    @Test
    void emptyRequestsReturnZeroRoomsAndZeroDeficit() throws InvalidTimeSlotException {
        PartitionResult result = engine.partition(
                Collections.emptyList(),
                List.of(room(1, "R-201", true), room(2, "R-202", true)));

        assertEquals(0, result.getMinRoomsNeeded());
        assertEquals(2, result.getActualInventoryCount());
        assertEquals(0, result.getRoomDeficit());
        assertTrue(result.getPeakConcurrencyTime().isEmpty());
        assertFalse(result.hasOverflow());
    }

    @Test
    void singleRequestRequiresOneRoomAndIsHonored() throws InvalidTimeSlotException {
        PartitionResult result = engine.partition(
                List.of(req("REQ-1", DAY, 9, 0, 10, 0)),
                List.of(room(1, "R-201", true)));

        assertEquals(1, result.getMinRoomsNeeded());
        assertEquals(0, result.getRoomDeficit());
        assertFalse(result.hasOverflow());
        assertEquals(1, result.getHonoredAssignments().size());
    }

    @Test
    void touchingIntervalsReuseSingleRoom() throws InvalidTimeSlotException {
        List<BookingRequest> requests = List.of(
                req("REQ-1", DAY, 9, 0, 10, 0),
                req("REQ-2", DAY, 10, 0, 11, 0),
                req("REQ-3", DAY, 11, 0, 12, 0)
        );
        PartitionResult result = engine.partition(requests, List.of(room(1, "R-201", true)));

        assertEquals(1, result.getMinRoomsNeeded());
        assertEquals(0, result.getRoomDeficit());
        assertEquals(3, result.getVirtualTrackAssignments().get(1).size());
    }

    @Test
    void mutuallyOverlappingIntervalsRequireKRooms() throws InvalidTimeSlotException {
        List<BookingRequest> requests = List.of(
                req("REQ-1", DAY, 10, 0, 12, 0),
                req("REQ-2", DAY, 10, 15, 11, 45),
                req("REQ-3", DAY, 10, 30, 11, 30),
                req("REQ-4", DAY, 10, 45, 11, 15)
        );
        PartitionResult result = engine.partition(requests, List.of(
                room(1, "R-201", true),
                room(2, "R-202", true),
                room(3, "R-203", true),
                room(4, "R-204", true)
        ));

        assertEquals(4, result.getMinRoomsNeeded());
        assertEquals(0, result.getRoomDeficit());
        assertEquals(LocalTime.of(10, 45), result.getPeakConcurrencyTime().orElseThrow());
    }

    @Test
    void unsortedRequestsAreSortedAndPackedOptimallyByMinHeap() throws InvalidTimeSlotException {
        List<BookingRequest> unsorted = List.of(
                req("REQ-4", DAY, 12, 0, 13, 0),
                req("REQ-3", DAY, 10, 30, 11, 30),
                req("REQ-1", DAY, 9, 0, 12, 0),
                req("REQ-2", DAY, 9, 30, 10, 30)
        );
        PartitionResult result = engine.partition(unsorted, List.of(
                room(1, "R-201", true),
                room(2, "R-202", true)
        ));

        assertEquals(2, result.getMinRoomsNeeded());
        assertEquals(0, result.getRoomDeficit());
        assertEquals(LocalTime.of(9, 30), result.getPeakConcurrencyTime().orElseThrow());
        assertFalse(result.hasOverflow());
    }

    @Test
    void inventoryDeficitFlagsOverflowAndIgnoresInactiveWorkspaces() throws InvalidTimeSlotException {
        List<BookingRequest> requests = List.of(
                req("REQ-1", DAY, 9, 0, 11, 0),
                req("REQ-2", DAY, 9, 15, 11, 0),
                req("REQ-3", DAY, 9, 30, 11, 0),
                req("REQ-4", DAY, 9, 45, 11, 0)
        );
        List<Workspace> inventory = List.of(
                room(1, "R-201", true),
                room(2, "R-202", true),
                room(3, "R-203", false) // inactive must be excluded
        );

        PartitionResult result = engine.partition(requests, inventory);

        assertEquals(4, result.getMinRoomsNeeded());
        assertEquals(2, result.getActualInventoryCount());
        assertEquals(2, result.getRoomDeficit());
        assertTrue(result.hasOverflow());
        assertEquals(2, result.getHonoredAssignments().size());
        assertEquals(2, result.getFlaggedOverflowRequests().size());
        assertEquals(result.getFlaggedOverflowRequests().size(), result.getFlaggedRequestIds().size());
        assertTrue(result.getFlaggedRequestIds().contains("REQ-3"));
        assertTrue(result.getFlaggedRequestIds().contains("REQ-4"));
    }

    @Test
    void zeroInventoryFlagsAllRequestsWhileComputingMinRoomsNeeded() throws InvalidTimeSlotException {
        List<BookingRequest> requests = List.of(
                req("REQ-1", DAY, 9, 0, 10, 0),
                req("REQ-2", DAY, 9, 30, 10, 30)
        );

        PartitionResult result = engine.partition(requests, Collections.emptyList());

        assertEquals(2, result.getMinRoomsNeeded());
        assertEquals(0, result.getActualInventoryCount());
        assertEquals(2, result.getRoomDeficit());
        assertEquals(2, result.getFlaggedOverflowRequests().size());
        assertTrue(result.getHonoredAssignments().isEmpty());
    }

    @Test
    void mixedDatesThrowInSingleDatePartitionAndGroupInPartitionByDate() throws InvalidTimeSlotException {
        LocalDate day2 = DAY.plusDays(1);
        List<BookingRequest> mixed = List.of(
                req("REQ-1", DAY, 9, 0, 10, 0),
                req("REQ-2", day2, 9, 0, 10, 0),
                req("REQ-3", day2, 9, 30, 10, 30)
        );
        List<Workspace> inventory = List.of(room(1, "R-201", true));

        assertThrows(InvalidTimeSlotException.class, () -> engine.partition(mixed, inventory));

        Map<LocalDate, PartitionResult> byDate = engine.partitionByDate(mixed, inventory);
        assertEquals(2, byDate.size());
        assertEquals(1, byDate.get(DAY).getMinRoomsNeeded());
        assertEquals(0, byDate.get(DAY).getRoomDeficit());
        assertEquals(2, byDate.get(day2).getMinRoomsNeeded());
        assertEquals(1, byDate.get(day2).getRoomDeficit());
    }

    @Test
    void toSnapshotCapturesResultStateCleanly() throws InvalidTimeSlotException {
        List<BookingRequest> requests = List.of(
                req("REQ-1", DAY, 10, 0, 11, 0),
                req("REQ-2", DAY, 10, 0, 11, 0)
        );
        PartitionResult result = engine.partition(requests, List.of(room(1, "R-201", true)));
        CapacitySimulationSnapshot snapshot = result.toSnapshot("Peak-Test", WorkspaceType.ROOM);

        assertEquals("Peak-Test", snapshot.getSnapshotName());
        assertEquals(WorkspaceType.ROOM, snapshot.getWorkspaceType());
        assertEquals(DAY, snapshot.getTargetDate());
        assertEquals(1, snapshot.getPhysicalInventoryCount());
        assertEquals(2, snapshot.getMinRoomsNeeded());
        assertEquals(1, snapshot.getRoomDeficit());
        assertEquals(2, snapshot.getAllRequests().size());
        assertEquals(1, snapshot.getFlaggedRequests().size());
        assertEquals(2, snapshot.getRoomAssignments().size());
    }
}
