package com.deskweave.service.rules;

import com.deskweave.model.CapacityRecommendation;

import java.util.List;

/**
 * Strategy contract for an analytics rule that emits capacity recommendations.
 */
public interface CapacityRule {

    String getRuleName();

    List<CapacityRecommendation> evaluate(CapacityAnalysisContext context);
}
