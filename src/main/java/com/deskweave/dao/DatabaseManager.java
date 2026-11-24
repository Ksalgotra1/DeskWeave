package com.deskweave.dao;

import com.deskweave.config.AppConfig;
import com.deskweave.exception.DataAccessException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Manages JDBC connections, SQL script execution, and explicit JDBC transactions.
 */
public class DatabaseManager {

    private final String url;
    private final String user;
    private final String password;

    @FunctionalInterface
    public interface SqlTransaction<T> {
        T execute(Connection conn) throws Exception;
    }

    public DatabaseManager(AppConfig config) {
        this(
                Objects.requireNonNull(config, "config must not be null").getDbUrl(),
                config.getDbUser(),
                config.getDbPassword()
        );
    }

    public DatabaseManager(String url, String user, String password) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("JDBC url must not be blank");
        }
        this.url = url.trim();
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.password = Objects.requireNonNull(password, "password must not be null");
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Checks connectivity by executing {@code SELECT 1}.
     */
    public boolean testConnection() {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        } catch (SQLException ex) {
            return false;
        }
    }

    /**
     * Executes a semicolon-delimited SQL script inside a single transaction.
     */
    public void executeSqlScript(Path scriptPath) {
        Objects.requireNonNull(scriptPath, "scriptPath must not be null");
        String rawSql;
        try {
            rawSql = Files.readString(scriptPath);
        } catch (IOException ex) {
            throw new DataAccessException("Failed to read SQL script: " + scriptPath, ex);
        }

        StringBuilder cleaned = new StringBuilder();
        for (String line : rawSql.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("--")) {
                cleaned.append(line).append('\n');
            }
        }

        inTransaction(conn -> {
            try (Statement stmt = conn.createStatement()) {
                for (String statement : cleaned.toString().split(";")) {
                    String sql = statement.trim();
                    if (!sql.isEmpty()) {
                        stmt.execute(sql);
                    }
                }
            }
            return null;
        });
    }

    /**
     * Runs {@code work} inside an explicit JDBC transaction with commit/rollback guarantees.
     */
    public <T> T inTransaction(SqlTransaction<T> work) {
        Objects.requireNonNull(work, "work must not be null");
        try (Connection conn = getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                T result = work.execute(conn);
                conn.commit();
                return result;
            } catch (Throwable ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                if (ex instanceof RuntimeException runtimeEx) {
                    throw runtimeEx;
                }
                throw new DataAccessException("Transaction failed and was rolled back", ex);
            } finally {
                try {
                    conn.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // Connection is about to be closed by try-with-resources.
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to acquire or close JDBC connection", ex);
        }
    }
}
