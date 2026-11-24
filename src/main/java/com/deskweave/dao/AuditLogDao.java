package com.deskweave.dao;

import com.deskweave.model.AuditLogEntry;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * DAO contract for {@link AuditLogEntry} persistence.
 */
public interface AuditLogDao {

    AuditLogEntry save(AuditLogEntry entry);

    AuditLogEntry insert(Connection conn, AuditLogEntry entry) throws SQLException;

    List<AuditLogEntry> findByBookingId(int bookingId);

    List<AuditLogEntry> findRecent(int limit);
}
