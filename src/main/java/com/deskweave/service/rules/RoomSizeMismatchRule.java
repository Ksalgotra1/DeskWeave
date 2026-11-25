package com.deskweave.service.rules;

import com.deskweave.dao.CapacityAnalyticsDao;
import com.deskweave.model.CapacityRecommendation;
import com.deskweave.model.RecommendationCategory;
import com.deskweave.model.Severity;
import com.deskweave.model.WorkspaceType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects large meeting rooms frequently booked by 1-2 person groups.
 */
public class RoomSizeMismatchRule implements CapacityRule {

    private static final int MIN_SAMPLE_BOOKINGS = 3;

    @Override
    public String getRuleName() {
        return "Room Size Mismatch Detector";
    }

    @Override
    public List<CapacityRecommendation> evaluate(CapacityAnalysisContext context) {
        double threshold = context.config().getCapacityMismatchThreshold();
        List<CapacityRecommendation> recommendations = new ArrayList<>();

        for (CapacityAnalyticsDao.SizeMismatchStat stat : context.mismatchStats()) {
            if (stat.totalBookings() < MIN_SAMPLE_BOOKINGS) {
                continue;
            }
            double mismatchRate = (double) stat.smallGroupBookings() / stat.totalBookings();
            if (mismatchRate > threshold) {
                int pct = (int) Math.round(mismatchRate * 100.0);
                recommendations.add(new CapacityRecommendation(
                        0,
                        LocalDateTime.now(),
                        RecommendationCategory.SIZE_MISMATCH,
                        Severity.MEDIUM,
                        stat.floor(),
                        WorkspaceType.ROOM,
                        mismatchRate,
                        pct + "% of " + stat.code() + " (" + stat.name() + ", cap " + stat.capacity()
                                + ") bookings had <= 2 attendees",
                        "Add 2-person focus pods on Floor " + stat.floor()
                                + " to free " + stat.code() + " for larger team meetings"
                ));
            }
        }
        return recommendations;
    }
}
