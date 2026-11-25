package com.deskweave.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Properties;

/**
 * Loads application settings from a {@code .properties} file with validated defaults.
 */
public final class AppConfig {

    public static final Path DEFAULT_CONFIG_PATH = Path.of("config/deskweave.properties");

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final int checkInEarlyMinutes;
    private final int expiryGraceMinutes;
    private final LocalTime officeOpen;
    private final LocalTime officeClose;
    private final double capacityHighThreshold;
    private final double capacityLowThreshold;
    private final double capacityNoShowThreshold;
    private final double capacityMismatchThreshold;
    private final Path logFilePath;

    private AppConfig(Properties props) {
        this.dbUrl = requireNonBlank(props.getProperty("db.url", "jdbc:postgresql://localhost:5432/deskweave"), "db.url");
        this.dbUser = requireNonBlank(props.getProperty("db.user", "deskweave"), "db.user");
        this.dbPassword = Objects.requireNonNull(props.getProperty("db.password", "deskweave"), "db.password");
        this.checkInEarlyMinutes = parseNonNegativeInt(props.getProperty("checkin.early.minutes", "15"), "checkin.early.minutes");
        this.expiryGraceMinutes = parsePositiveInt(props.getProperty("expiry.grace.minutes", "15"), "expiry.grace.minutes");
        this.officeOpen = LocalTime.parse(props.getProperty("office.open", "08:00").trim());
        this.officeClose = LocalTime.parse(props.getProperty("office.close", "20:00").trim());
        if (!this.officeOpen.isBefore(this.officeClose)) {
            throw new IllegalArgumentException("office.open must be strictly before office.close");
        }
        this.capacityHighThreshold = parseThreshold(props.getProperty("capacity.high.threshold", "0.85"), "capacity.high.threshold");
        this.capacityLowThreshold = parseThreshold(props.getProperty("capacity.low.threshold", "0.25"), "capacity.low.threshold");
        this.capacityNoShowThreshold = parseThreshold(props.getProperty("capacity.noshow.threshold", "0.20"), "capacity.noshow.threshold");
        this.capacityMismatchThreshold = parseThreshold(props.getProperty("capacity.mismatch.threshold", "0.35"), "capacity.mismatch.threshold");
        this.logFilePath = Path.of(requireNonBlank(props.getProperty("log.file", "logs/deskweave.log"), "log.file"));
    }

    /**
     * Loads configuration from {@link #DEFAULT_CONFIG_PATH}.
     */
    public static AppConfig loadDefault() {
        return load(DEFAULT_CONFIG_PATH);
    }

    /**
     * Loads configuration from the specified path, falling back to defaults if the file does not exist.
     */
    public static AppConfig load(Path path) {
        Objects.requireNonNull(path, "Config path must not be null");
        Properties props = new Properties();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to read configuration from " + path, ex);
            }
        }
        return new AppConfig(props);
    }

    private static String requireNonBlank(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Configuration key '" + key + "' must not be blank");
        }
        return value.trim();
    }

    private static int parseNonNegativeInt(String raw, String key) {
        int parsed = Integer.parseInt(raw.trim());
        if (parsed < 0) {
            throw new IllegalArgumentException("Configuration key '" + key + "' must be >= 0");
        }
        return parsed;
    }

    private static int parsePositiveInt(String raw, String key) {
        int parsed = Integer.parseInt(raw.trim());
        if (parsed <= 0) {
            throw new IllegalArgumentException("Configuration key '" + key + "' must be positive");
        }
        return parsed;
    }

    private static double parseThreshold(String raw, String key) {
        double parsed = Double.parseDouble(raw.trim());
        if (Double.isNaN(parsed) || parsed <= 0.0 || parsed > 1.0) {
            throw new IllegalArgumentException("Configuration key '" + key + "' must be in (0.0, 1.0]");
        }
        return parsed;
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public int getCheckInEarlyMinutes() {
        return checkInEarlyMinutes;
    }

    public int getExpiryGraceMinutes() {
        return expiryGraceMinutes;
    }

    public LocalTime getOfficeOpen() {
        return officeOpen;
    }

    public LocalTime getOfficeClose() {
        return officeClose;
    }

    public double getCapacityHighThreshold() {
        return capacityHighThreshold;
    }

    public double getCapacityLowThreshold() {
        return capacityLowThreshold;
    }

    public double getCapacityNoShowThreshold() {
        return capacityNoShowThreshold;
    }

    public double getCapacityMismatchThreshold() {
        return capacityMismatchThreshold;
    }

    public Path getLogFilePath() {
        return logFilePath;
    }
}
