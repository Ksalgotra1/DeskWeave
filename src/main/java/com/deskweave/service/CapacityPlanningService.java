package com.deskweave.service;

import com.deskweave.config.AppConfig;
import com.deskweave.config.AppLogger;
import com.deskweave.dao.BookingDao;
import com.deskweave.dao.CapacityAnalyticsDao;
import com.deskweave.dao.WorkspaceDao;
import com.deskweave.engine.IntervalPartitioningEngine;
import com.deskweave.model.Booking;
import com.deskweave.model.CapacityRecommendation;
import com.deskweave.model.Workspace;
import com.deskweave.service.rules.CapacityAnalysisContext;
import com.deskweave.service.rules.CapacityRule;
import com.deskweave.service.rules.NoShowWasteRule;
import com.deskweave.service.rules.PeakShortageRule;
import com.deskweave.service.rules.RoomSizeMismatchRule;
import com.deskweave.service.rules.UnderutilizedSpaceRule;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Orchestrates historical capacity mining across all registered {@link CapacityRule} strategies.
 */
public class CapacityPlanningService {

    private static final Logger LOGGER = AppLogger.getLogger(CapacityPlanningService.class);
    private static final int LARGE_ROOM_MIN_CAPACITY = 6;
    private static final int SMALL_GROUP_MAX_ATTENDEES = 2;

    private static final Comparator<CapacityRecommendation> RECOMMENDATION_ORDER = Comparator
            .comparingInt((CapacityRecommendation r) -> r.getSeverity().getPriorityRank())
            .thenComparing(Comparator.comparingDouble(CapacityRecommendation::getMetricValue).reversed());

    public record CapacityReport(
            LocalDate fromDate,
            LocalDate toDate,
            int totalActiveWorkspaces,
            int totalBookingsAnalyzed,
            double averageUtilizationPct,
            double overallNoShowPct,
            List<CapacityRecommendation> recommendations
    ) {
    }

    private final WorkspaceDao workspaceDao;
    private final BookingDao bookingDao;
    private final CapacityAnalyticsDao analyticsDao;
    private final IntervalPartitioningEngine partitioningEngine;
    private final AppConfig config;
    private final List<CapacityRule> rules;

    public CapacityPlanningService(WorkspaceDao workspaceDao,
                                   BookingDao bookingDao,
                                   CapacityAnalyticsDao analyticsDao,
                                   IntervalPartitioningEngine partitioningEngine,
                                   AppConfig config) {
        this(workspaceDao, bookingDao, analyticsDao, partitioningEngine, config, List.of(
                new PeakShortageRule(),
                new UnderutilizedSpaceRule(),
                new NoShowWasteRule(),
                new RoomSizeMismatchRule()
        ));
    }

    public CapacityPlanningService(WorkspaceDao workspaceDao,
                                   BookingDao bookingDao,
                                   CapacityAnalyticsDao analyticsDao,
                                   IntervalPartitioningEngine partitioningEngine,
                                   AppConfig config,
                                   List<CapacityRule> rules) {
        this.workspaceDao = Objects.requireNonNull(workspaceDao, "workspaceDao must not be null");
        this.bookingDao = Objects.requireNonNull(bookingDao, "bookingDao must not be null");
        this.analyticsDao = Objects.requireNonNull(analyticsDao, "analyticsDao must not be null");
        this.partitioningEngine = Objects.requireNonNull(partitioningEngine, "partitioningEngine must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules must not be null"));
    }

    public CapacityReport generateReport(int lookbackDays, boolean persist) {
        int safeDays = Math.max(1, lookbackDays);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(safeDays);
        return generateReport(from, to, persist);
    }

    /**
     * Evaluates all capacity rules across {@code [from, to]} and sorts recommendations by severity.
     */
    public CapacityReport generateReport(LocalDate from, LocalDate to, boolean persist) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to date must not be before from date");
        }

        List<Workspace> activeWorkspaces = workspaceDao.findActive();
        List<Booking> bookingsInRange = bookingDao.findByDateRange(from, to);
        List<CapacityAnalyticsDao.WorkspaceUsageStat> usageStats = analyticsDao.fetchWorkspaceBookedHours(from, to);
        List<CapacityAnalyticsDao.NoShowStat> noShowStats = analyticsDao.fetchNoShowStatsByFloorAndType(from, to);
        List<CapacityAnalyticsDao.SizeMismatchStat> mismatchStats = analyticsDao.fetchLargeRoomMismatchStats(
                from, to, LARGE_ROOM_MIN_CAPACITY, SMALL_GROUP_MAX_ATTENDEES);

        CapacityAnalysisContext context = new CapacityAnalysisContext(
                from, to, config, activeWorkspaces, bookingsInRange,
                usageStats, noShowStats, mismatchStats, partitioningEngine);

        List<CapacityRecommendation> recommendations = new ArrayList<>();
        for (CapacityRule rule : rules) {
            recommendations.addAll(rule.evaluate(context));
        }
        recommendations.sort(RECOMMENDATION_ORDER);

        if (persist && !recommendations.isEmpty()) {
            analyticsDao.saveRecommendations(recommendations);
        }

        double avgUtilizationPct = computeAverageUtilizationPct(from, to, usageStats);
        double overallNoShowPct = computeOverallNoShowPct(noShowStats);

        LOGGER.info("Generated capacity report (" + from + " to " + to + "): "
                + recommendations.size() + " recommendation(s), avgUtil="
                + String.format("%.1f%%", avgUtilizationPct) + ", noShow="
                + String.format("%.1f%%", overallNoShowPct));

        return new CapacityReport(
                from,
                to,
                activeWorkspaces.size(),
                bookingsInRange.size(),
                avgUtilizationPct,
                overallNoShowPct,
                List.copyOf(recommendations)
        );
    }

    private double computeAverageUtilizationPct(LocalDate from,
                                                LocalDate to,
                                                List<CapacityAnalyticsDao.WorkspaceUsageStat> usageStats) {
        if (usageStats.isEmpty()) {
            return 0.0;
        }
        long workingDays = UnderutilizedSpaceRule.countWorkingDays(from, to);
        double officeHoursPerDay = Duration.between(config.getOfficeOpen(), config.getOfficeClose()).toMinutes() / 60.0;
        double availablePerSpace = Math.max(1.0, workingDays * officeHoursPerDay);
        double sum = 0.0;
        for (CapacityAnalyticsDao.WorkspaceUsageStat stat : usageStats) {
            sum += (stat.bookedHours() / availablePerSpace) * 100.0;
        }
        return sum / usageStats.size();
    }

    private static double computeOverallNoShowPct(List<CapacityAnalyticsDao.NoShowStat> noShowStats) {
        int checkedIn = 0;
        int expired = 0;
        for (CapacityAnalyticsDao.NoShowStat stat : noShowStats) {
            checkedIn += stat.checkedInCount();
            expired += stat.expiredCount();
        }
        int total = checkedIn + expired;
        return total == 0 ? 0.0 : ((double) expired / total) * 100.0;
    }
}
