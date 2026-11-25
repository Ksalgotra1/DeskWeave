package com.deskweave.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigTest {

    @Test
    void loadsConfiguredPropertiesFile() {
        AppConfig config = AppConfig.loadDefault();
        assertEquals("jdbc:postgresql://localhost:5432/deskweave", config.getDbUrl());
        assertEquals("deskweave", config.getDbUser());
        assertEquals("deskweave", config.getDbPassword());
        assertEquals(15, config.getCheckInEarlyMinutes());
        assertEquals(15, config.getExpiryGraceMinutes());
        assertEquals(LocalTime.of(8, 0), config.getOfficeOpen());
        assertEquals(LocalTime.of(20, 0), config.getOfficeClose());
        assertEquals(0.85, config.getCapacityHighThreshold(), 1e-6);
        assertEquals(0.25, config.getCapacityLowThreshold(), 1e-6);
        assertEquals(0.20, config.getCapacityNoShowThreshold(), 1e-6);
        assertEquals(0.35, config.getCapacityMismatchThreshold(), 1e-6);
        assertEquals(Path.of("logs/deskweave.log"), config.getLogFilePath());
    }

    @Test
    void fallsBackToDefaultsWhenFileMissing(@TempDir Path tempDir) {
        AppConfig config = AppConfig.load(tempDir.resolve("nonexistent.properties"));
        assertEquals(15, config.getExpiryGraceMinutes());
        assertEquals(LocalTime.of(8, 0), config.getOfficeOpen());
        assertEquals(LocalTime.of(20, 0), config.getOfficeClose());
    }

    @Test
    void rejectsInvalidOfficeHours(@TempDir Path tempDir) throws IOException {
        Path badFile = tempDir.resolve("bad.properties");
        Files.writeString(badFile, "office.open=18:00\noffice.close=09:00\n");
        assertThrows(IllegalArgumentException.class, () -> AppConfig.load(badFile));
    }
}
