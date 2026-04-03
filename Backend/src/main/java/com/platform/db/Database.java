package com.platform.db;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
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
     *  - Split the file on ";" to get individual statements.
     *  - For each chunk, remove every line that is purely a SQL comment (starts with "--")
     *    BEFORE deciding whether the chunk is empty. This is necessary because the first
     *    chunk in schema.sql begins with several comment lines followed by the real
     *    CREATE TABLE statement - without this step the whole chunk would be discarded
     *    and the users table would never be created, breaking every foreign key after it.
     *  - Inline comments at the end of a data line (e.g. "role VARCHAR(20) -- ADMIN")
     *    are left in place; PostgreSQL handles them natively.
     */
    public static void initialize() {
        try (InputStream is = Database.class.getClassLoader().getResourceAsStream("schema.sql")) {
            if (is == null) throw new RuntimeException("schema.sql not found in resources");

            String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                for (String chunk : sql.split(";")) {
                    // Strip full-line comments (lines whose first non-space chars are --)
                    // then re-join and trim to see if any real SQL remains.
                    String cleaned = Arrays.stream(chunk.split("\n"))
                            .filter(line -> !line.trim().startsWith("--"))
                            .collect(Collectors.joining("\n"))
                            .trim();

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
