package com.deskweave.service;

import com.deskweave.config.AppConfig;
import com.deskweave.config.AppLogger;
import com.deskweave.dao.BookingDao;
import com.deskweave.dao.WorkspaceDao;
import com.deskweave.exception.AuthenticationException;
import com.deskweave.exception.BookingConflictException;
import com.deskweave.exception.DeskWeaveException;
import com.deskweave.exception.InsufficientCapacityException;
import com.deskweave.exception.InvalidTimeSlotException;
import com.deskweave.exception.ResourceNotFoundException;
import com.deskweave.model.Booking;
import com.deskweave.model.BookingStatus;
import com.deskweave.model.TimeSlot;
import com.deskweave.model.User;
import com.deskweave.model.Workspace;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Coordinates booking validation, transactional creation, check-in, cancellation, and FIFO waitlist promotion.
 */
public class BookingService {

    private static final Logger LOGGER = AppLogger.getLogger(BookingService.class);

    public record WaitlistEntry(
            int userId,
            String requesterName,
            int workspaceId,
            TimeSlot slot,
            int attendeeCount,
            String notes
    ) {
    }

    private final BookingDao bookingDao;
    private final WorkspaceDao workspaceDao;
    private final AppConfig config;
    private final LinkedList<WaitlistEntry> waitlist = new LinkedList<>();

