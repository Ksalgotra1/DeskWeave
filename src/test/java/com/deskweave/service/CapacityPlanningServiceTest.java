package com.deskweave.service;

import com.deskweave.config.AppConfig;
import com.deskweave.config.PasswordHasher;
import com.deskweave.dao.CapacityAnalyticsDao;
import com.deskweave.dao.DatabaseManager;
import com.deskweave.dao.PostgresAuditLogDao;
import com.deskweave.dao.PostgresBookingDao;
import com.deskweave.dao.PostgresUserDao;
import com.deskweave.dao.PostgresWorkspaceDao;
import com.deskweave.engine.IntervalPartitioningEngine;
import com.deskweave.model.Booking;
import com.deskweave.model.BookingStatus;
import com.deskweave.model.CapacityRecommendation;
import com.deskweave.model.Desk;
import com.deskweave.model.MeetingRoom;
import com.deskweave.model.RecommendationCategory;
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
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapacityPlanningServiceTest {

    private PostgresBookingDao bookingDao;
    private CapacityAnalyticsDao analyticsDao;
    private CapacityPlanningService planningService;

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:cap_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        DatabaseManager db = new DatabaseManager(url, "sa", "");
        db.executeSqlScript(Path.of("sql/schema.sql"));

        PostgresUserDao userDao = new PostgresUserDao(db);
        PostgresWorkspaceDao workspaceDao = new PostgresWorkspaceDao(db);
        PostgresAuditLogDao auditLogDao = new PostgresAuditLogDao(db);
        bookingDao = new PostgresBookingDao(db, auditLogDao);
        analyticsDao = new CapacityAnalyticsDao(db);

        planningService = new CapacityPlanningService(
                workspaceDao,
                bookingDao,
                analyticsDao,
                new IntervalPartitioningEngine(),
                AppConfig.loadDefault()
        );

        User emp1 = userDao.save(new User(0, "Alice", "alice@deskweave.io",
                PasswordHasher.hash("pass123"), UserRole.EMPLOYEE, "Eng", null));
        User emp2 = userDao.save(new User(0, "Bob", "bob@deskweave.io",
                PasswordHasher.hash("pass123"), UserRole.EMPLOYEE, "Design", null));
        User emp3 = userDao.save(new User(0, "Carol", "carol@deskweave.io",
                PasswordHasher.hash("pass123"), UserRole.EMPLOYEE, "Data", null));

        // Floor 2 rooms (3 rooms)
        Workspace r201 = workspaceDao.save(new MeetingRoom(0, "R-201", "Boardroom", 2, 10, true, true, true));
        Workspace r202 = workspaceDao.save(new MeetingRoom(0, "R-202", "Huddle", 2, 6, true, true, false));
        Workspace r203 = workspaceDao.save(new MeetingRoom(0, "R-203", "Focus", 2, 4, true, false, true));
        // Floor 1 desks
        Workspace d101 = workspaceDao.save(new Desk(0, "D-101", "Desk 1", 1, 1, true, true, false));
        // Floor 3 underutilized desk
        workspaceDao.save(new Desk(0, "D-304", "Quiet Corner", 3, 1, true, false, false));

        LocalDate base = LocalDate.of(2026, 11, 10);
        // 1) Floor 2 peak congestion on 4 days + 2) R-201 booked 75% by 1-2 attendees
        for (int dayOffset = 0; dayOffset < 4; dayOffset++) {
            LocalDate d = base.plusDays(dayOffset);
            int attendees = dayOffset < 3 ? 2 : 8;
            bookingDao.save(new Booking(0, emp1.getUserId(), r201.getId(),
                    TimeSlot.of(d, LocalTime.of(10, 0), LocalTime.of(12, 0)),
                    attendees, BookingStatus.COMPLETED, LocalDateTime.of(d, LocalTime.of(10, 2)), "", null));
            bookingDao.save(new Booking(0, emp2.getUserId(), r202.getId(),
                    TimeSlot.of(d, LocalTime.of(10, 0), LocalTime.of(12, 0)),
                    4, BookingStatus.COMPLETED, LocalDateTime.of(d, LocalTime.of(10, 3)), "", null));
            bookingDao.save(new Booking(0, emp3.getUserId(), r203.getId(),
                    TimeSlot.of(d, LocalTime.of(10, 0), LocalTime.of(12, 0)),
                    3, BookingStatus.COMPLETED, LocalDateTime.of(d, LocalTime.of(10, 1)), "", null));
        }

        // 3) Floor 1 no-show waste: 3 checked-in + 2 expired = 40% (> 20%)
        for (int i = 0; i < 3; i++) {
            LocalDate d = base.plusDays(i);
            bookingDao.save(new Booking(0, emp1.getUserId(), d101.getId(),
                    TimeSlot.of(d, LocalTime.of(9, 0), LocalTime.of(17, 0)),
                    1, BookingStatus.COMPLETED, LocalDateTime.of(d, LocalTime.of(9, 5)), "", null));
        }
        for (int i = 3; i < 5; i++) {
            LocalDate d = base.plusDays(i);
            bookingDao.save(new Booking(0, emp2.getUserId(), d101.getId(),
                    TimeSlot.of(d, LocalTime.of(9, 0), LocalTime.of(13, 0)),
                    1, BookingStatus.EXPIRED, null, "", null));
        }
    }

    @Test
    void evaluatesAllFourCapacityRulesSortsBySeverityAndPersists() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 20);

        CapacityPlanningService.CapacityReport report = planningService.generateReport(from, to, true);

        assertEquals(5, report.totalActiveWorkspaces());
        assertFalse(report.recommendations().isEmpty());

        Set<RecommendationCategory> categories = EnumSet.noneOf(RecommendationCategory.class);
        int lastRank = 0;
        for (CapacityRecommendation rec : report.recommendations()) {
            categories.add(rec.getCategory());
            assertTrue(rec.getSeverity().getPriorityRank() >= lastRank,
                    "Recommendations must be ordered from highest severity to lowest");
            lastRank = rec.getSeverity().getPriorityRank();
        }

        assertTrue(categories.contains(RecommendationCategory.SHORTAGE), "Expected PeakShortageRule");
        assertTrue(categories.contains(RecommendationCategory.UNDERUTILIZED), "Expected UnderutilizedSpaceRule");
        assertTrue(categories.contains(RecommendationCategory.NO_SHOW), "Expected NoShowWasteRule");
        assertTrue(categories.contains(RecommendationCategory.SIZE_MISMATCH), "Expected RoomSizeMismatchRule");

        assertEquals(report.recommendations().size(), analyticsDao.findLatestRecommendations(50).size());
    }
}
