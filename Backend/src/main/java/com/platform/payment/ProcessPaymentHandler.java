package com.platform.payment;

import com.platform.domain.Booking;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

/**
 * Second CoR handler: simulates the payment gateway call.
 *
 * <ul>
 *   <li>Sleeps 2–3 seconds to simulate network latency.</li>
 *   <li>10 % random failure rate (simulates gateway decline).</li>
 *   <li>Creates the {@link PaymentTransaction} and stores it on {@link PaymentService}
 *       so the next handler ({@link MarkPaidHandler}) can access it.</li>
 *   <li>On FAILED, returns immediately without calling the next handler.</li>
 * </ul>
 */
public class ProcessPaymentHandler extends AbstractPaymentHandler {

    private static final double FAILURE_RATE = 0.10;
    private final Random         random       = new Random();
    private final PaymentService paymentService;

    public ProcessPaymentHandler(PaymentService paymentService) {
        if (paymentService == null) throw new IllegalArgumentException("paymentService must not be null.");
        this.paymentService = paymentService;
    }

    @Override
    public PaymentTransaction handle(Booking booking, PaymentMethod method) {
        // Simulate 2–3 second processing delay
        long delayMs = 2000L + random.nextInt(1001);
        System.out.println("[CoR:Process] Processing payment... (simulated delay: " + delayMs + " ms)");
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[CoR:Process] Payment processing interrupted.");
        }

        // Determine outcome
        boolean failed = random.nextDouble() < FAILURE_RATE;
        PaymentStatus status = failed ? PaymentStatus.FAILED : PaymentStatus.SUCCESS;

        String txId = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        double amount = paymentService.getPolicyManager()
                .getPricingStrategy()
                .calculatePrice(booking.getService());

        PaymentTransaction tx = new PaymentTransaction(txId, booking, amount, status, LocalDateTime.now());
        paymentService.storeTransaction(tx);       // make available for MarkPaidHandler
        paymentService.setLastTransaction(tx);

        if (failed) {
            System.out.println("[CoR:Process] Payment FAILED. txId=" + txId);
            return tx;   // Short-circuit: do not call MarkPaidHandler
        }

        System.out.println("[CoR:Process] Payment SUCCESS. txId=" + txId);
        PaymentTransaction result = passToNext(booking, method);
        return (result != null) ? result : tx;
    }
}
