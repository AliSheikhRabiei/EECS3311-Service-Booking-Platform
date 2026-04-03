package com.platform.db;

import com.platform.payment.PaymentStatus;
import com.platform.payment.PaymentTransaction;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DB access for the payment_transactions table.
 * Returns lightweight TransactionRow objects rather than full PaymentTransaction
 * domain objects (which need a Booking reference) for history display.
 */
public class PaymentTransactionRepository {

    public void save(PaymentTransaction tx) {
        String sql = """
                INSERT INTO payment_transactions (id, booking_id, amount, status, timestamp)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE
                SET status = EXCLUDED.status
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tx.getTransactionId());
            ps.setString(2, tx.getBooking().getBookingId());
            ps.setDouble(3, tx.getAmount());
            ps.setString(4, tx.getStatus().name());
            ps.setTimestamp(5, Timestamp.valueOf(tx.getTimestamp()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PaymentTransactionRepository.save failed: " + e.getMessage(), e);
        }
    }

    /** Returns transaction rows for a given client (via booking join). */
    public List<TransactionRow> findByClientId(String clientId) {
        String sql = """
                SELECT pt.id, pt.booking_id, pt.amount, pt.status, pt.timestamp
                FROM payment_transactions pt
                JOIN bookings b ON pt.booking_id = b.id
                WHERE b.client_id = ?
                ORDER BY pt.timestamp DESC
                """;
        List<TransactionRow> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findByClientId failed: " + e.getMessage(), e);
        }
        return list;
    }

    /** Returns all transaction rows for a given booking (used to find success tx for refund). */
    public List<TransactionRow> findByBookingId(String bookingId) {
        String sql = "SELECT * FROM payment_transactions WHERE booking_id = ? ORDER BY timestamp";
        List<TransactionRow> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, bookingId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findByBookingId failed: " + e.getMessage(), e);
        }
        return list;
    }

    private TransactionRow mapRow(ResultSet rs) throws SQLException {
        TransactionRow r = new TransactionRow();
        r.id        = rs.getString("id");
        r.bookingId = rs.getString("booking_id");
        r.amount    = rs.getDouble("amount");
        r.status    = PaymentStatus.valueOf(rs.getString("status"));
        r.timestamp = rs.getTimestamp("timestamp").toLocalDateTime();
        return r;
    }

    /** Flat data holder for a transaction row — safe to return in API responses. */
    public static class TransactionRow {
        public String          id;
        public String          bookingId;
        public double          amount;
        public PaymentStatus   status;
        public java.time.LocalDateTime timestamp;
    }
}
