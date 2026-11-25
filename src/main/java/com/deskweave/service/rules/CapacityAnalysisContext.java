package com.deskweave.service.rules;

import com.deskweave.config.AppConfig;
import com.deskweave.dao.CapacityAnalyticsDao;
import com.deskweave.engine.IntervalPartitioningEngine;
import com.deskweave.model.Booking;
import com.deskweave.model.Workspace;

import java.time.LocalDate;
import java.util.List;

/**
 * Shared immutable snapshot of mined history passed to each {@link CapacityRule}.
 */
public record CapacityAnalysisContext(
        LocalDate fromDate,
        LocalDate toDate,
        AppConfig config,
        List<Workspace> activeWorkspaces,
        List<Booking> bookingsInRange,
        List<CapacityAnalyticsDao.WorkspaceUsageStat> usageStats,
        List<CapacityAnalyticsDao.NoShowStat> noShowStats,
        List<CapacityAnalyticsDao.SizeMismatchStat> mismatchStats,
        IntervalPartitioningEngine partitioningEngine
) {
}
