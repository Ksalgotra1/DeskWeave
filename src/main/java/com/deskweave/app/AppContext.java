package com.deskweave.app;

import com.deskweave.config.AppConfig;
import com.deskweave.dao.CapacityAnalyticsDao;
import com.deskweave.dao.DatabaseManager;
import com.deskweave.dao.PostgresAuditLogDao;
import com.deskweave.dao.PostgresBookingDao;
import com.deskweave.dao.PostgresUserDao;
import com.deskweave.dao.PostgresWorkspaceDao;
import com.deskweave.engine.IntervalPartitioningEngine;
import com.deskweave.io.CsvBookingIO;
import com.deskweave.io.SnapshotSerializer;
import com.deskweave.service.AuthService;
import com.deskweave.service.BookingExpiryService;
import com.deskweave.service.BookingService;
import com.deskweave.service.CapacityPlanningService;
import com.deskweave.service.WorkspaceService;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Central application wiring container connecting DAOs, engines, I/O helpers, and services.
 */
public class AppContext {

    private final AppConfig config;
    private final DatabaseManager databaseManager;
    private final IntervalPartitioningEngine partitioningEngine;
    private final CsvBookingIO csvBookingIO;
    private final SnapshotSerializer snapshotSerializer;
    private final AuthService authService;
    private final WorkspaceService workspaceService;
    private final BookingService bookingService;
    private final BookingExpiryService expiryService;
    private final CapacityPlanningService capacityPlanningService;
    private final ExecutorService backgroundExecutor;

    public AppContext(AppConfig config) {
        this(config, new DatabaseManager(Objects.requireNonNull(config, "config must not be null")));
    }

    public AppContext(AppConfig config, DatabaseManager databaseManager) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager must not be null");

        PostgresUserDao userDao = new PostgresUserDao(databaseManager);
        PostgresWorkspaceDao workspaceDao = new PostgresWorkspaceDao(databaseManager);
        PostgresAuditLogDao auditLogDao = new PostgresAuditLogDao(databaseManager);
        PostgresBookingDao bookingDao = new PostgresBookingDao(databaseManager, auditLogDao);
        CapacityAnalyticsDao analyticsDao = new CapacityAnalyticsDao(databaseManager);

        this.partitioningEngine = new IntervalPartitioningEngine();
        this.csvBookingIO = new CsvBookingIO();
        this.snapshotSerializer = new SnapshotSerializer();

        this.authService = new AuthService(userDao);
        this.workspaceService = new WorkspaceService(workspaceDao);
        this.bookingService = new BookingService(bookingDao, workspaceDao, config);
        this.expiryService = new BookingExpiryService(bookingDao, config);
        this.capacityPlanningService = new CapacityPlanningService(
                workspaceDao, bookingDao, analyticsDao, partitioningEngine, config);

        this.backgroundExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "deskweave-bg-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public AppConfig getConfig() {
        return config;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public IntervalPartitioningEngine getPartitioningEngine() {
        return partitioningEngine;
    }

    public CsvBookingIO getCsvBookingIO() {
        return csvBookingIO;
    }

    public SnapshotSerializer getSnapshotSerializer() {
        return snapshotSerializer;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    public BookingService getBookingService() {
        return bookingService;
    }

    public BookingExpiryService getExpiryService() {
        return expiryService;
    }

    public CapacityPlanningService getCapacityPlanningService() {
        return capacityPlanningService;
    }

    public ExecutorService getBackgroundExecutor() {
        return backgroundExecutor;
    }

    public void shutdown() {
        backgroundExecutor.shutdownNow();
    }
}
