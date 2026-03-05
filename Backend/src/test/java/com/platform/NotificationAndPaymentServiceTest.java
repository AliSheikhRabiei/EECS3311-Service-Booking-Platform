package com.platform;

import com.platform.domain.*;
import com.platform.notify.NotificationService;
import com.platform.payment.*;
import com.platform.policy.PolicyManager;
import com.platform.state.PendingPaymentState;
import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests NotificationService (policy integration + output)
 * and PaymentService history/refund/transaction management.
 */
@DisplayName("Notification & PaymentService History Tests")
class NotificationAndPaymentServiceTest {

    private Client              client;
    private Consultant          consultant;
    private Service             service;
    private Booking             booking;
    private NotificationService notificationService;
    private PaymentService      paymentService;

    // Capture System.out to verify notifications are printed
    private ByteArrayOutputStream outCapture;
    private PrintStream           originalOut;

    @BeforeEach
    void setUp() {
        PolicyManager.resetInstance();
        notificationService = new NotificationService();
        paymentService = new PaymentService(PolicyManager.getInstance(), notificationService);

        consultant = new Consultant("C1", "Alice", "alice@test.com", "Expert");
        client  = new Client("U1", "Bob", "bob@test.com");
        service = new Service("S1", "Tutoring", "Desc", 60, 100.0, consultant);
        TimeSlot slot = new TimeSlot(
                LocalDateTime.now().plusDays(1).withHour(10).withMinute(0),
                LocalDateTime.now().plusDays(1).withHour(11).withMinute(0));
        booking = new Booking("BK-1", client, service, slot);
        booking.confirm();
        booking.setState(new PendingPaymentState());

        // Redirect stdout so we can assert on printed output
        outCapture = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outCapture));
    }

    @AfterEach
    void restoreStdOut() {
        System.setOut(originalOut);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NotificationService
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("notify: prints message to console when policy says true")
    void notifyPrintsWhenAllowed() {
        PolicyManager.getInstance().setNotificationPolicy(e -> true);
        notificationService.notify(client, "Test notification for Bob");
        String output = outCapture.toString();
        assertTrue(output.contains("Bob"), "Output must contain the user's name");
        assertTrue(output.contains("Test notification for Bob"), "Output must contain the message");
    }

    @Test @DisplayName("notify: does NOT print when policy says false")
    void notifyDoesNotPrintWhenBlocked() {
        PolicyManager.getInstance().setNotificationPolicy(e -> false);
        notificationService.notify(client, "Should not appear");
        String output = outCapture.toString();
        assertFalse(output.contains("Should not appear"), "Silenced notification must not appear");
    }

    @Test @DisplayName("notify: correct event type inferred from 'confirmed' keyword")
    void notifyInfersConfirmedEvent() {
        // Policy only allows BOOKING_CONFIRMED events
        PolicyManager.getInstance().setNotificationPolicy(
                e -> e.equals("BOOKING_CONFIRMED"));
        notificationService.notify(client, "Your booking has been confirmed");
        String output = outCapture.toString();
        assertTrue(output.contains("confirmed"), "BOOKING_CONFIRMED event should pass");
    }

    @Test @DisplayName("notify: payment event passes through payment-specific policy")
    void notifyInfersPaymentEvent() {
        PolicyManager.getInstance().setNotificationPolicy(
                e -> e.equals("PAYMENT"));
        notificationService.notify(client, "Payment confirmed! receipt: REC-001");
        String output = outCapture.toString();
        assertTrue(output.contains("Payment confirmed"), "PAYMENT event should pass");
    }

    @Test @DisplayName("notify: refund event is correctly inferred")
    void notifyInfersRefundEvent() {
        PolicyManager.getInstance().setNotificationPolicy(
                e -> e.equals("REFUND"));
        notificationService.notify(client, "Refund processed for booking BK-1");
        String output = outCapture.toString();
        assertTrue(output.contains("Refund"), "REFUND event should pass");
    }

    @Test @DisplayName("notify: null user is handled gracefully without throwing")
    void notifyNullUserNoThrow() {
        assertDoesNotThrow(() -> notificationService.notify(null, "Hello"));
    }

    @Test @DisplayName("notify: null message is handled gracefully without throwing")
    void notifyNullMessageNoThrow() {
        assertDoesNotThrow(() -> notificationService.notify(client, null));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PaymentService — transaction storage & history
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("getPaymentHistory: returns empty list for client with no payments")
    void paymentHistoryEmptyForNewClient() {
        assertTrue(paymentService.getPaymentHistory(client).isEmpty());
    }

    @Test @DisplayName("getPaymentHistory: returns only transactions for the given client")
    void paymentHistoryFiltersByClient() {
        Client other = new Client("U2", "Carol", "carol@test.com");
        TimeSlot s2 = new TimeSlot(
                LocalDateTime.now().plusDays(2).withHour(10).withMinute(0),
                LocalDateTime.now().plusDays(2).withHour(11).withMinute(0));
        Booking otherBooking = new Booking("BK-2", other, service, s2);

        PaymentTransaction tx1 = new PaymentTransaction("TX-1", booking,      100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        PaymentTransaction tx2 = new PaymentTransaction("TX-2", otherBooking, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        paymentService.storeTransaction(tx1);
        paymentService.storeTransaction(tx2);

        List<PaymentTransaction> history = paymentService.getPaymentHistory(client);
        assertEquals(1, history.size());
        assertSame(tx1, history.get(0));
    }

    @Test @DisplayName("getPaymentHistory: null client returns empty list")
    void paymentHistoryNullClientEmpty() {
        paymentService.storeTransaction(new PaymentTransaction(
                "TX-1", booking, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now()));
        assertTrue(paymentService.getPaymentHistory(null).isEmpty());
    }

    @Test @DisplayName("storeTransaction: same transaction is not duplicated")
    void storeTransactionNoDuplication() {
        PaymentTransaction tx = new PaymentTransaction(
                "TX-1", booking, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        paymentService.storeTransaction(tx);
        paymentService.storeTransaction(tx); // store same object twice
        assertEquals(1, paymentService.getAllTransactions().size());
    }

    @Test @DisplayName("getAllTransactions: returns all stored transactions")
    void getAllTransactionsReturnsAll() {
        PaymentTransaction tx1 = new PaymentTransaction("TX-1", booking, 100.0, PaymentStatus.SUCCESS,  LocalDateTime.now());
        PaymentTransaction tx2 = new PaymentTransaction("TX-2", booking,  80.0, PaymentStatus.REFUNDED, LocalDateTime.now());
        paymentService.storeTransaction(tx1);
        paymentService.storeTransaction(tx2);
        assertEquals(2, paymentService.getAllTransactions().size());
    }

    @Test @DisplayName("getAllTransactions: returns a defensive copy")
    void getAllTransactionsDefensiveCopy() {
        PaymentTransaction tx = new PaymentTransaction(
                "TX-1", booking, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        paymentService.storeTransaction(tx);
        paymentService.getAllTransactions().clear();
        assertEquals(1, paymentService.getAllTransactions().size());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PaymentService — refund
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("refund: creates REFUNDED transaction with 80% of original amount")
    void refundCreatesRefundedTransaction() {
        // Set up a successful original transaction
        PaymentTransaction originalTx = new PaymentTransaction(
                "TX-ORIG", booking, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        paymentService.storeTransaction(originalTx);
        booking.paymentSuccessful(originalTx);   // booking → PAID

        PaymentTransaction refundTx = paymentService.refund(booking);

        assertNotNull(refundTx);
        assertEquals(PaymentStatus.REFUNDED, refundTx.getStatus());
        assertEquals(80.0, refundTx.getAmount(), 0.001, "Default refund policy is 80%");
    }

    @Test @DisplayName("refund: sets booking state to CANCELLED")
    void refundSetsCancelledState() {
        PaymentTransaction originalTx = new PaymentTransaction(
                "TX-ORIG", booking, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        paymentService.storeTransaction(originalTx);
        booking.paymentSuccessful(originalTx);

        paymentService.refund(booking);

        assertInstanceOf(com.platform.state.CancelledState.class, booking.getState());
    }

    @Test @DisplayName("refund: refund transaction appears in payment history")
    void refundAppearsInHistory() {
        PaymentTransaction originalTx = new PaymentTransaction(
                "TX-ORIG", booking, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        paymentService.storeTransaction(originalTx);
        booking.paymentSuccessful(originalTx);

        paymentService.refund(booking);

        long refunds = paymentService.getPaymentHistory(client).stream()
                .filter(t -> t.getStatus() == PaymentStatus.REFUNDED)
                .count();
        assertEquals(1, refunds);
    }

    @Test @DisplayName("refund: with no prior SUCCESS transaction refunds 0.0")
    void refundNoPriorSuccessReturnsZero() {
        // Booking has no prior SUCCESS tx (never paid)
        PaymentTransaction refundTx = paymentService.refund(booking);
        assertEquals(0.0, refundTx.getAmount(), 0.001);
        assertEquals(PaymentStatus.REFUNDED, refundTx.getStatus());
    }

    @Test @DisplayName("refund: null booking throws")
    void refundNullBookingThrows() {
        assertThrows(IllegalArgumentException.class, () -> paymentService.refund(null));
    }

    @Test @DisplayName("refund: custom refund policy (100% refund)")
    void refundCustomPolicy() {
        PolicyManager.getInstance().setRefundPolicy((tx, now) -> tx.getAmount());
        PaymentTransaction originalTx = new PaymentTransaction(
                "TX-ORIG", booking, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        paymentService.storeTransaction(originalTx);
        booking.paymentSuccessful(originalTx);

        PaymentTransaction refundTx = paymentService.refund(booking);
        assertEquals(100.0, refundTx.getAmount(), 0.001, "Custom 100% refund policy should refund full amount");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Receipt
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("Receipt: getters return correct values")
    void receiptGetters() {
        LocalDateTime now = LocalDateTime.now();
        Receipt r = new Receipt("REC-001", 100.0, "CREDIT_CARD", now);
        assertEquals("REC-001",     r.getReceiptId());
        assertEquals(100.0,          r.getAmount(), 0.001);
        assertEquals("CREDIT_CARD", r.getMethod());
        assertEquals(now,            r.getTimestamp());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PaymentTransaction
    // ══════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("PaymentTransaction: getters return correct values")
    void paymentTransactionGetters() {
        LocalDateTime now = LocalDateTime.now();
        PaymentTransaction tx = new PaymentTransaction(
                "TX-99", booking, 150.0, PaymentStatus.SUCCESS, now);
        assertEquals("TX-99",             tx.getTransactionId());
        assertSame(booking,               tx.getBooking());
        assertEquals(150.0,               tx.getAmount(), 0.001);
        assertEquals(PaymentStatus.SUCCESS, tx.getStatus());
        assertEquals(now,                 tx.getTimestamp());
    }

    @Test @DisplayName("PaymentTransaction: setStatus updates the status")
    void paymentTransactionSetStatus() {
        PaymentTransaction tx = new PaymentTransaction(
                "TX-99", booking, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        tx.setStatus(PaymentStatus.REFUNDED);
        assertEquals(PaymentStatus.REFUNDED, tx.getStatus());
    }

    @Test @DisplayName("PaymentTransaction: toString contains transactionId and status")
    void paymentTransactionToString() {
        PaymentTransaction tx = new PaymentTransaction(
                "TX-TOSTRING", booking, 100.0, PaymentStatus.SUCCESS, LocalDateTime.now());
        String s = tx.toString();
        assertTrue(s.contains("TX-TOSTRING"));
        assertTrue(s.contains("SUCCESS"));
    }
}
