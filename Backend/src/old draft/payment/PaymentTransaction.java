package payment;

import model.Booking;

import java.time.LocalDateTime;

public class PaymentTransaction {
    private String        transactionId;
    private Booking       booking;
    private double        amount;
    private PaymentStatus status;
    private String        methodType;
    private LocalDateTime timestamp;

    public PaymentTransaction(String transactionId, Booking booking,
                               double amount, PaymentStatus status,
                               String methodType, LocalDateTime timestamp) {
        this.transactionId = transactionId;
        this.booking       = booking;
        this.amount        = amount;
        this.status        = status;
        this.methodType    = methodType;
        this.timestamp     = timestamp;
    }

    public String        getTransactionId() { return transactionId; }
    public Booking       getBooking()       { return booking; }
    public double        getAmount()        { return amount; }
    public PaymentStatus getStatus()        { return status; }
    public String        getMethodType()    { return methodType; }
    public LocalDateTime getTimestamp()     { return timestamp; }

    public void setStatus(PaymentStatus status) { this.status = status; }

    @Override
    public String toString() {
        return String.format("Transaction[id=%s, booking=%s, amount=$%.2f, status=%s, method=%s, time=%s]",
                transactionId, booking.getBookingId(), amount, status, methodType, timestamp);
    }
}
