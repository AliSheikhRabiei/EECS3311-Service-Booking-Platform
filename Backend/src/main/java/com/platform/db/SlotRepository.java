package com.platform.db;

import com.platform.domain.TimeSlot;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DB access for the time_slots table.
 * Slots in the DB have a UUID primary key.
 * The domain TimeSlot.getSlotId() returns start.toString(), which we also
 * store so we can cross-reference with the AvailabilityService in-memory map.
 */
public class SlotRepository {

    /** Inserts a new slot; generates a UUID id. Returns the generated UUID. */
    public String save(String consultantId, TimeSlot slot) {
        String id = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO time_slots (id, consultant_id, start_time, end_time, is_available)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, consultantId);
            ps.setTimestamp(3, Timestamp.valueOf(slot.getStart()));
            ps.setTimestamp(4, Timestamp.valueOf(slot.getEnd()));
            ps.setBoolean(5, slot.isAvailable());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("SlotRepository.save failed: " + e.getMessage(), e);
        }
        return id;
    }

    /** Updates is_available flag for a slot identified by its UUID. */
    public void updateAvailability(String slotUuid, boolean available) {
        String sql = "UPDATE time_slots SET is_available = ? WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, available);
            ps.setString(2, slotUuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateAvailability failed: " + e.getMessage(), e);
        }
    }

    /** Deletes a slot by its UUID. */
    public void delete(String slotUuid) {
        String sql = "DELETE FROM time_slots WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, slotUuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("SlotRepository.delete failed: " + e.getMessage(), e);
        }
    }

    /** Returns a SlotRow by UUID, or null. */
    public SlotRow findByUuid(String uuid) {
        String sql = "SELECT * FROM time_slots WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("findByUuid failed: " + e.getMessage(), e);
        }
    }

    /** Returns a SlotRow by consultant + start time (used when reloading from DB into AvailabilityService). */
    public SlotRow findByConsultantAndStart(String consultantId, LocalDateTime start) {
        String sql = "SELECT * FROM time_slots WHERE consultant_id = ? AND start_time = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, consultantId);
            ps.setTimestamp(2, Timestamp.valueOf(start));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("findByConsultantAndStart failed: " + e.getMessage(), e);
        }
    }

    /** Returns all slots for a consultant. */
    public List<SlotRow> findByConsultantId(String consultantId) {
        String sql = "SELECT * FROM time_slots WHERE consultant_id = ? ORDER BY start_time";
        List<SlotRow> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, consultantId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findByConsultantId failed: " + e.getMessage(), e);
        }
        return list;
    }

    /** Returns ALL slots across all consultants (used on startup reload). */
    public List<SlotRow> findAll() {
        String sql = "SELECT * FROM time_slots ORDER BY start_time";
        List<SlotRow> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("SlotRepository.findAll failed: " + e.getMessage(), e);
        }
        return list;
    }

    private SlotRow mapRow(ResultSet rs) throws SQLException {
        SlotRow r = new SlotRow();
        r.id            = rs.getString("id");
        r.consultantId  = rs.getString("consultant_id");
        r.start         = rs.getTimestamp("start_time").toLocalDateTime();
        r.end           = rs.getTimestamp("end_time").toLocalDateTime();
        r.isAvailable   = rs.getBoolean("is_available");
        r.slot          = new TimeSlot(r.start, r.end);
        if (!r.isAvailable) r.slot.reserve();
        return r;
    }

    /** Plain data holder for a time_slots row (keeps UUID accessible alongside domain object). */
    public static class SlotRow {
        public String        id;            // UUID (DB primary key)
        public String        consultantId;
        public LocalDateTime start;
        public LocalDateTime end;
        public boolean       isAvailable;
        public TimeSlot      slot;          // reconstructed domain object
    }
}
