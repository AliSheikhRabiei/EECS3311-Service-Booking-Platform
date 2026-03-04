package com.platform.payment;

import com.platform.domain.Booking;

import java.time.LocalDateTime;

/**
 * Represents a simulated payment attempt (UC7 payment history).
 * Immutable except for {@code status} which can change to REFUNDED.
 */
public class PaymentTransaction {

    private final String        transactionId;
    private final Booking       booking;
    private final double        amount;
    private PaymentStatus       status;
    private final LocalDateTime timestamp;

    public PaymentTransaction(String transactionId, Booking booking,
                               double amount, PaymentStatus status,
                               LocalDateTime timestamp) {
        this.transactionId = transactionId;
        this.booking       = booking;
        this.amount        = amount;
        this.status        = status;
        this.timestamp     = timestamp;
    }

    public String        getTransactionId() { return transactionId; }
    public Booking       getBooking()       { return booking; }
    public double        getAmount()        { return amount; }
    public PaymentStatus getStatus()        { return status; }
    public LocalDateTime getTimestamp()     { return timestamp; }

    /** Updates status (used when marking as REFUNDED). */
    public void setStatus(PaymentStatus status) { this.status = status; }

    @Override
    public String toString() {
        return String.format("Transaction[id=%s, booking=%s, amount=$%.2f, status=%s, time=%s]",
                transactionId, booking.getBookingId(), amount, status, timestamp);
    }
}
