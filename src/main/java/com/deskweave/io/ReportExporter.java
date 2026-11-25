package com.deskweave.io;

import com.deskweave.model.CapacityRecommendation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Contract for exporting capacity recommendations to external files.
 */
public interface ReportExporter {

    void exportRecommendations(List<CapacityRecommendation> recommendations, Path outputPath) throws IOException;
}
