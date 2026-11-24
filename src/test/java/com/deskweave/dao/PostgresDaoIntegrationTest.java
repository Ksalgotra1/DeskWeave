package com.deskweave.dao;

import com.deskweave.config.PasswordHasher;
import com.deskweave.exception.BookingConflictException;
import com.deskweave.exception.InvalidTimeSlotException;
import com.deskweave.model.AuditLogEntry;
import com.deskweave.model.Booking;
import com.deskweave.model.BookingStatus;
import com.deskweave.model.CapacityRecommendation;
import com.deskweave.model.Desk;
import com.deskweave.model.MeetingRoom;
import com.deskweave.model.RecommendationCategory;
import com.deskweave.model.Severity;
import com.deskweave.model.TimeSlot;
import com.deskweave.model.User;
import com.deskweave.model.UserRole;
import com.deskweave.model.Workspace;
import com.deskweave.model.WorkspaceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresDaoIntegrationTest {

    private DatabaseManager db;
    private PostgresUserDao userDao;
    private PostgresWorkspaceDao workspaceDao;
    private PostgresAuditLogDao auditLogDao;
    private PostgresBookingDao bookingDao;
    private CapacityAnalyticsDao analyticsDao;

    @BeforeEach
    void setUpDatabase() {
        String url = "jdbc:h2:mem:dw_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        db = new DatabaseManager(url, "sa", "");
        assertTrue(db.testConnection());
        db.executeSqlScript(Path.of("sql/schema.sql"));

        userDao = new PostgresUserDao(db);
        workspaceDao = new PostgresWorkspaceDao(db);
        auditLogDao = new PostgresAuditLogDao(db);
        bookingDao = new PostgresBookingDao(db, auditLogDao);
        analyticsDao = new CapacityAnalyticsDao(db);
    }

    @Test
    void userAndWorkspaceCrudAndFilteringWork() {
        User savedUser = userDao.save(new User(
                0, "Alice Verma", "Alice@DeskWeave.io",
                PasswordHasher.hash("pass123"), UserRole.EMPLOYEE, "Engineering", null));
        assertTrue(savedUser.getUserId() > 0);
        assertEquals("alice@deskweave.io", savedUser.getEmail());
        assertTrue(userDao.findByEmail("ALICE@deskweave.io").isPresent());

        Workspace desk = workspaceDao.save(new Desk(0, "D-101", "Window Desk", 1, 1, true, true, false));
        Workspace room = workspaceDao.save(new MeetingRoom(0, "R-201", "Boardroom", 2, 10, true, true, true));
        Workspace inactive = workspaceDao.save(new Desk(0, "D-999", "Retired Desk", 3, 1, false, false, false));

        assertTrue(workspaceDao.findByCode("r-201").orElseThrow() instanceof MeetingRoom);
        assertTrue(workspaceDao.findById(desk.getId()).orElseThrow() instanceof Desk);
        assertEquals(2, workspaceDao.findActive().size());

        List<Workspace> filtered = workspaceDao.findByFilter(
                WorkspaceType.ROOM, 2, 8, false, false, true, true);
        assertEquals(1, filtered.size());
        assertEquals(room.getId(), filtered.get(0).getId());

        assertTrue(workspaceDao.deleteById(inactive.getId()));
        assertEquals(2, workspaceDao.findAll().size());
    }

    @Test
    void transactionalBookingAllowsTouchingSlotsAndRollsBackConflicts() throws Exception {
        User user = userDao.save(new User(
                0, "Bob", "bob@deskweave.io", PasswordHasher.hash("pass123"), UserRole.EMPLOYEE, "Design", null));
        Workspace room = workspaceDao.save(new MeetingRoom(
                0, "R-201", "Boardroom", 2, 8, true, true, true));
        LocalDate day = LocalDate.of(2026, 11, 26);

        Booking first = bookingDao.createWithAudit(
                new Booking(0, user.getUserId(), room.getId(),
                        TimeSlot.of(day, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                        4, BookingStatus.CONFIRMED, null, "Morning sync", null),
                user.getUserId(),
                "Booked morning sync");
        assertTrue(first.getBookingId() > 0);
        assertEquals(1, auditLogDao.findByBookingId(first.getBookingId()).size());

        // Touching slot [10:00, 11:00) must succeed without conflict.
        Booking touching = bookingDao.createWithAudit(
                new Booking(0, user.getUserId(), room.getId(),
                        TimeSlot.of(day, LocalTime.of(10, 0), LocalTime.of(11, 0)),
                        3, BookingStatus.CONFIRMED, null, "Next slot", null),
                user.getUserId(),
                "Booked touching slot");
        assertTrue(touching.getBookingId() > first.getBookingId());

        int auditCountBeforeConflict = auditLogDao.findRecent(100).size();

        // Overlapping slot [09:30, 10:30) must throw BookingConflictException and leave zero orphan rows.
        Booking overlapping = new Booking(0, user.getUserId(), room.getId(),
                TimeSlot.of(day, LocalTime.of(9, 30), LocalTime.of(10, 30)),
                2, BookingStatus.CONFIRMED, null, "Conflict", null);
        assertThrows(BookingConflictException.class,
                () -> bookingDao.createWithAudit(overlapping, user.getUserId(), "Should rollback"));

        assertEquals(2, bookingDao.findAll().size(), "Conflicting booking must not be inserted");
        assertEquals(auditCountBeforeConflict, auditLogDao.findRecent(100).size(),
                "Rollback must prevent orphan audit rows");
    }

    @Test
    void atomicStatusUpdateAndAutoExpirySweepUpdateOnlyOverdueConfirmedBookings() throws Exception {
        User admin = userDao.save(new User(
                0, "Admin", "admin@deskweave.io", PasswordHasher.hash("admin123"), UserRole.ADMIN, "Ops", null));
        User emp = userDao.save(new User(
                0, "Carol", "carol@deskweave.io", PasswordHasher.hash("pass123"), UserRole.EMPLOYEE, "Data", null));
        Workspace desk1 = workspaceDao.save(new Desk(0, "D-101", "Desk 1", 1, 1, true, true, false));
        Workspace desk2 = workspaceDao.save(new Desk(0, "D-102", "Desk 2", 1, 1, true, true, false));
        Workspace desk3 = workspaceDao.save(new Desk(0, "D-103", "Desk 3", 1, 1, true, true, false));

        LocalDate today = LocalDate.of(2026, 11, 25);

        Booking overdueUnclaimed = bookingDao.createWithAudit(
                new Booking(0, emp.getUserId(), desk1.getId(),
                        TimeSlot.of(today, LocalTime.of(9, 0), LocalTime.of(11, 0)),
                        1, BookingStatus.CONFIRMED, null, "Unclaimed", null),
                emp.getUserId(), "Created");

        Booking overdueCheckedIn = bookingDao.createWithAudit(
                new Booking(0, emp.getUserId(), desk2.getId(),
                        TimeSlot.of(today, LocalTime.of(9, 0), LocalTime.of(11, 0)),
                        1, BookingStatus.CONFIRMED, null, "Will check in", null),
                emp.getUserId(), "Created");
        assertTrue(bookingDao.updateStatusWithAudit(
                overdueCheckedIn.getBookingId(),
                BookingStatus.CHECKED_IN,
                LocalDateTime.of(today, LocalTime.of(9, 5)),
                emp.getUserId(),
                "CHECKED_IN",
                "Checked in on time"));

        Booking futureConfirmed = bookingDao.createWithAudit(
                new Booking(0, emp.getUserId(), desk3.getId(),
                        TimeSlot.of(today, LocalTime.of(14, 0), LocalTime.of(16, 0)),
                        1, BookingStatus.CONFIRMED, null, "Afternoon", null),
                emp.getUserId(), "Created");

        // Cutoff at 10:30 expires start_time <= 10:30 for CONFIRMED only.
        List<Booking> expired = bookingDao.expireOverdueBookings(
                LocalDateTime.of(today, LocalTime.of(10, 30)),
                admin.getUserId());

        assertEquals(1, expired.size());
        assertEquals(overdueUnclaimed.getBookingId(), expired.get(0).getBookingId());
        assertEquals(BookingStatus.EXPIRED,
                bookingDao.findById(overdueUnclaimed.getBookingId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.CHECKED_IN,
                bookingDao.findById(overdueCheckedIn.getBookingId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.CONFIRMED,
                bookingDao.findById(futureConfirmed.getBookingId()).orElseThrow().getStatus());

        List<AuditLogEntry> logs = auditLogDao.findByBookingId(overdueUnclaimed.getBookingId());
        assertEquals(2, logs.size());
        assertEquals("EXPIRED", logs.get(1).getAction());
    }

    @Test
    void analyticsQueriesAggregateHoursNoShowsMismatchesAndRecommendations() throws InvalidTimeSlotException {
        User emp = userDao.save(new User(
                0, "Dave", "dave@deskweave.io", PasswordHasher.hash("pass123"), UserRole.EMPLOYEE, "QA", null));
        Workspace desk = workspaceDao.save(new Desk(0, "D-304", "Quiet Desk", 3, 1, true, false, false));
        Workspace room = workspaceDao.save(new MeetingRoom(0, "R-201", "Boardroom", 2, 10, true, true, true));

        LocalDate d1 = LocalDate.of(2026, 11, 20);
        LocalDate d2 = LocalDate.of(2026, 11, 21);

        // Room booked twice by 2 attendees (100% small-group mismatch)
        bookingDao.save(new Booking(0, emp.getUserId(), room.getId(),
                TimeSlot.of(d1, LocalTime.of(10, 0), LocalTime.of(12, 0)),
                2, BookingStatus.COMPLETED, LocalDateTime.of(d1, LocalTime.of(10, 3)), "", null));
        bookingDao.save(new Booking(0, emp.getUserId(), room.getId(),
                TimeSlot.of(d2, LocalTime.of(14, 0), LocalTime.of(15, 30)),
                1, BookingStatus.CHECKED_IN, LocalDateTime.of(d2, LocalTime.of(14, 2)), "", null));

        // Desk expired once (100% no-show, 0 valid booked hours)
        bookingDao.save(new Booking(0, emp.getUserId(), desk.getId(),
                TimeSlot.of(d1, LocalTime.of(9, 0), LocalTime.of(11, 0)),
                1, BookingStatus.EXPIRED, null, "", null));

        List<CapacityAnalyticsDao.WorkspaceUsageStat> usage = analyticsDao.fetchWorkspaceBookedHours(d1, d2);
        assertEquals(2, usage.size());
        CapacityAnalyticsDao.WorkspaceUsageStat roomStat = usage.stream()
                .filter(s -> s.code().equals("R-201")).findFirst().orElseThrow();
        CapacityAnalyticsDao.WorkspaceUsageStat deskStat = usage.stream()
                .filter(s -> s.code().equals("D-304")).findFirst().orElseThrow();
        assertEquals(3.5, roomStat.bookedHours(), 1e-6);
        assertEquals(0.0, deskStat.bookedHours(), 1e-6);

        List<CapacityAnalyticsDao.NoShowStat> noShows = analyticsDao.fetchNoShowStatsByFloorAndType(d1, d2);
        assertEquals(2, noShows.size());

        List<CapacityAnalyticsDao.SizeMismatchStat> mismatches =
                analyticsDao.fetchLargeRoomMismatchStats(d1, d2, 6, 2);
        assertEquals(1, mismatches.size());
        assertEquals(2, mismatches.get(0).totalBookings());
        assertEquals(2, mismatches.get(0).smallGroupBookings());

        analyticsDao.saveRecommendations(List.of(new CapacityRecommendation(
                0, LocalDateTime.now(), RecommendationCategory.SIZE_MISMATCH, Severity.MEDIUM,
                2, WorkspaceType.ROOM, 1.0, "Boardroom under-filled", "Split into focus pods"
        )));
        List<CapacityRecommendation> recs = analyticsDao.findLatestRecommendations(10);
        assertEquals(1, recs.size());
        assertEquals(RecommendationCategory.SIZE_MISMATCH, recs.get(0).getCategory());
    }
}
