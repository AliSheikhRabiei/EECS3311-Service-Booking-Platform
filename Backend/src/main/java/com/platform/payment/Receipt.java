package com.platform.payment;

import java.time.LocalDateTime;

/**
 * Confirmation record generated on a successful payment.
 * A transaction has 0..1 receipt; receipt is only created on SUCCESS.
 */
public class Receipt {

    private final String        receiptId;
    private final double        amount;
    private final String        method;
    private final LocalDateTime timestamp;

    public Receipt(String receiptId, double amount, String method, LocalDateTime timestamp) {
        this.receiptId = receiptId;
        this.amount    = amount;
        this.method    = method;
        this.timestamp = timestamp;
    }

    public String        getReceiptId() { return receiptId; }
    public double        getAmount()    { return amount; }
    public String        getMethod()    { return method; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("Receipt[id=%s, amount=$%.2f, method=%s, time=%s]",
                receiptId, amount, method, timestamp);
    }
}
