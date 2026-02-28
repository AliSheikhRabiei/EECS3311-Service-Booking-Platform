package payment;

import model.Booking;
import model.Client;
import payment.cor.*;
import service.NotificationService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PaymentService {
    private final List<PaymentTransaction> transactions = new ArrayList<>();
    private PaymentHandler   handlerChain;
    private PaymentTransaction lastTransaction;
    private Receipt            lastReceipt;

    public PaymentService(NotificationService notifService) {
        buildChain(notifService);
    }

    private void buildChain(NotificationService notifService) {
        ValidateMethodHandler  validate  = new ValidateMethodHandler();
        ProcessPaymentHandler  process   = new ProcessPaymentHandler(this);
        ConfirmBookingHandler  confirm   = new ConfirmBookingHandler(this, notifService);

        validate.setNext(process);
        process.setNext(confirm);

        this.handlerChain = validate;
    }

    /** UC5 — Process payment through the CoR chain */
    public PaymentTransaction processPayment(Booking booking, PaymentMethod method) {
        lastTransaction = null;
        lastReceipt     = null;
        try {
            handlerChain.handle(booking, method);
        } catch (IllegalArgumentException e) {
            System.out.println("[PaymentService] Validation error: " + e.getMessage());
            // Store failed transaction if it was created
            if (lastTransaction == null) {
                // Create a FAILED transaction for the validation failure
                lastTransaction = new PaymentTransaction(
                        "TX-INVALID-" + System.currentTimeMillis(),
                        booking,
                        booking.getService().getPrice(),
                        PaymentStatus.FAILED,
                        method.getMethodType(),
                        java.time.LocalDateTime.now());
                transactions.add(lastTransaction);
            }
        } catch (RuntimeException e) {
            System.out.println("[PaymentService] Payment failed: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("[PaymentService] Unexpected error: " + e.getMessage());
        }
        return lastTransaction;
    }

    /** UC7 — Payment history for a client */
    public List<PaymentTransaction> getPaymentHistory(Client client) {
        return transactions.stream()
                .filter(tx -> tx.getBooking().getClient().getId().equals(client.getId()))
                .collect(Collectors.toList());
    }

    // ── Internal helpers used by CoR handlers ────────────────────────────────
    public void addTransaction(PaymentTransaction tx)      { transactions.add(tx); }
    public void setLastTransaction(PaymentTransaction tx)  { this.lastTransaction = tx; }
    public PaymentTransaction getLastTransaction()         { return lastTransaction; }
    public void setLastReceipt(Receipt r)                  { this.lastReceipt = r; }
    public Receipt getLastReceipt()                        { return lastReceipt; }
    public List<PaymentTransaction> getAllTransactions()    { return new ArrayList<>(transactions); }
}
