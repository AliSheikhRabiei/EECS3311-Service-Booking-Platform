package com.platform.db;

import com.platform.domain.Client;
import com.platform.domain.Consultant;
import com.platform.domain.RegistrationStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DB access for the users + consultants tables.
 * Reconstructs domain Client / Consultant objects from rows.
 */
public class UserRepository {

    // ── Write operations ─────────────────────────────────────────────────────

    public void saveUser(String id, String name, String email,
                         String passwordHash, String role) {
        String sql = """
                INSERT INTO users (id, name, email, password_hash, role)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE
                SET name = EXCLUDED.name,
                    email = EXCLUDED.email,
                    password_hash = EXCLUDED.password_hash
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, email);
            ps.setString(4, passwordHash);
            ps.setString(5, role);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("saveUser failed: " + e.getMessage(), e);
        }
    }

    public void saveConsultantProfile(String userId, String bio, String status) {
        String sql = """
                INSERT INTO consultants (user_id, bio, registration_status)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE
                SET bio = EXCLUDED.bio,
                    registration_status = EXCLUDED.registration_status
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, bio == null ? "" : bio);
            ps.setString(3, status);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("saveConsultantProfile failed: " + e.getMessage(), e);
        }
    }

    public void updateConsultantStatus(String userId, String status) {
        String sql = "UPDATE consultants SET registration_status = ? WHERE user_id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateConsultantStatus failed: " + e.getMessage(), e);
        }
    }

    // ── Read: raw rows ───────────────────────────────────────────────────────

    /** Returns {id, name, email, password_hash, role, bio, registration_status} or empty. */
    public Optional<UserRow> findByEmail(String email) {
        String sql = """
                SELECT u.id, u.name, u.email, u.password_hash, u.role,
                       c.bio, c.registration_status
                FROM users u
                LEFT JOIN consultants c ON u.id = c.user_id
                WHERE u.email = ?
                """;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("findByEmail failed: " + e.getMessage(), e);
        }
    }

    public Optional<UserRow> findRowById(String id) {
        String sql = """
                SELECT u.id, u.name, u.email, u.password_hash, u.role,
                       c.bio, c.registration_status
                FROM users u
                LEFT JOIN consultants c ON u.id = c.user_id
                WHERE u.id = ?
                """;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("findRowById failed: " + e.getMessage(), e);
        }
    }

    // ── Read: domain objects ─────────────────────────────────────────────────

    /** Returns a Client domain object for the given user id, or null. */
    public Client loadClient(String id) {
        return findRowById(id)
                .filter(r -> "CLIENT".equals(r.role))
                .map(r -> new Client(r.id, r.name, r.email))
                .orElse(null);
    }

    /** Returns a Consultant domain object for the given user id, or null. */
    public Consultant loadConsultant(String id) {
        return findRowById(id)
                .filter(r -> "CONSULTANT".equals(r.role))
                .map(r -> {
                    Consultant con = new Consultant(r.id, r.name, r.email,
                            r.bio == null ? "" : r.bio);
                    if (r.registrationStatus != null) {
                        con.setRegistrationStatus(
                                RegistrationStatus.valueOf(r.registrationStatus));
                    }
                    return con;
                })
                .orElse(null);
    }

    /** Returns all consultants stored in the DB. */
    public List<Consultant> findAllConsultants() {
        String sql = """
                SELECT u.id, u.name, u.email, c.bio, c.registration_status
                FROM users u
                JOIN consultants c ON u.id = c.user_id
                ORDER BY u.name
                """;
        List<Consultant> result = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Consultant con = new Consultant(
                        rs.getString("id"), rs.getString("name"), rs.getString("email"),
                        rs.getString("bio") == null ? "" : rs.getString("bio"));
                String status = rs.getString("registration_status");
                if (status != null) con.setRegistrationStatus(RegistrationStatus.valueOf(status));
                result.add(con);
            }
        } catch (SQLException e) {
            throw new RuntimeException("findAllConsultants failed: " + e.getMessage(), e);
        }
        return result;
    }

    /** Returns all clients stored in the DB. */
    public List<Client> findAllClients() {
        String sql = "SELECT id, name, email FROM users WHERE role = 'CLIENT' ORDER BY name";
        List<Client> result = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new Client(rs.getString("id"),
                        rs.getString("name"), rs.getString("email")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findAllClients failed: " + e.getMessage(), e);
        }
        return result;
    }

    public boolean emailExists(String email) {
        String sql = "SELECT 1 FROM users WHERE email = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            throw new RuntimeException("emailExists failed", e);
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private UserRow mapRow(ResultSet rs) throws SQLException {
        UserRow r = new UserRow();
        r.id                 = rs.getString("id");
        r.name               = rs.getString("name");
        r.email              = rs.getString("email");
        r.passwordHash       = rs.getString("password_hash");
        r.role               = rs.getString("role");
        r.bio                = rs.getString("bio");
        r.registrationStatus = rs.getString("registration_status");
        return r;
    }

    /** Plain data holder for a joined user row. */
    public static class UserRow {
        public String id, name, email, passwordHash, role, bio, registrationStatus;
    }
}
