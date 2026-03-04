package payment.cor;

import model.Booking;
import payment.*;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

public class ProcessPaymentHandler extends AbstractPaymentHandler {
    private final PaymentService paymentService;
    private final Random random = new Random();

    public ProcessPaymentHandler(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public void handle(Booking booking, PaymentMethod method) throws Exception {
        // Simulate 2-3 second processing delay
        long delayMs = 2000 + random.nextInt(1000);
        System.out.println("[CoR] Processing payment... (simulating " + delayMs + "ms delay)");
        Thread.sleep(delayMs);

        String transactionId = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        double amount = booking.getService().getPrice();

        // 10% random failure rate
        boolean failed = random.nextDouble() < 0.10;
        PaymentStatus status = failed ? PaymentStatus.FAILED : PaymentStatus.SUCCESS;

        PaymentTransaction tx = new PaymentTransaction(
                transactionId, booking, amount, status,
                method.getMethodType(), LocalDateTime.now());

        paymentService.setLastTransaction(tx);
        paymentService.addTransaction(tx);

        if (failed) {
            System.out.println("[CoR] Payment FAILED. Transaction: " + transactionId);
            throw new RuntimeException("Payment processing failed. Transaction ID: " + transactionId);
        }

        System.out.println("[CoR] Payment SUCCESS. Transaction: " + transactionId);
        handleNext(booking, method);
    }
}
