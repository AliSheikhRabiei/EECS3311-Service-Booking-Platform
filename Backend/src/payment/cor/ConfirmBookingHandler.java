package payment.cor;

import model.Booking;
import payment.*;
import service.NotificationService;

import java.time.LocalDateTime;
import java.util.UUID;

public class ConfirmBookingHandler extends AbstractPaymentHandler {
    private final PaymentService     paymentService;
    private final NotificationService notifService;

    public ConfirmBookingHandler(PaymentService paymentService,
                                  NotificationService notifService) {
        this.paymentService = paymentService;
        this.notifService   = notifService;
    }

    @Override
    public void handle(Booking booking, PaymentMethod method) throws Exception {
        PaymentTransaction tx = paymentService.getLastTransaction();
        if (tx == null) {
            throw new IllegalStateException("No transaction found to confirm.");
        }

        // Transition booking to PAID via State pattern
        booking.paymentSuccessful(tx);

        // Create receipt
        String receiptId = "REC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Receipt receipt  = new Receipt(receiptId, tx.getAmount(),
                method.getMethodType(), LocalDateTime.now());
        paymentService.setLastReceipt(receipt);

        notifService.notify(booking.getClient(),
                "Payment confirmed! Receipt: " + receiptId
                + " | Amount: $" + String.format("%.2f", tx.getAmount())
                + " | Booking: " + booking.getBookingId());

        System.out.println("[CoR] Booking confirmed and receipt created: " + receiptId);

        handleNext(booking, method);
    }
}
