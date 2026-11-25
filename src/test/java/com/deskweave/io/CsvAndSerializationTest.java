package com.deskweave.io;

import com.deskweave.engine.IntervalPartitioningEngine;
import com.deskweave.engine.PartitionResult;
import com.deskweave.model.BookingRequest;
import com.deskweave.model.CapacityRecommendation;
import com.deskweave.model.CapacitySimulationSnapshot;
import com.deskweave.model.MeetingRoom;
import com.deskweave.model.RecommendationCategory;
import com.deskweave.model.Severity;
import com.deskweave.model.Workspace;
import com.deskweave.model.WorkspaceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvAndSerializationTest {

    private final CsvBookingIO csvIO = new CsvBookingIO();
    private final SnapshotSerializer serializer = new SnapshotSerializer();
    private final IntervalPartitioningEngine engine = new IntervalPartitioningEngine();

    @Test
    void importsPeakCsvRunsPartitioningAndExportsCsvAndSnapshot(@TempDir Path tempDir) throws Exception {
        List<BookingRequest> imported = csvIO.importRequests(Path.of("sample-data/peak_day_requests.csv"));
        assertEquals(26, imported.size());

        List<Workspace> rooms = List.of(
                new MeetingRoom(1, "R-201", "Boardroom Alpha", 2, 10, true, true, true),
                new MeetingRoom(2, "R-202", "Huddle Beta", 2, 6, true, true, false),
                new MeetingRoom(3, "R-203", "Focus Gamma", 2, 4, true, false, true)
        );

        PartitionResult result = engine.partition(imported, rooms);
        assertEquals(6, result.getMinRoomsNeeded());
        assertEquals(3, result.getActualInventoryCount());
        assertEquals(3, result.getRoomDeficit());
        assertTrue(result.hasOverflow());
        assertEquals(LocalTime.of(10, 30), result.getPeakConcurrencyTime().orElseThrow());

        // Unit 3 Byte Stream Object Serialization round-trip (.dws)
        CapacitySimulationSnapshot original = result.toSnapshot("Peak-Day-Nov26", WorkspaceType.ROOM);
        Path snapshotFile = tempDir.resolve("peak_nov26.dws");
        serializer.saveSnapshot(original, snapshotFile);
        assertTrue(Files.size(snapshotFile) > 0);

        CapacitySimulationSnapshot loaded = serializer.loadSnapshot(snapshotFile);
        assertEquals(original.getSnapshotName(), loaded.getSnapshotName());
        assertEquals(original.getTargetDate(), loaded.getTargetDate());
        assertEquals(original.getMinRoomsNeeded(), loaded.getMinRoomsNeeded());
        assertEquals(original.getRoomDeficit(), loaded.getRoomDeficit());
        assertEquals(original.getAllRequests().size(), loaded.getAllRequests().size());
        assertEquals(original.getFlaggedRequests().size(), loaded.getFlaggedRequests().size());
        assertEquals(original.getRoomAssignments().size(), loaded.getRoomAssignments().size());
        assertNull(loaded.getTransientLogger(), "transient field must not be serialized");

        // Unit 3 Character Stream CSV exports
        Path partitionCsv = tempDir.resolve("partition_out.csv");
        csvIO.exportPartitionResult(result, partitionCsv);
        List<String> partitionLines = Files.readAllLines(partitionCsv);
        assertEquals(27, partitionLines.size(), "Expected header + 26 request rows");
        assertTrue(partitionLines.get(0).startsWith("status,assigned_space,request_id"));

        Path recCsv = tempDir.resolve("recommendations.csv");
        csvIO.exportRecommendations(List.of(
                new CapacityRecommendation(1, LocalDateTime.now(), RecommendationCategory.SHORTAGE,
                        Severity.HIGH, 2, WorkspaceType.ROOM, 2.0,
                        "Peak hit 200%, \"critical\" load", "Add 3 rooms")
        ), recCsv);
        List<String> recLines = Files.readAllLines(recCsv);
        assertEquals(2, recLines.size());
        assertTrue(recLines.get(1).contains("\"Peak hit 200%, \"\"critical\"\" load\""));
    }
}
