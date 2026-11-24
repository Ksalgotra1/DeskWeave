package com.deskweave.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapSmokeTest {

    @Test
    void java21ConfigAndPostgresDriverAvailable() {
        assertTrue(Runtime.version().feature() >= 21, "Expected Java 21 or higher");
        assertTrue(Files.exists(Path.of("config/deskweave.properties")),
                "Expected config/deskweave.properties to exist");
        assertDoesNotThrow(() -> Class.forName("org.postgresql.Driver"),
                "Expected PostgreSQL JDBC driver on classpath");
    }
}
