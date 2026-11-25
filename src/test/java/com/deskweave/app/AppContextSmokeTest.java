package com.deskweave.app;

import com.deskweave.config.AppConfig;
import com.deskweave.dao.DatabaseManager;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppContextSmokeTest {

    @Test
    void wiresAllServicesAndFindsStylesheet() {
        String url = "jdbc:h2:mem:ctx_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        DatabaseManager db = new DatabaseManager(url, "sa", "");
        db.executeSqlScript(Path.of("sql/schema.sql"));

        AppContext ctx = new AppContext(AppConfig.loadDefault(), db);
        try {
            assertNotNull(ctx.getAuthService());
            assertNotNull(ctx.getWorkspaceService());
            assertNotNull(ctx.getBookingService());
            assertNotNull(ctx.getExpiryService());
            assertNotNull(ctx.getCapacityPlanningService());
            assertNotNull(ctx.getPartitioningEngine());
            assertNotNull(ctx.getCsvBookingIO());
            assertNotNull(ctx.getSnapshotSerializer());
            assertNotNull(ctx.getBackgroundExecutor());
            assertTrue(db.testConnection());
            assertNotNull(AppContextSmokeTest.class.getResource("/styles/deskweave.css"),
                    "Expected /styles/deskweave.css on test/runtime classpath");
        } finally {
            ctx.shutdown();
        }
    }
}
