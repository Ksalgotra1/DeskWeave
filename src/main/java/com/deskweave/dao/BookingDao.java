package com.deskweave.dao;

import com.deskweave.exception.BookingConflictException;
import com.deskweave.model.Booking;
import com.deskweave.model.BookingStatus;
import com.deskweave.model.TimeSlot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DAO contract for {@link Booking} persistence and transactional lifecycle operations.
 */
public interface BookingDao extends GenericDao<Booking, Integer> {

    Booking createWithAudit(Booking booking, int actorUserId, String auditDetails) throws BookingConflictException;

    boolean updateStatusWithAudit(int bookingId,
                                  BookingStatus newStatus,
                                  LocalDateTime checkedInAt,
                                  int actorUserId,
                                  String auditAction,
                                  String details);

    List<Booking> expireOverdueBookings(LocalDateTime cutoffDateTime, int systemActorUserId);

    boolean hasOverlappingActiveBooking(int workspaceId, TimeSlot slot);

    List<Booking> findByUserId(int userId);

    List<Booking> findByDateRange(LocalDate from, LocalDate to);

    List<Booking> findActiveByWorkspaceAndDate(int workspaceId, LocalDate date);
}
