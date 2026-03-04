package payment;

import java.time.LocalDateTime;

public class Receipt {
    private String        receiptId;
    private double        amount;
    private String        method;
    private LocalDateTime timestamp;

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
