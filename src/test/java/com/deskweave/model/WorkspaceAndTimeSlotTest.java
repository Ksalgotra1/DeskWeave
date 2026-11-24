package com.deskweave.model;

import com.deskweave.exception.InvalidTimeSlotException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceAndTimeSlotTest {

    @Test
    void deskAndMeetingRoomImplementPolymorphicWorkspaceAndBookable() {
        Workspace desk = new Desk(1, "D-101", "Window Desk 1", 1, 1, true, true, false);
        Workspace room = new MeetingRoom(2, "R-201", "Boardroom Alpha", 2, 8, true, true, true);
        Bookable inactiveRoom = new MeetingRoom(3, "R-202", "Maintenance Room", 2, 6, false, false, false);

        assertEquals(WorkspaceType.DESK, desk.getWorkspaceType());
        assertEquals(15, desk.getDefaultGraceMinutes());
        assertEquals("Monitor", desk.getAmenitySummary());
        assertTrue(desk.canAccommodate(1));
        assertFalse(desk.canAccommodate(2));

        assertEquals(WorkspaceType.ROOM, room.getWorkspaceType());
        assertEquals(10, room.getDefaultGraceMinutes());
        assertEquals("Projector, Whiteboard", room.getAmenitySummary());
        assertTrue(room.canAccommodate(6));
        assertFalse(room.canAccommodate(9));

        assertFalse(inactiveRoom.canAccommodate(2));
    }

    @Test
    void timeSlotDetectsOverlapAndTouchingBoundaries() throws InvalidTimeSlotException {
        LocalDate day1 = LocalDate.of(2026, 11, 25);
        LocalDate day2 = LocalDate.of(2026, 11, 26);

        TimeSlot morning = TimeSlot.of(day1, LocalTime.of(9, 0), LocalTime.of(10, 0));
        TimeSlot touching = TimeSlot.of(day1, LocalTime.of(10, 0), LocalTime.of(11, 0));
        TimeSlot overlapping = TimeSlot.of(day1, LocalTime.of(9, 30), LocalTime.of(10, 30));
        TimeSlot nextDay = TimeSlot.of(day2, LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertEquals(60, morning.durationMinutes());
        assertEquals(1.0, morning.durationHours(), 1e-6);
        assertFalse(morning.overlaps(touching), "Touching intervals must not overlap");
        assertTrue(morning.overlaps(overlapping));
        assertTrue(overlapping.overlaps(morning));
        assertFalse(morning.overlaps(nextDay));
    }

    @Test
    void timeSlotRejectsNonIncreasingEndpoints() {
        LocalDate today = LocalDate.of(2026, 11, 25);
        assertThrows(InvalidTimeSlotException.class,
                () -> TimeSlot.of(today, LocalTime.of(10, 0), LocalTime.of(10, 0)));
        assertThrows(InvalidTimeSlotException.class,
                () -> TimeSlot.of(today, LocalTime.of(11, 0), LocalTime.of(10, 0)));
    }
}