    public BookingService(BookingDao bookingDao, WorkspaceDao workspaceDao, AppConfig config) {
        this.bookingDao = Objects.requireNonNull(bookingDao, "bookingDao must not be null");
        this.workspaceDao = Objects.requireNonNull(workspaceDao, "workspaceDao must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    /**
     * Validates office hours, capacity, and overlap constraints before persisting a booking atomically.
     */
    public Booking createBooking(int userId,
                                 int workspaceId,
                                 TimeSlot slot,
                                 int attendeeCount,
                                 String notes) throws DeskWeaveException {
        Objects.requireNonNull(slot, "slot must not be null");
        validateWithinOfficeHours(slot);

        Workspace workspace = workspaceDao.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
        if (!workspace.isActive()) {
            throw new DeskWeaveException("Workspace " + workspace.getCode() + " is currently inactive");
        }
        if (attendeeCount < 1 || attendeeCount > workspace.getCapacity()) {
            throw new InsufficientCapacityException(
                    "Workspace " + workspace.getCode() + " supports up to "
                            + workspace.getCapacity() + " attendee(s), requested " + attendeeCount,
                    attendeeCount,
                    workspace.getCapacity());
        }

        Booking draft = new Booking(
                0,
                userId,
                workspaceId,
                slot,
                attendeeCount,
                BookingStatus.CONFIRMED,
                null,
                notes,
                LocalDateTime.now()
        );
        Booking created = bookingDao.createWithAudit(draft, userId, "Reserved " + workspace.getCode());
        LOGGER.info("Created booking#" + created.getBookingId() + " for workspace "
                + workspace.getCode() + " slot=" + slot);
        return created;
    }

    public Booking checkInNow(int bookingId, User actor) throws DeskWeaveException {
        return checkIn(bookingId, actor, LocalDateTime.now());
    }

    /**
     * Marks a confirmed booking as checked in if the actor is authorized and within the grace window.
     */
    public Booking checkIn(int bookingId, User actor, LocalDateTime now) throws DeskWeaveException {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(now, "now must not be null");

        Booking booking = bookingDao.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (!actor.isAdmin() && booking.getUserId() != actor.getUserId()) {
            throw new AuthenticationException("You are not authorized to check in for booking #" + bookingId);
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new DeskWeaveException("Cannot check in: booking #" + bookingId
                    + " is currently " + booking.getStatus());
        }

        LocalDateTime graceDeadline = LocalDateTime.of(booking.getSlot().getDate(), booking.getSlot().getStartTime())
                .plusMinutes(config.getExpiryGraceMinutes());
        if (now.isAfter(graceDeadline)) {
            throw new InvalidTimeSlotException(
                    "Check-in grace window closed at " + graceDeadline.toLocalTime(),
                    booking.getSlot().getStartTime(),
                    booking.getSlot().getEndTime());
        }

        bookingDao.updateStatusWithAudit(
                bookingId,
                BookingStatus.CHECKED_IN,
                now,
                actor.getUserId(),
                "CHECKED_IN",
                "Checked in by " + actor.getFullName());

        LOGGER.info("Checked in booking#" + bookingId + " by user " + actor.getEmail());
        return bookingDao.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
    }

    /**
     * Cancels an active booking and promotes the first compatible entry from the FIFO waitlist.
     */
    public Optional<Booking> cancelBooking(int bookingId, User actor) throws DeskWeaveException {
        Objects.requireNonNull(actor, "actor must not be null");
        Booking booking = bookingDao.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (!actor.isAdmin() && booking.getUserId() != actor.getUserId()) {
            throw new AuthenticationException("You are not authorized to cancel booking #" + bookingId);
        }
        if (!booking.getStatus().isActiveSlot()) {
            throw new DeskWeaveException("Cannot cancel booking #" + bookingId
                    + " with status " + booking.getStatus());
        }

        bookingDao.updateStatusWithAudit(
                bookingId,
                BookingStatus.CANCELLED,
                null,
                actor.getUserId(),
                "CANCELLED",
                "Cancelled by " + actor.getFullName());

        LOGGER.info("Cancelled booking#" + bookingId + " by " + actor.getEmail());
        return promoteNextWaitlistMatch(booking.getWorkspaceId());
    }

    public synchronized void joinWaitlist(WaitlistEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        waitlist.addLast(entry);
        LOGGER.info("Added waitlist entry for userId=" + entry.userId()
                + " ws=" + entry.workspaceId() + " slot=" + entry.slot());
    }

    public synchronized List<WaitlistEntry> getWaitlistSnapshot() {
        return List.copyOf(waitlist);
    }

    public List<Booking> findBookingsForUser(int userId) {
        return bookingDao.findByUserId(userId);
    }

    public List<Booking> findAllBookings() {
        return bookingDao.findAll();
    }

    public List<Booking> findBookingsInRange(LocalDate from, LocalDate to) {
        return bookingDao.findByDateRange(from, to);
    }

    public List<Booking> findActiveForWorkspaceOnDate(int workspaceId, LocalDate date) {
        return bookingDao.findActiveByWorkspaceAndDate(workspaceId, date);
    }

    private synchronized Optional<Booking> promoteNextWaitlistMatch(int workspaceId) {
        Iterator<WaitlistEntry> iterator = waitlist.iterator();
        while (iterator.hasNext()) {
            WaitlistEntry candidate = iterator.next();
            if (candidate.workspaceId() != workspaceId) {
                continue;
            }
            if (bookingDao.hasOverlappingActiveBooking(workspaceId, candidate.slot())) {
                continue;
            }
            try {
                Booking promoted = createBooking(
                        candidate.userId(),
                        candidate.workspaceId(),
                        candidate.slot(),
                        candidate.attendeeCount(),
                        candidate.notes() + " [Waitlist Promoted]");
                iterator.remove();
                LOGGER.info("Promoted waitlist entry for " + candidate.requesterName()
                        + " into booking#" + promoted.getBookingId());
                return Optional.of(promoted);
            } catch (BookingConflictException ex) {
                // Another booking claimed the slot concurrently; keep looking.
            } catch (DeskWeaveException ex) {
                iterator.remove();
                LOGGER.warning("Dropped invalid waitlist entry for " + candidate.requesterName()
                        + ": " + ex.getMessage());
            }
        }
        return Optional.empty();
    }

    private void validateWithinOfficeHours(TimeSlot slot) throws InvalidTimeSlotException {
        if (slot.getStartTime().isBefore(config.getOfficeOpen())
                || slot.getEndTime().isAfter(config.getOfficeClose())) {
            throw new InvalidTimeSlotException(
                    "Bookings must fall within office hours ("
                            + config.getOfficeOpen() + " - " + config.getOfficeClose() + ")",
                    slot.getStartTime(),
                    slot.getEndTime());
        }
    }
}
