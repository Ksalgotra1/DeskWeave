package com.deskweave.service.rules;

import com.deskweave.dao.CapacityAnalyticsDao;
import com.deskweave.model.CapacityRecommendation;
import com.deskweave.model.RecommendationCategory;
import com.deskweave.model.Severity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Flags floors/types where unclaimed expired bookings exceed {@code capacity.noshow.threshold}.
 */
public class NoShowWasteRule implements CapacityRule {

    private static final int MIN_SAMPLE_BOOKINGS = 3;

    @Override
    public String getRuleName() {
        return "No-Show Waste Detector";
    }

    @Override
    public List<CapacityRecommendation> evaluate(CapacityAnalysisContext context) {
        double threshold = context.config().getCapacityNoShowThreshold();
        List<CapacityRecommendation> recommendations = new ArrayList<>();

        for (CapacityAnalyticsDao.NoShowStat stat : context.noShowStats()) {
            int total = stat.checkedInCount() + stat.expiredCount();
            if (total < MIN_SAMPLE_BOOKINGS) {
                continue;
            }
            double noShowRate = (double) stat.expiredCount() / total;
            if (noShowRate > threshold) {
                int pct = (int) Math.round(noShowRate * 100.0);
                Severity severity = noShowRate >= 0.30 ? Severity.HIGH : Severity.MEDIUM;
                recommendations.add(new CapacityRecommendation(
                        0,
                        LocalDateTime.now(),
                        RecommendationCategory.NO_SHOW,
                        severity,
                        stat.floor(),
                        stat.type(),
                        noShowRate,
                        "Floor " + stat.floor() + " " + stat.type() + " had " + pct + "% unclaimed no-shows ("
                                + stat.expiredCount() + " of " + total + " bookings expired)",
                        "Shorten check-in grace window to 10 min or enable automated reminder notifications"
                ));
            }
        }
        return recommendations;
    }
}
