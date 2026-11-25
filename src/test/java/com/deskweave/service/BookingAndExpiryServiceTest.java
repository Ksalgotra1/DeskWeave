package com.deskweave.service;

import com.deskweave.config.AppConfig;
import com.deskweave.dao.DatabaseManager;
import com.deskweave.dao.PostgresAuditLogDao;
import com.deskweave.dao.PostgresBookingDao;
import com.deskweave.dao.PostgresUserDao;
import com.deskweave.dao.PostgresWorkspaceDao;
import com.deskweave.exception.AuthenticationException;
import com.deskweave.exception.BookingConflictException;
import com.deskweave.exception.DeskWeaveException;
import com.deskweave.exception.InsufficientCapacityException;
import com.deskweave.exception.InvalidTimeSlotException;
import com.deskweave.model.Booking;
import com.deskweave.model.BookingStatus;
import com.deskweave.model.Desk;
import com.deskweave.model.TimeSlot;
import com.deskweave.model.User;
import com.deskweave.model.UserRole;
import com.deskweave.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingAndExpiryServiceTest {

    private AuthService authService;
    private WorkspaceService workspaceService;
    private BookingService bookingService;
    private BookingExpiryService expiryService;
    private User admin;
    private User alice;
    private User bob;
    private Workspace desk1;

    @BeforeEach
    void setUp() throws DeskWeaveException {
        String url = "jdbc:h2:mem:svc_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        DatabaseManager db = new DatabaseManager(url, "sa", "");
        db.executeSqlScript(Path.of("sql/schema.sql"));

        AppConfig config = AppConfig.loadDefault();
        PostgresUserDao userDao = new PostgresUserDao(db);
        PostgresWorkspaceDao workspaceDao = new PostgresWorkspaceDao(db);
        PostgresAuditLogDao auditLogDao = new PostgresAuditLogDao(db);
        PostgresBookingDao bookingDao = new PostgresBookingDao(db, auditLogDao);

        authService = new AuthService(userDao);
        workspaceService = new WorkspaceService(workspaceDao);
        bookingService = new BookingService(bookingDao, workspaceDao, config);
        expiryService = new BookingExpiryService(bookingDao, config);

        admin = authService.registerUser("Admin", "admin@deskweave.io", "admin123", UserRole.ADMIN, "Ops");
        alice = authService.registerUser("Alice", "alice@deskweave.io", "pass123", UserRole.EMPLOYEE, "Eng");
        bob = authService.registerUser("Bob", "bob@deskweave.io", "pass123", UserRole.EMPLOYEE, "Design");

        desk1 = workspaceService.createWorkspace(
                new Desk(0, "D-101", "Window Desk 1", 1, 1, true, true, false));
    }

    @Test
    void authRejectsBadPasswordAndDuplicateEmail() throws AuthenticationException {
        User signedIn = authService.authenticate("ALICE@deskweave.io", "pass123");
        assertEquals(alice.getUserId(), signedIn.getUserId());

        assertThrows(AuthenticationException.class,
                () -> authService.authenticate("alice@deskweave.io", "wrongpass"));
        assertThrows(DeskWeaveException.class,
                () -> authService.registerUser("Alice 2", "alice@deskweave.io", "pass123", UserRole.EMPLOYEE, "Eng"));
    }

    @Test
    void bookingValidationCheckInAndWaitlistPromotionWork() throws Exception {
        LocalDate day = LocalDate.now().plusDays(1);

        // Outside office hours (08:00-20:00)
        TimeSlot tooEarly = TimeSlot.of(day, LocalTime.of(7, 0), LocalTime.of(8, 30));
        assertThrows(InvalidTimeSlotException.class,
                () -> bookingService.createBooking(alice.getUserId(), desk1.getId(), tooEarly, 1, "Early"));

        // Exceeds capacity (desk capacity = 1)
        TimeSlot validSlot = TimeSlot.of(day, LocalTime.of(9, 0), LocalTime.of(11, 0));
        assertThrows(InsufficientCapacityException.class,
                () -> bookingService.createBooking(alice.getUserId(), desk1.getId(), validSlot, 2, "Pair"));

        Booking aliceBooking = bookingService.createBooking(alice.getUserId(), desk1.getId(), validSlot, 1, "Focus");

        // Conflict throws BookingConflictException
        assertThrows(BookingConflictException.class,
                () -> bookingService.createBooking(bob.getUserId(), desk1.getId(), validSlot, 1, "Want slot"));

        // Bob joins waitlist for exact slot
        bookingService.joinWaitlist(new BookingService.WaitlistEntry(
                bob.getUserId(), bob.getFullName(), desk1.getId(), validSlot, 1, "Waiting"));
        assertEquals(1, bookingService.getWaitlistSnapshot().size());

        // Too-early check-in fails (09:00 - 15m = 08:45; 08:40 is too early)
        assertThrows(InvalidTimeSlotException.class, () -> bookingService.checkIn(
                aliceBooking.getBookingId(), alice, LocalDateTime.of(day, LocalTime.of(8, 40))));

        // Past-grace check-in fails (09:00 + 15m = 09:15; 09:20 is too late)
        assertThrows(InvalidTimeSlotException.class, () -> bookingService.checkIn(
                aliceBooking.getBookingId(), alice, LocalDateTime.of(day, LocalTime.of(9, 20))));

        // Alice cancels pre-start -> Bob is auto-promoted from LinkedList waitlist!
        Optional<Booking> promoted = bookingService.cancelBooking(
                aliceBooking.getBookingId(), alice, LocalDateTime.of(day, LocalTime.of(8, 0)), null);
        assertTrue(promoted.isPresent());
        assertEquals(bob.getUserId(), promoted.get().getUserId());
        assertEquals(BookingStatus.CONFIRMED, promoted.get().getStatus());
        assertTrue(bookingService.getWaitlistSnapshot().isEmpty());

        // Bob checks in within early window (08:50 is within [08:45, 09:15])
        Booking checkedIn = bookingService.checkIn(
                promoted.get().getBookingId(), bob, LocalDateTime.of(day, LocalTime.of(8, 50)));
        assertEquals(BookingStatus.CHECKED_IN, checkedIn.getStatus());

        // Bob cannot self-cancel after check-in; Admin can override with audit reason
        assertThrows(DeskWeaveException.class, () -> bookingService.cancelBooking(
                checkedIn.getBookingId(), bob, LocalDateTime.of(day, LocalTime.of(8, 55)), null));
        bookingService.cancelBooking(
                checkedIn.getBookingId(), admin, LocalDateTime.of(day, LocalTime.of(9, 10)), "Room maintenance");
        assertEquals(BookingStatus.CANCELLED,
                bookingService.findBookingsForUser(bob.getUserId()).get(0).getStatus());
    }

    @Test
    void expirySweepExpiresOverdueBookingsAndSupportsSimulatedDemoClock() throws Exception {
        LocalDate today = LocalDate.now();
        TimeSlot morningSlot = TimeSlot.of(today, LocalTime.of(9, 0), LocalTime.of(11, 0));
        Booking b = bookingService.createBooking(alice.getUserId(), desk1.getId(), morningSlot, 1, "Demo");

        // Before grace window expires -> 0 expired
        List<Booking> noneExpired = expiryService.runExpirySweepAt(
                LocalDateTime.of(today, LocalTime.of(9, 10)), admin.getUserId());
        assertTrue(noneExpired.isEmpty());

        // After 15m grace window -> expires cleanly and adds to feed
        List<Booking> swept = expiryService.runExpirySweepAt(
                LocalDateTime.of(today, LocalTime.of(9, 20)), admin.getUserId());
        assertEquals(1, swept.size());
        assertEquals(b.getBookingId(), swept.get(0).getBookingId());
        assertEquals(1, expiryService.getRecentExpiryFeed().size());

        // Re-create slot and verify simulated clock offset on runExpirySweep()
        TimeSlot laterSlot = TimeSlot.of(today, LocalTime.of(14, 0), LocalTime.of(15, 0));
        bookingService.createBooking(bob.getUserId(), desk1.getId(), laterSlot, 1, "Sim");
        expiryService.setSimulatedMinutesOffset(24 * 60L); // +1 day ahead
        List<Booking> offsetExpired = expiryService.runExpirySweep(admin.getUserId());
        assertEquals(1, offsetExpired.size());
        assertEquals(2, expiryService.getRecentExpiryFeed().size());
    }

    @Test
    void sweepUpgradesFinishedCheckedInBookingsToCompletedWhileLeavingCancelledUntouched() throws Exception {
        LocalDate today = LocalDate.now();
        Booking checkedInBooking = bookingService.createBooking(
                alice.getUserId(), desk1.getId(),
                TimeSlot.of(today, LocalTime.of(9, 0), LocalTime.of(11, 0)),
                1, "Checked in session");
        bookingService.checkIn(checkedInBooking.getBookingId(), alice, LocalDateTime.of(today, LocalTime.of(9, 5)));

        Booking cancelledBooking = bookingService.createBooking(
                bob.getUserId(), desk1.getId(),
                TimeSlot.of(today, LocalTime.of(12, 0), LocalTime.of(13, 0)),
                1, "Cancelled session");
        bookingService.cancelBooking(
                cancelledBooking.getBookingId(), bob, LocalDateTime.of(today, LocalTime.of(11, 30)), null);

        // Employee cannot cancel after start time
        Booking startedBooking = bookingService.createBooking(
                bob.getUserId(), desk1.getId(),
                TimeSlot.of(today, LocalTime.of(14, 0), LocalTime.of(15, 0)),
                1, "Started session");
        assertThrows(InvalidTimeSlotException.class, () -> bookingService.cancelBooking(
                startedBooking.getBookingId(), bob, LocalDateTime.of(today, LocalTime.of(14, 5)), null));

        // At 10:30, checked-in booking is still active -> remains CHECKED_IN
        expiryService.runExpirySweepAt(LocalDateTime.of(today, LocalTime.of(10, 30)), admin.getUserId());
        assertEquals(BookingStatus.CHECKED_IN,
                bookingService.findBookingsForUser(alice.getUserId()).get(0).getStatus());

        // At 11:05 (past end_time 11:00), checked-in booking upgrades to COMPLETED; cancelled stays CANCELLED
        expiryService.runExpirySweepAt(LocalDateTime.of(today, LocalTime.of(11, 5)), admin.getUserId());
        assertEquals(BookingStatus.COMPLETED,
                bookingService.findBookingsForUser(alice.getUserId()).get(0).getStatus());
        assertEquals(BookingStatus.CANCELLED,
                bookingService.findBookingsForUser(bob.getUserId()).get(0).getStatus());
    }
}
