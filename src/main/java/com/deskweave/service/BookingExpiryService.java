package com.deskweave.service;

import com.deskweave.config.AppConfig;
import com.deskweave.config.AppLogger;
import com.deskweave.dao.BookingDao;
import com.deskweave.model.Booking;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Periodically expires unclaimed {@code CONFIRMED} bookings past the check-in grace window.
 */
public class BookingExpiryService {

    private static final Logger LOGGER = AppLogger.getLogger(BookingExpiryService.class);
    private static final int MAX_FEED_ENTRIES = 50;
    private static final DateTimeFormatter FEED_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final BookingDao bookingDao;
    private final AppConfig config;
    private final LinkedList<String> recentExpiryFeed = new LinkedList<>();
    private volatile long simulatedMinutesOffset;

    public BookingExpiryService(BookingDao bookingDao, AppConfig config) {
        this.bookingDao = Objects.requireNonNull(bookingDao, "bookingDao must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public void setSimulatedMinutesOffset(long minutes) {
        this.simulatedMinutesOffset = minutes;
    }

    public long getSimulatedMinutesOffset() {
        return simulatedMinutesOffset;
    }

    public LocalDateTime getEffectiveNow() {
        return LocalDateTime.now().plusMinutes(simulatedMinutesOffset);
    }

    /**
     * Runs an auto-expiry sweep using the current (or demo-offset) clock.
     */
    public List<Booking> runExpirySweep(int systemActorUserId) {
        return runExpirySweepAt(getEffectiveNow(), systemActorUserId);
    }

    /**
     * Expires overdue {@code CONFIRMED} bookings and marks finished {@code CHECKED_IN} bookings as {@code COMPLETED}.
     */
    public List<Booking> runExpirySweepAt(LocalDateTime referenceNow, int systemActorUserId) {
        Objects.requireNonNull(referenceNow, "referenceNow must not be null");
        LocalDateTime cutoff = referenceNow.minusMinutes(config.getExpiryGraceMinutes());
        List<Booking> expired = bookingDao.expireOverdueBookings(cutoff, systemActorUserId);
        List<Booking> completed = bookingDao.completeFinishedBookings(referenceNow, systemActorUserId);

        String timeStamp = referenceNow.format(FEED_TIME_FMT);
        recordFeedEntries(expired, timeStamp, "Expired");
        recordFeedEntries(completed, timeStamp, "Completed");
        return expired;
    }

    private void recordFeedEntries(List<Booking> bookings, String timeStamp, String verb) {
        if (bookings.isEmpty()) {
            return;
        }
        synchronized (recentExpiryFeed) {
            for (Booking booking : bookings) {
                String msg = "[" + timeStamp + "] " + verb + " booking #" + booking.getBookingId()
                        + " (workspace #" + booking.getWorkspaceId() + ", " + booking.getSlot() + ")";
                recentExpiryFeed.addFirst(msg);
                while (recentExpiryFeed.size() > MAX_FEED_ENTRIES) {
                    recentExpiryFeed.removeLast();
                }
                LOGGER.info(msg);
            }
        }
    }

    public List<String> getRecentExpiryFeed() {
        synchronized (recentExpiryFeed) {
            return List.copyOf(recentExpiryFeed);
        }
    }
}
