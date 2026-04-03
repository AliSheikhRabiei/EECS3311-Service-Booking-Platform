package com.platform.payment;

import com.platform.domain.Booking;
import com.platform.domain.Client;
import com.platform.notify.NotificationService;
import com.platform.policy.PolicyManager;
import com.platform.state.CancelledState;
import com.platform.state.ConfirmedState;
import com.platform.state.PaidState;
import com.platform.state.PendingPaymentState;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Coordinates the payment workflow (UC5, UC7).
 *
 * <p><strong>Important lifecycle note (per spec):</strong>
 * {@code processPayment} transitions the booking from CONFIRMED → PENDING_PAYMENT
 * <em>before</em> running the handler chain.  This ensures the state machine
 * accurately reflects that payment has been initiated.</p>
 *
 * <p>The CoR chain is built in the constructor:
 * {@code ValidateMethodHandler → ProcessPaymentHandler → MarkPaidHandler}</p>
 */
public class PaymentService {

    private final List<PaymentTransaction> transactions  = new ArrayList<>();
    private final PolicyManager            policyManager;
    private final NotificationService      notificationService;

    private PaymentHandler    handler;
    private PaymentTransaction lastTransaction;
    private Receipt            lastReceipt;

    public PaymentService(PolicyManager policyManager,
                          NotificationService notificationService) {
        if (policyManager      == null) throw new IllegalArgumentException("policyManager must not be null.");
        if (notificationService== null) throw new IllegalArgumentException("notificationService must not be null.");
        this.policyManager       = policyManager;
        this.notificationService = notificationService;
        buildChain();
    }

    /** Constructs and wires the CoR chain. */
    private void buildChain() {
        ValidateMethodHandler validate = new ValidateMethodHandler();
        ProcessPaymentHandler process  = new ProcessPaymentHandler(this);
        MarkPaidHandler       markPaid = new MarkPaidHandler(this, notificationService);

        validate.setNext(process);
        process.setNext(markPaid);

        this.handler = validate;
    }

    /**
     * Allows replacing the chain head (e.g., in tests).
     */
    public void setHandler(PaymentHandler handler) {
        if (handler == null) throw new IllegalArgumentException("handler must not be null.");
        this.handler = handler;
    }

    /**
     * UC5 – Process a payment.
     *
     * <ol>
     *   <li>Transitions booking CONFIRMED → PENDING_PAYMENT.</li>
     *   <li>Runs the CoR chain (validate → process → markPaid).</li>
     *   <li>Stores the resulting transaction.</li>
     * </ol>
     *
     * @param booking the booking to pay for (must be in CONFIRMED state)
     * @param method  the payment method chosen by the client
     * @return the resulting {@link PaymentTransaction}
     */
    public PaymentTransaction processPayment(Booking booking, PaymentMethod method) {
        if (booking == null) throw new IllegalArgumentException("booking must not be null.");
        if (method  == null) throw new IllegalArgumentException("method must not be null.");

        // Guard: only allow payment from CONFIRMED state.
        // Prevents re-paying a booking that is already PAID, COMPLETED, or CANCELLED,
        // and avoids silently overwriting the state on a double-submit.
        if (!(booking.getState() instanceof ConfirmedState)) {
            throw new IllegalStateException(
                    "Payment can only be processed for a CONFIRMED booking. Current state: "
                    + booking.getStateName());
        }

        // Per spec: Confirmed → PendingPayment BEFORE the handler chain runs.
        System.out.println("[PaymentService] Transitioning booking " + booking.getBookingId()
                + ": CONFIRMED → PENDING_PAYMENT");
        booking.setState(new PendingPaymentState());

        lastTransaction = null;
        lastReceipt     = null;

        PaymentTransaction tx = handler.handle(booking, method);

        // Ensure transaction is in the history list (ProcessPaymentHandler also calls storeTransaction,
        // but if validate short-circuits we still capture it here)
        if (tx != null && !transactions.contains(tx)) {
            transactions.add(tx);
        }

        return tx;
    }

    /**
     * Processes a refund for a previously paid booking.
     * Creates a REFUNDED transaction using the configured {@link com.platform.policy.RefundPolicy}.
     * The booking state is forcibly set to CANCELLED (bypassing the state machine) after refund.
     *
     * @param booking the booking to refund (must have a prior SUCCESS transaction)
     * @return the REFUNDED transaction
     */
    public PaymentTransaction refund(Booking booking) {
        if (booking == null) throw new IllegalArgumentException("booking must not be null.");

        // Find the original successful transaction for this booking
        PaymentTransaction original = transactions.stream()
                .filter(t -> t.getBooking().getBookingId().equals(booking.getBookingId())
                          && t.getStatus() == PaymentStatus.SUCCESS)
                .findFirst()
                .orElse(null);

        double refundAmount = 0.0;
        if (original != null) {
            refundAmount = policyManager.getRefundPolicy()
                    .calculateRefund(original, LocalDateTime.now());
        }

        String txId = "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        PaymentTransaction refundTx = new PaymentTransaction(
                txId, booking, refundAmount, PaymentStatus.REFUNDED, LocalDateTime.now());
        transactions.add(refundTx);

        // Force booking state to CANCELLED (service layer bypasses state machine here)
        booking.setState(new CancelledState());

        notificationService.notify(
                booking.getClient(),
                "Refund processed for booking " + booking.getBookingId()
                + " | Amount: $" + String.format("%.2f", refundAmount)
                + " | txId: " + txId
        );

        System.out.println("[PaymentService] Refund transaction created: " + refundTx);
        return refundTx;
    }

    /**
     * UC7 – Returns all payment transactions for the given client.
     */
    public List<PaymentTransaction> getPaymentHistory(Client client) {
        if (client == null) return new ArrayList<>();
        return transactions.stream()
                .filter(t -> t.getBooking().getClient().getId().equals(client.getId()))
                .collect(Collectors.toList());
    }

    // ── Internal helpers used by CoR handlers ────────────────────────────────

    /** Called by ProcessPaymentHandler to make the tx accessible to MarkPaidHandler. */
    public void storeTransaction(PaymentTransaction tx) {
        if (tx != null && !transactions.contains(tx)) transactions.add(tx);
    }

    public void setLastTransaction(PaymentTransaction tx) { this.lastTransaction = tx; }
    public PaymentTransaction getLastTransaction()        { return lastTransaction; }

    public void setLastReceipt(Receipt r) { this.lastReceipt = r; }
    public Receipt getLastReceipt()       { return lastReceipt; }

    public PolicyManager getPolicyManager()            { return policyManager; }
    public List<PaymentTransaction> getAllTransactions() { return new ArrayList<>(transactions); }
}
