package com.deskweave.service.rules;

import com.deskweave.dao.CapacityAnalyticsDao;
import com.deskweave.model.CapacityRecommendation;
import com.deskweave.model.RecommendationCategory;
import com.deskweave.model.Severity;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Identifies active desks/rooms whose utilization falls below {@code capacity.low.threshold}.
 */
public class UnderutilizedSpaceRule implements CapacityRule {

    @Override
    public String getRuleName() {
        return "Underutilized Space Detector";
    }

    @Override
    public List<CapacityRecommendation> evaluate(CapacityAnalysisContext context) {
        long workingDays = countWorkingDays(context.fromDate(), context.toDate());
        double officeHoursPerDay = Duration.between(
                context.config().getOfficeOpen(),
                context.config().getOfficeClose()).toMinutes() / 60.0;
        double totalAvailableHours = Math.max(1.0, workingDays * officeHoursPerDay);
        double lowThreshold = context.config().getCapacityLowThreshold();

        List<CapacityRecommendation> recommendations = new ArrayList<>();
        for (CapacityAnalyticsDao.WorkspaceUsageStat stat : context.usageStats()) {
            double utilization = stat.bookedHours() / totalAvailableHours;
            if (utilization < lowThreshold) {
                int pct = (int) Math.round(utilization * 100.0);
                Severity severity = utilization < 0.15 ? Severity.MEDIUM : Severity.LOW;
                recommendations.add(new CapacityRecommendation(
                        0,
                        LocalDateTime.now(),
                        RecommendationCategory.UNDERUTILIZED,
                        severity,
                        stat.floor(),
                        stat.type(),
                        utilization,
                        stat.code() + " (" + stat.name() + ") averaged " + pct
                                + "% utilization (" + String.format("%.1f", stat.bookedHours()) + "h booked)",
                        "Reallocate or convert " + stat.code() + " on Floor " + stat.floor()
                                + " into higher-demand seating"
                ));
            }
        }
        return recommendations;
    }

    public static long countWorkingDays(LocalDate from, LocalDate to) {
        long count = 0;
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            DayOfWeek dow = cursor.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return Math.max(1L, count);
    }
}
