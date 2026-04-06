package com.platform.db;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * Thin JDBC connection factory.
 * All config is read from environment variables so Docker compose can inject them.
 *
 * Env vars (with defaults for local development):
 *   DB_URL      = jdbc:postgresql://localhost:5432/booking_platform
 *   DB_USER     = postgres
 *   DB_PASSWORD = postgres
 */
public class Database {

    private static final String URL =
            System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/booking_platform");
    private static final String USER =
            System.getenv().getOrDefault("DB_USER", "postgres");
    private static final String PASSWORD =
            System.getenv().getOrDefault("DB_PASSWORD", "postgres");

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL JDBC driver not found on classpath", e);
        }
    }

    /** Returns a fresh JDBC connection. Caller must close it (use try-with-resources). */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /**
     * Runs schema.sql from the classpath.
     * Called once on server startup; CREATE TABLE IF NOT EXISTS makes it idempotent.
     *
     * Parsing rules:
     *  - Remove SQL line comments ("-- ...") before splitting into statements.
     *    This keeps semicolons inside comments from breaking statement boundaries.
     *  - Split the cleaned file on ";" to get individual statements.
     *  - Trim and execute only non-empty statements.
     */
    public static void initialize() {
        try (InputStream is = Database.class.getClassLoader().getResourceAsStream("schema.sql")) {
            if (is == null) throw new RuntimeException("schema.sql not found in resources");

            String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    // Strip both full-line and inline "--" comments from schema.sql.
                    // This is sufficient for our controlled schema file and avoids
                    // semicolons inside comments corrupting statement splitting.
                    .map(line -> {
                        int commentStart = line.indexOf("--");
                        return commentStart >= 0 ? line.substring(0, commentStart) : line;
                    })
                    .collect(Collectors.joining("\n"));

            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                for (String statement : sql.split(";")) {
                    String cleaned = statement.trim();
                    if (!cleaned.isEmpty()) {
                        stmt.execute(cleaned);
                    }
                }
            }
            System.out.println("[Database] Schema initialised.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise database schema", e);
        }
    }
}
