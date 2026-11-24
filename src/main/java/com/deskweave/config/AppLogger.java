package com.deskweave.config;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Configures {@link java.util.logging.Logger} console and rotating file handlers for DeskWeave.
 */
public final class AppLogger {

    private static final String ROOT_NAMESPACE = "com.deskweave";
    private static final int MAX_LOG_BYTES = 1_048_576;
    private static final int ROTATION_COUNT = 3;
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private AppLogger() {
    }

    /**
     * Configures console and rotating file handlers for the application namespace.
     */
    public static synchronized void configure(Path logFilePath) {
        Objects.requireNonNull(logFilePath, "logFilePath must not be null");
        shutdown();

        Logger root = Logger.getLogger(ROOT_NAMESPACE);
        root.setUseParentHandlers(false);
        root.setLevel(Level.INFO);

        Formatter formatter = new SingleLineFormatter();

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        consoleHandler.setFormatter(formatter);
        root.addHandler(consoleHandler);

        try {
            Path parent = logFilePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            FileHandler fileHandler = new FileHandler(logFilePath.toString(), MAX_LOG_BYTES, ROTATION_COUNT, true);
            fileHandler.setLevel(Level.INFO);
            fileHandler.setFormatter(formatter);
            root.addHandler(fileHandler);
        } catch (IOException ex) {
            root.log(Level.WARNING, "Unable to initialize file logging at " + logFilePath, ex);
        }
    }

    /**
     * Returns a class-scoped logger under the {@code com.deskweave} hierarchy.
     */
    public static Logger getLogger(Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz must not be null");
        return Logger.getLogger(clazz.getName());
    }

    /**
     * Flushes and closes all handlers attached to the application root logger.
     */
    public static synchronized void shutdown() {
        Logger root = Logger.getLogger(ROOT_NAMESPACE);
        for (Handler handler : root.getHandlers()) {
            try {
                handler.flush();
                handler.close();
            } catch (SecurityException ignored) {
                // Ignore if disallowed by security manager.
            }
            root.removeHandler(handler);
        }
    }

    private static final class SingleLineFormatter extends Formatter {

        @Override
        public String format(LogRecord record) {
            String timestamp = TIMESTAMP_FMT.format(Instant.ofEpochMilli(record.getMillis()));
            String source = record.getLoggerName();
            int lastDot = source != null ? source.lastIndexOf('.') : -1;
            String simpleName = lastDot >= 0 ? source.substring(lastDot + 1) : String.valueOf(source);

            StringBuilder sb = new StringBuilder(128)
                    .append('[').append(timestamp).append("] ")
                    .append('[').append(record.getLevel().getName()).append("] ")
                    .append('[').append(simpleName).append("] ")
                    .append(formatMessage(record))
                    .append(System.lineSeparator());

            if (record.getThrown() != null) {
                StringWriter sw = new StringWriter();
                try (PrintWriter pw = new PrintWriter(sw)) {
                    record.getThrown().printStackTrace(pw);
                }
                sb.append(sw);
            }
            return sb.toString();
        }
    }
}
