package com.deskweave.io;

import com.deskweave.config.AppLogger;
import com.deskweave.engine.PartitionResult;
import com.deskweave.exception.InvalidTimeSlotException;
import com.deskweave.model.BookingRequest;
import com.deskweave.model.CapacityRecommendation;
import com.deskweave.model.TimeSlot;
import com.deskweave.model.Workspace;
import com.deskweave.model.WorkspaceType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Character-stream ({@link BufferedReader} / {@link BufferedWriter}) CSV importer and exporter.
 */
public class CsvBookingIO implements ReportExporter {

    private static final Logger LOGGER = AppLogger.getLogger(CsvBookingIO.class);

    /**
     * Imports demand requests from a CSV file with columns:
     * {@code request_id,requester_name,workspace_type,floor,attendees,date,start_time,end_time,notes}.
     */
    public List<BookingRequest> importRequests(Path csvPath) throws IOException, InvalidTimeSlotException {
        Objects.requireNonNull(csvPath, "csvPath must not be null");
        List<BookingRequest> requests = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String line;
            int lineNum = 0;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                List<String> cols = parseCsvLine(trimmed);
                if (!headerSkipped && cols.get(0).equalsIgnoreCase("request_id")) {
                    headerSkipped = true;
                    continue;
                }
                headerSkipped = true;

                if (cols.size() < 8) {
                    throw new IOException("Invalid CSV column count at line " + lineNum + " (expected >= 8)");
                }
                String requestId = cols.get(0).trim();
                String requester = cols.get(1).trim();
                WorkspaceType type = WorkspaceType.valueOf(cols.get(2).trim().toUpperCase());
                Integer floor = cols.get(3).trim().isEmpty() ? null : Integer.parseInt(cols.get(3).trim());
                int attendees = Integer.parseInt(cols.get(4).trim());
                LocalDate date = LocalDate.parse(cols.get(5).trim());
                LocalTime start = LocalTime.parse(cols.get(6).trim());
                LocalTime end = LocalTime.parse(cols.get(7).trim());
                String notes = cols.size() > 8 ? cols.get(8).trim() : "";

                requests.add(new BookingRequest(
                        requestId,
                        requester,
                        type,
                        floor,
                        attendees,
                        TimeSlot.of(date, start, end),
                        notes
                ));
            }
        }
        LOGGER.info("Imported " + requests.size() + " booking request(s) from " + csvPath);
        return requests;
    }

    @Override
    public void exportRecommendations(List<CapacityRecommendation> recommendations, Path outputPath)
            throws IOException {
        Objects.requireNonNull(recommendations, "recommendations must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        ensureParentDirs(outputPath);

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("category,severity,floor,workspace_type,metric_pct,summary,suggested_action");
            writer.newLine();
            for (CapacityRecommendation rec : recommendations) {
                writer.write(String.join(",",
                        escapeCsv(rec.getCategory().name()),
                        escapeCsv(rec.getSeverity().name()),
                        String.valueOf(rec.getFloor()),
                        escapeCsv(rec.getWorkspaceType().name()),
                        String.format("%.1f", rec.getMetricValue() * 100.0),
                        escapeCsv(rec.getSummary()),
                        escapeCsv(rec.getSuggestedAction())
                ));
                writer.newLine();
            }
        }
        LOGGER.info("Exported " + recommendations.size() + " capacity recommendation(s) to " + outputPath);
    }

    /**
     * Exports honored room assignments and flagged overflow requests from a simulation run to CSV.
     */
    public void exportPartitionResult(PartitionResult result, Path outputPath) throws IOException {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        ensureParentDirs(outputPath);

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("status,assigned_space,request_id,requester,date,start_time,end_time,attendees,notes");
            writer.newLine();
            for (Map.Entry<Workspace, List<BookingRequest>> entry : result.getHonoredAssignments().entrySet()) {
                String spaceLabel = entry.getKey().getDisplayName();
                for (BookingRequest req : entry.getValue()) {
                    writePartitionRow(writer, "HONORED", spaceLabel, req);
                }
            }
            for (BookingRequest overflow : result.getFlaggedOverflowRequests()) {
                writePartitionRow(writer, "OVERFLOW", "UNASSIGNED", overflow);
            }
        }
        LOGGER.info("Exported partition result to " + outputPath);
    }

    private static void writePartitionRow(BufferedWriter writer,
                                          String status,
                                          String spaceLabel,
                                          BookingRequest req) throws IOException {
        writer.write(String.join(",",
                escapeCsv(status),
                escapeCsv(spaceLabel),
                escapeCsv(req.getRequestId()),
                escapeCsv(req.getRequesterName()),
                req.getSlot().getDate().toString(),
                req.getSlot().getStartTime().toString(),
                req.getSlot().getEndTime().toString(),
                String.valueOf(req.getAttendeeCount()),
                escapeCsv(req.getNotes())
        ));
        writer.newLine();
    }

    private static void ensureParentDirs(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
