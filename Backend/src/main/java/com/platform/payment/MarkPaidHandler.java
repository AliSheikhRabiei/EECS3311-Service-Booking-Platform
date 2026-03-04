package com.platform.payment;

import com.platform.domain.Booking;
import com.platform.notify.NotificationService;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Third (final) CoR handler: called only on SUCCESS.
 *
 * <ol>
 *   <li>Calls {@code booking.paymentSuccessful(tx)} → transitions booking to PAID.</li>
 *   <li>Creates and stores a {@link Receipt}.</li>
 *   <li>Sends a payment confirmation notification to the client.</li>
 * </ol>
 */
public class MarkPaidHandler extends AbstractPaymentHandler {

    private final PaymentService     paymentService;
    private final NotificationService notificationService;

    public MarkPaidHandler(PaymentService paymentService,
                            NotificationService notificationService) {
        if (paymentService      == null) throw new IllegalArgumentException("paymentService must not be null.");
        if (notificationService == null) throw new IllegalArgumentException("notificationService must not be null.");
        this.paymentService      = paymentService;
        this.notificationService = notificationService;
    }

    @Override
    public PaymentTransaction handle(Booking booking, PaymentMethod method) {
        PaymentTransaction tx = paymentService.getLastTransaction();
        if (tx == null || tx.getStatus() != PaymentStatus.SUCCESS) {
            System.out.println("[CoR:MarkPaid] No successful transaction to confirm.");
            return tx;
        }

        // Transition booking: PENDING_PAYMENT → PAID
        booking.paymentSuccessful(tx);

        // Create receipt
        String receiptId = "REC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Receipt receipt = new Receipt(receiptId, tx.getAmount(), method.getMethodType(), LocalDateTime.now());
        paymentService.setLastReceipt(receipt);

        // Notify client
        notificationService.notify(
                booking.getClient(),
                "Payment confirmed! Booking " + booking.getBookingId()
                + " | Receipt: " + receiptId
                + " | Amount: $" + String.format("%.2f", tx.getAmount())
        );

        System.out.println("[CoR:MarkPaid] Booking " + booking.getBookingId() + " marked as PAID. " + receipt);
        return tx;
    }
}
