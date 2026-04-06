package com.platform.db;

import com.platform.domain.Booking;
import com.platform.domain.Client;
import com.platform.domain.Consultant;
import com.platform.domain.Service;
import com.platform.domain.TimeSlot;
import com.platform.repository.BookingRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DB-backed replacement for the in-memory BookingRepository.
 *
 * Extends BookingRepository so it can be passed anywhere the parent type is expected.
 * The parent's in-memory HashMap is unused - all operations go to PostgreSQL.
 *
 * Requires UserRepository, ServiceRepository, and SlotRepository to reconstruct
 * the full Booking object graph from DB rows.
 */
public class DbBookingRepository extends BookingRepository {

    private final UserRepository    userRepository;
    private final ServiceRepository serviceRepository;
    private final SlotRepository    slotRepository;

    public DbBookingRepository(UserRepository userRepository,
                                ServiceRepository serviceRepository,
                                SlotRepository slotRepository) {
        this.userRepository    = userRepository;
        this.serviceRepository = serviceRepository;
        this.slotRepository    = slotRepository;
    }

    // ── INSERT / UPDATE ──────────────────────────────────────────────────────

    /**
     * Inserts a new booking row or updates the status of an existing one.
     * New bookings reserve the slot and insert the booking in one DB transaction
     * so two simultaneous requests cannot both claim the same slot.
     */
    @Override
    public void save(Booking b) {
        if (b == null) throw new IllegalArgumentException("Booking must not be null.");

        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (bookingExists(c, b.getBookingId())) {
                    updateStatus(c, b.getBookingId(), BookingStateFactory.toDbString(b.getState()));
                } else {
                    String slotUuid = reserveSlotForCreate(c, b);
                    if (slotUuid == null) {
                        throw new IllegalStateException(
                                "The selected time slot is not available: " + b.getSlot());
                    }
                    insertBooking(c, b, slotUuid);
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("DbBookingRepository.save failed: " + e.getMessage(), e);
        }
    }

    /** Updates ONLY the status column (called after state transitions). */
    public void updateStatus(String bookingId, String status) {
        try (Connection c = Database.getConnection()) {
            updateStatus(c, bookingId, status);
        } catch (SQLException e) {
            throw new RuntimeException("updateStatus failed: " + e.getMessage(), e);
        }
    }

    // ── FIND ─────────────────────────────────────────────────────────────────

    @Override
    public Booking findById(String id) {
        String sql = "SELECT * FROM bookings WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return reconstructBooking(rs);
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("findById failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Booking> findByClient(Client client) {
        String sql = "SELECT * FROM bookings WHERE client_id = ? ORDER BY created_at DESC";
        return findWhere(sql, client.getId());
    }

    @Override
    public List<Booking> findByConsultant(Consultant consultant) {
        String sql = """
                SELECT b.* FROM bookings b
                JOIN services s ON b.service_id = s.id
                WHERE s.consultant_id = ?
                ORDER BY b.created_at DESC
                """;
        return findWhere(sql, consultant.getId());
    }

    @Override
    public List<Booking> findAll() {
        String sql = "SELECT * FROM bookings ORDER BY created_at DESC";
        List<Booking> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(reconstructBooking(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findAll failed: " + e.getMessage(), e);
        }
        return list;
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private List<Booking> findWhere(String sql, String param) {
        List<Booking> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(reconstructBooking(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findWhere failed: " + e.getMessage(), e);
        }
        return list;
    }

    private boolean bookingExists(Connection c, String bookingId) throws SQLException {
        String sql = "SELECT 1 FROM bookings WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String reserveSlotForCreate(Connection c, Booking b) throws SQLException {
        String sql = """
                UPDATE time_slots
                SET is_available = FALSE
                WHERE consultant_id = ?
                  AND start_time = ?
                  AND is_available = TRUE
                RETURNING id
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, b.getService().getConsultant().getId());
            ps.setTimestamp(2, Timestamp.valueOf(b.getSlot().getStart()));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("id") : null;
            }
        }
    }

    private void insertBooking(Connection c, Booking b, String slotUuid) throws SQLException {
        String sql = """
                INSERT INTO bookings (id, client_id, service_id, slot_id, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, b.getBookingId());
            ps.setString(2, b.getClient().getId());
            ps.setString(3, b.getService().getServiceId());
            ps.setString(4, slotUuid);
            ps.setString(5, BookingStateFactory.toDbString(b.getState()));
            ps.setTimestamp(6, Timestamp.valueOf(b.getCreatedAt()));
            ps.executeUpdate();
        }
    }

    private void updateStatus(Connection c, String bookingId, String status) throws SQLException {
        String sql = "UPDATE bookings SET status = ? WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, bookingId);
            ps.executeUpdate();
        }
    }

    /**
     * Reconstructs a full Booking object from a ResultSet row.
     * Loads client, service, and slot from their respective repositories.
     */
    private Booking reconstructBooking(ResultSet rs) throws SQLException {
        String bookingId = rs.getString("id");
        String clientId  = rs.getString("client_id");
        String serviceId = rs.getString("service_id");
        String slotUuid  = rs.getString("slot_id");
        String status    = rs.getString("status");

        Client client = userRepository.loadClient(clientId);
        Service service = serviceRepository.findById(serviceId);

        SlotRepository.SlotRow slotRow = slotRepository.findByUuid(slotUuid);
        TimeSlot slot = slotRow != null ? slotRow.slot : null;

        if (client == null || service == null || slot == null) {
            throw new RuntimeException(
                    "Cannot reconstruct booking " + bookingId +
                    ": missing client=" + client + " service=" + service + " slot=" + slot);
        }

        Booking booking = new Booking(bookingId, client, service, slot);
        // Restore state from DB (constructor sets RequestedState; override here)
        booking.setState(BookingStateFactory.fromString(status));
        return booking;
    }
}
