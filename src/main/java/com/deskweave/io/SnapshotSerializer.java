package com.deskweave.io;

import com.deskweave.config.AppLogger;
import com.deskweave.model.CapacitySimulationSnapshot;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Byte-stream ({@link ObjectOutputStream} / {@link ObjectInputStream}) persistence for {@code .dws} snapshots.
 */
public class SnapshotSerializer {

    private static final Logger LOGGER = AppLogger.getLogger(SnapshotSerializer.class);

    public void saveSnapshot(CapacitySimulationSnapshot snapshot, Path outputPath) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");

        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(outputPath)))) {
            oos.writeObject(snapshot);
        }
        LOGGER.info("Serialized simulation snapshot '" + snapshot.getSnapshotName() + "' to " + outputPath);
    }

    public CapacitySimulationSnapshot loadSnapshot(Path inputPath) throws IOException, ClassNotFoundException {
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(inputPath)))) {
            Object obj = ois.readObject();
            if (!(obj instanceof CapacitySimulationSnapshot snapshot)) {
                throw new IOException("Invalid snapshot file format: " + inputPath);
            }
            LOGGER.info("Deserialized simulation snapshot '" + snapshot.getSnapshotName() + "' from " + inputPath);
            return snapshot;
        }
    }
}
